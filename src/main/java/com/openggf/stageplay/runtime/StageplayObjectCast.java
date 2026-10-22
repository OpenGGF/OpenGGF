package com.openggf.stageplay.runtime;

import com.openggf.game.mutators.ObjectHeadRole;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.objects.*;
import com.openggf.level.objects.StageplayObjectDirection.*;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline.LiveObject;

import java.util.*;

import static com.openggf.stageplay.StageplayDocument.*;

/** Shot-local portable object roles and visual intent; native routines remain owned by ObjectManager. */
final class StageplayObjectCast implements AutoCloseable {
    private static final String REWIND_KEY = "stageplay-object-direction";
    private final GameplayModeContext context;
    private final SetSource source;
    private final int start, end;
    private final List<Role> roles = new ArrayList<>();
    private final List<WorldCommand> commands;
    private final Map<PlacementIdentity, ObjectSpawn> placements = new HashMap<>();
    private ObjectManager objects;
    private StageplayObjectDirection direction;
    private ObjectRegistry registry;
    private int commandIndex;
    private final Map<String, Boolean> badnikRoles = new HashMap<>();

    StageplayObjectCast(GameplayModeContext context, StageplayDocument document, SetSource source,
                        int start, int end, List<ObjectSpawn> originalObjects,
                        Map<String, VisualTemplate> templates) {
        this.context = Objects.requireNonNull(context); this.source = source; this.start = start; this.end = end;
        for (var spawn : originalObjects) if (spawn.layoutIndex() >= 0) placements.put(PlacementIdentity.of(source.gameId(), source.zone(), source.act(), spawn), spawn);
        Set<PlacementIdentity> initiallyRemoved = new HashSet<>();
        for (var actor : document.objectActors()) {
            ObjectKey anchor = actor.keys().stream().filter(k -> k.frame() == start).findFirst().orElseThrow();
            if (actor.layoutIndex() >= 0 && sameSource(actor, source) && !anchor.visible())
                initiallyRemoved.add(identity(actor));
            if (actor.keys().stream().noneMatch(k -> k.frame() >= start && k.frame() < end && k.visible())) continue;
            if (actor.layoutIndex() >= 0 && !placements.containsKey(identity(actor)))
                throw new IllegalArgumentException("Unknown portable placed object for " + actor.id() + ": " + identity(actor));
            for (var key : actor.keys()) if (key.frame() >= start && key.frame() < end && key.visible() && key.mode() == ObjectMode.FREE)
                requireNativeContext(actor, source);
            roles.add(new Role(actor, anchor, templates.get(actor.id()), start));
        }
        commands = document.worldCommands();
        while (commandIndex < commands.size() && commands.get(commandIndex).frame() < start) commandIndex++;
        bindManager();
        initiallyRemoved.forEach(direction::remove);
        // Registered after native object snapshots: pointer identities have been restored
        // before suspended graphs and controller-only visual handles are reconstructed.
        context.getRewindRegistry().register(new RewindSnapshottable<StageplayObjectDirection.Snapshot>() {
            public String key() { return REWIND_KEY; }
            public StageplayObjectDirection.Snapshot capture() { ensureManager(); return direction.rewindSnapshottable().capture(); }
            public void restore(StageplayObjectDirection.Snapshot saved) {
                if (objects != context.getLevelManager().getObjectManager()) bindManager();
                direction.rewindSnapshottable().restore(saved);
            }
            public void resetForMissingSnapshot() { direction.close(); }
        });
    }

    private void bindManager() {
        objects = context.getLevelManager().getObjectManager();
        direction = ObjectDirectionAccess.forManager(objects);
        registry = ObjectDirectionAccess.registry(objects);
    }

    private void requireCurrentSource() {
        var level = context.getLevelManager();
        var game = context.getWorldSession().getGameModule().getGameId().code();
        if (!source.gameId().equals(game) || source.zone() != level.getCurrentZone() || source.act() != level.getCurrentAct())
            throw new IllegalStateException("Stageplay shot source changed from " + source.gameId() + "/"
                    + source.zone() + "/" + source.act() + "; use a new shot for the destination set");
    }

    private void ensureManager() {
        requireCurrentSource();
        if (objects == context.getLevelManager().getObjectManager()) return;
        requireCurrentSource();
        direction.close(); bindManager();
        for (var role : roles) {
            role.root = null; role.synthetic = false; role.goal = null;
            // Reassemble surviving authored roles at their native shot anchors. Explicit
            // removal remains latched until an authored visibility re-entry.
            role.instantiated = false;
        }
    }

    void beforeTick(int frame) {
        ensureManager();
        while (commandIndex < commands.size() && commands.get(commandIndex).frame() <= frame) {
            var command = commands.get(commandIndex++);
            if (command.frame() >= start && command.frame() < end) apply(command);
        }
        for (var role : roles) update(role, frame);
    }

    void afterTick() { ensureManager(); }

    private void update(Role role, int frame) {
        var keys = role.actor.keys();
        int oldIndex = role.keyIndex;
        while (role.keyIndex + 1 < keys.size() && keys.get(role.keyIndex + 1).frame() <= frame) role.keyIndex++;
        ObjectKey key = keys.get(role.keyIndex);
        boolean changed = oldIndex != role.keyIndex || role.mode == null;
        boolean entering = key.visible() && !role.visible;
        boolean modeChanged = role.mode != key.mode();
        if (changed) {
            ObjectKey previous = oldIndex >= 0 ? keys.get(oldIndex) : null;
            if (previous == null || modeChanged || previous.animation() != key.animation()
                    || previous.pose() != key.pose() || !previous.parts().equals(key.parts())) role.phaseStart = key.frame();
            role.x = key.x(); role.y = key.y();
            role.stopped = false;
            if (modeChanged) role.goal = null;
        }
        if (entering && role.removalFrame != frame) { role.removed = false; role.instantiated = false; }
        if (!key.visible()) {
            if (role.root != null) retire(role);
            else if (changed && role.actor.layoutIndex() >= 0 && sameSource(role.actor, source)) {
                var identity = identity(role.actor); direction.remove(identity);
            }
            role.visible = false; role.mode = key.mode(); role.goal = null;
            while (role.commandIndex < role.actor.commands().size() && role.actor.commands().get(role.commandIndex).frame() <= frame)
                role.commandIndex++;
            return;
        }
        if (role.removed) { role.visible = true; role.mode = key.mode(); return; }
        if (role.root == null && !role.instantiated) create(role, key);
        else if (role.root != null && modeChanged) changeMode(role, key);
        role.visible = true; role.mode = key.mode();
        while (role.commandIndex < role.actor.commands().size() && role.actor.commands().get(role.commandIndex).frame() <= frame) {
            ObjectCommand command = role.actor.commands().get(role.commandIndex++);
            if (command.frame() >= start && key.mode() == ObjectMode.PUPPET) {
                if (command.kind() == ObjectCommandKind.MOVE_TO) { role.goal = command; role.stopped = false; }
                else { role.goal = null; role.stopped = true; }
            }
        }
        if (role.root == null || key.mode() == ObjectMode.FREE) return;
        if (key.mode() == ObjectMode.PUPPET && role.goal != null) {
            var point = move(role.x, role.y, role.goal.x(), role.goal.y(), role.goal.speed());
            role.x = point[0]; role.y = point[1];
        } else if (key.mode() != ObjectMode.PUPPET || !role.stopped) {
            ObjectKey next = role.keyIndex + 1 < keys.size() ? keys.get(role.keyIndex + 1) : key;
            double weight = next.frame() >= end ? 0 : weight(key.interpolation(), key.frame(), next.frame(), frame);
            role.x = key.x() + (next.x() - key.x()) * weight;
            role.y = key.y() + (next.y() - key.y()) * weight;
        }
        direction.setDirection(role.root, visual(role, key, frame));
    }

    private void create(Role role, ObjectKey key) {
        if (key.mode() == ObjectMode.FREE) {
            requireNativeContext(role.actor, source);
            if (role.actor.layoutIndex() >= 0)
                role.root = direction.materializePlaced(placements.get(identity(role.actor)));
            else role.root = StageplayObjectTemplateBake.create(objects, registry, role.actor, role.anchor);
            if (role.root == null || role.root instanceof PlaceholderObjectInstance)
                throw new IllegalArgumentException("No concrete native object factory/slot for " + role.actor.id());
            direction.claimAuthored(role.actor.id(), role.root);
            role.synthetic = false;
        } else {
            if (role.actor.layoutIndex() >= 0) {
                var identity = identity(role.actor);
                var existing = direction.findPlaced(identity).orElse(null);
                if (existing != null) {
                    role.root = existing; role.synthetic = false;
                    direction.claimAuthored(role.actor.id(), existing);
                    role.template = capture(role);
                    direction.direct(existing, role.template, visual(role, key, key.frame()));
                    role.instantiated = true;
                    return;
                }
                // Native loading is suppressed for this portable tuple. Directed admission
                // consumes only a privately prepared picture; no shared factory is invoked.
                direction.remove(identity);
            }
            if (role.template == null) throw new IllegalArgumentException("No prepared native visual for " + role.actor.id()
                    + "; start as Free in its native set, then switch once appearance initializes");
            role.root = direction.installVisual(role.actor.id(), role.template, visual(role, key, key.frame()));
            role.synthetic = true;
        }
        role.instantiated = true;
    }

    private void changeMode(Role role, ObjectKey key) {
        if (key.mode() == ObjectMode.FREE) {
            requireNativeContext(role.actor, source);
            if (role.synthetic) { retire(role); role.instantiated = false; create(role, key); }
            else direction.releaseToFree(role.root);
            role.template = null;
        } else if (!direction.suspended(role.root)) {
            role.template = capture(role);
            direction.direct(role.root, role.template, visual(role, key, key.frame()));
        }
    }

    private VisualTemplate capture(Role role) {
        try {
            // Logic-only worlds do not draw/publish every level palette. Capture the current
            // authoritative ROM owners here, after native boss/event writes and before freezing
            // the appearance; scene-entry publication alone would retain an older palette phase.
            context.getLevelManager().reloadLevelPalettes();
            return direction.captureDynamic(role.root);
        }
        catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot direct " + role.actor.id() + ": " + failure.getMessage()
                    + "; start as Free in its native set, then switch once appearance initializes", failure);
        }
    }

    private Direction visual(Role role, ObjectKey key, int frame) {
        Map<String, PartSelection> parts = new LinkedHashMap<>();
        for (var part : key.parts()) parts.put(part.partId(), new PartSelection(part.animation(), part.pose()));
        return new Direction(role.x, role.y, key.flipX(), key.visible(), key.animation(), key.pose(), parts,
                Math.max(0, frame - role.phaseStart));
    }

    private void apply(WorldCommand command) {
        if (command.kind() == WorldCommandKind.CLEAR_OBJECTS) {
            direction.clearObjects();
            for (var role : roles) {
                role.root = null; role.synthetic = false; role.removed = true; role.goal = null;
            }
        } else if (command.kind() == WorldCommandKind.CLEAR_BADNIKS) {
            for (var role : roles) if (isBadnik(role)) removeRole(role);
            direction.clearBadniks();
        } else if (!command.actorId().isEmpty()) {
            roles.stream().filter(role -> role.actor.id().equals(command.actorId())).findFirst().ifPresent(role -> {
                removeRole(role); role.removalFrame = command.frame();
            });
        } else {
            var target = command.target();
            var identity = new PlacementIdentity(source.gameId(), source.zone(), source.act(), target.layoutIndex(), target.objectId(), target.subtype());
            if (!placements.containsKey(identity)) throw new IllegalArgumentException("Unknown removal placement for " + command.id() + ": " + identity);
            direction.remove(identity);
            for (var role : roles) if (role.actor.layoutIndex() >= 0 && identity(role.actor).equals(identity)) {
                removeRole(role); role.removalFrame = command.frame();
            }
        }
    }

    private boolean isBadnik(Role role) {
        if (role.root != null && !role.synthetic && (role.root instanceof AbstractBadnikInstance
                || role.root.headPresentationRole() == ObjectHeadRole.BADNIK)) return true;
        if (role.actor.sourceKind() != ObjectSourceKind.LAYOUT_OBJECT) return false;
        return badnikRoles.computeIfAbsent(role.actor.id(), ignored ->
                com.openggf.stageplay.StageplayObjectCatalog.entry(role.actor.gameId(), role.actor.zone(), role.actor.act(),
                        role.actor.objectId()).role() == ObjectRegistryCatalog.Role.BADNIK);
    }

    private void removeRole(Role role) { retire(role); role.removed = true; role.goal = null; }

    private void retire(Role role) {
        if (role.root != null) direction.removeDynamic(role.root);
        if (role.actor.layoutIndex() >= 0 && sameSource(role.actor, source)) {
            var identity = identity(role.actor); direction.remove(identity);
        }
        role.root = null; role.synthetic = false;
    }

    LiveObject observation(ObjectInstance object) {
        for (var role : roles) if (role.root == object && role.mode != ObjectMode.FREE)
            return observation(role);
        var spawn = object.getSpawn();
        return new LiveObject(spawn.layoutIndex(), spawn.objectId(), spawn.subtype(), object.getX(), object.getY(), object.getClass().getSimpleName());
    }

    List<LiveObject> visualObservations() {
        return roles.stream().filter(role -> role.synthetic && role.root != null && role.visible && !role.removed)
                .map(StageplayObjectCast::observation).toList();
    }

    boolean allowsPlacement(ObjectSpawn spawn) { return direction.allowsPlacement(spawn); }

    List<StageplayObjectInspection> inspection() {
        return roles.stream().map(role -> {
            if (role.template == null)
                return new StageplayObjectInspection(role.actor.id(), role.actor.name(), List.of(),
                        List.of("Native Free graph has no cached visual template; switch after its appearance initializes to inspect its directed parts"));
            var parts = role.template.parts().stream().map(part -> new StageplayObjectInspection.Part(
                    part.partId(), part.defaultPose(), part.poses().stream().map(VisualPose::pose).toList(),
                    part.animations().stream().map(VisualAnimation::id).toList())).toList();
            return new StageplayObjectInspection(role.actor.id(), role.actor.name(), parts, role.template.limitations());
        }).toList();
    }

    private static LiveObject observation(Role role) {
        return new LiveObject(role.actor.layoutIndex(), role.actor.objectId(), role.actor.subtype(),
                (int) Math.round(role.x), (int) Math.round(role.y), "Stageplay:" + role.actor.id() + ":" + role.mode);
    }

    Snapshot capture() {
        ensureManager();
        return new Snapshot(commandIndex, roles.stream().map(role ->
                new RoleSnapshot(role.actor.id(), role.synthetic, role.root == null || role.synthetic ? null
                        : objects.objectQuery().identityOf(role.root).orElse(null), role.keyIndex, role.commandIndex,
                        role.phaseStart, role.removalFrame, role.visible, role.instantiated, role.removed, role.stopped, role.mode, role.x, role.y, role.goal, role.template)).toList());
    }

    void restore(Snapshot saved) {
        if (objects != context.getLevelManager().getObjectManager()) bindManager();
        commandIndex = saved.commandIndex();
        for (int i = 0; i < roles.size(); i++) {
            var role = roles.get(i); var state = saved.roles().get(i);
            if (!role.actor.id().equals(state.id())) throw new IllegalStateException("Object control snapshot belongs to another cast");
            role.synthetic = state.synthetic();
            role.root = role.synthetic ? direction.findVisual(role.actor.id()).orElseThrow()
                    : state.root() == null ? null : objects.objectQuery().resolve(state.root()).orElse(null);
            role.keyIndex = state.keyIndex(); role.commandIndex = state.commandIndex(); role.phaseStart = state.phaseStart();
            role.removalFrame = state.removalFrame();
            role.visible = state.visible(); role.instantiated = state.instantiated(); role.removed = state.removed();
            role.stopped = state.stopped();
            role.mode = state.mode(); role.x = state.x(); role.y = state.y(); role.goal = state.goal(); role.template = state.template();
        }
    }

    record Snapshot(int commandIndex, List<RoleSnapshot> roles) { }
    private record RoleSnapshot(String id, boolean synthetic, ObjectRefId root, int keyIndex, int commandIndex,
                                int phaseStart, int removalFrame, boolean visible, boolean instantiated, boolean removed, boolean stopped, ObjectMode mode,
                                double x, double y, ObjectCommand goal, VisualTemplate template) { }

    static void requireNativeContext(ObjectActor actor, SetSource source) {
        if (!sameSource(actor, source)) throw new IllegalArgumentException("Free object " + actor.id()
                + " requires its native source set " + actor.gameId() + "/" + actor.zone() + "/" + actor.act()
                + "; choose that set or a prepared Puppet/Pose visual");
    }
    private static boolean sameSource(ObjectActor actor, SetSource source) {
        return actor.gameId().equals(source.gameId()) && actor.zone() == source.zone() && actor.act() == source.act();
    }
    private static PlacementIdentity identity(ObjectActor actor) { return new PlacementIdentity(actor.gameId(), actor.zone(), actor.act(), actor.layoutIndex(), actor.objectId(), actor.subtype()); }

    static double[] move(double x, double y, double targetX, double targetY, double speed) {
        return StageplayObjectMotion.move(x, y, targetX, targetY, speed);
    }

    com.openggf.stageplay.route.StageplayRoutePoint routePosition(String actorId, int frame) {
        for (var role : roles) if (role.actor.id().equals(actorId)
                && role.visible && !role.removed && role.instantiated && role.mode == ObjectMode.PUPPET)
            return new com.openggf.stageplay.route.StageplayRoutePoint(frame, role.x, role.y, true);
        return null;
    }

    List<com.openggf.level.objects.StageplayActivationPolicy.Window> activationWindows(int width, int height) {
        var windows = new ArrayList<com.openggf.level.objects.StageplayActivationPolicy.Window>();
        for (var role : roles) if (role.visible && !role.removed && role.instantiated) {
            double x = role.root != null && !role.synthetic ? role.root.getX() : role.x;
            double y = role.root != null && !role.synthetic ? role.root.getY() : role.y;
            windows.add(new com.openggf.level.objects.StageplayActivationPolicy.Window(
                    (int) Math.round(x) - width / 2, (int) Math.round(y) - height / 2, width, height));
        }
        return windows;
    }
    private static double weight(Interpolation interpolation, int first, int last, int frame) {
        if (first == last || interpolation == Interpolation.HOLD) return 0;
        double t = Math.clamp((double) (frame - first) / (last - first), 0, 1);
        return interpolation == Interpolation.SMOOTH ? t * t * (3 - 2 * t) : t;
    }

    private static final class Role {
        final ObjectActor actor; final ObjectKey anchor;
        ObjectInstance root; VisualTemplate template; ObjectCommand goal;
        int keyIndex, commandIndex, phaseStart, removalFrame = -1; double x, y;
        // Authored visibility/removal latch, distinct from the session policy's
        // sole native placement-admission state. Re-entry in the timeline clears it.
        boolean visible, instantiated, synthetic, removed, stopped; ObjectMode mode;
        Role(ObjectActor actor, ObjectKey anchor, VisualTemplate template, int start) {
            this.actor = actor; this.anchor = anchor; this.template = template;
            while (keyIndex < actor.keys().size() && actor.keys().get(keyIndex).frame() < start) keyIndex++;
            while (commandIndex < actor.commands().size() && actor.commands().get(commandIndex).frame() < start) commandIndex++;
            x = anchor.x(); y = anchor.y(); phaseStart = start;
        }
    }

    @Override public void close() {
        context.getRewindRegistry().deregister(REWIND_KEY);
        direction.close();
    }
}

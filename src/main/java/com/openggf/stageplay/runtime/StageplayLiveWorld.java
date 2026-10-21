package com.openggf.stageplay.runtime;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.game.*;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.*;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.StageplayTimeline.*;

/**
 * One production world owned by an OFFLINE Stageplay process. Never opens a second world
 * over an interactive session. LevelFrameStep owns all native update ordering; directing
 * only publishes per-performer controller bytes and observes the resulting state.
 * Origin: Stageplay live-world plan, 2026-10-21.
 */
public final class StageplayLiveWorld implements AutoCloseable {
    private final SceneRomLibrary library;
    private GameplayModeContext owned;
    private StageplayDocument document;
    private SetSource source;
    private int start = -1, cursor = -1;
    private List<Performer> cast = List.of();
    private final TreeMap<Integer, Saved> checkpoints = new TreeMap<>();
    private CameraPose director;
    private Frame last;
    private final List<LiveEvent> events = new ArrayList<>();
    private boolean closed;

    /** Logic-only host: fail before touching services if a gameplay/editor session is active. */
    public static StageplayLiveWorld openOffline(SceneRomLibrary library) {
        requireUnoccupied();
        var services = EngineServices.current();
        services.graphics().initHeadless();
        services.audio().setBackend(new NullAudioBackend());
        return new StageplayLiveWorld(library, null);
    }

    /** A capture host explicitly transfers its own disposable session, never the user's session. */
    public static StageplayLiveWorld adoptOffline(SceneRomLibrary library, GameplayModeContext owned) {
        if (owned == null || SessionManager.getCurrentGameplayMode() != owned)
            throw new IllegalArgumentException("The offline host must transfer its exact current session");
        return new StageplayLiveWorld(library, owned);
    }

    private static void requireUnoccupied() {
        if (SessionManager.getCurrentWorldSession() != null)
            throw new IllegalStateException("LIVE Stageplay requires an isolated offline process; an active world must not be replaced");
    }
    private StageplayLiveWorld(SceneRomLibrary library, GameplayModeContext owned) {
        this.library = Objects.requireNonNull(library); this.owned = owned;
    }

    public Frame resolve(StageplayDocument document, Frame authored) {
        if (closed) throw new IllegalStateException("Live world is closed");
        int first = 0; SetSource set = document.set();
        for (var change : document.sceneChanges()) if (change.frame() <= authored.frame()) {
            first = change.frame(); set = change.set();
        }
        if (set.worldPolicy() != WorldPolicy.LIVE) throw new IllegalArgumentException("Not a LIVE shot");
        if (this.document != document && !document.equals(this.document) || start != first) begin(document, set, first);
        if (authored.frame() == cursor) return last;
        boolean replay = authored.frame() < cursor || authored.frame() > cursor + 1;
        var checkpoint = checkpoints.floorEntry(authored.frame());
        if (authored.frame() < cursor) {
            restore(checkpoint.getValue()); cursor = checkpoint.getKey();
            last = cursor < start ? null : sample(StageplayTimeline.sample(document, cursor));
        }
        try (var silence = replay ? EngineServices.current().audio().beginRewindReplay(cursor, authored.frame(),
                com.openggf.audio.rewind.AudioReplayReason.SEEK) : null) {
        while (cursor < authored.frame()) {
            int frame = ++cursor;
            tick(frame);
            last = sample(StageplayTimeline.sample(document, frame));
            if ((frame - start + 1) % 120 == 0) {
                checkpoints.put(frame, capture());
                while (checkpoints.size() > 16) checkpoints.remove(checkpoints.higherKey(start - 1));
            }
        }
        }
        return last;
    }

    private void begin(StageplayDocument document, SetSource set, int first) {
        if (owned != null && SessionManager.getCurrentGameplayMode() != owned)
            throw new IllegalStateException("Offline Stageplay lost its session ownership");
        if (owned == null) requireUnoccupied();
        int end = document.sceneChanges().stream().filter(c -> c.frame() > first)
                .mapToInt(SceneChange::frame).min().orElse(document.durationFrames());
        var performers = new ArrayList<Performer>();
        for (var track : document.puppets()) {
            var actor = document.actors().stream().filter(a -> a.id().equals(track.actorId())).findFirst().orElseThrow();
            if (actor.keys().stream().noneMatch(k -> k.frame() >= first && k.frame() < end && k.visible())) continue;
            if (!actor.gameId().equals(set.gameId()))
                throw new IllegalArgumentException("Live performer " + actor.id() + " requires native donor " + set.gameId());
            var anchor = actor.keys().stream().filter(k -> k.frame() == first).findFirst().orElseThrow();
            performers.add(new Performer(actor, track, anchor));
        }
        if (performers.isEmpty()) throw new IllegalArgumentException("LIVE shot needs at least one Puppet");
        var rom = Objects.requireNonNull(library.sourceRom(set.gameId()), "Supply the live world's ROM");
        if (!set.romSha1().isEmpty() && !set.romSha1().equalsIgnoreCase(library.rom(set.gameId()).romSha1()))
            throw new IllegalArgumentException("Live world ROM identity mismatch");
        var services = EngineServices.current();
        var config = services.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, performers.getFirst().actor.character());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                String.join(",", performers.stream().skip(1).map(p -> p.actor.character()).toList()));
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, document.width());
        closeOwned();
        // Match the production game-entry boundary: sidekick/donor palette banks and CRAM
        // belong to the retired world. Keep the initialized GL atlas/tile renderers alive.
        com.openggf.graphics.RenderContext.reset();
        services.graphics().clearPaletteTextures();
        try {
            services.roms().setRom(rom);
            var module = GameModuleRegistry.createStockModule(GameId.fromCode(set.gameId()));
            owned = SessionManager.openGameplaySession(module, module, StockGameDataSources.pinned(rom, module), null);
            GameplaySessionFactory.attachManagers(owned, services);
            services.graphics().bindRuntimeManagedReferences(owned.getCamera(), owned.getFadeManager());
            GameModuleRegistry.setCurrent(module);
            var team = GameplayTeamBootstrap.registerActiveTeam(module, owned.getSpriteManager(), config);
            var nativeCast = new ArrayList<AbstractPlayableSprite>(); nativeCast.add(team.mainSprite()); nativeCast.addAll(team.sidekicks());
            if (nativeCast.size() != performers.size()) throw new IllegalArgumentException("Native module cannot register the requested cast");
            owned.getLevelManager().loadZoneAndAct(set.zone(), set.act());
            owned.getLevelManager().skipPendingInitialTitleCardPresentation();
            // Each authored shot is a fresh world, independent of the host's prior seek order.
            owned.getLevelManager().getObjectManager().initVblaCounter(0);
            // Retire the one native assembly scan before authoring initial positions. It is not a story tick.
            LevelFrameStep.admit(LevelFrameContext.from(owned), owned.getLevelManager(), false);
            for (int i = 0; i < performers.size(); i++) {
                var performer = performers.get(i); var sprite = nativeCast.get(i); performer.sprite = sprite;
                sprite.setCpuController(null); // each participant has its own scripted controller, never follow AI
                NativePositionOps.writeXPosResetSubpixel(sprite, nativePixel(performer.anchor.x(), true));
                NativePositionOps.writeYPosResetSubpixel(sprite, nativePixel(performer.anchor.y(), false));
                sprite.setDirection(performer.anchor.flipX() ? Direction.LEFT : Direction.RIGHT);
                sprite.setTopSolidBit((byte) (performer.track.collisionPath() == 0 ? 0x0C : 0x0E));
                sprite.setLrbSolidBit((byte) (performer.track.collisionPath() == 0 ? 0x0D : 0x0F));
                sprite.setAir(true);
                if (performer.track.snapToFloor()) owned.getCollisionSystem().resolveGroundAttachment(sprite, 14, () -> false);
                while (performer.index < performer.track.commands().size()
                        && performer.track.commands().get(performer.index).frame() < first) performer.index++;
                performer.lastProgress = first;
            }
            owned.getCamera().setFocusedSprite(nativeCast.getFirst());
            com.openggf.level.LevelCameraInitialization.recenterPositionedEntry(owned.getLevelManager());
            this.document = document; source = set; start = first; cursor = first - 1;
            cast = List.copyOf(performers); checkpoints.clear(); last = null;
            director = StageplayTimeline.sample(document, first).baseCamera();
            validateTargets();
            checkpoints.put(cursor, capture());
        } catch (IOException error) { closeOwned(); throw new UncheckedIOException("Cannot load live Stageplay world", error); }
        catch (RuntimeException | Error failure) { closeOwned(); throw failure; }
    }

    private static int nativePixel(double value, boolean horizontal) {
        long rounded = Math.round(value);
        if (rounded < (horizontal ? 0 : Short.MIN_VALUE) || rounded > Short.MAX_VALUE)
            throw new IllegalArgumentException("Live spawn exceeds native coordinates");
        return (int) rounded;
    }

    private void validateTargets() {
        int end = document.sceneChanges().stream().filter(c -> c.frame() > start)
                .mapToInt(SceneChange::frame).min().orElse(document.durationFrames());
        for (var p : cast) for (var command : p.track.commands()) {
            if (command.frame() < start || command.frame() >= end) continue;
            var target = command.target(); if (target.layoutIndex() == -1) continue;
            boolean found = owned.getLevelManager().getCurrentLevel().getObjects().stream().anyMatch(o ->
                    o.layoutIndex() == target.layoutIndex() && o.objectId() == target.objectId() && o.subtype() == target.subtype());
            if (!found) throw new IllegalArgumentException("Unknown placed object for " + command.id() + ": " + target);
        }
    }

    private void tick(int frame) {
        events.clear();
        var audio = EngineServices.current().audio();
        audio.beginGameplayAudioFrame(audio.commandTimeline().currentFrame() + 1);
        for (var p : cast) {
            int x = p.sprite.getCentreX(), cameraX = owned.getCamera().getX();
            if (x < cameraX - 128 || x > cameraX + owned.getCamera().getWidth() + 128)
                throw new IllegalStateException("Live cast left the native activation window: " + p.actor.id()
                        + "; author a closer route or a shot boundary");
        }
        var inputs = new LinkedHashMap<String, PlayerInputState>();
        for (var p : cast) inputs.put(p.sprite.getCode(), input(p, frame));
        var context = LevelFrameContext.from(owned);
        EngineTiming.vIntRunCounter().serviceRepresentedVBlank();
        owned.getSpriteManager().withScriptedInputs(inputs, handler ->
                owned.plcFrameLifecycle().runLogicalIteration(owned.getFadeManager()::update, token -> {
                    LevelFrameStep.updateTimers(context);
                    return LevelFrameStep.execute(context, token, PlcLifecyclePhase.ORDINARY_LEVEL,
                            owned.getLevelManager(), owned.getCamera(), () -> owned.getSpriteManager().update(handler),
                            LevelFrameStep.DIRECT_WRAPPER);
                }));
        for (var p : cast) observe(p, frame);
        follow(StageplayTimeline.sample(document, frame));
    }

    private PlayerInputState input(Performer p, int frame) {
        if (p.wait != null && waitSatisfied(p)) { event(p, frame, "WAIT_SATISFIED", p.wait.condition().name()); p.wait = null; p.status = PuppetStatus.SATISFIED; }
        if (p.wait != null && frame >= p.deadline) { event(p, frame, "WAIT_TIMED_OUT", p.wait.condition().name()); p.wait = null; p.status = PuppetStatus.TIMED_OUT; }
        var commands = p.track.commands();
        while (p.wait == null && p.index < commands.size() && commands.get(p.index).frame() <= frame) {
            var command = commands.get(p.index++);
            p.command = command;
            if (command.kind() == PuppetCommandKind.JUMP) p.jumpEnd = frame + command.holdFrames();
            else if (command.kind() == PuppetCommandKind.WAIT) {
                p.wait = command; p.deadline = frame + command.timeoutFrames(); p.waitRings = p.sprite.getRingCount();
                p.status = PuppetStatus.WAITING;
            } else {
                p.goal = command; p.goalStart = frame; p.best = Double.POSITIVE_INFINITY; p.lastProgress = frame;
                p.status = PuppetStatus.IDLE;
                if (command.kind() == PuppetCommandKind.RELEASE) p.jumpEnd = frame;
                p.hoverY = p.sprite.getCentreY();
            }
        }
        int held = 0;
        var goal = p.goal;
        boolean jump = frame < p.jumpEnd;
        if (goal != null) {
            var kind = goal.kind();
            boolean move = kind == PuppetCommandKind.MOVE_TO || kind == PuppetCommandKind.FLY_TO || kind == PuppetCommandKind.GLIDE_TO;
            double[] target = move ? target(p, goal) : new double[]{p.sprite.getCentreX(),p.sprite.getCentreY()};
            int direction = steer(p.sprite, move ? target[0] : p.sprite.getCentreX(), move ? goal.urgency() : 0);
            if (direction < 0) held |= AbstractPlayableSprite.INPUT_LEFT;
            if (direction > 0) held |= AbstractPlayableSprite.INPUT_RIGHT;
            if (kind == PuppetCommandKind.CROUCH || kind == PuppetCommandKind.ROLL) held |= AbstractPlayableSprite.INPUT_DOWN;
            if (kind == PuppetCommandKind.LOOK_UP) held |= AbstractPlayableSprite.INPUT_UP;
            if (kind == PuppetCommandKind.FLY_TO || kind == PuppetCommandKind.HOVER) {
                // Native second jump edge starts flight. Subsequent flaps are ordinary button edges.
                int age = frame - p.goalStart;
                double targetY = kind == PuppetCommandKind.HOVER ? p.hoverY : target[1];
                jump |= !p.sprite.getAir() ? age < 12 : age >= 14 && age % 12 == 2
                        && (p.sprite.getDoubleJumpFlag() == 0 || p.sprite.getCentreY() > targetY && p.sprite.getYSpeed() >= 0);
            } else if (kind == PuppetCommandKind.GLIDE_TO) {
                int age = frame - p.goalStart;
                jump |= age < 12 || age >= 14;
            }
            if (kind == PuppetCommandKind.HOVER || kind == PuppetCommandKind.CROUCH || kind == PuppetCommandKind.LOOK_UP) {
                if (frame - p.goalStart >= goal.holdFrames()) { p.goal = null; jump = false; held = 0; }
            }
            if (goal.timeoutFrames() > 0 && frame - p.goalStart >= goal.timeoutFrames()) {
                event(p, frame, "INTENT_TIMED_OUT", goal.kind().name()); p.goal = null; p.status = PuppetStatus.TIMED_OUT; held = 0; jump = false;
            }
        }
        if (jump) held |= AbstractPlayableSprite.INPUT_JUMP;
        int pressed = held & ~p.previousHeld; p.previousHeld = held;
        return PlayerInputState.of(held, pressed, jump ? InputActionMasks.ACTION_C : 0,
                (pressed & AbstractPlayableSprite.INPUT_JUMP) != 0 ? InputActionMasks.ACTION_C : 0, false, false);
    }

    private static int steer(AbstractPlayableSprite sprite, double targetX, double urgency) {
        double speed = sprite.getXSpeed() / 256.0;
        // Sonic_ChgJumpDir uses twice runAccel, unlike the ground countersteer deceleration.
        double brake = Math.max(1.0 / 256, (sprite.getAir() ? 2 * sprite.getRunAccel() : sprite.getRunDecel()) / 256.0);
        double dx = targetX - sprite.getCentreX();
        if (urgency == 0 || Math.abs(dx) <= 4 || Math.abs(dx) <= speed * speed / (2 * brake) + Math.abs(speed) + 4) {
            return Math.abs(speed) <= Math.max(.25, brake) ? 0 : speed < 0 ? 1 : -1;
        }
        int toward = dx < 0 ? -1 : 1;
        return speed * toward < 0 || Math.abs(speed) < sprite.getMax() / 256.0 * urgency ? toward : 0;
    }

    private double[] target(Performer p, PuppetCommand command) {
        var target = command.target();
        if (target.layoutIndex() == -1) { p.targetStatus = "COORDINATE"; return new double[]{command.x(), command.y()}; }
        var placed = resolveObject(p,target);
        if (placed == null) return new double[]{p.sprite.getCentreX(), p.sprite.getCentreY()};
        return new double[]{placed.getX() + target.offsetX(), placed.getY() + target.offsetY()};
    }
    private ObjectInstance resolveObject(Performer p, ObjectTarget target) {
        var value = object(target);
        if (value != null) { p.seenTargets.add(target.layoutIndex()); p.targetStatus="ACTIVE"; }
        else p.targetStatus = p.seenTargets.contains(target.layoutIndex()) ? "DESPAWNED" : "NOT_ACTIVE";
        return value;
    }
    private ObjectInstance object(ObjectTarget target) {
        var level=owned.getLevelManager();
        var spawn=level.getCurrentLevel().getObjects().stream().filter(o -> o.layoutIndex()==target.layoutIndex()
                && o.objectId()==target.objectId() && o.subtype()==target.subtype()).findFirst().orElse(null);
        // Query the placement owner itself, not a dynamic child that copied its parent's spawn record.
        var value=spawn==null ? null : level.getObjectManager().getActiveObjectForRewind(spawn);
        return value==null || value.isDestroyed() ? null : value;
    }
    private boolean waitSatisfied(Performer p) {
        var sprite = p.sprite;
        var expected = p.wait.target().layoutIndex() < 0 ? null : resolveObject(p,p.wait.target());
        return switch (p.wait.condition()) {
            case NONE -> false;
            case ARRIVED -> p.arrived;
            case ON_OBJECT -> sprite.isOnObject() && (p.wait.target().layoutIndex() == -1
                    || expected != null && owned.getLevelManager().getObjectManager().getRidingObject(sprite) == expected);
            case GROUNDED -> !sprite.getAir();
            case AIRBORNE -> sprite.getAir();
            case HURT -> sprite.isHurt();
            case RECOVERED -> !sprite.isHurt() && !sprite.getAir();
            case RING_COLLECTED -> sprite.getRingCount() > p.waitRings;
            case TARGET_AVAILABLE -> expected != null;
        };
    }
    private void observe(Performer p, int frame) {
        int rings = p.sprite.getRingCount(); p.ringDelta = rings - p.rings; p.rings = rings;
        if (p.ringDelta != 0) event(p, frame, "RINGS_CHANGED", Integer.toString(p.ringDelta));
        if (p.hurt != p.sprite.isHurt()) event(p, frame, p.sprite.isHurt() ? "HURT" : "RECOVERED", "native damage state");
        p.hurt = p.sprite.isHurt();
        if (p.onObject != p.sprite.isOnObject()) event(p, frame, p.sprite.isOnObject() ? "BOARDED" : "LEFT_OBJECT", "native solid contact");
        p.onObject = p.sprite.isOnObject();
        if (p.abilityState != p.sprite.getDoubleJumpFlag()) event(p, frame, "ABILITY_STATE", Integer.toString(p.sprite.getDoubleJumpFlag()));
        p.abilityState = p.sprite.getDoubleJumpFlag();
        p.arrived = false;
        if (p.sprite.getDead()) { p.status = PuppetStatus.DEAD; return; }
        if (p.wait != null) { p.status = PuppetStatus.WAITING; }
        if (p.goal == null || p.goal.kind() == PuppetCommandKind.STOP || p.goal.kind() == PuppetCommandKind.RELEASE) {
            if (p.wait == null && p.status != PuppetStatus.TIMED_OUT) p.status = PuppetStatus.IDLE; return;
        }
        if (p.goal.kind() != PuppetCommandKind.MOVE_TO && p.goal.kind() != PuppetCommandKind.FLY_TO
                && p.goal.kind() != PuppetCommandKind.GLIDE_TO) {
            if (p.wait == null) p.status = PuppetStatus.PERFORMING;
            return;
        }
        double[] target = target(p, p.goal);
        double distance = StrictMath.hypot(target[0] - p.sprite.getCentreX(), target[1] - p.sprite.getCentreY());
        p.arrived = (p.goal.target().layoutIndex() == -1 || p.targetStatus.equals("ACTIVE")) && Math.abs(target[0] - p.sprite.getCentreX()) <= 6 && Math.abs(target[1] - p.sprite.getCentreY()) <= 12;
        if (distance < p.best - .5) { p.best = distance; p.lastProgress = frame; }
        if (p.wait == null) p.status = !p.targetStatus.equals("ACTIVE") && p.goal.target().layoutIndex() != -1
                ? PuppetStatus.TARGET_UNAVAILABLE : p.arrived ? PuppetStatus.ARRIVED
                : frame - p.lastProgress >= 120 ? PuppetStatus.BLOCKED : PuppetStatus.MOVING;
    }

    private void event(Performer p, int frame, String kind, String detail) {
        events.add(new LiveEvent(frame, p.actor.id(), p.command == null ? "" : p.command.id(), kind, detail));
    }

    private void follow(Frame authored) {
        CameraKey key = document.camera().getFirst();
        for (var candidate : document.camera()) if (candidate.frame() <= cursor) key = candidate;
        var follow = key.follow();
        if (follow.actors().isEmpty()) { director = authored.baseCamera(); return; }
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, minY = minX, maxY = maxX, velocity = 0;
        for (String id : follow.actors()) {
            var p = cast.stream().filter(a -> a.actor.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Camera target is absent from this shot: " + id));
            minX = Math.min(minX, p.sprite.getCentreX()); maxX = Math.max(maxX, p.sprite.getCentreX());
            minY = Math.min(minY, p.sprite.getCentreY()); maxY = Math.max(maxY, p.sprite.getCentreY());
            velocity += p.sprite.getXSpeed() / 256.0;
        }
        double wantedX = (minX + maxX) / 2 - document.width() / 2.0 + velocity / follow.actors().size() * follow.lookAhead() + follow.offsetX();
        double wantedY = (minY + maxY) / 2 - document.height() / 2.0 + follow.offsetY();
        double x = deadZone(director.x(), wantedX, follow.deadZoneX(), follow.smoothing());
        double y = deadZone(director.y(), wantedY, follow.deadZoneY(), follow.smoothing());
        director = boundedCamera(new CameraPose(x, y, 1));
    }
    private static double deadZone(double previous, double wanted, double zone, double smoothing) {
        double delta = wanted - previous;
        return Math.abs(delta) <= zone ? previous : previous + (delta - Math.copySign(zone, delta)) * smoothing;
    }

    private Frame sample(Frame authored) {
        var poses = new ArrayList<ActorPose>(); var samples = new ArrayList<PuppetSample>(); var actors = new ArrayList<LiveActor>();
        for (var pose : authored.actors()) {
            var p = cast.stream().filter(a -> a.actor.id().equals(pose.actorId())).findFirst().orElse(null);
            if (p == null) {
                poses.add(pose);
                document.puppets().stream().filter(t -> t.actorId().equals(pose.actorId())).findFirst().ifPresent(t ->
                        samples.add(new PuppetSample(pose.actorId(), "", PuppetStatus.INACTIVE, 0, 0, 0, false, 0, t.collisionPath())));
                continue;
            }
            var sprite = p.sprite;
            poses.add(new ActorPose(p.actor.id(), p.actor.gameId(), p.actor.character(),
                    sprite.getCentreX() + sprite.getXSubpixelRaw() / 65536.0,
                    sprite.getCentreY() + sprite.getYSubpixelRaw() / 65536.0, sprite.getAnimationId(), sprite.getAnimationTick(),
                    sprite.getMappingFrame(), sprite.getRenderHFlip(), pose.visible(), 1, sprite.getRenderVFlip()));
            var controller = sprite.getTailsTailsController();
            var tail = controller == null ? null : controller.presentation();
            samples.add(new PuppetSample(p.actor.id(), p.command == null ? "" : p.command.id(), p.status,
                    sprite.getXSpeed() / 256.0, sprite.getYSpeed() / 256.0, sprite.getGSpeed() / 256.0,
                    sprite.getAir(), sprite.getAngle() & 255, p.track.collisionPath(), tail == null ? null : new PuppetAccessory(tail.mappingFrame(), tail.flipX(), tail.flipY())));
            var riding = owned.getLevelManager().getObjectManager().getRidingObject(sprite);
            actors.add(new LiveActor(p.actor.id(), p.rings, p.ringDelta, sprite.isHurt(), sprite.getDead(), sprite.isOnObject(),
                    riding == null || riding.getSpawn() == null ? -1 : riding.getSpawn().layoutIndex(),
                    sprite.getSecondaryAbility().name(), sprite.getDoubleJumpFlag(), sprite.getDoubleJumpProperty() & 255,
                    sprite.getAnimationId(), sprite.getMappingFrame(), p.targetStatus));
        }
        var objects = owned.getLevelManager().getObjectManager().getActiveObjects().stream().filter(o -> o.getSpawn() != null)
                .map(o -> new LiveObject(o.getSpawn().layoutIndex(), o.getSpawn().objectId(), o.getSpawn().subtype(), o.getX(), o.getY(), o.getClass().getSimpleName()))
                .sorted(Comparator.comparingInt(LiveObject::layoutIndex).thenComparing(LiveObject::type)
                        .thenComparingInt(LiveObject::x).thenComparingInt(LiveObject::y)).toList();
        var ringManager = owned.getLevelManager().getRingManager();
        var rings = owned.getLevelManager().getCurrentLevel().getRings().stream().map(r -> new LiveRing(r.placementId(), r.x(), r.y(),
                ringManager.isCollected(r), ringManager.getSparkleStartFrame(r))).toList();
        double shakeX = authored.camera().x() - authored.baseCamera().x(), shakeY = authored.camera().y() - authored.baseCamera().y();
        CameraKey key = document.camera().getFirst();
        for (var candidate : document.camera()) if (candidate.frame() <= cursor) key = candidate;
        var anchor = StageplayTimeline.sample(document, start).baseCamera();
        double panX = key.follow().actors().isEmpty() ? 0 : authored.baseCamera().x() - anchor.x();
        double panY = key.follow().actors().isEmpty() ? 0 : authored.baseCamera().y() - anchor.y();
        var base = boundedCamera(new CameraPose(director.x() + panX, director.y() + panY, 1));
        var camera = new CameraPose(base.x() + shakeX, base.y() + shakeY, 1);
        return new Frame(cursor, poses, camera, authored.captions(), source, authored.transition(), base, authored.dialogue(), samples,
                new WorldSample(owned.getLevelManager().getFrameCounter(), owned.getLevelManager().getObjectManager().getVblaCounter(), actors, objects, rings, events));
    }

    /** Draw the native priority renderer with a temporary cinematic camera; activation uses the native camera. */
    public CameraPose boundedCamera(CameraPose view) {
        if (!Double.isFinite(view.x()) || !Double.isFinite(view.y()) || view.zoom()!=1)
            throw new IllegalArgumentException("Live cameras need finite coordinates and fixed zoom 1");
        var level = owned.getLevelManager().getCurrentLevel();
        // Level bounds are ROM camera origins (maxLeft + 320 is the native right edge),
        // not terrain dimensions. Height stays native; only extra viewport width needs an inset.
        return new CameraPose(Math.clamp(view.x(),level.getMinX(),Math.max(level.getMinX(),level.getMaxX()-Math.max(0,document.width()-320))),
                Math.clamp(view.y(),level.getMinY(),Math.max(level.getMinY(),level.getMaxY())),1);
    }

    public void draw(Frame frame) { draw(frame,frame.camera()); }
    public void draw(Frame frame, CameraPose view) {
        if (last != frame || owned == null) throw new IllegalStateException("Resolve the live frame before drawing");
        var camera = owned.getCamera(); var saved = camera.capture();
        var hidden = cast.stream().map(p -> p.sprite.isHidden()).toList();
        try {
            for (var p : cast) {
                boolean visible = frame.actors().stream().anyMatch(a -> a.actorId().equals(p.actor.id()) && a.visible());
                p.sprite.setHidden(p.sprite.isHidden() || !visible);
            }
            camera.setX((short) Math.round(view.x())); camera.setY((short) Math.round(view.y()));
            camera.setXCopy(camera.getX()); camera.setYCopy(camera.getY());
            owned.getLevelManager().setClearColor();
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            owned.getLevelManager().drawCinematicWorld(owned.getSpriteManager());
            var graphics = EngineServices.current().graphics();
            graphics.flush();
            if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        } finally {
            for (int i=0; i<cast.size(); i++) cast.get(i).sprite.setHidden(hidden.get(i));
            camera.restore(saved, camera.getFocusedSprite());
        }
    }

    private Saved capture() {
        return new Saved(owned.getRewindRegistry().capture(), director, cast.stream().map(p -> new Control(p.index, p.previousHeld,
                p.jumpEnd, p.goalStart, p.deadline, p.waitRings, p.lastProgress, p.rings, p.ringDelta, p.best, p.hoverY,
                p.goal, p.wait, p.command, p.status, p.arrived, p.targetStatus, p.hurt, p.onObject, p.abilityState, Set.copyOf(p.seenTargets))).toList(), List.copyOf(events));
    }
    private void restore(Saved saved) {
        owned.getRewindRegistry().restore(saved.world()); director = saved.camera();
        events.clear(); events.addAll(saved.events());
        for (int i = 0; i < cast.size(); i++) {
            var p = cast.get(i); var s = saved.cast().get(i);
            p.index=s.index(); p.previousHeld=s.previousHeld(); p.jumpEnd=s.jumpEnd(); p.goalStart=s.goalStart();
            p.deadline=s.deadline(); p.waitRings=s.waitRings(); p.lastProgress=s.lastProgress(); p.rings=s.rings(); p.ringDelta=s.ringDelta();
            p.best=s.best(); p.hoverY=s.hoverY(); p.goal=s.goal(); p.wait=s.waiting(); p.command=s.command(); p.status=s.status(); p.arrived=s.arrived(); p.targetStatus=s.targetStatus(); p.hurt=s.hurt(); p.onObject=s.onObject(); p.abilityState=s.abilityState(); p.seenTargets.clear(); p.seenTargets.addAll(s.seenTargets());
        }
    }
    private record Saved(CompositeSnapshot world, CameraPose camera, List<Control> cast, List<LiveEvent> events) { }
    private record Control(int index, int previousHeld, int jumpEnd, int goalStart, int deadline, int waitRings,
                           int lastProgress, int rings, int ringDelta, double best, double hoverY, PuppetCommand goal,
                           PuppetCommand waiting, PuppetCommand command, PuppetStatus status, boolean arrived, String targetStatus, boolean hurt, boolean onObject, int abilityState, Set<Integer> seenTargets) { }
    private static final class Performer {
        final Actor actor; final PuppetTrack track; final ActorKey anchor; AbstractPlayableSprite sprite;
        final Set<Integer> seenTargets = new HashSet<>();
        int index, previousHeld, jumpEnd, goalStart, deadline, waitRings, lastProgress, rings, ringDelta;
        double best = Double.POSITIVE_INFINITY, hoverY; boolean arrived, hurt, onObject; int abilityState;
        PuppetCommand goal, wait, command; PuppetStatus status = PuppetStatus.IDLE; String targetStatus = "NONE";
        Performer(Actor actor, PuppetTrack track, ActorKey anchor) { this.actor=actor; this.track=track; this.anchor=anchor; }
    }
    public void reset() { closeOwned(); document=null; start=-1; cursor=-1; last=null; checkpoints.clear(); cast=List.of(); }
    private void closeOwned() {
        if (owned != null) {
            if (SessionManager.getCurrentGameplayMode() == owned) {
                owned.getWorldSession().getGameModule().resetModuleScopedState();
                SessionManager.closeGameplaySession();
            }
            owned=null;
        }
    }
    @Override public void close() { if (!closed) { reset(); closed=true; } }
}

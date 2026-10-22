package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.graphics.*;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.*;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.*;

/**
 * Picture-only object direction. Native RAM, terrain and object routines are never driven by these
 * transforms. Releasing direction resumes the frozen native graph at its native state/anchor. A new
 * directed cast member supplies a baked template; its factory must not run in the shared scene
 * merely to obtain a picture.
 */
public final class StageplayObjectDirection implements AutoCloseable {
    public static final int TEMPLATE_VERSION = 1;
    public static final int MAX_PATTERNS = 6144;
    public static final int MAX_PARTS = 1024;
    public static final int MAX_POSES = 1024;

    public static final class UnsupportedRenderOrderException extends IllegalArgumentException {
        UnsupportedRenderOrderException(String objectName) {
            this(objectName, "native custom primitives require ordered visual capture");
        }

        UnsupportedRenderOrderException(String objectName, String detail) {
            super(
                    objectName
                            + ": "
                            + detail
                            + "; use Free mode for this object. "
                            + "Puppet/Pose cannot preserve this render order yet");
        }
    }

    public record PatternPixels(long a, long b, long c, long d) {
        SpritePresentation.PatternVersion version() {
            return new SpritePresentation.PatternVersion(a, b, c, d);
        }
    }

    public record PaletteColors(List<Integer> argb) {
        public PaletteColors {
            argb = List.copyOf(argb);
            if (argb.size() != 16) {
                throw new IllegalArgumentException("Palette requires 16 colors");
            }
        }
    }

    public record VisualTile(
            int pattern,
            int palette,
            boolean flipX,
            boolean flipY,
            boolean priority,
            float x,
            float y,
            float width,
            float height,
            boolean priorityShader,
            int occlusionMask,
            int rowStart,
            int rowEnd) {}

    public record VisualPrimitive(
            int beforeTile,
            SpritePresentation.PrimitiveKind kind,
            int method,
            List<SpritePresentation.Vertex> vertices) {
        public VisualPrimitive {
            vertices = List.copyOf(vertices);
        }
    }

    public record VisualPose(int pose, List<VisualTile> tiles, List<VisualPrimitive> primitives) {
        public VisualPose {
            tiles = List.copyOf(tiles);
            primitives = List.copyOf(primitives);
        }
    }

    public record VisualAnimation(
            int id, int delay, List<Integer> frames, String endAction, int endParam) {
        public VisualAnimation {
            frames = List.copyOf(frames);
            SpriteAnimationEndAction.valueOf(endAction);
        }
    }

    public record VisualPart(
            String partId,
            int bucket,
            boolean highPriority,
            int slotOrder,
            int defaultPose,
            List<VisualPose> poses,
            List<VisualAnimation> animations) {
        public VisualPart {
            Objects.requireNonNull(partId);
            poses = List.copyOf(poses);
            animations = List.copyOf(animations);
            if (poses.isEmpty() || poses.size() > MAX_POSES) {
                throw new IllegalArgumentException("Invalid visual pose count");
            }
        }

        public VisualPart(
                String partId,
                int bucket,
                boolean highPriority,
                int slotOrder,
                int defaultPose,
                List<VisualPose> poses) {
            this(partId, bucket, highPriority, slotOrder, defaultPose, poses, List.of());
        }
    }

    public record VisualTemplate(
            int version,
            int anchorX,
            int anchorY,
            List<VisualPart> parts,
            List<PatternPixels> patterns,
            List<PaletteColors> palettes,
            List<String> limitations) {
        public VisualTemplate {
            parts = List.copyOf(parts);
            patterns = List.copyOf(patterns);
            palettes = List.copyOf(palettes);
            limitations = List.copyOf(limitations);
            if (version != TEMPLATE_VERSION) {
                throw new IllegalArgumentException("Unsupported object visual template version");
            }
            if (parts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Object has no initialized native visual. Start as Free in its native set,"
                                + " then switch after appearance initializes");
            }
            if (parts.size() > MAX_PARTS || patterns.size() > MAX_PATTERNS || palettes.size() > 4) {
                throw new IllegalArgumentException(
                        "Object visual template exceeds bounded art budget");
            }
            Set<String> ids = new HashSet<>();
            for (var part : parts) {
                if (!ids.add(part.partId())) {
                    throw new IllegalArgumentException("Duplicate visual part " + part.partId());
                }
                if (part.poses().stream().noneMatch(p -> p.pose() == part.defaultPose())) {
                    throw new IllegalArgumentException("Missing default visual pose");
                }
                for (var pose : part.poses()) {
                    if (!pose.primitives().isEmpty()) {
                        throw new UnsupportedRenderOrderException("Object visual template");
                    }
                    for (var tile : pose.tiles()) {
                        if (tile.pattern() < 0
                                || tile.pattern() >= patterns.size()
                                || tile.palette() < 0
                                || tile.palette() >= palettes.size()) {
                            throw new IllegalArgumentException("Invalid visual art reference");
                        }
                        if (!Float.isFinite(tile.x())
                                || !Float.isFinite(tile.y())
                                || tile.width() != 8
                                || tile.height() != 8
                                || tile.rowStart() < 0
                                || tile.rowEnd() > 8
                                || tile.rowStart() > tile.rowEnd()) {
                            throw new IllegalArgumentException("Unsupported visual tile geometry");
                        }
                    }
                }
            }
        }
    }

    /** Exact portable ROM layout identity, independent of dynamic slots/generations. */
    public record PlacementIdentity(int layoutIndex, int objectId, int subtype) {
        public PlacementIdentity {
            if (layoutIndex < 0 || objectId < 0 || objectId > 255 || subtype < 0 || subtype > 255) {
                throw new IllegalArgumentException("Invalid portable object identity");
            }
        }

        public static PlacementIdentity of(ObjectSpawn spawn) {
            return new PlacementIdentity(spawn.layoutIndex(), spawn.objectId(), spawn.subtype());
        }
    }

    public record PartSelection(int animation, int pose) {}

    public record Direction(
            double x,
            double y,
            boolean flipX,
            boolean visible,
            int animation,
            int pose,
            Map<String, PartSelection> parts,
            int animationFrame) {
        public Direction {
            parts = Map.copyOf(parts);
            if (!Double.isFinite(x) || !Double.isFinite(y) || animationFrame < 0) {
                throw new IllegalArgumentException("Invalid visual direction");
            }
        }

        public Direction(
                double x, double y, boolean flipX, boolean visible, int animation, int pose) {
            this(x, y, flipX, visible, animation, pose, Map.of(), 0);
        }
    }

    public record DirectedSnapshot(
            ObjectRefId root,
            VisualTemplate template,
            Direction direction,
            List<ObjectRefId> members) {
        public DirectedSnapshot {
            members = List.copyOf(members);
        }
    }

    public record EdgeSnapshot(ObjectRefId parent, ObjectRefId child, int ordinal) {}

    private record ChildLink(ObjectInstance child, int ordinal) {}

    public record VisualSnapshot(String id, VisualTemplate template, Direction direction) {}

    public record Snapshot(
            List<DirectedSnapshot> directed,
            List<VisualSnapshot> visuals,
            List<EdgeSnapshot> edges,
            Map<ObjectRefId, Integer> nextChildOrdinals,
            Map<ObjectRefId, String> authored,
            Set<PlacementIdentity> removed,
            boolean clearObjects,
            boolean clearBadniks,
            long nextVisualId) {
        public Snapshot {
            directed = List.copyOf(directed);
            visuals = List.copyOf(visuals);
            edges = List.copyOf(edges);
            nextChildOrdinals = Map.copyOf(nextChildOrdinals);
            authored = Map.copyOf(authored);
            removed = Set.copyOf(removed);
        }
    }

    private static final class VisualObject implements ObjectInstance {
        final String id;

        VisualObject(String id) {
            this.id = id;
        }

        public ObjectSpawn getSpawn() {
            return new ObjectSpawn(0, 0, 0, 0, 0, false, 0);
        }

        public void update(int clock, com.openggf.game.PlayableEntity player) {}

        public void appendRenderCommands(List<GLCommand> commands) {}

        public boolean isDestroyed() {
            return false;
        }

        public boolean isHighPriority() {
            // Every template part supplies its own priority class in the controller's render
            // entries.
            return false;
        }
    }

    private static final class SharedArt {
        final List<PatternPixels> key;
        ObjectVisualGraphicsAccess.ArtBank bank;
        int references;

        SharedArt(List<PatternPixels> key) {
            this.key = key;
        }
    }

    private static final class SharedColors {
        final List<PaletteColors> key;
        ScopedPaletteGraphicsAccess.Bank bank;
        int references;

        SharedColors(List<PaletteColors> key) {
            this.key = key;
        }
    }

    private final class Directed implements AutoCloseable {
        final ObjectInstance root;
        final VisualTemplate template;
        Direction direction;
        SharedArt art;
        SharedColors colors;

        Directed(ObjectInstance root, VisualTemplate template, Direction direction) {
            this.root = root;
            this.template = template;
            this.direction = direction;
        }

        public void close() {
            if (art != null && --art.references == 0) {
                sharedArt.remove(art.key, art);
                ObjectVisualGraphicsAccess.releaseArt(art.bank, art);
            }
            if (colors != null && --colors.references == 0) {
                sharedColors.remove(colors.key, colors);
                ScopedPaletteGraphicsAccess.release(colors.bank, colors);
            }
            art = null;
            colors = null;
        }
    }

    private final ObjectManager objects;
    private final Map<List<PatternPixels>, SharedArt> sharedArt = new HashMap<>();
    private final Map<List<PaletteColors>, SharedColors> sharedColors = new HashMap<>();
    private final IdentityHashMap<ObjectInstance, List<ChildLink>> children =
            new IdentityHashMap<>();
    private final IdentityHashMap<ObjectInstance, Integer> nextChildOrdinals =
            new IdentityHashMap<>();
    private final IdentityHashMap<ObjectInstance, Directed> roots = new IdentityHashMap<>();
    private final IdentityHashMap<ObjectInstance, Directed> membership = new IdentityHashMap<>();
    private final LinkedHashMap<String, VisualObject> visuals = new LinkedHashMap<>();
    private long nextVisualId;
    private final StageplayObjectLoadPolicy loadPolicy;
    private int explicitNativeAdmission;
    private String authoredAdmissionId;
    private final IdentityHashMap<ObjectInstance, String> authored = new IdentityHashMap<>();

    StageplayObjectDirection(ObjectManager objects) {
        this(objects, new StageplayObjectLoadPolicy());
    }

    StageplayObjectDirection(ObjectManager objects, StageplayObjectLoadPolicy loadPolicy) {
        this.objects = objects;
        this.loadPolicy = loadPolicy;
    }

    void childSpawned(ObjectInstance parent, ObjectInstance child) {
        if (parent == child) {
            return;
        }
        var list = children.computeIfAbsent(parent, k -> new ArrayList<>());
        if (list.stream().noneMatch(link -> link.child() == child)) {
            int ordinal = nextChildOrdinals.getOrDefault(parent, 0);
            nextChildOrdinals.put(parent, ordinal + 1);
            list.add(new ChildLink(child, ordinal));
        }
        String authoredId = authored.get(parent);
        if (authoredId != null) {
            claimAuthored(authoredId, child);
        }
        Directed owner = membership.get(parent);
        if (owner != null) {
            for (ObjectInstance member : graph(child)) {
                membership.put(member, owner);
            }
        }
    }

    private List<ObjectInstance> graph(ObjectInstance root) {
        List<ObjectInstance> result = new ArrayList<>();
        Set<ObjectInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ObjectInstance> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            ObjectInstance next = pending.removeFirst();
            if (!seen.add(next)) {
                continue;
            }
            result.add(next);
            for (ChildLink link : children.getOrDefault(next, List.of())) {
                pending.add(link.child());
            }
            if (next instanceof AbstractBossInstance boss) {
                pending.addAll(List.copyOf(boss.getChildComponents()));
            }
        }
        return result;
    }

    private Map<ObjectInstance, String> structuralPaths(ObjectInstance root) {
        Map<ObjectInstance, String> paths = new IdentityHashMap<>();
        ArrayDeque<ObjectInstance> pending = new ArrayDeque<>();
        paths.put(root, "root");
        pending.add(root);
        while (!pending.isEmpty()) {
            ObjectInstance parent = pending.removeFirst();
            if (parent instanceof AbstractBossInstance boss) {
                for (var child : boss.getChildComponents()) {
                    childSpawned(parent, child);
                }
            }
            for (ChildLink link : children.getOrDefault(parent, List.of())) {
                ObjectInstance child = link.child();
                if (paths.containsKey(child)) {
                    continue;
                }
                paths.put(child, paths.get(parent) + "/" + link.ordinal());
                pending.add(child);
            }
        }
        return paths;
    }

    public Optional<ObjectInstance> findPlaced(PlacementIdentity identity) {
        return objects.getActiveObjects().stream()
                .filter(o -> matches(o.getSpawn(), identity))
                .findFirst();
    }

    private static boolean matches(ObjectSpawn spawn, PlacementIdentity identity) {
        return spawn != null
                && spawn.layoutIndex() == identity.layoutIndex()
                && spawn.objectId() == identity.objectId()
                && spawn.subtype() == identity.subtype();
    }

    public boolean allowsPlacement(ObjectSpawn spawn) {
        if (spawn == null) {
            return true;
        }
        if (loadPolicy.clearObjects && !objects.isStageplayRingPlacement(spawn)) {
            return false;
        }
        return spawn.layoutIndex() < 0 || !loadPolicy.removed.contains(PlacementIdentity.of(spawn));
    }

    public ObjectInstance materializePlaced(PlacementIdentity identity) {
        return objects.materializeStageplayPlacement(identity);
    }

    public ObjectInstance materializePlaced(ObjectSpawn originalSpawn) {
        return objects.materializeStageplayPlacement(originalSpawn);
    }

    public ObjectInstance installVisual(String id, VisualTemplate template, Direction direction) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Visual actor requires a stable identity");
        }
        if (visuals.containsKey(id)) {
            throw new IllegalArgumentException("Visual actor already exists: " + id);
        }
        VisualObject object = new VisualObject(id);
        visuals.put(id, object);
        direct(object, template, direction);
        return object;
    }

    public ObjectInstance installVisual(VisualTemplate template, Direction direction) {
        return installVisual("visual-" + nextVisualId++, template, direction);
    }

    public Optional<ObjectInstance> findVisual(String id) {
        return Optional.ofNullable(visuals.get(id));
    }

    public boolean allowsObject(ObjectInstance object) {
        return explicitNativeAdmission > 0
                || authored.containsKey(object)
                || objects.stageplayAuthoredExecutionParent() != null
                || objects.isStageplayPlayerAttachmentOrRing(object)
                || !loadPolicy.clearObjects && (!loadPolicy.clearBadniks || !isBadnik(object));
    }

    void noteNativeAdmission(ObjectInstance object) {
        String id =
                explicitNativeAdmission > 0
                        ? authoredAdmissionId
                        : objects.stageplayAuthoredExecutionParent();
        if (id != null) {
            claimAuthored(id, object);
        }
    }

    String authoredId(ObjectInstance object) {
        return authored.get(object);
    }

    public void claimAuthored(ObjectInstance root) {
        claimAuthored("stageplay-cast", root);
    }

    public void claimAuthored(String actorId, ObjectInstance root) {
        Objects.requireNonNull(actorId, "authored actor identity");
        for (ObjectInstance member : graph(root)) {
            authored.put(member, actorId);
        }
    }

    public <T> T withAuthoredAdmission(String actorId, java.util.function.Supplier<T> action) {
        String previous = authoredAdmissionId;
        authoredAdmissionId = Objects.requireNonNull(actorId);
        explicitNativeAdmission++;
        try {
            return action.get();
        } finally {
            explicitNativeAdmission--;
            authoredAdmissionId = previous;
        }
    }

    private boolean isBadnik(ObjectInstance object) {
        if (object instanceof AbstractBadnikInstance
                || object.headPresentationRole()
                        == com.openggf.game.mutators.ObjectHeadRole.BADNIK) {
            return true;
        }
        var level = objects.services().levelManager();
        var registry = objects.objectRegistry();
        var spawn = object.getSpawn();
        if (level == null
                || registry == null
                || spawn == null
                || spawn.objectId() < 0
                || spawn.objectId() > 255) {
            return false;
        }
        int zone = level.getCurrentZone(), act = level.getCurrentAct();
        if (zone < 0 || act < 0) {
            return false;
        }
        return ObjectRegistryCatalog.entry(
                                registry,
                                new ObjectRegistryCatalog.Context(zone, act),
                                spawn.objectId())
                        .semantics()
                        .role()
                == ObjectRegistryCatalog.Role.BADNIK;
    }

    <T> T explicitNativeAdmission(java.util.function.Supplier<T> action) {
        return withAuthoredAdmission("stageplay-cast", action);
    }

    boolean hasSuspension() {
        return !membership.isEmpty();
    }

    public boolean suspended(ObjectInstance object) {
        return membership.containsKey(object);
    }

    public void direct(ObjectInstance root, VisualTemplate template, Direction direction) {
        Objects.requireNonNull(root);
        Objects.requireNonNull(template);
        Objects.requireNonNull(direction);
        if (!(root instanceof VisualObject visual && visuals.get(visual.id) == root)
                && objects.objectQuery().identityOf(root).isEmpty()) {
            throw new IllegalArgumentException("Directed native object is not owned by this world");
        }
        validateSelection(template, direction);
        Directed old = roots.get(root);
        if (old != null && old.template == template) {
            old.direction = direction;
            return;
        }
        for (ObjectInstance member : graph(root)) {
            Directed existing = membership.get(member);
            if (existing != null && existing != old) {
                throw new IllegalStateException("Native graph is already directed by another root");
            }
        }
        releaseDirection(root);
        Directed state = new Directed(root, template, direction);
        roots.put(root, state);
        for (ObjectInstance member : graph(root)) {
            membership.put(member, state);
            objects.suspendStageplayContacts(member);
        }
        objects.stageplayBucketsChanged();
    }

    public void setDirection(ObjectInstance root, Direction direction) {
        Directed state = roots.get(root);
        if (state == null) {
            throw new IllegalArgumentException("Object is not directed");
        }
        validateSelection(state.template, direction);
        state.direction = direction;
    }

    public void releaseToFree(ObjectInstance root) {
        if (root instanceof VisualObject) {
            throw new IllegalArgumentException(
                    "Baked visual has no native routine; cast Free in the native set to obtain a"
                            + " native graph");
        }
        releaseDirection(root);
    }

    private void releaseDirection(ObjectInstance root) {
        Directed state = roots.remove(root);
        if (state == null) {
            return;
        }
        membership.values().removeIf(value -> value == state);
        state.close();
        objects.stageplayBucketsChanged();
    }

    public void remove(PlacementIdentity identity) {
        loadPolicy.removed.add(identity);
        findPlaced(identity).ifPresent(this::removeDynamic);
    }

    public void removeDynamic(ObjectInstance root) {
        if (root instanceof VisualObject visual) {
            visuals.remove(visual.id);
            releaseDirection(root);
            return;
        }
        List<ObjectInstance> graph = graph(root);
        releaseDirection(root);
        graph.forEach(authored::remove);
        for (int i = graph.size() - 1; i >= 0; i--) {
            objects.retireStageplayObject(graph.get(i));
        }
    }

    public void clearObjects() {
        loadPolicy.clearObjects = true;
        for (ObjectInstance root : List.copyOf(objects.getActiveObjects())) {
            if (!objects.isStageplayPlayerAttachmentOrRing(root)) {
                removeDynamic(root);
            }
        }
        for (ObjectInstance visual : List.copyOf(visuals.values())) {
            removeDynamic(visual);
        }
    }

    /**
     * Classification is supplied by the ROM registry/catalog; never inferred by constructing
     * objects.
     */
    public void clearBadniks() {
        loadPolicy.clearBadniks = true;
        for (ObjectInstance object : List.copyOf(objects.getActiveObjects())) {
            if (isBadnik(object)) {
                removeDynamic(object);
            }
        }
    }

    public void clearBadniks(Collection<PlacementIdentity> identities) {
        clearBadniks();
        identities.forEach(this::remove);
    }

    public VisualTemplate captureDynamic(ObjectInstance root) {
        Objects.requireNonNull(root);
        return new Capture(root).capture();
    }

    private final class Capture {
        final ObjectInstance root;
        final GraphicsManager graphics = objects.services().graphicsManager();
        final int anchorX, anchorY;
        final List<VisualPart> parts = new ArrayList<>();
        final List<PatternPixels> patterns = new ArrayList<>();
        final Map<PatternPixels, Integer> patternIds = new LinkedHashMap<>();
        final List<PaletteColors> palettes = new ArrayList<>();
        final Map<PaletteColors, Integer> paletteIds = new LinkedHashMap<>();
        final List<String> limitations = new ArrayList<>();

        Capture(ObjectInstance root) {
            this.root = root;
            anchorX = root.getX();
            anchorY = root.getY();
        }

        VisualTemplate capture() {
            Map<ObjectInstance, String> paths = structuralPaths(root);
            List<ObjectInstance> members = graph(root);
            Set<ObjectInstance> inline = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ObjectInstance member : members) {
                if (member instanceof AbstractBossInstance boss) {
                    inline.addAll(boss.getChildComponents());
                }
            }
            List<ObjectRenderParts.RenderEntry>[] unified =
                    new List[RenderPriority.MAX - RenderPriority.MIN + 1];
            List<ObjectRenderParts.RenderEntry>[] low = new List[unified.length],
                    high = new List[unified.length];
            for (int i = 0; i < unified.length; i++) {
                unified[i] = new ArrayList<>();
                low[i] = new ArrayList<>();
                high[i] = new ArrayList<>();
            }
            for (ObjectInstance member : members) {
                if (inline.contains(member) && objects.objectQuery().identityOf(member).isEmpty()) {
                    continue;
                }
                ObjectRenderParts.addToRenderBuckets(member, unified, low, high);
            }
            for (int b = 0; b < unified.length; b++) {
                unified[b].sort(ObjectRenderParts.SLOT_DESCENDING);
                for (var entry : unified[b]) {
                    captureEntry(paths.get(entry.instance()), entry, b + RenderPriority.MIN);
                }
            }
            limitations.add(
                    "Native PLC, palette and child-graph state is held at capture; mapping poses"
                            + " use that frozen ROM art bank");
            return new VisualTemplate(
                    TEMPLATE_VERSION,
                    anchorX,
                    anchorY,
                    parts,
                    patterns,
                    palettes,
                    limitations.stream().distinct().toList());
        }

        void captureEntry(String path, ObjectRenderParts.RenderEntry entry, int bucket) {
            List<ObjectSpriteCapture.Draw> draws = new ArrayList<>();
            List<GLCommand> commands = new ArrayList<>();
            int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
            SpritePresentation.Frame residual;
            try {
                graphics.setCurrentSpriteTileOcclusionPaletteMask(
                        ObjectRenderParts.tileOcclusionPaletteMask(entry));
                residual =
                        SpritePresentationRenderer.prepare(
                                graphics,
                                anchorX,
                                anchorY,
                                () ->
                                        ObjectSpriteCapture.observe(
                                                draws::add,
                                                detail -> {
                                                    throw new UnsupportedRenderOrderException(
                                                            root.getName(), detail);
                                                },
                                                () -> {
                                                    ObjectRenderParts.appendRenderCommands(
                                                            entry, commands, bucket);
                                                    for (GLCommand command : commands) {
                                                        graphics.registerCommand(command);
                                                    }
                                                }));
            } finally {
                graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
            }
            if (!residual.primitives().isEmpty()) {
                throw new UnsupportedRenderOrderException(root.getName());
            }
            String prefix = path + "/bucket" + bucket + (entry.high() ? "h" : "l");
            if (!residual.tiles().isEmpty() || !residual.primitives().isEmpty()) {
                parts.add(
                        new VisualPart(
                                prefix + "/native",
                                bucket,
                                entry.high(),
                                entry.slot(),
                                -1,
                                List.of(convert(-1, residual))));
                limitations.add(
                        prefix
                                + ": custom draw/primitive state is held; native routines remain"
                                + " suspended");
            }
            int ordinal = 0;
            for (var draw : draws) {
                if (draw.sheet().getFrameCount() > MAX_POSES) {
                    throw new IllegalStateException(
                            "Native mapping frame count exceeds visual template budget");
                }
                int defaultPose = draw.defaultPose();
                List<VisualPose> poses = new ArrayList<>();
                if (defaultPose < 0) {
                    poses.add(convert(-1, prepare(draw, -1)));
                }
                for (int pose = 0; pose < draw.sheet().getFrameCount(); pose++) {
                    poses.add(convert(pose, prepare(draw, pose)));
                }
                List<VisualAnimation> animations = animations(draw);
                if (animations.isEmpty()) {
                    limitations.add(
                            prefix
                                    + ": routine-owned animation is held; author a mapping pose per"
                                    + " part");
                }
                if (poses.stream()
                        .anyMatch(p -> !p.tiles().isEmpty() || !p.primitives().isEmpty())) {
                    parts.add(
                            new VisualPart(
                                    prefix + "/draw" + ordinal,
                                    bucket,
                                    entry.high(),
                                    entry.slot(),
                                    defaultPose,
                                    poses,
                                    animations));
                }
                ordinal++;
            }
        }

        SpritePresentation.Frame prepare(ObjectSpriteCapture.Draw draw, int pose) {
            return SpritePresentationRenderer.prepare(
                    graphics,
                    anchorX,
                    anchorY,
                    () -> {
                        bindMappedPatterns(draw, pose);
                        int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
                        try {
                            graphics.setCurrentSpriteTileOcclusionPaletteMask(draw.occlusionMask());
                            SpritePresentation.withSubject(
                                    graphics, draw.subject(), () -> draw.drawPose(pose));
                        } finally {
                            graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
                        }
                    });
        }

        private void bindMappedPatterns(ObjectSpriteCapture.Draw draw, int pose) {
            var pieces = pose < 0 ? draw.pieces() : draw.sheet().getFrame(pose).pieces();
            var bank = draw.sheet().getPatterns();
            for (var piece : pieces) {
                int first = piece.tileIndex();
                long end = first + (long) piece.widthTiles() * piece.heightTiles();
                if (bank == null || first < 0 || end > bank.length) {
                    throw new IllegalStateException(
                            "Native mapping pose "
                                    + pose
                                    + " references ROM patterns outside its initialized art bank");
                }
                for (int index = first; index < end; index++) {
                    if (bank[index] == null) {
                        throw new IllegalStateException(
                                "Native mapping pose "
                                        + pose
                                        + " references unloaded ROM pattern "
                                        + index);
                    }
                    SpritePresentation.bindPatternVersion(
                            graphics,
                            draw.renderer().getPatternBase() + index,
                            SpritePresentationRenderer.version(bank[index]));
                }
            }
        }

        List<VisualAnimation> animations(ObjectSpriteCapture.Draw draw) {
            ObjectRenderManager render = objects.services().renderManager();
            if (render == null) {
                return List.of();
            }
            List<VisualAnimation> result = new ArrayList<>();
            for (String key : render.getRendererKeys()) {
                if (render.getRenderer(key) == draw.renderer()) {
                    SpriteAnimationSet set = render.getAnimations(key);
                    if (set == null) {
                        set = render.getAnimations("anim_" + key);
                    }
                    if (set == null) {
                        continue;
                    }
                    result.addAll(pureAnimations(set, draw.sheet().getFrameCount()));
                }
            }
            return result;
        }

        VisualPose convert(int pose, SpritePresentation.Frame frame) {
            List<VisualTile> tiles = new ArrayList<>();
            for (var tile : frame.tiles()) {
                var pixels = frame.patternVersions().get(tile.patternId());
                if (pixels == null) {
                    pixels = SpritePresentation.patternSample(graphics, tile.patternId());
                }
                if (pixels == null) {
                    throw new IllegalStateException(
                            "Native object pattern " + tile.patternId() + " is not initialized");
                }
                PatternPixels pattern =
                        new PatternPixels(pixels.a(), pixels.b(), pixels.c(), pixels.d());
                int patternIndex =
                        patternIds.computeIfAbsent(
                                pattern,
                                p -> {
                                    patterns.add(p);
                                    return patterns.size() - 1;
                                });
                // Trusted native producers may project a logical CRAM line into their own
                // scoped bank. Resolve it while that exact source world/owner is alive; only
                // the resulting raw ROM colors, never the resolver or lease, enter the template.
                var projection = tile.subject().paletteReplay();
                int nativePalette =
                        projection == null
                                ? tile.palette()
                                : projection.palette(graphics, tile.palette());
                if (nativePalette < 0) {
                    throw new IllegalStateException(
                            "Native scoped object palette is not ready for visual capture");
                }
                PaletteColors palette =
                        new PaletteColors(
                                ObjectVisualGraphicsAccess.palette(graphics, nativePalette));
                int paletteIndex =
                        paletteIds.computeIfAbsent(
                                palette,
                                p -> {
                                    palettes.add(p);
                                    return palettes.size() - 1;
                                });
                tiles.add(
                        new VisualTile(
                                patternIndex,
                                paletteIndex,
                                tile.hFlip(),
                                tile.vFlip(),
                                tile.priority(),
                                tile.x(),
                                tile.y(),
                                tile.width(),
                                tile.height(),
                                tile.priorityShader(),
                                tile.occlusionMask(),
                                tile.rowStart(),
                                tile.rowEnd()));
            }
            List<VisualPrimitive> primitives = new ArrayList<>();
            for (var primitive : frame.primitives()) {
                var geometry = SpritePresentation.geometry(primitive);
                primitives.add(
                        new VisualPrimitive(
                                primitive.beforeTile(),
                                geometry.kind(),
                                geometry.method(),
                                geometry.vertices()));
            }
            return new VisualPose(pose, tiles, primitives);
        }
    }

    /**
     * Parsed ROM terminators, rather than the native decoder's compatibility enum, admit pure
     * controls.
     */
    static List<VisualAnimation> pureAnimations(SpriteAnimationSet source, int frameCount) {
        Map<Integer, SpriteAnimationScript> admitted = new TreeMap<>();
        for (var entry : source.getAllScripts().entrySet()) {
            SpriteAnimationScript script = entry.getValue();
            // Signed duration admission is conservative across native object interpreters:
            // Anim_Run (s2.asm:30431-30444) branches on the decremented byte's sign.
            if (script.trailingBytes().isEmpty()
                    || script.frames().isEmpty()
                    || script.delay() < 0
                    || script.delay() > 127
                    || script.frames().stream()
                            .anyMatch(frame -> frame < 0 || frame >= Math.min(128, frameCount))) {
                continue;
            }
            int terminator = script.trailingBytes().getFirst();
            // Only FF/FE/FD are picture controls. FC advances the object routine:
            // Anim_End_FF (s2.asm:30457-30491), loc_1AC38 (sonic3k.asm:36229-36263).
            boolean pure =
                    switch (terminator) {
                        case 0xff -> script.endAction() == SpriteAnimationEndAction.LOOP;
                        case 0xfe ->
                                script.endAction() == SpriteAnimationEndAction.LOOP_BACK
                                        && script.endParam() > 0
                                        && script.endParam() <= script.frames().size()
                                        && script.trailingBytes().size() > 1
                                        && script.trailingBytes().get(1) == script.endParam();
                        case 0xfd ->
                                script.endAction() == SpriteAnimationEndAction.SWITCH
                                        && script.trailingBytes().size() > 1
                                        && script.trailingBytes().get(1) == script.endParam();
                        default -> false;
                    };
            if (pure) {
                admitted.put(entry.getKey(), script);
            }
        }
        boolean changed;
        do {
            changed =
                    admitted.values()
                            .removeIf(
                                    script ->
                                            script.endAction() == SpriteAnimationEndAction.SWITCH
                                                    && !admitted.containsKey(script.endParam()));
        } while (changed);
        List<VisualAnimation> result = new ArrayList<>();
        for (var entry : admitted.entrySet()) {
            SpriteAnimationScript script = entry.getValue();
            result.add(
                    new VisualAnimation(
                            entry.getKey(),
                            script.delay(),
                            script.frames(),
                            script.endAction().name(),
                            script.endParam()));
        }
        return List.copyOf(result);
    }

    public static void validateSelection(VisualTemplate template, Direction direction) {
        for (String part : direction.parts().keySet()) {
            if (template.parts().stream().noneMatch(p -> p.partId().equals(part))) {
                throw new IllegalArgumentException("Unknown native visual part " + part);
            }
        }
        for (var part : template.parts()) {
            selectedPose(part, direction);
        }
    }

    static VisualPose selectedPose(VisualPart part, Direction direction) {
        PartSelection selection =
                direction
                        .parts()
                        .getOrDefault(
                                part.partId(),
                                new PartSelection(direction.animation(), direction.pose()));
        int pose = selection.pose();
        if (pose < 0
                && (selection.animation() > 0
                        || selection.animation() == 0
                                && part.animations().stream().anyMatch(a -> a.id() == 0))) {
            if (part.animations().stream().noneMatch(a -> a.id() == selection.animation())) {
                throw new IllegalArgumentException(
                        "Part "
                                + part.partId()
                                + " has no pure native animation "
                                + selection.animation()
                                + "; choose a mapping pose or keep its native held frame");
            }
            SpriteAnimationSet set = new SpriteAnimationSet();
            for (var animation : part.animations()) {
                set.addScript(
                        animation.id(),
                        new SpriteAnimationScript(
                                animation.delay(),
                                animation.frames(),
                                SpriteAnimationEndAction.valueOf(animation.endAction()),
                                animation.endParam()));
            }
            ObjectAnimationState state =
                    new ObjectAnimationState(set, selection.animation(), part.defaultPose());
            Map<ObjectAnimationState, Integer> seen = new HashMap<>();
            int frame = 0;
            while (frame <= direction.animationFrame()) {
                Integer previous = seen.putIfAbsent(state.copyForRewind(), frame);
                if (previous != null) {
                    int cycle = frame - previous;
                    int skip = (direction.animationFrame() + 1 - frame) / cycle;
                    if (skip > 0) {
                        frame += skip * cycle;
                        continue;
                    }
                }
                state.update();
                frame++;
            }
            pose = state.getMappingFrame();
        }
        int target = pose < 0 ? part.defaultPose() : pose;
        return part.poses().stream()
                .filter(p -> p.pose() == target)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Part "
                                                + part.partId()
                                                + " has no native mapping pose "
                                                + target));
    }

    boolean addRenderEntries(
            ObjectInstance object,
            List<ObjectRenderParts.RenderEntry>[] unified,
            List<ObjectRenderParts.RenderEntry>[] low,
            List<ObjectRenderParts.RenderEntry>[] high) {
        Directed state = membership.get(object);
        if (state == null) {
            return false;
        }
        if (state.root != object) {
            return true;
        }
        Set<String> entries = new HashSet<>();
        for (var part : state.template.parts()) {
            int slot = renderSlot(state, part);
            String key = part.bucket() + ":" + part.highPriority() + ":" + slot;
            if (!entries.add(key)) {
                continue;
            }
            int index = RenderPriority.clamp(part.bucket()) - RenderPriority.MIN;
            var entry = new ObjectRenderParts.RenderEntry(object, part.highPriority(), slot);
            unified[index].add(entry);
            (part.highPriority() ? high[index] : low[index]).add(entry);
        }
        return true;
    }

    void addVisualRenderEntries(
            List<ObjectRenderParts.RenderEntry>[] unified,
            List<ObjectRenderParts.RenderEntry>[] low,
            List<ObjectRenderParts.RenderEntry>[] high) {
        for (var visual : visuals.values()) {
            addRenderEntries(visual, unified, low, high);
        }
    }

    private int renderSlot(Directed state, VisualPart part) {
        if (!(state.root instanceof VisualObject)) {
            return part.slotOrder();
        }
        int first = state.template.parts().stream().mapToInt(VisualPart::slotOrder).min().orElse(0);
        return Integer.MAX_VALUE - 65536 + Math.min(65535, Math.max(0, part.slotOrder() - first));
    }

    boolean drawReplacement(ObjectInstance object, int bucket, boolean high, int slot) {
        Directed state = membership.get(object);
        if (state == null) {
            return false;
        }
        if (object == state.root) {
            draw(state, bucket, high, slot);
        }
        return true;
    }

    private void draw(Directed state, int bucket, boolean high, int slot) {
        if (!state.direction.visible()) {
            return;
        }
        GraphicsManager graphics = objects.services().graphicsManager();
        if (state.art == null) {
            state.art = sharedArt.computeIfAbsent(state.template.patterns(), SharedArt::new);
            state.art.references++;
        }
        if (!ObjectVisualGraphicsAccess.validArt(state.art.bank, graphics, state.art)) {
            state.art.bank =
                    ObjectVisualGraphicsAccess.acquireArt(
                            graphics, state.art, state.template.patterns().size());
            int base = ObjectVisualGraphicsAccess.artBase(state.art.bank, graphics, state.art);
            graphics.beginPatternAtlasBatch();
            try {
                for (int i = 0; i < state.template.patterns().size(); i++) {
                    graphics.cachePatternTexture(
                            SpritePresentationRenderer.pattern(
                                    state.template.patterns().get(i).version()),
                            base + i);
                }
            } finally {
                graphics.endPatternAtlasBatch();
            }
        }
        if (state.colors == null) {
            state.colors =
                    sharedColors.computeIfAbsent(state.template.palettes(), SharedColors::new);
            state.colors.references++;
        }
        if (!ScopedPaletteGraphicsAccess.valid(state.colors.bank, graphics, state.colors)) {
            PaletteView[] palettes = new PaletteView[4];
            for (int i = 0; i < 4; i++) {
                List<Integer> rgb =
                        i < state.template.palettes().size()
                                ? state.template.palettes().get(i).argb()
                                : Collections.nCopies(16, 0);
                palettes[i] =
                        new PaletteView() {
                            public byte red(int c) {
                                return (byte) (rgb.get(c) >> 16);
                            }

                            public byte green(int c) {
                                return (byte) (rgb.get(c) >> 8);
                            }

                            public byte blue(int c) {
                                return (byte) (int) rgb.get(c);
                            }
                        };
            }
            try {
                state.colors.bank =
                        ScopedPaletteGraphicsAccess.acquire(graphics, state.colors, palettes);
            } catch (IllegalStateException full) {
                throw new IllegalStateException(
                        "Directed cast exceeds the bounded distinct palette snapshot budget; reuse"
                                + " an appearance phase or reduce simultaneous palette variants",
                        full);
            }
        }
        int patternBase = ObjectVisualGraphicsAccess.artBase(state.art.bank, graphics, state.art);
        int paletteBase =
                ScopedPaletteGraphicsAccess.base(state.colors.bank, graphics, state.colors);
        if (SpritePresentation.isPreparing(graphics)) {
            for (int i = 0; i < state.template.patterns().size(); i++) {
                SpritePresentation.bindPatternVersion(
                        graphics, patternBase + i, state.template.patterns().get(i).version());
            }
        }
        boolean previousHigh = graphics.getCurrentSpriteHighPriority();
        int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        graphics.flushPatternBatch();
        graphics.setCurrentSpriteHighPriority(high);
        graphics.setCurrentSpriteTileOcclusionPaletteMask(
                MultiBucketRenderable.tileOcclusionPaletteMask(high));
        try {
            for (var part : state.template.parts()) {
                if (part.bucket() != bucket
                        || part.highPriority() != high
                        || renderSlot(state, part) != slot) {
                    continue;
                }
                VisualPose pose = selectedPose(part, state.direction);
                List<VisualTile> tiles = pose.tiles();
                for (int index = graphics.isSpriteSatCollectionActive() ? tiles.size() - 1 : 0;
                        graphics.isSpriteSatCollectionActive() ? index >= 0 : index < tiles.size();
                        index += graphics.isSpriteSatCollectionActive() ? -1 : 1) {
                    VisualTile tile = tiles.get(index);
                    graphics.setCurrentSpriteTileOcclusionPaletteMask(tile.occlusionMask());
                    boolean flip = state.direction.flipX();
                    int x =
                            (int)
                                    Math.round(
                                            state.direction.x()
                                                    + (flip ? -tile.x() - tile.width() : tile.x()));
                    int y = (int) Math.round(state.direction.y() + tile.y());
                    int pattern = patternBase + tile.pattern(),
                            palette = paletteBase + tile.palette();
                    if (graphics.isSpriteSatCollectionActive()) {
                        graphics.submitSpriteSatPiece(
                                new SpritePieceRenderer.PreparedPiece(
                                        x,
                                        y,
                                        1,
                                        1,
                                        pattern,
                                        pattern & 0x7ff,
                                        palette,
                                        tile.flipX() ^ flip,
                                        tile.flipY(),
                                        tile.priority(),
                                        high,
                                        0,
                                        1));
                        ObjectVisualGraphicsAccess.attachLastVisualMask(
                                graphics, tile.occlusionMask());
                        ObjectVisualGraphicsAccess.clipLastSatTile(
                                graphics, tile.rowStart(), tile.rowEnd());
                    } else {
                        PatternDesc descriptor = new PatternDesc();
                        descriptor.setPaletteIndex(palette);
                        descriptor.setHFlip(tile.flipX() ^ flip);
                        descriptor.setVFlip(tile.flipY());
                        descriptor.setPriority(tile.priority());
                        if (tile.rowStart() != 0 || tile.rowEnd() != 8) {
                            var rows =
                                    new SpritePresentation.Tile(
                                            SpritePresentation.Layer.OBJECT,
                                            pattern,
                                            palette,
                                            tile.flipX() ^ flip,
                                            tile.flipY(),
                                            tile.priority(),
                                            x,
                                            y,
                                            8,
                                            8,
                                            tile.priorityShader(),
                                            tile.occlusionMask(),
                                            false,
                                            1,
                                            tile.rowStart(),
                                            tile.rowEnd());
                            SpritePresentation.renderTileRows(graphics, rows, descriptor, x, y);
                        } else {
                            graphics.renderPatternWithId(pattern, descriptor, x, y);
                        }
                    }
                }
            }
        } finally {
            graphics.flushPatternBatch();
            graphics.setCurrentSpriteHighPriority(previousHigh);
            graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
        }
    }

    public RewindSnapshottable<Snapshot> rewindSnapshottable() {
        return new RewindSnapshottable<>() {
            public String key() {
                return "stageplay-object-direction";
            }

            public Snapshot capture() {
                List<DirectedSnapshot> directed = new ArrayList<>();
                List<VisualSnapshot> visualSnapshots = new ArrayList<>();
                List<EdgeSnapshot> edges = new ArrayList<>();
                for (Directed root : roots.values()) {
                    if (root.root instanceof VisualObject visual) {
                        visualSnapshots.add(
                                new VisualSnapshot(visual.id, root.template, root.direction));
                        continue;
                    }
                    ObjectRefId id = objects.objectQuery().identityOf(root.root).orElseThrow();
                    List<ObjectRefId> members =
                            graph(root.root).stream()
                                    .map(o -> objects.objectQuery().identityOf(o))
                                    .flatMap(Optional::stream)
                                    .toList();
                    directed.add(new DirectedSnapshot(id, root.template, root.direction, members));
                }
                for (var entry : children.entrySet()) {
                    var parent = objects.objectQuery().identityOf(entry.getKey());
                    if (parent.isEmpty()) {
                        continue;
                    }
                    for (ChildLink link : entry.getValue()) {
                        objects.objectQuery()
                                .identityOf(link.child())
                                .ifPresent(
                                        id ->
                                                edges.add(
                                                        new EdgeSnapshot(
                                                                parent.get(), id, link.ordinal())));
                    }
                }
                directed.sort(java.util.Comparator.comparing(entry -> entry.root().toString()));
                visualSnapshots.sort(java.util.Comparator.comparing(VisualSnapshot::id));
                edges.sort(
                        Comparator.comparing((EdgeSnapshot edge) -> edge.parent().toString())
                                .thenComparingInt(EdgeSnapshot::ordinal));
                Map<ObjectRefId, Integer> nextOrdinals = new HashMap<>();
                for (var entry : nextChildOrdinals.entrySet()) {
                    objects.objectQuery()
                            .identityOf(entry.getKey())
                            .ifPresent(id -> nextOrdinals.put(id, entry.getValue()));
                }
                Map<ObjectRefId, String> authoredIds = new HashMap<>();
                for (var entry : authored.entrySet()) {
                    objects.objectQuery()
                            .identityOf(entry.getKey())
                            .ifPresent(id -> authoredIds.put(id, entry.getValue()));
                }
                return new Snapshot(
                        directed,
                        visualSnapshots,
                        edges,
                        nextOrdinals,
                        authoredIds,
                        loadPolicy.removed,
                        loadPolicy.clearObjects,
                        loadPolicy.clearBadniks,
                        nextVisualId);
            }

            public void restore(Snapshot snapshot) {
                Map<String, VisualObject> retained = new HashMap<>(visuals);
                close();
                loadPolicy.restorePolicy(
                        new StageplayObjectLoadPolicy.Snapshot(
                                snapshot.removed(),
                                snapshot.clearObjects(),
                                snapshot.clearBadniks()));
                nextVisualId = snapshot.nextVisualId();
                for (var entry : snapshot.authored().entrySet()) {
                    objects.objectQuery()
                            .resolve(entry.getKey())
                            .ifPresent(object -> claimAuthored(entry.getValue(), object));
                }
                for (var visual : snapshot.visuals()) {
                    VisualObject object =
                            retained.getOrDefault(visual.id(), new VisualObject(visual.id()));
                    visuals.put(visual.id(), object);
                    direct(object, visual.template(), visual.direction());
                }
                for (var entry : snapshot.nextChildOrdinals().entrySet()) {
                    objects.objectQuery()
                            .resolve(entry.getKey())
                            .ifPresent(parent -> nextChildOrdinals.put(parent, entry.getValue()));
                }
                for (var edge : snapshot.edges()) {
                    ObjectInstance parent =
                            objects.objectQuery().resolve(edge.parent()).orElse(null);
                    ObjectInstance child = objects.objectQuery().resolve(edge.child()).orElse(null);
                    if (parent != null && child != null) {
                        children.computeIfAbsent(parent, key -> new ArrayList<>())
                                .add(new ChildLink(child, edge.ordinal()));
                        childSpawned(parent, child);
                    }
                }
                for (var entry : snapshot.directed()) {
                    ObjectInstance root =
                            objects.objectQuery()
                                    .resolve(entry.root())
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "Native graph missing after rewind"
                                                                    + " restore"));
                    direct(root, entry.template(), entry.direction());
                    Directed owner = roots.get(root);
                    for (var id : entry.members()) {
                        objects.objectQuery()
                                .resolve(id)
                                .ifPresent(child -> membership.put(child, owner));
                    }
                }
            }

            public void resetForMissingSnapshot() {
                close();
            }
        };
    }

    @Override
    public void close() {
        roots.values().forEach(Directed::close);
        roots.clear();
        membership.clear();
        children.clear();
        nextChildOrdinals.clear();
        authored.clear();
        visuals.clear();
        nextVisualId = 0;
        objects.stageplayBucketsChanged();
    }
}

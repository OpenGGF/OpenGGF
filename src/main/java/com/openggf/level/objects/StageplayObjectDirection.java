package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.SpritePresentation;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.animation.SpriteAnimationScript;

import java.util.*;

/**
 * Picture-only object direction. Native RAM, terrain and object routines are never driven by these
 * transforms. Releasing direction resumes the frozen native graph at its native state/anchor. A new
 * directed cast member supplies a baked template; its factory must not run in the shared scene
 * merely to obtain a picture.
 */
public final class StageplayObjectDirection implements AutoCloseable {
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
    public record PlacementIdentity(String gameId, int zone, int act, int layoutIndex, int objectId, int subtype) {
        public PlacementIdentity {
            Objects.requireNonNull(gameId);
            if (gameId.isBlank() || zone < 0 || act < 0 || layoutIndex < 0
                    || objectId < 0 || objectId > 255 || subtype < 0 || subtype > 255) {
                throw new IllegalArgumentException("Invalid portable object identity");
            }
        }

        public static PlacementIdentity of(String gameId, int zone, int act, ObjectSpawn spawn) {
            return new PlacementIdentity(gameId, zone, act, spawn.layoutIndex(), spawn.objectId(), spawn.subtype());
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


    public record VisualSnapshot(String id, VisualTemplate template, Direction direction) {}

    public record Snapshot(
            List<DirectedSnapshot> directed,
            List<VisualSnapshot> visuals,
            List<EdgeSnapshot> edges,
            Map<ObjectRefId, Integer> nextChildOrdinals,
            Map<ObjectRefId, String> authored,
            long nextVisualId) {
        public Snapshot {
            directed = List.copyOf(directed);
            visuals = List.copyOf(visuals);
            edges = List.copyOf(edges);
            nextChildOrdinals = Map.copyOf(nextChildOrdinals);
            authored = Map.copyOf(authored);
        }
    }

    static final class VisualObject implements ObjectInstance {
        final String id;

        VisualObject(String id) {
            this.id = id;
        }

        public ObjectSpawn getSpawn() {
            return new ObjectSpawn(0, 0, 0, 0, 0, false, 0);
        }

        public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {}

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

    final ObjectManager objects;
    private final StageplayObjectDraw drawing;
    private final StageplayObjectGraph graphState;
    final IdentityHashMap<ObjectInstance, List<StageplayObjectGraph.ChildLink>> children;
    final IdentityHashMap<ObjectInstance, Integer> nextChildOrdinals;
    final IdentityHashMap<ObjectInstance, StageplayObjectDraw.Directed> roots = new IdentityHashMap<>();
    final IdentityHashMap<ObjectInstance, StageplayObjectDraw.Directed> membership = new IdentityHashMap<>();
    final LinkedHashMap<String, VisualObject> visuals = new LinkedHashMap<>();
    long nextVisualId;
    private final StageplayObjectRewind rewind;
    private final StageplayObjectLoadPolicy loadPolicy;
    private final StageplayObjectAdmission admission;
    final IdentityHashMap<ObjectInstance, String> authored;

    StageplayObjectDirection(ObjectManager objects) {
        this(objects, new StageplayObjectLoadPolicy());
    }

    StageplayObjectDirection(ObjectManager objects, StageplayObjectLoadPolicy loadPolicy) {
        this.objects = objects;
        this.loadPolicy = loadPolicy;
        graphState = new StageplayObjectGraph(this);
        children = graphState.children;
        nextChildOrdinals = graphState.nextChildOrdinals;
        authored = graphState.authored;
        drawing = new StageplayObjectDraw(this);
        rewind = new StageplayObjectRewind(this);
        admission = new StageplayObjectAdmission(this, loadPolicy);
    }

    void childSpawned(ObjectInstance parent, ObjectInstance child) { graphState.childSpawned(parent, child); }
    void retired(ObjectInstance object) { graphState.retired(object); }
    List<ObjectInstance> graph(ObjectInstance root) { return graphState.graph(root); }
    Map<ObjectInstance, String> structuralPaths(ObjectInstance root) { return graphState.structuralPaths(root); }

    public Optional<ObjectInstance> findPlaced(PlacementIdentity identity) {
        return objects.findStageplayPlacement(identity);
    }

    public PlacementIdentity placementIdentity(ObjectSpawn spawn) {
        return objects.stageplayPlacementIdentity(spawn);
    }

    public boolean allowsPlacement(ObjectSpawn spawn) {
        if (spawn == null) {
            return true;
        }
        if (loadPolicy.clearObjects && !objects.isStageplayRingPlacement(spawn)) {
            return false;
        }
        if (loadPolicy.clearBadniks && admission.isBadnik(spawn)) {
            return false;
        }
        return spawn.layoutIndex() < 0 || !loadPolicy.removed.contains(placementIdentity(spawn));
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

    public boolean allowsObject(ObjectInstance object) { return admission.allowsObject(object); }
    void noteNativeAdmission(ObjectInstance object) { admission.noteNativeAdmission(object); }
    String authoredId(ObjectInstance object) { return admission.authoredId(object); }
    public void claimAuthored(ObjectInstance root) { admission.claimAuthored(root); }
    public void claimAuthored(String actorId, ObjectInstance root) { admission.claimAuthored(actorId, root); }
    public <T> T withAuthoredAdmission(String actorId, java.util.function.Supplier<T> action) {
        return admission.withAuthoredAdmission(actorId, action);
    }
    <T> T explicitNativeAdmission(java.util.function.Supplier<T> action) { return admission.explicitNativeAdmission(action); }

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
        StageplayObjectDraw.Directed old = roots.get(root);
        if (old != null && old.template == template) {
            old.direction = direction;
            return;
        }
        for (ObjectInstance member : graph(root)) {
            StageplayObjectDraw.Directed existing = membership.get(member);
            if (existing != null && existing != old) {
                throw new IllegalStateException("Native graph is already directed by another root");
            }
        }
        releaseDirection(root);
        StageplayObjectDraw.Directed state = drawing.new Directed(root, template, direction);
        roots.put(root, state);
        for (ObjectInstance member : graph(root)) {
            membership.put(member, state);
            objects.suspendStageplayContacts(member);
        }
        objects.stageplayBucketsChanged();
    }

    public void setDirection(ObjectInstance root, Direction direction) {
        StageplayObjectDraw.Directed state = roots.get(root);
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
        StageplayObjectDraw.Directed state = roots.remove(root);
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
            if (admission.isBadnik(object)) {
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
        return new StageplayObjectCapture(this, root, objects.services().graphicsManager()).capture();
    }

    static List<VisualAnimation> pureAnimations(SpriteAnimationSet source, int frameCount) {
        return StageplayObjectCapture.pureAnimations(source, frameCount);
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

    boolean addRenderEntries(ObjectInstance object, List<ObjectRenderParts.RenderEntry>[] unified,
            List<ObjectRenderParts.RenderEntry>[] low, List<ObjectRenderParts.RenderEntry>[] high) {
        return drawing.addRenderEntries(object, unified, low, high);
    }
    void addVisualRenderEntries(List<ObjectRenderParts.RenderEntry>[] unified,
            List<ObjectRenderParts.RenderEntry>[] low, List<ObjectRenderParts.RenderEntry>[] high) {
        drawing.addVisualRenderEntries(unified, low, high);
    }
    boolean drawReplacement(ObjectInstance object, int bucket, boolean high, int slot) {
        return drawing.drawReplacement(object, bucket, high, slot);
    }

    public RewindSnapshottable<Snapshot> rewindSnapshottable() { return rewind; }

    @Override
    public void close() {
        roots.values().forEach(StageplayObjectDraw.Directed::close);
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

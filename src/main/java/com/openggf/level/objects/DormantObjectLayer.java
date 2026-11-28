package com.openggf.level.objects;

import com.openggf.game.rewind.RewindPresentationProbeAccess;
import com.openggf.camera.CameraPresentationAccess;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenDormantStyle;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.render.SpriteSlotEmission;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.PatternDesc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NATIVE widescreen draw mode's presentation-only layer: layout objects the native
 * 320-pixel loader has not loaded (or has unloaded) are drawn in the extra display
 * width, outside the object slot system.
 *
 * <p>Each pose is baked from detached copies of the object, constructed from its layout
 * spawn with the camera centred on it ({@link DormantPoseLoop}): a stationary copy whose
 * render output loops within {@link #MAX_LOOP_UPDATES} updates keeps one copy per frame of
 * the loop and plays it back on this layer's own presentation tick; any other copy is
 * frozen after one update. All side effects are rolled back by one rewind capture/restore
 * around the whole bake, with audio requests suppressed and child spawns unregistered. A
 * copy that cannot be constructed (no registry entry or a placeholder) has no pose; a
 * failure while constructing, updating or drawing one propagates like any engine failure.
 *
 * <p>Live objects always win. When the native loader unloads a live object that stands
 * at its spawn position, its last render state is kept (derived, never snapshotted, and
 * forgotten on rewind restore, reset and layout replacement) and drawn instead of a frozen
 * pose; a looping pose keeps looping. Moving objects return to the spawn pose, where the
 * ROM reloads them. Nothing is drawn once the native latches say it will never reload.
 *
 * <p>See docs/architecture/designs/2026-11-13-widescreen-native-draw-mode.md.
 */
final class DormantObjectLayer {
    /** Dormant entries sort after every live SST slot, so they draw behind live objects. */
    static final int SLOT_BASE = 0x10000;
    /** Allowance for sprite extents around a spawn's centre. */
    static final int MARGIN = 64;
    /** Longest window a pose bake watches for an animation loop, in updates. */
    static final int MAX_LOOP_UPDATES = 64;
    /** Copy updates one frame boundary may spend baking; keeps a slice well inside one frame (D18). */
    static final int SLICE_UPDATES = 512;
    private static final int BUCKET_COUNT = RenderPriority.MAX - RenderPriority.MIN + 1;

    /** One drawable state: a detached instance and the render buckets recorded for it. */
    record Frame(ObjectInstance instance, List<ObjectRenderParts.RenderEntry>[] buckets) { }

    /** A spawn's baked presentation; {@code frames[segmentAt[tick % length]]} draws at a tick. */
    record Pose(ObjectSpawn spawn, Frame[] frames, int[] segmentAt) {
        boolean looped() {
            return segmentAt.length > 1;
        }

        Frame frameAt(long tick) {
            return frames[segmentAt[(int) Math.floorMod(tick, (long) segmentAt.length)]];
        }
    }

    /** Render output compared by the loop search: bucket/priority keys plus the prepared tiles. */
    private record RenderSignature(List<Integer> entries, SpritePresentation.Frame frame) { }

    private static final java.util.function.Function<Object, SpritePresentation.Attributes> DESCRIPTORS =
            descriptor -> {
                PatternDesc desc = (PatternDesc) descriptor;
                return new SpritePresentation.Attributes(desc.getPaletteIndex(), desc.getHFlip(),
                        desc.getVFlip(), desc.getPriority());
            };

    private final ObjectManager manager;
    private final List<Pose> poses = new ArrayList<>();
    private final Map<Object, Frame> lastStates = new HashMap<>();
    private final List<GLCommand> scratch = new ArrayList<>();
    private List<ObjectSpawn> bakedFor;
    private boolean baking;
    /** Presentation clock for baked loops; advances once per frame boundary, never read by gameplay. */
    private long presentationTick;
    /** Index of the next layout spawn to bake. */
    private int nextSpawn;
    private int loopedPoses;
    private int bakedUpdates;
    private int bakeSlices;

    DormantObjectLayer(ObjectManager manager) {
        this.manager = manager;
    }

    /** NATIVE draw mode on a display wider than the 320-pixel simulation window. */
    boolean active() {
        Camera camera = manager.camera();
        return camera != null && CameraPresentationAccess.hasMargin(camera) && style() != WidescreenDormantStyle.HIDDEN;
    }

    private WidescreenDormantStyle style() {
        var config = manager.objectServices == null ? null : manager.objectServices.configuration();
        return config == null ? WidescreenDormantStyle.NORMAL
                : WidescreenDormantStyle.parse(config.getString(SonicConfiguration.WIDESCREEN_DORMANT_STYLE));
    }

    int poseCount() {
        return poses.size();
    }

    int loopedPoseCount() {
        return loopedPoses;
    }

    int lastStateCount() {
        return lastStates.size();
    }

    String bakeSummary() {
        int frames = 0;
        for (Pose pose : poses) frames += pose.frames().length;
        return "poses=" + poses.size() + " looped=" + loopedPoses + " keptFrames=" + frames
                + " updates=" + bakedUpdates + " slices=" + bakeSlices;
    }

    List<Pose> poses() {
        return List.copyOf(poses);
    }

    /** Whether every spawn of the current layout has been baked. */
    boolean bakeComplete() {
        return bakedFor != null && nextSpawn >= bakedFor.size();
    }

    /**
     * Bakes the next slice of the current layout's poses at a frame boundary (before the
     * frame's work runs), so its rewind bracket restores exactly the state the frame starts
     * from. A slice ends once it has spent {@link #SLICE_UPDATES} copy updates, keeping each
     * frame's stall bounded; the next boundary continues with the next spawn. Locked
     * title-card iterations call this too, so a level's bake normally completes while its
     * gameplay is frozen (D18). Also advances the presentation tick that baked loops play on.
     */
    void prepareAtFrameBoundary(RewindRegistry registry) {
        if (baking || !active()) return;
        presentationTick++;
        List<ObjectSpawn> spawns = manager.getAllSpawns();
        if (spawns != bakedFor) {
            poses.clear();
            lastStates.clear();
            loopedPoses = 0;
            bakedUpdates = 0;
            bakeSlices = 0;
            nextSpawn = 0;
            bakedFor = spawns;
        }
        if (registry == null || nextSpawn >= spawns.size()) return;
        baking = true;
        bakeSlices++;
        var audio = manager.objectServices.audioManager();
        try (var silenced = audio == null ? null : audio.beginRewindReplay(0, 0, AudioReplayReason.SEEK)) {
            var snapshot = registry.capture();
            try {
                GraphicsManager graphics = signatureGraphics();
                int sliceStart = bakedUpdates;
                while (nextSpawn < spawns.size() && bakedUpdates - sliceStart < SLICE_UPDATES) {
                    Pose pose = probe(spawns.get(nextSpawn++), graphics);
                    if (pose != null) poses.add(pose);
                }
            } finally {
                RewindPresentationProbeAccess.restoreAfterProbe(registry, snapshot);
                manager.refreshCameraBoundsAfterPresentationProbe();
            }
        } finally {
            baking = false;
        }
    }

    /** Forget poses and last states; the next frame boundary bakes the current layout again. */
    void reset() {
        poses.clear();
        lastStates.clear();
        bakedFor = null;
        nextSpawn = 0;
    }

    /** Rewind restore or object reset: a recorded last state could be from a future frame. */
    void forgetLastStates() {
        lastStates.clear();
    }

    /**
     * The native loader is retiring {@code instance}. A stationary object (at its spawn
     * position) keeps its last render state as its dormant draw. Reads getters only: no
     * object logic runs and no state changes.
     */
    void recordUnloaded(ObjectSpawn spawn, ObjectInstance instance) {
        if (instance == null || baking || bakedFor == null || !active()) return;
        if (instance.isDestroyed() && !instance.isDestroyedRespawnable()) return;
        if (instance.getX() != spawn.x() || instance.getY() != spawn.y()) return;
        lastStates.put(stateKey(spawn), new Frame(instance, buckets(instance)));
    }

    /** S3K re-registers a reloaded entry under an occurrence key; the layout index names it. */
    private static Object stateKey(ObjectSpawn spawn) {
        return spawn.layoutIndex() >= 0 ? Integer.valueOf(spawn.layoutIndex()) : spawn;
    }

    /** Loop signatures need a sprite preparation of their own; without one every pose is frozen. */
    private GraphicsManager signatureGraphics() {
        GraphicsManager graphics = manager.objectServices == null ? null : manager.objectServices.graphicsManager();
        if (graphics == null || SpritePresentation.isPreparing(graphics) || graphics.isSpriteSatCollectionActive()) {
            return null;
        }
        return graphics;
    }

    private Pose probe(ObjectSpawn spawn, GraphicsManager graphics) {
        if (manager.registry == null || spawn.objectId() == 0) return null;
        Camera camera = manager.camera();
        // Centre the camera on the spawn so on-screen and out-of-range tests in the
        // object's first routine see it as the native loader would when admitting it.
        camera.setX((short) (spawn.x() - camera.getWidth() / 2));
        camera.setY((short) (spawn.y() - camera.getHeight() / 2));
        manager.refreshCameraBoundsAfterPresentationProbe();
        PlayableEntity player = camera.getFocusedSprite();
        int vint = manager.getVblaCounter();
        var plan = DormantPoseLoop.bake(() -> {
            ObjectInstance instance = construct(spawn);
            return instance == null ? null : new ProbeCopy(instance, player, vint, graphics);
        }, MAX_LOOP_UPDATES);
        if (plan == null) return null;
        bakedUpdates += plan.updates();
        if (plan.looped()) loopedPoses++;
        Frame[] frames = new Frame[plan.frames().size()];
        for (int i = 0; i < frames.length; i++) {
            ObjectInstance instance = plan.frames().get(i).instance;
            frames[i] = new Frame(instance, buckets(instance));
        }
        return new Pose(spawn, frames, plan.segmentAt());
    }

    private ObjectInstance construct(ObjectSpawn spawn) {
        ObjectInstance instance = ObjectConstructionContext.withProbeConstruction(() ->
                ObjectConstructionContext.with(manager.objectServices, -1,
                        () -> manager.objectCallbacks.construct(spawn, () -> manager.registry.create(spawn))));
        if (instance == null || instance instanceof PlaceholderObjectInstance) return null;
        if (instance instanceof AbstractObjectInstance object) {
            manager.runObjectCallback(object, () -> manager.bindObjectServices(object));
        }
        return instance;
    }

    /** One probe copy: updates run under probe construction; output is a prepared tile frame. */
    private final class ProbeCopy implements DormantPoseLoop.Copy {
        final ObjectInstance instance;
        private final PlayableEntity player;
        private final int vint;
        private final GraphicsManager graphics;

        ProbeCopy(ObjectInstance instance, PlayableEntity player, int vint, GraphicsManager graphics) {
            this.instance = instance;
            this.player = player;
            this.vint = vint;
            this.graphics = graphics;
        }

        @Override
        public void update(int tick) {
            ObjectConstructionContext.withProbeConstruction(() -> {
                manager.runObjectCallback(instance, () -> instance.update(vint + tick, player));
                return null;
            });
        }

        @Override
        public Object signature() {
            return graphics == null ? null : renderSignature(instance, graphics);
        }

        @Override
        public long position() {
            return ((long) instance.getX() << 32) | (instance.getY() & 0xFFFFFFFFL);
        }
    }

    private Object renderSignature(ObjectInstance instance, GraphicsManager graphics) {
        var buckets = buckets(instance);
        List<Integer> entries = new ArrayList<>();
        SpritePresentation.Frame frame;
        try {
            frame = SpritePresentation.prepare(graphics, 0, 0, () -> {
                for (int i = 0; i < BUCKET_COUNT; i++) {
                    int bucket = i + RenderPriority.MIN;
                    for (var entry : buckets[i]) {
                        entries.add(i << 1 | (entry.high() ? 1 : 0));
                        manager.runObjectCallback(instance,
                                () -> ObjectRenderParts.appendRenderCommands(entry, scratch, bucket));
                    }
                }
            }, DESCRIPTORS);
        } finally {
            scratch.clear();
        }
        // Primitive geometry has no value equality; such an object keeps a frozen pose.
        return frame.primitives().isEmpty() ? new RenderSignature(List.copyOf(entries), frame) : null;
    }

    private static List<ObjectRenderParts.RenderEntry>[] buckets(ObjectInstance instance) {
        @SuppressWarnings("unchecked")
        List<ObjectRenderParts.RenderEntry>[] unified = new List[BUCKET_COUNT];
        @SuppressWarnings("unchecked")
        List<ObjectRenderParts.RenderEntry>[] low = new List[BUCKET_COUNT];
        @SuppressWarnings("unchecked")
        List<ObjectRenderParts.RenderEntry>[] high = new List[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            unified[i] = new ArrayList<>();
            low[i] = new ArrayList<>();
            high[i] = new ArrayList<>();
        }
        ObjectRenderParts.addToRenderBuckets(instance, unified, low, high);
        return unified;
    }

    /** The state drawn for a pose this frame: its loop, else a recorded last state, else the pose. */
    Frame frameFor(Pose pose) {
        if (pose.looped()) return pose.frameAt(presentationTick);
        Frame last = lastStates.get(stateKey(pose.spawn()));
        return last != null ? last : pose.frames()[0];
    }

    /**
     * Adds this bucket's dormant draws after the live slot emissions. Candidates lie in
     * the presentation viewport but outside the native window, have no live instance and
     * are not retired by the native latches.
     */
    List<SpriteSlotEmission> withDormantEmissions(int bucket, GraphicsManager graphics,
                                                  List<SpriteSlotEmission> external) {
        if (poses.isEmpty() || baking || !active()) return external;
        Camera camera = manager.camera();
        int nativeLeft = camera.getX();
        int nativeRight = nativeLeft + camera.getWidth();
        int viewLeft = CameraPresentationAccess.leftWithShake(camera) - MARGIN;
        int viewRight = CameraPresentationAccess.leftWithShake(camera) + CameraPresentationAccess.width(camera) + MARGIN;
        int top = camera.getYWithShake() - MARGIN;
        int bottom = camera.getYWithShake() + camera.getHeight() + MARGIN;
        int wrap = camera.isVerticalWrapEnabled() ? camera.getVerticalWrapRange() : 0;
        int index = bucket - RenderPriority.MIN;
        boolean greyscale = style() == WidescreenDormantStyle.GREYSCALE;
        List<SpriteSlotEmission> merged = null;
        var placed = manager.population().placed;
        // S3K re-registers a reloaded entry under an occurrence key; match by layout index too.
        BitSet liveLayout = new BitSet();
        for (ObjectSpawn key : placed.keySet()) if (key.layoutIndex() >= 0) liveLayout.set(key.layoutIndex());
        for (int poseIndex = 0; poseIndex < poses.size(); poseIndex++) {
            Pose pose = poses.get(poseIndex);
            var entries = frameFor(pose).buckets()[index];
            if (entries.isEmpty()) continue;
            ObjectSpawn spawn = pose.spawn();
            int x = spawn.x();
            if (x < viewLeft || x >= viewRight || (x >= nativeLeft && x < nativeRight)) continue;
            if (!withinRows(spawn.y(), top, bottom, wrap)) continue;
            if (placed.containsKey(spawn) || (spawn.layoutIndex() >= 0 && liveLayout.get(spawn.layoutIndex()))
                    || manager.placement.isRetiredForPresentation(spawn)) continue;
            if (merged == null) merged = new ArrayList<>(external);
            for (var entry : entries) {
                int slot = SLOT_BASE + poseIndex;
                var dormant = new ObjectRenderParts.RenderEntry(entry.instance(), entry.high(), slot);
                merged.add(new SpriteSlotEmission(slot, bucket, ObjectRenderParts.tileOcclusionPaletteMask(dormant),
                        () -> draw(dormant, bucket, graphics, greyscale)));
            }
        }
        return merged == null ? external : merged;
    }

    private static boolean withinRows(int y, int top, int bottom, int wrap) {
        if (wrap <= 0) return y >= top && y < bottom;
        int span = bottom - top;
        return Math.floorMod(y - top, wrap) < span;
    }

    private void draw(ObjectRenderParts.RenderEntry entry, int bucket, GraphicsManager graphics,
                      boolean greyscale) {
        if (greyscale) {
            graphics.flushPatternBatch();
            graphics.beginGhostRenderEffect(1.0f, false);
            graphics.beginPatternBatch();
        }
        try {
            scratch.clear();
            // Ring objects (S1) draw through the live ring manager, on the live rings' spin frame.
            manager.runObjectCallback(entry.instance(),
                    () -> ObjectRenderParts.appendRenderCommands(entry, scratch, bucket, manager.objectServices));
        } finally {
            scratch.clear();
            if (greyscale) {
                graphics.flushPatternBatch();
                graphics.endGhostRenderEffect();
                graphics.beginPatternBatch();
            }
        }
    }
}

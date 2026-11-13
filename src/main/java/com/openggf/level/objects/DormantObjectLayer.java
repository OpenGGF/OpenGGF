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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NATIVE widescreen draw mode's presentation-only layer: layout objects the native
 * 320-pixel loader has not loaded (or has unloaded) are drawn frozen in the extra
 * display width, outside the object slot system.
 *
 * <p>Each pose is a detached copy of the object, constructed from its layout spawn
 * and run for one update with the camera centred on it, then never updated again.
 * Its side effects are rolled back by one rewind capture/restore around the whole
 * bake, with audio requests suppressed and child spawns unregistered. A copy that
 * fails to construct, update or draw has no pose. Live objects always win; once the
 * native loader unloads an object its frozen pose is drawn again, unless the native
 * latches say it will never reload.
 *
 * <p>See docs/architecture/designs/2026-11-13-widescreen-native-draw-mode.md.
 */
final class DormantObjectLayer {
    private static final Logger LOG = Logger.getLogger(DormantObjectLayer.class.getName());
    /** Dormant entries sort after every live SST slot, so they draw behind live objects. */
    static final int SLOT_BASE = 0x10000;
    /** Allowance for sprite extents around a spawn's centre. */
    static final int MARGIN = 64;
    private static final int BUCKET_COUNT = RenderPriority.MAX - RenderPriority.MIN + 1;

    record Pose(ObjectSpawn spawn, ObjectInstance instance, List<ObjectRenderParts.RenderEntry>[] buckets) { }

    private final ObjectManager manager;
    private final List<Pose> poses = new ArrayList<>();
    private final BitSet broken = new BitSet();
    private final List<GLCommand> scratch = new ArrayList<>();
    private List<ObjectSpawn> bakedFor;
    private boolean baking;

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
        return config == null ? WidescreenDormantStyle.GREYSCALE
                : WidescreenDormantStyle.parse(config.getString(SonicConfiguration.WIDESCREEN_DORMANT_STYLE));
    }

    int poseCount() {
        return poses.size();
    }

    List<Pose> poses() {
        return List.copyOf(poses);
    }

    /**
     * Bakes poses for the current layout once, at a frame boundary (before the frame's
     * gameplay runs), so the rewind bracket restores exactly the state the frame starts from.
     */
    void prepareAtFrameBoundary(RewindRegistry registry) {
        if (baking || !active()) return;
        List<ObjectSpawn> spawns = manager.getAllSpawns();
        if (spawns == bakedFor) return;
        poses.clear();
        broken.clear();
        bakedFor = spawns;
        if (registry == null || spawns.isEmpty()) return;
        baking = true;
        var audio = manager.objectServices.audioManager();
        try (var silenced = audio == null ? null : audio.beginRewindReplay(0, 0, AudioReplayReason.SEEK)) {
            var snapshot = registry.capture();
            try {
                for (ObjectSpawn spawn : spawns) {
                    Pose pose = probe(spawn);
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

    /** Forget poses; the next frame boundary bakes the current layout again. */
    void reset() {
        poses.clear();
        broken.clear();
        bakedFor = null;
    }

    private Pose probe(ObjectSpawn spawn) {
        if (manager.registry == null || spawn.objectId() == 0) return null;
        Camera camera = manager.camera();
        try {
            // Centre the camera on the spawn so on-screen and out-of-range tests in the
            // object's first routine see it as the native loader would when admitting it.
            camera.setX((short) (spawn.x() - camera.getWidth() / 2));
            camera.setY((short) (spawn.y() - camera.getHeight() / 2));
            manager.refreshCameraBoundsAfterPresentationProbe();
            ObjectInstance instance = ObjectConstructionContext.withProbeConstruction(() ->
                    ObjectConstructionContext.with(manager.objectServices, -1,
                            () -> manager.objectCallbacks.construct(spawn, () -> manager.registry.create(spawn))));
            if (instance == null || instance instanceof PlaceholderObjectInstance) return null;
            if (instance instanceof AbstractObjectInstance object) {
                manager.runObjectCallback(object, () -> manager.bindObjectServices(object));
            }
            PlayableEntity player = camera.getFocusedSprite();
            int vint = manager.getVblaCounter();
            ObjectConstructionContext.withProbeConstruction(() -> {
                manager.runObjectCallback(instance, () -> instance.update(vint, player));
                return null;
            });
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
            return new Pose(spawn, instance, unified);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            LOG.log(Level.FINE, failure, () -> "No dormant pose for object 0x"
                    + Integer.toHexString(spawn.objectId()) + " at " + spawn.x() + "," + spawn.y());
            return null;
        }
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
            if (broken.get(poseIndex)) continue;
            var entries = pose.buckets()[index];
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
                int poseSlot = poseIndex;
                merged.add(new SpriteSlotEmission(slot, bucket, ObjectRenderParts.tileOcclusionPaletteMask(dormant),
                        () -> draw(dormant, bucket, graphics, greyscale, poseSlot)));
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
                      boolean greyscale, int poseIndex) {
        if (greyscale) {
            graphics.flushPatternBatch();
            graphics.beginGhostRenderEffect(1.0f, false);
            graphics.beginPatternBatch();
        }
        try {
            scratch.clear();
            manager.runObjectCallback(entry.instance(),
                    () -> ObjectRenderParts.appendRenderCommands(entry, scratch, bucket, manager.objectServices));
        } catch (RuntimeException failure) {
            broken.set(poseIndex);
            LOG.log(Level.FINE, failure, () -> "Dormant pose stopped drawing: " + entry.instance());
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

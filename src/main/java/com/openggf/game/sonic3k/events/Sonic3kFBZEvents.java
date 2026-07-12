package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canonical mutable FBZ event workspace.
 *
 * <p>This task intentionally establishes only the ROM-shaped state/ownership
 * skeleton. Act 1 layout mutation and Act 2 boss behavior are ported by their
 * later route tasks.
 */
public final class Sonic3kFBZEvents extends Sonic3kZoneEvents {
    public enum RedrawDirection { NONE, TOP_DOWN, BOTTOM_UP, LEFT_TO_RIGHT, RIGHT_TO_LEFT }
    public enum MagneticPolarity { NEUTRAL, ATTRACT, REPEL }
    public enum PlaneAssignmentMode { NORMAL, REVERSED }
    public enum CollisionMode { FOREGROUND_ONLY, FOREGROUND_AND_BACKGROUND }

    private int act;
    private int foregroundLayoutRegion;
    private boolean foregroundOutdoor;
    private boolean backgroundOutdoor;
    private int backgroundRedrawStage;
    private RedrawDirection backgroundRedrawDirection = RedrawDirection.NONE;
    private int outdoorBobOffset;
    /** ROM HScroll_table+$1FC: one 32-bit accumulator shared by every FBZ deform mode. */
    private int hScrollAccumulator;
    private boolean hScrollAccumulatorSampled;
    private int hScrollAccumulatorLastFrame;
    private int hScrollAccumulatorLastRead;
    private MagneticPolarity magneticPolarity = MagneticPolarity.NEUTRAL;
    private int magneticTimerPhase;
    private boolean magneticEdgeObserved;
    private int magneticLastEdgeFrame;
    private int act2ForegroundStage;
    private int bossBackgroundStage;
    private int bossBackgroundOffsetX;
    private int bossBackgroundOffsetY;
    private boolean bossLoadPositionAdjustmentPending;
    private final ObjectRefId[] cloudRewindIds = new ObjectRefId[Sonic3kConstants.FBZ_CLOUD_REWIND_SLOT_COUNT];
    private boolean cloudCleanupTerminal;
    private PlaneAssignmentMode planeAssignmentMode = PlaneAssignmentMode.NORMAL;
    private CollisionMode collisionMode = CollisionMode.FOREGROUND_ONLY;
    private int collisionCameraDiffX;
    private int collisionCameraDiffY;
    private boolean screenShakeActive;
    private int screenShakeOffset;
    private int screenShakePhase;
    private boolean eventsFg5;

    @Override
    public void init(int act) {
        if (act < 0 || act > 1) throw new IllegalArgumentException("FBZ act must be 0 or 1: " + act);
        super.init(act);
        this.act = act;
        foregroundLayoutRegion = 0;
        foregroundOutdoor = false;
        backgroundOutdoor = false;
        backgroundRedrawStage = 0;
        backgroundRedrawDirection = RedrawDirection.NONE;
        outdoorBobOffset = 0;
        hScrollAccumulator = 0;
        hScrollAccumulatorSampled = false;
        hScrollAccumulatorLastFrame = 0;
        hScrollAccumulatorLastRead = 0;
        magneticPolarity = MagneticPolarity.NEUTRAL;
        magneticTimerPhase = 0;
        magneticEdgeObserved = false;
        magneticLastEdgeFrame = 0;
        act2ForegroundStage = 0;
        bossBackgroundStage = 0;
        bossBackgroundOffsetX = 0;
        bossBackgroundOffsetY = 0;
        bossLoadPositionAdjustmentPending = false;
        Arrays.fill(cloudRewindIds, null);
        cloudCleanupTerminal = false;
        planeAssignmentMode = PlaneAssignmentMode.NORMAL;
        collisionMode = CollisionMode.FOREGROUND_ONLY;
        collisionCameraDiffX = 0;
        collisionCameraDiffY = 0;
        screenShakeActive = false;
        screenShakeOffset = 0;
        screenShakePhase = 0;
        eventsFg5 = false;
    }

    @Override public void update(int act, int frameCounter) {
        if (act != this.act) throw new IllegalArgumentException("FBZ handler act mismatch: " + act);
    }

    public int getAct() { return act; }
    public int getForegroundLayoutRegion() { return foregroundLayoutRegion; }
    public void setForegroundLayoutRegion(int value) { validateLayoutRegion(act, value); foregroundLayoutRegion = value; }
    public boolean isForegroundOutdoor() { return foregroundOutdoor; }
    public void setForegroundOutdoor(boolean value) { foregroundOutdoor = value; }
    public boolean isBackgroundOutdoor() { return backgroundOutdoor; }
    public void setBackgroundOutdoor(boolean value) { backgroundOutdoor = value; }
    public int getBackgroundRedrawStage() { return backgroundRedrawStage; }
    public RedrawDirection getBackgroundRedrawDirection() { return backgroundRedrawDirection; }
    public void setBackgroundRedraw(int stage, RedrawDirection direction) {
        validateFourStepStage("background redraw", stage, 16);
        backgroundRedrawStage = stage;
        backgroundRedrawDirection = Objects.requireNonNull(direction, "direction");
    }
    public int getOutdoorBobOffset() { return outdoorBobOffset; }
    public void setOutdoorBobOffset(int value) { outdoorBobOffset = value; }
    public int getHScrollAccumulator() { return hScrollAccumulator; }
    public boolean isHScrollAccumulatorSampled() { return hScrollAccumulatorSampled; }
    public int getHScrollAccumulatorLastFrame() { return hScrollAccumulatorLastFrame; }
    public int getHScrollAccumulatorLastRead() { return hScrollAccumulatorLastRead; }

    /** ROM FBZ_Deform outdoor path: read old HScroll+$1FC, then add $E00 once. */
    public int sampleOutdoorHScrollAccumulator(int frameCounter) {
        return sampleHScrollAccumulator(frameCounter, 0xE00);
    }

    /** ROM FBZ2_CloudDeform: read old HScroll+$1FC >> 3, then add $8000 once. */
    public int sampleBossHScrollAccumulator(int frameCounter) {
        return sampleHScrollAccumulator(frameCounter, 0x8000) >> 3;
    }

    private int sampleHScrollAccumulator(int frameCounter, int increment) {
        if (hScrollAccumulatorSampled && hScrollAccumulatorLastFrame == frameCounter) {
            return hScrollAccumulatorLastRead;
        }
        hScrollAccumulatorLastRead = hScrollAccumulator;
        hScrollAccumulator += increment; // Java int overflow is the ROM's 32-bit wrap.
        hScrollAccumulatorLastFrame = frameCounter;
        hScrollAccumulatorSampled = true;
        return hScrollAccumulatorLastRead;
    }

    public void restoreHScrollAccumulatorState(int value, boolean sampled, int lastFrame, int lastRead) {
        hScrollAccumulator = value;
        hScrollAccumulatorSampled = sampled;
        hScrollAccumulatorLastFrame = lastFrame;
        hScrollAccumulatorLastRead = lastRead;
    }
    public MagneticPolarity getMagneticPolarity() { return magneticPolarity; }
    public int getMagneticTimerPhase() { return magneticTimerPhase; }
    public boolean isMagneticEdgeObserved() { return magneticEdgeObserved; }
    public int getMagneticLastEdgeFrame() { return magneticLastEdgeFrame; }
    public void setMagneticState(MagneticPolarity polarity, int phase) {
        if (phase < 0 || phase > 0xFF) throw new IllegalArgumentException("magnetic timer phase: " + phase);
        magneticPolarity = Objects.requireNonNull(polarity, "polarity");
        magneticTimerPhase = phase;
    }

    /** ROM AnPal_FBZ, guarded so a recompute of one frame cannot toggle twice. */
    public void advanceMagneticPhase(int frameCounter) {
        int phase = frameCounter & 0xFF;
        if (phase == 0 && (!magneticEdgeObserved || magneticLastEdgeFrame != frameCounter)) {
            magneticPolarity = magneticPolarity == MagneticPolarity.ATTRACT
                    ? MagneticPolarity.REPEL : MagneticPolarity.ATTRACT;
            magneticEdgeObserved = true;
            magneticLastEdgeFrame = frameCounter;
        }
        magneticTimerPhase = phase;
    }

    public void restoreMagneticState(MagneticPolarity polarity, int phase,
                                     boolean edgeObserved, int lastEdgeFrame) {
        setMagneticState(polarity, phase);
        magneticEdgeObserved = edgeObserved;
        magneticLastEdgeFrame = lastEdgeFrame;
    }
    public int getAct2ForegroundStage() { return act2ForegroundStage; }
    public void setAct2ForegroundStage(int stage) {
        requireAct2("foreground stage"); validateFourStepStage("Act 2 foreground", stage, 12); act2ForegroundStage = stage;
    }
    public int getBossBackgroundStage() { return bossBackgroundStage; }
    public int getBossBackgroundOffsetX() { return bossBackgroundOffsetX; }
    public int getBossBackgroundOffsetY() { return bossBackgroundOffsetY; }
    public void setBossBackgroundState(int stage, int x, int y) {
        requireAct2("boss background state"); validateFourStepStage("boss background", stage, 16);
        bossBackgroundStage = stage; bossBackgroundOffsetX = x; bossBackgroundOffsetY = y;
    }
    public void setBossBackgroundOffsets(int x, int y) {
        requireAct2("boss background offsets"); bossBackgroundOffsetX = x; bossBackgroundOffsetY = y;
    }
    public boolean isBossLoadPositionAdjustmentPending() { return bossLoadPositionAdjustmentPending; }
    public void setBossLoadPositionAdjustmentPending(boolean value) {
        requireAct2("boss position adjustment"); bossLoadPositionAdjustmentPending = value;
    }
    public ObjectRefId getCloudRewindId(int index) { return cloudRewindIds[checkedCloudIndex(index)]; }
    public List<ObjectRefId> getCloudRewindIds() {
        return Collections.unmodifiableList(Arrays.asList(cloudRewindIds.clone()));
    }
    public void setCloudRewindId(int index, ObjectRefId id) {
        requireAct2("cloud identity");
        if (cloudCleanupTerminal) throw new IllegalStateException("FBZ clouds are terminally cleaned up");
        cloudRewindIds[checkedCloudIndex(index)] = id;
    }
    public boolean isCloudCleanupTerminal() { return cloudCleanupTerminal; }
    public void setCloudCleanupTerminal(boolean value) {
        requireAct2("cloud cleanup terminal");
        if (cloudCleanupTerminal && !value) {
            throw new IllegalStateException("FBZ terminal cloud cleanup is monotonic outside rewind restore");
        }
        if (value) Arrays.fill(cloudRewindIds, null);
        cloudCleanupTerminal = value;
    }

    public void restoreCloudState(List<ObjectRefId> ids, boolean terminal) {
        requireAct2("cloud restore");
        Objects.requireNonNull(ids, "ids");
        if (ids.size() != cloudRewindIds.length) throw new IllegalArgumentException("FBZ cloud restore requires ten IDs");
        if (terminal && ids.stream().anyMatch(Objects::nonNull))
            throw new IllegalArgumentException("terminal FBZ cloud restore contains identities");
        ids.toArray(cloudRewindIds);
        cloudCleanupTerminal = terminal;
    }

    public void reconcileCloudsAfterObjectRestore(FbzCloudIdentityResolver resolver,
                                                   FbzCloudRecreationBatchFactory batchFactory) {
        Objects.requireNonNull(resolver, "resolver");
        if (cloudCleanupTerminal) return;
        ObjectRefId[] stagedIds = cloudRewindIds.clone();
        List<FbzCloudRecreationRequest> missing = new java.util.ArrayList<>();
        for (int i = 0; i < stagedIds.length; i++) {
            ObjectRefId id = stagedIds[i];
            if (id != null && !resolver.isLive(id)) missing.add(new FbzCloudRecreationRequest(i, id));
        }
        if (missing.isEmpty()) return;
        if (batchFactory == null) throw new IllegalStateException("Missing FBZ cloud recreation batch factory");
        FbzCloudRecreationBatch batch = Objects.requireNonNull(batchFactory.begin(List.copyOf(missing)), "batch");
        try {
            List<ObjectRefId> rebound = Objects.requireNonNull(batch.recreateAll(), "rebound IDs");
            if (rebound.size() != missing.size()) throw new IllegalStateException("FBZ cloud batch result count mismatch");
            for (int i = 0; i < missing.size(); i++) {
                FbzCloudRecreationRequest request = missing.get(i);
                ObjectRefId id = rebound.get(i);
                if (id == null) throw new IllegalStateException("FBZ cloud batch returned null identity");
                if (!request.stableId().equals(id)) throw new IllegalStateException("FBZ cloud stable identity changed");
            }
            batch.commit();
            resolver.refresh();
            for (int i = 0; i < missing.size(); i++) {
                FbzCloudRecreationRequest request = missing.get(i);
                ObjectRefId id = rebound.get(i);
                if (!resolver.isLive(id)) throw new IllegalStateException("Recreated FBZ cloud identity is not live: " + id);
                stagedIds[request.cloudIndex()] = id;
            }
        } catch (RuntimeException | Error failure) {
            try {
                batch.rollback();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        System.arraycopy(stagedIds, 0, cloudRewindIds, 0, stagedIds.length);
    }
    public PlaneAssignmentMode getPlaneAssignmentMode() { return planeAssignmentMode; }
    public CollisionMode getCollisionMode() { return collisionMode; }
    public int getCollisionCameraDiffX() { return collisionCameraDiffX; }
    public int getCollisionCameraDiffY() { return collisionCameraDiffY; }
    public void setPlaneAssignmentMode(PlaneAssignmentMode plane) {
        requireAct2("plane assignment mode");
        planeAssignmentMode = Objects.requireNonNull(plane, "plane");
    }
    public void setCollisionMode(CollisionMode collision, int diffX, int diffY) {
        requireAct2("collision mode");
        collision = Objects.requireNonNull(collision, "collision");
        if (collision == CollisionMode.FOREGROUND_ONLY && (diffX != 0 || diffY != 0)) {
            throw new IllegalArgumentException("foreground-only FBZ collision cannot retain background differences");
        }
        collisionMode = collision;
        collisionCameraDiffX = diffX;
        collisionCameraDiffY = diffY;
    }
    public boolean isScreenShakeActive() { return screenShakeActive; }
    public int getScreenShakeOffset() { return screenShakeOffset; }
    public int getScreenShakePhase() { return screenShakePhase; }
    public void setScreenShakeState(boolean active, int offset, int phase) {
        requireAct2("screen shake");
        if (phase < 0) throw new IllegalArgumentException("screen shake phase: " + phase);
        screenShakeActive = active; screenShakeOffset = offset; screenShakePhase = phase;
    }
    public boolean isEventsFg5() { return eventsFg5; }
    public void setEventsFg5(boolean value) { eventsFg5 = value; }

    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public void setDynamicResizeRoutine(int routine) { /* FBZ has no Dynamic_resize_routine authority. */ }

    private void requireAct2(String field) {
        if (act != 1) throw new IllegalArgumentException(field + " is invalid in FBZ Act " + (act + 1));
    }
    private static void validateLayoutRegion(int act, int value) {
        int max = act == 0 ? 24 : 4;
        validateFourStepStage("foreground layout region", value, max);
    }
    private static void validateFourStepStage(String name, int value, int max) {
        if (value < 0 || value > max || (value & 3) != 0) throw new IllegalArgumentException(name + " stage: " + value);
    }
    private static int checkedCloudIndex(int index) {
        if (index < 0 || index >= Sonic3kConstants.FBZ_CLOUD_REWIND_SLOT_COUNT)
            throw new IndexOutOfBoundsException("FBZ cloud index: " + index);
        return index;
    }
}

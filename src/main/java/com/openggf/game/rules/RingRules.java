package com.openggf.game.rules;

@com.openggf.game.ModApi
public record RingRules(
        int ringFloorCheckMask,
        int ringFloorCheckCounterPhase,
        boolean ringFloorProbeRequiresRenderFlag,
        boolean lostRingBoundaryChecksOnlyOnProbeCadence,
        int lostRingRenderVerticalMargin,
        int ringCollisionWidth,
        int ringCollisionHeight,
        boolean stageRingsUseObjectTouchCollection,
        boolean stageRingSweepUsesRawCameraWindow,
        boolean checkpointRestoresSavedRings) {

    public RingRules(int ringFloorCheckMask, int ringFloorCheckCounterPhase,
            boolean ringFloorProbeRequiresRenderFlag, boolean lostRingBoundaryChecksOnlyOnProbeCadence,
            int lostRingRenderVerticalMargin, int ringCollisionWidth, int ringCollisionHeight,
            boolean stageRingsUseObjectTouchCollection, boolean stageRingSweepUsesRawCameraWindow) {
        this(ringFloorCheckMask, ringFloorCheckCounterPhase, ringFloorProbeRequiresRenderFlag,
                lostRingBoundaryChecksOnlyOnProbeCadence, lostRingRenderVerticalMargin,
                ringCollisionWidth, ringCollisionHeight, stageRingsUseObjectTouchCollection,
                stageRingSweepUsesRawCameraWindow, false);
    }

    /** Compatibility name retained for the develop trace-fleet fixes. */
    public int lostRingRenderYMargin() {
        return lostRingRenderVerticalMargin;
    }
}

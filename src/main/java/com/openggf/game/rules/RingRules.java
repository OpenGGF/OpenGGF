package com.openggf.game.rules;

import com.openggf.game.ModApi;

@ModApi
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

    /** Compatibility name retained for the develop trace-fleet fixes. */
    public int lostRingRenderYMargin() {
        return lostRingRenderVerticalMargin;
    }
}

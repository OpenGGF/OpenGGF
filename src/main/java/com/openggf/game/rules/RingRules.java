package com.openggf.game.rules;

@com.openggf.game.ModApi
public record RingRules(
        int ringFloorCheckMask,
        int ringFloorCheckCounterPhase,
        boolean ringFloorProbeRequiresRenderFlag,
        int ringCollisionWidth,
        int ringCollisionHeight,
        boolean stageRingsUseObjectTouchCollection,
        boolean stageRingSweepUsesRawCameraWindow) {
    /** Compatibility constructor for API 1.1, whose floor probe phase was always zero. */
    public RingRules(int ringFloorCheckMask, boolean ringFloorProbeRequiresRenderFlag,
                     int ringCollisionWidth, int ringCollisionHeight,
                     boolean stageRingsUseObjectTouchCollection, boolean stageRingSweepUsesRawCameraWindow) {
        this(ringFloorCheckMask, 0, ringFloorProbeRequiresRenderFlag, ringCollisionWidth,
                ringCollisionHeight, stageRingsUseObjectTouchCollection, stageRingSweepUsesRawCameraWindow);
    }
}

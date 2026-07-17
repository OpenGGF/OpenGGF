package com.openggf.game.rules;

@com.openggf.game.ModApi
public record RingRules(
        int ringFloorCheckMask,
        int ringFloorCheckCounterPhase,
        boolean ringFloorProbeRequiresRenderFlag,
        int lostRingRenderVerticalMargin,
        int ringCollisionWidth,
        int ringCollisionHeight,
        boolean stageRingsUseObjectTouchCollection,
        boolean stageRingSweepUsesRawCameraWindow) {
    /** Compatibility constructor for API 1.2-2.4, before lost-ring render margins were configurable. */
    public RingRules(int ringFloorCheckMask, int ringFloorCheckCounterPhase,
                     boolean ringFloorProbeRequiresRenderFlag,
                     int ringCollisionWidth, int ringCollisionHeight,
                     boolean stageRingsUseObjectTouchCollection, boolean stageRingSweepUsesRawCameraWindow) {
        this(ringFloorCheckMask, ringFloorCheckCounterPhase, ringFloorProbeRequiresRenderFlag, 32,
                ringCollisionWidth, ringCollisionHeight,
                stageRingsUseObjectTouchCollection, stageRingSweepUsesRawCameraWindow);
    }

    /** Compatibility constructor for API 1.1, whose floor probe phase was always zero. */
    public RingRules(int ringFloorCheckMask, boolean ringFloorProbeRequiresRenderFlag,
                     int ringCollisionWidth, int ringCollisionHeight,
                     boolean stageRingsUseObjectTouchCollection, boolean stageRingSweepUsesRawCameraWindow) {
        this(ringFloorCheckMask, 0, ringFloorProbeRequiresRenderFlag, 32,
                ringCollisionWidth, ringCollisionHeight,
                stageRingsUseObjectTouchCollection, stageRingSweepUsesRawCameraWindow);
    }
}

package com.openggf.game.rules;

@com.openggf.game.ModApi
public record RingRules(
        int ringFloorCheckMask,
        boolean ringFloorProbeRequiresRenderFlag,
        int ringCollisionWidth,
        int ringCollisionHeight,
        boolean stageRingsUseObjectTouchCollection,
        boolean stageRingSweepUsesRawCameraWindow) {
}

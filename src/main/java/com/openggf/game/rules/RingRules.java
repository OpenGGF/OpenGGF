package com.openggf.game.rules;

public record RingRules(
        int ringFloorCheckMask,
        boolean ringFloorProbeRequiresRenderFlag,
        int ringCollisionWidth,
        int ringCollisionHeight,
        boolean stageRingsUseObjectTouchCollection,
        boolean stageRingSweepUsesRawCameraWindow) {
}

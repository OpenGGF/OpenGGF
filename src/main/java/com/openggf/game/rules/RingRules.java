package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record RingRules(
        int ringFloorCheckMask,
        boolean ringFloorProbeRequiresRenderFlag,
        int ringCollisionWidth,
        int ringCollisionHeight,
        boolean stageRingsUseObjectTouchCollection,
        boolean stageRingSweepUsesRawCameraWindow) {

    public static RingRules fromLegacy(PhysicsFeatureSet fs) {
        return new RingRules(
                fs.ringFloorCheckMask(),
                fs.ringFloorProbeRequiresRenderFlag(),
                fs.ringCollisionWidth(),
                fs.ringCollisionHeight(),
                fs.stageRingsUseObjectTouchCollection(),
                fs.stageRingSweepUsesRawCameraWindow());
    }
}

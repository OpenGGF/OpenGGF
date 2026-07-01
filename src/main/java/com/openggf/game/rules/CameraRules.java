package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record CameraRules(
        short lookScrollDelay,
        boolean waterShimmerEnabled,
        int fastScrollCap,
        boolean uncappedLeftwardHorizontalScroll,
        boolean useScreenYWrapValueForVisibility,
        boolean playerControlAppliesVerticalWrapMask) {

    public static CameraRules fromLegacy(PhysicsFeatureSet fs) {
        return new CameraRules(
                fs.lookScrollDelay(),
                fs.waterShimmerEnabled(),
                fs.fastScrollCap(),
                fs.uncappedLeftwardHorizontalScroll(),
                fs.useScreenYWrapValueForVisibility(),
                fs.playerControlAppliesVerticalWrapMask());
    }
}

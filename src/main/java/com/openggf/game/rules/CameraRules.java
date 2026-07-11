package com.openggf.game.rules;

@com.openggf.game.ModApi
public record CameraRules(
        short lookScrollDelay,
        boolean waterShimmerEnabled,
        int fastScrollCap,
        boolean uncappedLeftwardHorizontalScroll,
        boolean useScreenYWrapValueForVisibility,
        boolean playerControlAppliesVerticalWrapMask) {
}

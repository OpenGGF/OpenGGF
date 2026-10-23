package com.openggf.game.rules;

@com.openggf.game.ModApi
public record CameraRules(
        short lookScrollDelay,
        boolean waterShimmerEnabled,
        int fastScrollCap,
        boolean uncappedLeftwardHorizontalScroll,
        boolean useScreenYWrapValueForVisibility,
        boolean playerControlAppliesVerticalWrapMask,
        boolean groundedRollRecentersCameraBias) {

    /** Preserve the existing recentering behavior for callers of the six-argument API. */
    public CameraRules(short lookScrollDelay, boolean waterShimmerEnabled, int fastScrollCap,
                       boolean uncappedLeftwardHorizontalScroll,
                       boolean useScreenYWrapValueForVisibility,
                       boolean playerControlAppliesVerticalWrapMask) {
        this(lookScrollDelay, waterShimmerEnabled, fastScrollCap,
                uncappedLeftwardHorizontalScroll, useScreenYWrapValueForVisibility,
                playerControlAppliesVerticalWrapMask, true);
    }
}

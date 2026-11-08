package com.openggf.net.hub;

/** ROM-free numeric metadata for hub-side stream sanity checks. */
@com.openggf.game.ModApi
public record TrackValidationProfile(int levelWidthPx, int levelHeightPx,
                                     int maxSpeedPxPerFrame, int maxFramesPerSecond,
                                     int wrapWidthPx, int wrapHeightPx, int lapCount, int maxSwapSources) {
    public TrackValidationProfile(int levelWidthPx, int levelHeightPx, int maxSpeedPxPerFrame, int maxFramesPerSecond) {
        this(levelWidthPx, levelHeightPx, maxSpeedPxPerFrame, maxFramesPerSecond, 0, 0, 0, 0);
    }
    public static final int GLOBAL_SPEED_CEILING_PX_PER_FRAME = 32;
    public static final int FRAME_RATE_CAP = 60;
    public static final int BOUNDS_MARGIN_PX = 64;

    public TrackValidationProfile {
        if (levelWidthPx < 0 || levelHeightPx < 0 || maxSpeedPxPerFrame < 1
                || maxFramesPerSecond < 1 || wrapWidthPx < 0 || wrapHeightPx < 0
                || lapCount < 0 || lapCount > 5 || maxSwapSources < 0) {
            throw new IllegalArgumentException("invalid track validation profile");
        }
    }
}

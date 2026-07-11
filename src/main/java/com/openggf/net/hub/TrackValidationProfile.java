package com.openggf.net.hub;

/** ROM-free numeric metadata for hub-side stream sanity checks. */
@com.openggf.game.ModApi
public record TrackValidationProfile(int levelWidthPx, int levelHeightPx,
                                     int maxSpeedPxPerFrame, int maxFramesPerSecond) {
    public static final int GLOBAL_SPEED_CEILING_PX_PER_FRAME = 32;
    public static final int FRAME_RATE_CAP = 60;
    public static final int BOUNDS_MARGIN_PX = 64;

    public TrackValidationProfile {
        if (levelWidthPx < 0 || levelHeightPx < 0 || maxSpeedPxPerFrame < 1
                || maxFramesPerSecond < 1) {
            throw new IllegalArgumentException("invalid track validation profile");
        }
    }
}

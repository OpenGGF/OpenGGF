package com.openggf.game;

import java.util.Objects;

/**
 * Optional ending frames with their own sixteen-colour palette. Frame IDs retain the
 * native sequence selectors or mapping IDs; scenes keep their native timing.
 */
@ModApi
public record EndingSpriteSet(StageSpriteSet sprites, int[] rgb) {
    @ModApi public enum Surface {
        S1_ENDING, S2_ENDING, S2_FLOAT, S2_SUPER,
        /** Active first sidekick supplies selectors 1..3; selector 0 always stays native. */
        S2_PHOTOS,
        /** First sidekick cockpit crops, native Tails pilot mapping IDs 1..4/16, 24x16. */
        S2_TORNADO_PILOT,
        /** First sidekick supplies native Sonic-plane pilot selector 12. */
        S3K_TORNADO_PILOT,
        S3K_NORMAL_POSE, S3K_POWERED_POSE, S3K_PASSENGER, S3K_EYECATCH,
        S3K_PLAYER, S3K_POWERED_PLAYER
    }
    public EndingSpriteSet {
        Objects.requireNonNull(sprites, "sprites");
        if (rgb.length != 16) throw new IllegalArgumentException("Ending palette requires sixteen colours");
        rgb = rgb.clone();
    }
    @Override public int[] rgb() { return rgb.clone(); }
}

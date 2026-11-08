package com.openggf.game;

import java.util.Map;

/** Optional ending frames retain native mapping IDs and scene-owned timing. */
@ModApi
public record EndingSpriteSet(StageSpriteSet sprites, CharacterPresentation.Backing palette) {
    @ModApi public enum Surface {
        S3K_NORMAL_POSE, S3K_POWERED_POSE, S3K_PASSENGER, S3K_EYECATCH,
        S3K_PLAYER, S3K_POWERED_PLAYER
    }
    public EndingSpriteSet {
        java.util.Objects.requireNonNull(sprites, "sprites");
        java.util.Objects.requireNonNull(palette, "palette");
    }
}

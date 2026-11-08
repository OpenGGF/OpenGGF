package com.openggf.game;

import java.util.Objects;

/** Optional ending frames with their own palette. IDs retain the native sequence selectors. */
@ModApi
public record EndingSpriteSet(StageSpriteSet sprites, int[] rgb) {
    @ModApi public enum Surface { S1_ENDING, S2_ENDING, S2_FLOAT, S2_SUPER, S2_PHOTOS }
    public EndingSpriteSet {
        Objects.requireNonNull(sprites, "sprites");
        if (rgb.length != 16) throw new IllegalArgumentException("Ending palette requires sixteen colours");
        rgb = rgb.clone();
    }
    @Override public int[] rgb() { return rgb.clone(); }
}

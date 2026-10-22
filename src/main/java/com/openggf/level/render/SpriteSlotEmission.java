package com.openggf.level.render;

/** A frozen engine-owned render callback occupying a native SST slot, not a gameplay object. */
public record SpriteSlotEmission(int slot, int bucket, int tileOcclusionPaletteMask, Runnable draw) {
    public SpriteSlotEmission { java.util.Objects.requireNonNull(draw, "draw"); }
}

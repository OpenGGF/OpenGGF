package com.openggf.mods.objects;

import com.openggf.game.ModApi;

/**
 * Bounded native Caterkiller Jr body recipes. These presets configure only the
 * body graph; the native owner retains its ROM art, movement and touch behavior.
 * No arbitrary length, physics, renderer key or projectile timing is accepted.
 */
@ModApi
public enum CaterkillerJrRecipe {
    /** Shipped six-segment graph, including its literal native delays. */
    STOCK,
    /** Twelve segments: three firing tall, three middle, two thin, four tail. */
    DOUBLE_LENGTH
}

package com.openggf.game.sonic3k.difficulty;

import com.openggf.game.ModApi;

/** Finite opt-in patterns committed by the native AIZ ship's original bomb script. */
@ModApi
public enum AizBombingRunRecipe {
    STOCK,
    /** Stagger existing ports by eight pixels and shorten ordinary gaps; retain recovery rows. */
    STAGGERED_PORTS
}

package com.openggf.mods.ui;

/** Logical screen pixels, origin at the top left. Colours are {@code 0xAARRGGBB}. */
@com.openggf.game.ModApi
public interface PixelCanvas {
    int width();
    int height();
    /** Nonpositive rectangles and transparent colours have no visible effect. */
    void fill(int x, int y, int width, int height, int argb);
}

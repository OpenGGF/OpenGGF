package com.openggf.game.internal;

/** Render-only layout addressing for independently streamed background rows. */
public interface BackgroundRowLayout {
    int backgroundLayoutX(int sourceX, int sourceY);
}

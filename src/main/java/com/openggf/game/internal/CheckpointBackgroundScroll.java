package com.openggf.game.internal;

/** Immutable six-word native background camera bank saved by a checkpoint. */
public record CheckpointBackgroundScroll(int bgX, int bgY, int bg2X, int bg2Y, int bg3X, int bg3Y) {
    public CheckpointBackgroundScroll {
        bgX &= 0xFFFF;
        bgY &= 0xFFFF;
        bg2X &= 0xFFFF;
        bg2Y &= 0xFFFF;
        bg3X &= 0xFFFF;
        bg3Y &= 0xFFFF;
    }
}

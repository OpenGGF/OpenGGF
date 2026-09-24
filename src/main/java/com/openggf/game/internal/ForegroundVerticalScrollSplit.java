package com.openggf.game.internal;

/** Internal, render-only VSRAM write partway through a foreground scanout. */
public interface ForegroundVerticalScrollSplit {
    /** Null uses the ordinary foreground scroll for every scanline. */
    com.openggf.graphics.ForegroundScrollSplit foregroundVerticalScrollSplit();
}

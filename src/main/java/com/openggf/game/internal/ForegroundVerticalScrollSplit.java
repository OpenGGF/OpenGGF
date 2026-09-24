package com.openggf.game.internal;

/** Internal, render-only VSRAM write partway through a foreground scanout. */
public interface ForegroundVerticalScrollSplit {
    /** Null uses the ordinary foreground scroll for every scanline. */
    Split foregroundVerticalScrollSplit();

    /** The lower band retains the ordinary foreground camera Y. */
    record Split(int scanline, int upperScrollY) {
        public Split {
            if (scanline < 0) throw new IllegalArgumentException("Negative scanline");
        }
    }
}

package com.openggf.graphics;

/** Render data for a scanline scroll split; the lower band retains ordinary camera Y. */
public record ForegroundScrollSplit(int scanline, int upperScrollY) {
    public ForegroundScrollSplit {
        if (scanline < 0) throw new IllegalArgumentException("Negative scanline");
    }
}

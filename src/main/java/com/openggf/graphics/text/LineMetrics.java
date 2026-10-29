package com.openggf.graphics.text;

/**
 * Vertical metrics for one host text role in one language, in unscaled pixels.
 * {@code cellHeight} and {@code baseline} describe the nominal native cell that a caller's
 * {@code y} refers to. Scripts with taller fonts keep that baseline and extend
 * {@code above} the cell top and {@code below} its bottom; {@code lineHeight} is the pitch
 * that stacks such lines without overlap.
 */
public record LineMetrics(int cellHeight, int baseline, int ascent, int descent) {
    public int above() {
        return Math.max(0, ascent - baseline);
    }

    public int below() {
        return Math.max(0, descent - (cellHeight - baseline));
    }

    public int lineHeight() {
        return cellHeight + above() + below();
    }

    LineMetrics include(int otherAscent, int otherDescent) {
        return new LineMetrics(cellHeight, baseline, Math.max(ascent, otherAscent), Math.max(descent, otherDescent));
    }

    public LineMetrics scaled(int scale) {
        return new LineMetrics(cellHeight * scale, baseline * scale, ascent * scale, descent * scale);
    }
}

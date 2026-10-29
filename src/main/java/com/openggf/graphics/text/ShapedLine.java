package com.openggf.graphics.text;

import java.util.List;

/**
 * One measured, shaped and visually ordered line of host text: the single result that width,
 * fitting, drawing, caret placement and hit-testing all use. Coordinates are unscaled pixels
 * from the line start; the baseline sits {@link #baseline()} below the nominal cell top.
 */
public final class ShapedLine {
    private final String text;
    private final List<PlacedGlyph> glyphs;
    private final int width;
    private final LineMetrics metrics;
    private final int[] caret;
    private final List<String> missing;
    private final boolean rightToLeft;

    ShapedLine(String text, List<PlacedGlyph> glyphs, int width, LineMetrics metrics, int[] caret,
               List<String> missing, boolean rightToLeft) {
        this.rightToLeft = rightToLeft;
        this.text = text;
        this.glyphs = List.copyOf(glyphs);
        this.width = width;
        this.metrics = metrics;
        this.caret = caret;
        this.missing = List.copyOf(missing);
    }

    public String text() { return text; }
    public List<PlacedGlyph> glyphs() { return glyphs; }
    public int width() { return width; }
    /** Nominal cell and the extents of every font this line actually uses. */
    public LineMetrics metrics() { return metrics; }
    public int baseline() { return metrics.baseline(); }
    /** Rows above the nominal cell top that this line's fonts may ink (zero for native text). */
    public int above() { return metrics.above(); }
    public int below() { return metrics.below(); }

    /** True when every cluster has a real glyph from a font that can draw it faithfully. */
    public boolean complete() { return missing.isEmpty(); }

    /** Resolved paragraph direction: right-to-left lines align to the end of their box. */
    public boolean rightToLeft() { return rightToLeft; }

    /** Clusters drawn as missing-glyph boxes. */
    public List<String> missing() { return missing; }

    /** Caret x for a UTF-16 boundary; positions inside a cluster snap to its start. */
    public int caretX(int index) {
        if (index <= 0) return caret[0];
        if (index >= caret.length) return caret[caret.length - 1];
        return caret[index];
    }

    /** The grapheme boundary nearest to {@code x}. */
    public int hitIndex(int x) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index <= text.length(); index = Graphemes.next(text, index)) {
            int distance = Math.abs(caretX(index) - x);
            if (distance < bestDistance) {
                best = index;
                bestDistance = distance;
            }
            if (index == text.length()) break;
        }
        return best;
    }
}

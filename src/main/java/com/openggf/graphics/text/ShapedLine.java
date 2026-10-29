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
    private final VisualCarets carets;
    private final List<String> missing;
    private final boolean rightToLeft;

    /**
     * An editing position. {@code index} is a UTF-16 insertion boundary; {@code trailing}
     * selects the end edge of the cluster before it rather than the start edge of the cluster
     * after it. The two differ only where bidirectional runs meet, where one index has two
     * visual positions; elsewhere both affinities name the same place.
     */
    public record Caret(int index, boolean trailing) {
        public Caret {
            if (index < 0) throw new IllegalArgumentException("Caret index must not be negative: " + index);
        }
    }

    ShapedLine(String text, List<PlacedGlyph> glyphs, int width, LineMetrics metrics, VisualCarets carets,
               List<String> missing, boolean rightToLeft) {
        this.rightToLeft = rightToLeft;
        this.text = text;
        this.glyphs = List.copyOf(glyphs);
        this.width = width;
        this.metrics = metrics;
        this.carets = carets;
        this.missing = List.copyOf(missing);
    }

    public String text() { return text; }
    public List<PlacedGlyph> glyphs() { return glyphs; }

    /** Visual intervals occupied by a logical UTF-16 range, using the drawn cluster edges. */
    public List<VisualRange> visualRanges(int start, int end) { return carets.ranges(start, end); }
    public record VisualRange(int left, int right) { }
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

    /**
     * Caret x for a UTF-16 boundary: {@code caretX(caretAt(index))}. Positions inside a
     * grapheme take the following boundary; indices outside the text clamp to its ends.
     */
    public int caretX(int index) {
        return caretX(caretAt(index));
    }

    /**
     * The canonical caret for a logical index: the leading edge of the following cluster,
     * or the trailing edge of the last cluster at the end of non-empty text. An index inside
     * a grapheme moves to the following boundary.
     */
    public Caret caretAt(int index) {
        return carets.caretAt(index);
    }

    /**
     * Caret x in unscaled pixels from the line start: the exact shaped edge of the cluster the
     * caret is attached to. A right-to-left cluster's leading edge is its right side. A
     * ligature's interior boundaries use the font's ligature carets, or an even division of
     * its advance when the font has none.
     */
    public int caretX(Caret caret) {
        return carets.caretX(caret);
    }

    /**
     * One visual step left ({@code direction < 0}) or right ({@code > 0}); zero or a line edge
     * returns the caret's own position. Steps cover every grapheme boundary in visual order. Where
     * bidi runs meet, the boundary holds two positions at the same x, attached first to the
     * cluster on the left and then to the one on the right, so a step there changes the edit
     * point without moving the caret. Each step is undone by the opposite step.
     */
    public Caret moveCaret(Caret caret, int direction) {
        return carets.move(caret, direction);
    }

    /** The visually leftmost ({@code direction < 0}) or rightmost ({@code > 0}) caret. */
    public Caret edgeCaret(int direction) {
        return carets.edge(direction);
    }

    /**
     * The caret position nearest to {@code x}. At a bidi boundary the position attached to the
     * cluster on the clicked side wins; equal distances otherwise prefer the left position.
     */
    public Caret hitCaret(int x) {
        return carets.hit(x);
    }

    /** The grapheme boundary nearest to {@code x}: {@code hitCaret(x).index()}. */
    public int hitIndex(int x) {
        return hitCaret(x).index();
    }
}

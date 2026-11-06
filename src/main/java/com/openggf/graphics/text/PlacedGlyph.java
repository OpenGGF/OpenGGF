package com.openggf.graphics.text;

/**
 * One positioned glyph of a {@link ShapedLine}. {@code x} is the pen position from the line
 * start and {@code y} the offset from the baseline (positive is down), in unscaled pixels.
 */
public final class PlacedGlyph {
    /**
     * {@link #NATIVE}: a menu atlas cell ({@link #glyph()} is the cell index).
     * {@link #SYMBOL}: native arrow/check geometry ({@link #glyph()} is the codepoint).
     * {@link #BITMAP}: a rasterized glyph from {@link #bitmap()}, cached by {@link #cacheKey()}.
     * {@link #MISSING}: no bundled font can draw the cluster faithfully; drawn as a visible box.
     */
    public enum Kind { NATIVE, SYMBOL, BITMAP, MISSING }

    private final Kind kind;
    private final HostFace face;
    private final int glyph;
    private final int x;
    private final int y;
    private final int advance;
    private final int cluster;

    PlacedGlyph(Kind kind, HostFace face, int glyph, int x, int y, int advance, int cluster) {
        this.kind = kind;
        this.face = face;
        this.glyph = glyph;
        this.x = x;
        this.y = y;
        this.advance = advance;
        this.cluster = cluster;
    }

    public Kind kind() { return kind; }
    public int glyph() { return glyph; }
    public int x() { return x; }
    public int y() { return y; }
    public int advance() { return advance; }
    /** UTF-16 index of the first character of the cluster that produced this glyph. */
    public int cluster() { return cluster; }

    /** Stable atlas key for {@link Kind#BITMAP} glyphs. */
    public long cacheKey() {
        return ((long) face.id << 32) | (glyph & 0xFFFFFFFFL);
    }

    /** Rasterized coverage; empty for native, symbol and missing glyphs. */
    public GlyphBitmap bitmap() {
        return kind == Kind.BITMAP ? face.bitmap(glyph) : GlyphBitmap.EMPTY;
    }

    /** Name of the font that produced the glyph, for diagnostics and probes. */
    public String fontName() {
        return face == null ? "missing" : face.name;
    }
}

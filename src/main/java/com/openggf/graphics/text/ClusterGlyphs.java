package com.openggf.graphics.text;

/** Glyphs one bitmap face draws for one grapheme cluster, in logical order. */
record ClusterGlyphs(PlacedGlyph.Kind[] kinds, int[] glyphs, int[] advances, int[] xOffsets, boolean[] overlays) {
    static ClusterGlyphs single(PlacedGlyph.Kind kind, int glyph, int advance) {
        return new ClusterGlyphs(new PlacedGlyph.Kind[] {kind}, new int[] {glyph}, new int[] {advance},
                new int[] {0}, new boolean[] {false});
    }

    static final ClusterGlyphs IGNORABLE = new ClusterGlyphs(new PlacedGlyph.Kind[0], new int[0], new int[0],
            new int[0], new boolean[0]);
}

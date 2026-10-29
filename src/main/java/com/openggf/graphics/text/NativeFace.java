package com.openggf.graphics.text;

import com.openggf.graphics.MenuGlyphs;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The authored menu lettering: light 6x8 cells for {@link TextFace#COMPACT}, bold 9x10 for
 * {@link TextFace#LABEL}. Atlas cells come from {@link MenuGlyphs}; a cluster with no
 * precomposed form but a faithful base-plus-marks composition becomes a runtime bitmap.
 */
final class NativeFace extends BitmapFaceBase {
    /** Runtime compositions use glyph numbers above every atlas cell. */
    private static final int COMPOSED_BASE = 1 << 20;
    private final boolean full;
    private final boolean fixed;
    private final MenuGlyphs.Face artwork;
    private final Map<String, Integer> composedIndex = new HashMap<>();
    private final List<byte[]> composed = new ArrayList<>();

    NativeFace(TextFace role) {
        super(role == TextFace.LABEL ? "menu-bold" : "menu");
        this.full = role == TextFace.LABEL;
        this.fixed = role == TextFace.FIXED;
        this.artwork = fixed ? MenuGlyphs.Face.FIXED : full ? MenuGlyphs.Face.BOLD : MenuGlyphs.Face.LIGHT;
    }

    int advance() {
        return full ? MenuGlyphs.FULL_ADVANCE : MenuGlyphs.ADVANCE;
    }

    int advance(int codePoint) {
        return MenuGlyphs.advance(artwork, codePoint);
    }

    int cellHeight() {
        return full ? MenuGlyphs.FULL_HEIGHT : MenuGlyphs.HEIGHT;
    }

    int baseline() {
        return full ? MenuGlyphs.FULL_BASELINE : MenuGlyphs.BASELINE;
    }

    @Override
    int ascent() {
        return baseline();
    }

    @Override
    int descent() {
        return cellHeight() - baseline();
    }

    /** Glyph for one printable ASCII character, matching the original fixed-cell path. */
    int ascii(char character) {
        return MenuGlyphs.index(character);
    }

    @Override
    ClusterGlyphs cluster(String cluster) {
        String visible = visible(cluster);
        if (visible.isEmpty()) return ClusterGlyphs.IGNORABLE;
        int first = visible.codePointAt(0);
        if (Character.charCount(first) == visible.length()) {
            if (MenuGlyphs.symbol(first)) return ClusterGlyphs.single(PlacedGlyph.Kind.SYMBOL, first, advance());
            int index = MenuGlyphs.index(first);
            if (index >= 0) return ClusterGlyphs.single(PlacedGlyph.Kind.NATIVE, index, MenuGlyphs.advanceIndex(artwork, index));
            return null;
        }
        synchronized (composed) {
            Integer known = composedIndex.get(visible);
            if (known == null) {
                byte[] rows = MenuGlyphs.compose(Normalizer.normalize(visible, Normalizer.Form.NFD));
                if (rows == null) return null;
                known = COMPOSED_BASE + composed.size();
                composed.add(rows);
                composedIndex.put(visible, known);
            }
            return ClusterGlyphs.single(PlacedGlyph.Kind.BITMAP, known, fixed ? advance() : bitmap(known).width() + 1);
        }
    }

    @Override
    GlyphBitmap bitmap(int glyph) {
        byte[] rows;
        synchronized (composed) {
            rows = glyph >= COMPOSED_BASE ? composed.get(glyph - COMPOSED_BASE) : fixed ? MenuGlyphs.rows(glyph) : MenuGlyphs.proportionalRows(glyph);
        }
        int height = cellHeight();
        int width = full ? MenuGlyphs.FULL_ADVANCE : 5;
        int[] masks = new int[height];
        for (int y = 0; y < height; y++) masks[y] = full ? MenuGlyphs.fullRow(rows, y) : rows[y];
        if (fixed) return GlyphBitmap.fromRows(masks, width, 0, -baseline());
        int ink = 0;
        for (int mask : masks) ink |= mask;
        if (ink == 0) return GlyphBitmap.EMPTY;
        int left = Integer.numberOfLeadingZeros(ink) - (32 - width);
        int right = Integer.numberOfTrailingZeros(ink);
        for (int y = 0; y < masks.length; y++) masks[y] >>>= right;
        return GlyphBitmap.fromRows(masks, width - left - right, 0, -baseline());
    }
}

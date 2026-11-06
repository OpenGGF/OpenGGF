package com.openggf.graphics.text;

import java.lang.Character.UnicodeScript;
import java.util.Map;
import java.util.function.Supplier;

/** A bundled BDF or Unifont table, loaded on first use. Combining marks overlay their base. */
final class BitmapFace extends BitmapFaceBase {
    private final Supplier<Map<Integer, BitmapFontTables.Glyph>> loader;
    private final int ascent;
    private final int descent;
    private final boolean overlayMarks;
    private volatile Map<Integer, BitmapFontTables.Glyph> glyphs;

    BitmapFace(String name, Supplier<Map<Integer, BitmapFontTables.Glyph>> loader, int ascent, int descent,
               boolean overlayMarks) {
        super(name);
        this.loader = loader;
        this.ascent = ascent;
        this.descent = descent;
        this.overlayMarks = overlayMarks;
    }

    private Map<Integer, BitmapFontTables.Glyph> glyphs() {
        Map<Integer, BitmapFontTables.Glyph> loaded = glyphs;
        if (loaded == null) {
            synchronized (this) {
                if (glyphs == null) glyphs = loader.get();
                loaded = glyphs;
            }
        }
        return loaded;
    }

    @Override
    int ascent() {
        return ascent;
    }

    @Override
    int descent() {
        return descent;
    }

    @Override
    ClusterGlyphs cluster(String cluster) {
        String visible = visible(cluster);
        if (visible.isEmpty()) return ClusterGlyphs.IGNORABLE;
        Map<Integer, BitmapFontTables.Glyph> table = glyphs();
        int count = visible.codePointCount(0, visible.length());
        PlacedGlyph.Kind[] kinds = new PlacedGlyph.Kind[count];
        int[] ids = new int[count];
        int[] advances = new int[count];
        int[] offsets = new int[count];
        boolean[] overlays = new boolean[count];
        int n = 0;
        for (int i = 0; i < visible.length(); ) {
            int codePoint = visible.codePointAt(i);
            i += Character.charCount(codePoint);
            BitmapFontTables.Glyph glyph = table.get(codePoint);
            if (glyph == null) return null;
            if (n > 0) {
                // Only a stored combining mark may follow the base; anything else needs shaping.
                if (!overlayMarks || !glyph.overlay()) return null;
                if (UnicodeScript.of(codePoint) != UnicodeScript.INHERITED) return null;
            } else if (glyph.overlay()) return null;
            kinds[n] = PlacedGlyph.Kind.BITMAP;
            ids[n] = codePoint;
            advances[n] = glyph.advance();
            offsets[n] = 0; // the bitmap carries the glyph's own offset
            overlays[n] = glyph.overlay();
            n++;
        }
        return new ClusterGlyphs(kinds, ids, advances, offsets, overlays);
    }

    @Override
    GlyphBitmap bitmap(int glyph) {
        BitmapFontTables.Glyph found = glyphs().get(glyph);
        if (found == null) return GlyphBitmap.EMPTY;
        return GlyphBitmap.fromRows(found.rows(), found.width(), found.xOffset(), found.top());
    }
}

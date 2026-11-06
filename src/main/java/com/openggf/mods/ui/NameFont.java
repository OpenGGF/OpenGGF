package com.openggf.mods.ui;

import com.openggf.graphics.text.BitmapFontTables;
import com.openggf.graphics.text.BitmapFontTables.Glyph;

import java.io.UncheckedIOException;
import java.text.BreakIterator;
import java.util.Map;
import java.util.Objects;

/**
 * Unicode bitmap text for player-chosen strings such as display names. Glyphs come from the
 * bundled Fusion Pixel 10px proportional font (Latin, Greek, Cyrillic, kana, Hangul, CJK), with
 * GNU Unifont as the fallback for the rest of the Basic Multilingual Plane; both are unmodified
 * OFL files listed in NOTICE.md. Text is drawn left to right without shaping or bidirectional
 * reordering, so right-to-left and joined scripts show their isolated glyphs in logical order.
 *
 * <p>Coordinates name the top of a {@link #LINE_HEIGHT}-pixel line box. Fusion glyphs fit
 * the box; a few fallback glyphs (stacked marks, tall scripts) extend beyond it, and
 * {@link #extent} reports how far so callers can size a backing plate.
 */
@com.openggf.game.ModApi
public final class NameFont {
    /** Rows from the tallest common accent to the lowest common descender. */
    public static final int LINE_HEIGHT = 13;
    /** Baseline distance from the top of the line box. */
    public static final int BASELINE = 10;
    private static final String ELLIPSIS = "…";

    private NameFont() { }

    /** Starts loading the glyph tables in the background so the first draw does not wait. */
    public static void preload() {
        Thread.ofVirtual().name("openggf-name-font").start(Tables::get);
    }

    /** Advance width in pixels. */
    public static int width(String text) {
        Objects.requireNonNull(text, "text");
        Tables tables = Tables.get();
        int width = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            width += tables.glyph(codePoint).advance();
        }
        return width;
    }

    /**
     * Rows actually covered by {@code text}, relative to the line-box top, never smaller than
     * {@code [0, LINE_HEIGHT)}. Returns {@code {firstRow, lastRow}} inclusive.
     */
    public static int[] extent(String text) {
        Objects.requireNonNull(text, "text");
        Tables tables = Tables.get();
        int first = 0;
        int last = LINE_HEIGHT - 1;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            Glyph glyph = tables.glyph(codePoint);
            if (glyph.rows().length == 0) continue;
            int top = BASELINE + glyph.top();
            first = Math.min(first, top);
            last = Math.max(last, top + glyph.rows().length - 1);
        }
        return new int[] {first, last};
    }

    /** Fits {@code text} into {@code pixels}, cutting whole graphemes and ending with an ellipsis. */
    public static String fit(String text, int pixels) {
        Objects.requireNonNull(text, "text");
        if (width(text) <= pixels) return text;
        int budget = pixels - width(ELLIPSIS);
        if (budget < 0) return "";
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int end = 0;
        int used = 0;
        for (int next = graphemes.next(); next != BreakIterator.DONE; next = graphemes.next()) {
            int cluster = width(text.substring(end, next));
            if (used + cluster > budget) break;
            used += cluster;
            end = next;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    /** Emits horizontal pixel runs for {@code text}, with the line box's top at {@code y}. */
    public static void glyphs(String text, int x, int y, CompactFont.RunSink sink) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sink, "sink");
        Tables tables = Tables.get();
        int penX = x;
        int previousX = x;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            Glyph glyph = tables.glyph(codePoint);
            // Unifont stores combining marks in their own cell; place them over the previous glyph.
            int originX = glyph.overlay() ? previousX : penX;
            int left = originX + glyph.xOffset();
            int[] rows = glyph.rows();
            for (int row = 0; row < rows.length; row++) {
                int bits = rows[row];
                for (int column = 0; column < glyph.width(); column++) {
                    if ((bits & (1 << (glyph.width() - 1 - column))) == 0) continue;
                    int start = column;
                    while (column + 1 < glyph.width()
                            && (bits & (1 << (glyph.width() - 2 - column))) != 0) column++;
                    sink.run(left + start, y + BASELINE + glyph.top() + row, column - start + 1, 1);
                }
            }
            if (!glyph.overlay()) {
                previousX = penX;
                penX += glyph.advance();
            }
        }
    }

    public static void draw(PixelCanvas canvas, String text, int x, int y, int argb) {
        Objects.requireNonNull(canvas, "canvas");
        glyphs(text, x, y, (rx, ry, w, h) -> canvas.fill(rx, ry, w, h, argb));
    }

    /** Whether either bundled font has a real glyph for {@code codePoint}. */
    public static boolean covers(int codePoint) {
        Tables tables = Tables.get();
        return tables.fusion.containsKey(codePoint) || tables.unifont.containsKey(codePoint);
    }

    private static final class Tables {
        private static volatile Tables instance;
        /** Missing-glyph box; {@code top} is baseline-relative like the shared tables. */
        private static final Glyph MISSING = new Glyph(7, 1, -7, 5,
                new int[] {0b11111, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11111}, false);
        final Map<Integer, Glyph> fusion;
        final Map<Integer, Glyph> unifont;

        private Tables(Map<Integer, Glyph> fusion, Map<Integer, Glyph> unifont) {
            this.fusion = fusion;
            this.unifont = unifont;
        }

        static Tables get() {
            Tables loaded = instance;
            if (loaded != null) return loaded;
            synchronized (Tables.class) {
                if (instance == null) {
                    try {
                        // Shared with host menu text, so each bundled table is parsed once.
                        instance = new Tables(BitmapFontTables.bdf("fonts/fusion-pixel-10px-proportional-ja.bdf.gz"),
                                BitmapFontTables.hex("fonts/unifont-18.0.01.hex.gz"));
                    } catch (UncheckedIOException e) {
                        throw new UncheckedIOException("bundled name fonts are unreadable", e.getCause());
                    }
                }
                return instance;
            }
        }

        Glyph glyph(int codePoint) {
            Glyph glyph = fusion.get(codePoint);
            if (glyph == null) glyph = unifont.get(codePoint);
            return glyph != null ? glyph : MISSING;
        }
    }
}

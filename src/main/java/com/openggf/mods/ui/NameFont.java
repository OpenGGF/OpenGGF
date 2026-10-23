package com.openggf.mods.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

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
    private static final int FUSION_LINE_TOP = 1;
    private static final int UNIFONT_TO_FUSION_ROWS = -3;

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
            first = Math.min(first, glyph.top());
            last = Math.max(last, glyph.top() + glyph.rows().length - 1);
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
                    sink.run(left + start, y + glyph.top() + row, column - start + 1, 1);
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

    /** One glyph in line-box coordinates: {@code top} is the first bitmap row. */
    private record Glyph(int advance, int xOffset, int top, int width, int[] rows, boolean overlay) { }

    private static final class Tables {
        private static volatile Tables instance;
        private static final Glyph MISSING = missingGlyph();
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
                        instance = new Tables(readBdf("fonts/fusion-pixel-10px-proportional-ja.bdf.gz"),
                                readUnifont("fonts/unifont-18.0.01.hex.gz"));
                    } catch (IOException e) {
                        throw new UncheckedIOException("bundled name fonts are unreadable", e);
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

        private static BufferedReader open(String resource) throws IOException {
            InputStream raw = NameFont.class.getClassLoader().getResourceAsStream(resource);
            if (raw == null) throw new IOException("missing resource " + resource);
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(raw, 1 << 16),
                    StandardCharsets.ISO_8859_1), 1 << 16);
        }

        static Map<Integer, Glyph> readBdf(String resource) throws IOException {
            Map<Integer, Glyph> glyphs = new HashMap<>(32_768);
            try (BufferedReader reader = open(resource)) {
                int ascent = 0;
                int code = -1;
                int advance = 0;
                int width = 0;
                int height = 0;
                int xOffset = 0;
                int yOffset = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("FONT_ASCENT ")) {
                        ascent = Integer.parseInt(line.substring(12).trim());
                    } else if (line.startsWith("ENCODING ")) {
                        code = Integer.parseInt(line.substring(9).trim());
                    } else if (line.startsWith("DWIDTH ")) {
                        advance = Integer.parseInt(line.split(" ")[1]);
                    } else if (line.startsWith("BBX ")) {
                        String[] parts = line.split(" ");
                        width = Integer.parseInt(parts[1]);
                        height = Integer.parseInt(parts[2]);
                        xOffset = Integer.parseInt(parts[3]);
                        yOffset = Integer.parseInt(parts[4]);
                    } else if (line.equals("BITMAP")) {
                        int[] rows = new int[height];
                        int paddedBits = ((width + 7) / 8) * 8;
                        for (int row = 0; row < height; row++) {
                            int value = Integer.parseInt(reader.readLine().trim(), 16);
                            rows[row] = value >>> (paddedBits - width);
                        }
                        int top = ascent - yOffset - height - FUSION_LINE_TOP;
                        if (code >= 0) {
                            glyphs.put(code, new Glyph(advance, xOffset, top, width, rows, false));
                        }
                        code = -1;
                    }
                }
            }
            return glyphs;
        }

        static Map<Integer, Glyph> readUnifont(String resource) throws IOException {
            Map<Integer, Glyph> glyphs = new HashMap<>(65_536);
            try (BufferedReader reader = open(resource)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    int code = Integer.parseInt(line.substring(0, colon), 16);
                    String data = line.substring(colon + 1);
                    int width = data.length() / 4; // 16 rows: 32 digits = 8 wide, 64 = 16 wide
                    int digits = width / 4;
                    int firstRow = 16;
                    int lastRow = -1;
                    int[] all = new int[16];
                    for (int row = 0; row < 16; row++) {
                        all[row] = Integer.parseInt(data.substring(row * digits, row * digits + digits), 16);
                        if (all[row] != 0) {
                            firstRow = Math.min(firstRow, row);
                            lastRow = row;
                        }
                    }
                    int[] rows = lastRow < 0 ? new int[0]
                            : java.util.Arrays.copyOfRange(all, firstRow, lastRow + 1);
                    int type = Character.getType(code);
                    boolean overlay = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
                    int top = firstRow + UNIFONT_TO_FUSION_ROWS - FUSION_LINE_TOP;
                    glyphs.put(code, new Glyph(overlay ? 0 : width, 0, lastRow < 0 ? 0 : top, width, rows, overlay));
                }
            }
            return glyphs;
        }

        private static Glyph missingGlyph() {
            int[] rows = {0b11111, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11111};
            return new Glyph(7, 1, BASELINE - 7, 5, rows, false);
        }
    }
}

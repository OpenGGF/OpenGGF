package com.openggf.graphics.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Parsed bundled bitmap fonts (gzip BDF and GNU Unifont {@code .hex}), loaded once per
 * resource and shared by host text and {@code NameFont}. Glyph rows are baseline-relative.
 */
public final class BitmapFontTables {
    /** One bitmap glyph. {@code top} is the first row's offset from the baseline (negative is above). */
    public record Glyph(int advance, int xOffset, int top, int width, int[] rows, boolean overlay) { }

    /** Unifont cells are sixteen rows with the baseline below row thirteen. */
    private static final int UNIFONT_BASELINE = 14;
    private static final Map<String, Map<Integer, Glyph>> LOADED = new ConcurrentHashMap<>();

    private BitmapFontTables() { }

    public static Map<Integer, Glyph> bdf(String resource) {
        return LOADED.computeIfAbsent(resource, BitmapFontTables::readBdf);
    }

    public static Map<Integer, Glyph> hex(String resource) {
        return LOADED.computeIfAbsent(resource, BitmapFontTables::readHex);
    }

    private static BufferedReader open(String resource) throws IOException {
        InputStream raw = BitmapFontTables.class.getClassLoader().getResourceAsStream(resource);
        if (raw == null) throw new IOException("missing resource " + resource);
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(raw, 1 << 16),
                StandardCharsets.ISO_8859_1), 1 << 16);
    }

    private static Map<Integer, Glyph> readBdf(String resource) {
        Map<Integer, Glyph> glyphs = new HashMap<>(32_768);
        try (BufferedReader reader = open(resource)) {
            int code = -1;
            int advance = 0;
            int width = 0;
            int height = 0;
            int xOffset = 0;
            int yOffset = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ENCODING ")) {
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
                    if (code >= 0) glyphs.put(code, new Glyph(advance, xOffset, -(yOffset + height), width, rows, false));
                    code = -1;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("bundled bitmap font is unreadable: " + resource, e);
        }
        return java.util.Collections.unmodifiableMap(glyphs);
    }

    private static Map<Integer, Glyph> readHex(String resource) {
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
                int[] rows = lastRow < 0 ? new int[0] : Arrays.copyOfRange(all, firstRow, lastRow + 1);
                int type = Character.getType(code);
                boolean overlay = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK;
                int top = lastRow < 0 ? 0 : firstRow - UNIFONT_BASELINE;
                glyphs.put(code, new Glyph(overlay ? 0 : width, 0, top, width, rows, overlay));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("bundled bitmap font is unreadable: " + resource, e);
        }
        return java.util.Collections.unmodifiableMap(glyphs);
    }
}

package com.openggf.graphics.text;

/**
 * Coverage for one rasterized glyph: {@code alpha} is row-major from the top row, 0-255.
 * {@code left} and {@code top} place the bitmap relative to the pen on the baseline
 * ({@code top} negative is above the baseline).
 */
public record GlyphBitmap(int width, int height, int left, int top, byte[] alpha) {
    public static final GlyphBitmap EMPTY = new GlyphBitmap(0, 0, 0, 0, new byte[0]);

    public boolean empty() {
        return width == 0 || height == 0;
    }

    static GlyphBitmap fromRows(int[] rows, int width, int left, int top) {
        if (rows.length == 0 || width == 0) return EMPTY;
        byte[] alpha = new byte[rows.length * width];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                if ((rows[y] & (1 << (width - 1 - x))) != 0) alpha[y * width + x] = (byte) 255;
            }
        }
        return new GlyphBitmap(width, rows.length, left, top, alpha);
    }
}

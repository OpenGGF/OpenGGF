package com.openggf.mods.ui;

import java.util.HashSet;
import java.util.Objects;

/** Immutable original bitmap face; case selection and styling belong to the mod. */
@com.openggf.game.ModApi
public final class BitmapFont {
    private final String alphabet;
    private final String bits;
    private final int glyphWidth;
    private final int glyphHeight;
    private final int advance;

    private BitmapFont(String alphabet, String bits, int glyphWidth, int glyphHeight, int advance) {
        this.alphabet = alphabet;
        this.bits = bits;
        this.glyphWidth = glyphWidth;
        this.glyphHeight = glyphHeight;
        this.advance = advance;
    }

    /** Row-major 0/1 pixels for every character, without normalization or an implicit fallback. */
    public static BitmapFont binary(String alphabet, String bits, int glyphWidth, int glyphHeight, int advance) {
        Objects.requireNonNull(alphabet, "alphabet");
        Objects.requireNonNull(bits, "bits");
        if (alphabet.isEmpty() || alphabet.length() > 256 || glyphWidth < 1 || glyphWidth > 32
                || glyphHeight < 1 || glyphHeight > 64 || advance < 1 || advance > 128) {
            throw new IllegalArgumentException("Bitmap face dimensions exceed limits");
        }
        if (bits.length() != alphabet.length() * glyphWidth * glyphHeight) {
            throw new IllegalArgumentException("Bitmap pixels do not match the alphabet and dimensions");
        }
        HashSet<Character> seen = new HashSet<>();
        for (int i = 0; i < alphabet.length(); i++) {
            if (!seen.add(alphabet.charAt(i))) throw new IllegalArgumentException("Duplicate bitmap character");
        }
        for (int i = 0; i < bits.length(); i++) {
            if (bits.charAt(i) != '0' && bits.charAt(i) != '1') {
                throw new IllegalArgumentException("Bitmap pixels must be 0 or 1");
            }
        }
        return new BitmapFont(alphabet, bits, glyphWidth, glyphHeight, advance);
    }

    public int glyphWidth() { return glyphWidth; }
    public int glyphHeight() { return glyphHeight; }
    public int advance() { return advance; }

    /** Measured grid extent without a trailing inter-character gap. Unknown characters still advance. */
    public int width(String text, int scale) {
        Objects.requireNonNull(text, "text");
        requireScale(scale);
        return text.isEmpty() ? 0 : Math.multiplyExact(
                Math.addExact(Math.multiplyExact(text.length() - 1, advance), glyphWidth), scale);
    }

    public void glyphs(String text, int x, int y, int scale, CompactFont.RunSink sink) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sink, "sink");
        requireScale(scale);
        for (int letter = 0; letter < text.length(); letter++) {
            int index = alphabet.indexOf(text.charAt(letter));
            if (index < 0) continue;
            int left = Math.addExact(x, Math.multiplyExact(letter, advance * scale));
            for (int row = 0; row < glyphHeight; row++) {
                int base = (index * glyphHeight + row) * glyphWidth;
                for (int column = 0; column < glyphWidth; column++) {
                    if (bits.charAt(base + column) != '1') continue;
                    int start = column;
                    while (column + 1 < glyphWidth && bits.charAt(base + column + 1) == '1') column++;
                    sink.run(Math.addExact(left, start * scale), Math.addExact(y, row * scale),
                            (column - start + 1) * scale, scale);
                }
            }
        }
    }

    public void draw(PixelCanvas canvas, String text, int x, int y, int scale, int argb) {
        Objects.requireNonNull(canvas, "canvas");
        glyphs(text, x, y, scale, (rx, ry, w, h) -> canvas.fill(rx, ry, w, h, argb));
    }

    private static void requireScale(int scale) {
        if (scale < 1 || scale > 8) throw new IllegalArgumentException("Bitmap scale must be 1..8");
    }
}

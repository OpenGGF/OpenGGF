package com.openggf.mods.ui;

import java.util.Objects;

/** Code-drawn 5x7 glyphs on a six-pixel advance at scales 1–8, shared by scenes and level overlays. */
@com.openggf.game.ModApi
public final class CompactFont {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String GLYPHS = "0E11111F1111111E11111E11111E0F10101010100F1E11111111111E1F10101E10101F1F10101E1010100F10101311110F1111111F1111111F04040404041F0702020212120C111214181412111010101010101F111B1515111111111915131111110E11111111110E1E11111E1010100E11111115120D1E11111E1412110F10100E01011E1F0404040404041111111111110E11111111110A0411111115151B1111110A040A111111110A040404041F01020408101F0E11131519110E040C040404040E0E11010204081F1E01010E01011E02060A121F02021F10101E01011E0E10101E11110E1F0102040808080E11110E11110E0E11110F01010E";
    private static final String PUNCTUATION = ".:-_/+=><!?%[]()*,'&;";
    private static final String PUNCTUATION_GLYPHS = "00000000000004000400000400000000001F0000000000000000001F010102040810100004041F04040000001F001F00001008040204081001020408040201040404040400040E110102040004191A0204080B130E08080808080E0E02020202020E020408080804020804020202040800150E1F0E1500000000000C04080C0408000000000C12140815120D00000400000408";
    private CompactFont() { }
    @com.openggf.game.ModApi
    @FunctionalInterface public interface RunSink { void run(int x, int y, int width, int height); }
    private static void scale(int scale) {
        if (scale < 1 || scale > 8) throw new IllegalArgumentException("Font scale must be 1..8");
    }
    public static int width(String text, int scale) {
        Objects.requireNonNull(text, "text"); scale(scale);
        return text.isEmpty() ? 0 : Math.multiplyExact(text.length(), 6 * scale) - scale;
    }
    public static String fit(String text, int pixels, int scale) {
        Objects.requireNonNull(text, "text"); scale(scale);
        int count = (int) Math.max(0, ((long) pixels + scale) / (6 * scale));
        if (text.length() <= count) return text;
        return count <= 3 ? ".".repeat(count) : text.substring(0, count - 3) + "...";
    }
    public static void glyphs(String text, int x, int y, int scale, RunSink sink) {
        Objects.requireNonNull(text, "text"); Objects.requireNonNull(sink, "sink"); scale(scale);
        for (int letter = 0; letter < text.length(); letter++) {
            char c = Character.toUpperCase(text.charAt(letter));
            if (c == ' ') continue;
            int index = ALPHABET.indexOf(c);
            String glyph;
            if (index >= 0) glyph = GLYPHS.substring(index * 14, index * 14 + 14);
            else {
                int punctuation = PUNCTUATION.indexOf(c);
                if (punctuation < 0) punctuation = PUNCTUATION.indexOf('?');
                glyph = PUNCTUATION_GLYPHS.substring(punctuation * 14, punctuation * 14 + 14);
            }
            for (int row = 0; row < 7; row++) {
                int bits = Integer.parseInt(glyph.substring(row * 2, row * 2 + 2), 16);
                for (int column = 0; column < 5; column++) {
                    if ((bits & (16 >> column)) == 0) continue;
                    int start = column;
                    while (column + 1 < 5 && (bits & (16 >> (column + 1))) != 0) column++;
                    sink.run(x + letter * 6 * scale + start * scale, y + row * scale,
                            (column - start + 1) * scale, scale);
                }
            }
        }
    }
    public static void draw(PixelCanvas canvas, String text, int x, int y, int scale, int argb) {
        Objects.requireNonNull(canvas, "canvas");
        glyphs(text, x, y, scale, (rx, ry, w, h) -> canvas.fill(rx, ry, w, h, argb));
    }
    public static void shadowed(PixelCanvas canvas, String text, int x, int y, int scale, int argb, int shadow) {
        draw(canvas, text, x + scale, y + scale, scale, shadow);
        draw(canvas, text, x, y, scale, argb);
    }
    public static void outlined(PixelCanvas canvas, String text, int x, int y, int scale, int argb, int outline) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
            if (dx != 0 || dy != 0) draw(canvas, text, x + dx, y + dy, scale, outline);
        draw(canvas, text, x, y, scale, argb);
    }
}

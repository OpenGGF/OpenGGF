package com.openggf.game.presentation;

import com.openggf.game.CharacterPresentation.Image;
import com.openggf.level.Pattern;
import java.util.*;

/** Pixel-level composition; native glyphs always take precedence over creator supplements. */
public final class CharacterNameComposer {
    private CharacterNameComposer() { }
    public record Composition(Image image, Set<Character> nativeGlyphs,
                              Set<Character> supplementaryGlyphs, Set<Character> missingGlyphs) {
        public boolean complete() { return missingGlyphs.isEmpty(); }
    }
    public static Composition compose(String name, Map<Character, Image> nativeFont,
            Map<Character, Image> supplements, int gap) {
        if (name == null || name.isBlank() || gap < 0 || gap > 16)
            throw new IllegalArgumentException("A name and a gap of 0..16 pixels are required");
        var nativeUsed = new LinkedHashSet<Character>();
        var supplementaryUsed = new LinkedHashSet<Character>();
        var missing = new LinkedHashSet<Character>();
        var letters = new ArrayList<Image>();
        int width = 0, height = 1;
        for (char letter : name.toUpperCase(Locale.ROOT).toCharArray()) {
            Image glyph = nativeFont.get(letter);
            if (glyph != null) nativeUsed.add(letter);
            else {
                glyph = supplements.get(letter);
                if (glyph != null) supplementaryUsed.add(letter);
                else missing.add(letter);
            }
            letters.add(glyph);
            if (glyph != null) { width += glyph.width() + gap; height = Math.max(height, glyph.height()); }
        }
        Image result = null;
        if (missing.isEmpty()) {
            width = Math.max(1, width - gap);
            byte[] pixels = new byte[width * height];
            int x = 0;
            for (Image glyph : letters) {
                byte[] source = glyph.pixels();
                for (int y = 0; y < glyph.height(); y++)
                    System.arraycopy(source, y * glyph.width(), pixels, y * width + x, glyph.width());
                x += glyph.width() + gap;
            }
            result = new Image(width, height, pixels);
        }
        return new Composition(result, Set.copyOf(nativeUsed), Set.copyOf(supplementaryUsed), Set.copyOf(missing));
    }
    /** Rasterize a column-major native sprite, including non-tile-aligned glyph crops. */
    public static Image extract(Pattern[] patterns, int firstTile, int columns, int rows,
            int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x + width > columns * 8 || y + height > rows * 8)
            throw new IllegalArgumentException("Glyph crop outside native sprite");
        byte[] pixels = new byte[width * height];
        for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
            int sx = x + px, sy = y + py;
            pixels[py * width + px] = patterns[firstTile + sx / 8 * rows + sy / 8].getPixel(sx % 8, sy % 8);
        }
        return new Image(width, height, pixels);
    }
    public static Pattern[] tiles(Image image) {
        int columns = (image.width() + 7) / 8, rows = (image.height() + 7) / 8;
        Pattern[] patterns = new Pattern[columns * rows];
        Arrays.setAll(patterns, ignored -> new Pattern());
        byte[] pixels = image.pixels();
        for (int y = 0; y < image.height(); y++) for (int x = 0; x < image.width(); x++)
            patterns[x / 8 * rows + y / 8].setPixel(x % 8, y % 8, pixels[y * image.width() + x]);
        return patterns;
    }
}

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
    /** Fit a packed native word by removing only empty glyph-edge columns when needed.
     * Ink, shadows, vertical alignment and native glyph precedence are unchanged.
     * If padding cannot provide enough room, return the oversized composition so
     * the surface owner can reject it rather than silently truncate letters.
     */
    public static Composition composeToWidth(String name, Map<Character, Image> nativeFont,
            Map<Character, Image> supplements, int maxWidth) {
        if (maxWidth < 1) throw new IllegalArgumentException("Positive name cell width required");
        var normal = compose(name, nativeFont, supplements, 0);
        if (!normal.complete() || normal.image().width() <= maxWidth) return normal;
        int remove = normal.image().width() - maxWidth;
        var letters = new ArrayList<Image>();
        for (char letter : name.toUpperCase(Locale.ROOT).toCharArray()) {
            Image glyph = nativeFont.getOrDefault(letter, supplements.get(letter));
            byte[] source = glyph.pixels();
            int left = 0, right = glyph.width();
            while (remove > 0 && right - left > 1 && emptyColumn(glyph, source, left)) {
                left++;
                remove--;
            }
            while (remove > 0 && right - left > 1 && emptyColumn(glyph, source, right - 1)) {
                right--;
                remove--;
            }
            byte[] pixels = new byte[(right - left) * glyph.height()];
            for (int y = 0; y < glyph.height(); y++)
                System.arraycopy(source, y * glyph.width() + left, pixels, y * (right - left), right - left);
            letters.add(new Image(right - left, glyph.height(), pixels));
        }
        int width = letters.stream().mapToInt(Image::width).sum();
        int height = normal.image().height();
        byte[] pixels = new byte[width * height];
        int x = 0;
        for (Image glyph : letters) {
            byte[] source = glyph.pixels();
            for (int y = 0; y < glyph.height(); y++)
                System.arraycopy(source, y * glyph.width(), pixels, y * width + x, glyph.width());
            x += glyph.width();
        }
        return new Composition(new Image(width, height, pixels), normal.nativeGlyphs(),
                normal.supplementaryGlyphs(), normal.missingGlyphs());
    }

    private static boolean emptyColumn(Image glyph, byte[] pixels, int x) {
        for (int y = 0; y < glyph.height(); y++)
            if (pixels[y * glyph.width() + x] != 0) return false;
        return true;
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

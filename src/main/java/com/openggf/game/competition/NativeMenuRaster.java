package com.openggf.game.competition;

import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.graphics.PaletteView;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Indexed native menu composition, before CRAM fade or display conversion.
 * Inputs are detached ROM nametables/patterns and whole SAT entries. Native
 * admission remains in the shared SAT evaluator; this owner merges its pixels.
 *
 * <p>Hardware corroboration: Genesis Plus GX vdp_render.c, make_lut_bg_ste
 * and make_lut_bgobj_ste (revision 49c584764893b0505ac7f768a754f97330fa4392).
 * Plane priority contributes intensity even for transparent pixels. Sprite
 * colors $0E/$1E/$2E force normal intensity; plane colors never act as operators.
 * The caller's ROM audit must exclude sprite $3E/$3F operators for this port.
 */
public final class NativeMenuRaster {
    public static final int WIDTH = 320, HEIGHT = 224;
    private static final int SHADOW = 0x40;

    private NativeMenuRaster() { }

    public record Plane(int widthTiles, int heightTiles, int[] words, int horizontalScroll, int verticalScroll) {
        public Plane {
            if (widthTiles <= 0 || heightTiles <= 0 || words.length != widthTiles * heightTiles)
                throw new IllegalArgumentException("Native plane geometry and words disagree");
            words = words.clone();
        }
        @Override public int[] words() { return words.clone(); }
    }

    public record Picture(int[] indexedPixels) {
        public Picture {
            if (indexedPixels.length != WIDTH * HEIGHT) throw new IllegalArgumentException("Native raster size");
            indexedPixels = indexedPixels.clone();
        }
        @Override public int[] indexedPixels() { return indexedPixels.clone(); }
        public int colorIndex(int x, int y) { return indexedPixels[y * WIDTH + x] & 0x3F; }
        public boolean shadow(int x, int y) { return (indexedPixels[y * WIDTH + x] & SHADOW) != 0; }

        /** Fade native CRAM first, then select native DAC intensity and display profile. */
        public int[] rgb(PaletteView[] palettes, PaletteFadePresentation fade,
                         DisplayColorProfile profile, DisplayColorExpansion expansion) {
            if (palettes.length != 4) throw new IllegalArgumentException("Four native CRAM lines required");
            Objects.requireNonNull(fade, "fade");
            int[] colors = new int[128];
            for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) {
                var palette = Objects.requireNonNull(palettes[line], "palette");
                int r = palette.red(color) & 255, g = palette.green(color) & 255, b = palette.blue(color) & 255;
                if (fade.affects(line)) {
                    int faded = fade.fadeRgb(r, g, b);
                    r = (faded >>> 16) & 255; g = (faded >>> 8) & 255; b = faded & 255;
                }
                int index = line * 16 + color;
                colors[index] = DisplayColorConverter.toPackedRgb(r, g, b, profile, expansion);
                colors[index | SHADOW] = DisplayColorConverter.toNativeShadowPackedRgb(
                        nativeChannel(r), nativeChannel(g), nativeChannel(b), profile, expansion);
            }
            int[] result = new int[indexedPixels.length];
            for (int i = 0; i < result.length; i++) result[i] = colors[indexedPixels[i]];
            return result;
        }
    }

    private static int nativeChannel(int rgb8) { return ((rgb8 & 255) * 7 + 127) / 255; }

    public static Picture compose(Plane a, Plane b, Pattern[] patterns, List<SpriteSatEntry> nativeSat,
                                  boolean shadowHighlight, int backdropColorIndex) {
        Objects.requireNonNull(a, "plane A"); Objects.requireNonNull(b, "plane B");
        if (backdropColorIndex < 0 || backdropColorIndex >= 64) throw new IllegalArgumentException("Backdrop CRAM index");
        int[] sprites = spritePixels(patterns, nativeSat);
        int[] pixels = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int planeA = planePixel(a, patterns, x, y), planeB = planePixel(b, patterns, x, y);
            boolean aOpaque = (planeA & 15) != 0, bOpaque = (planeB & 15) != 0;
            boolean aHigh = (planeA & 0x80) != 0, bHigh = (planeB & 0x80) != 0;
            int background = aOpaque && (aHigh || !bHigh || !bOpaque) ? planeA
                    : bOpaque ? planeB : backdropColorIndex;
            boolean backgroundHigh = (background & 0x80) != 0;
            boolean shadow = shadowHighlight && !aHigh && !bHigh;
            int sprite = sprites[y * WIDTH + x];
            if (sprite >= 0 && ((sprite & 0x80) != 0 || !backgroundHigh)) {
                int color = sprite & 0x3F;
                if (shadowHighlight && color >= 0x3E)
                    throw new IllegalArgumentException("Native sprite intensity operator requires a different scene port");
                background = sprite;
                // Palette 0..2 color E is the native normal-intensity exception.
                if ((sprite & 0x80) != 0 || (color & 15) == 14 && color < 0x30) shadow = false;
            }
            pixels[y * WIDTH + x] = (background & 0x3F) | (shadow ? SHADOW : 0);
        }
        return new Picture(pixels);
    }

    private static int planePixel(Plane plane, Pattern[] patterns, int x, int y) {
        int sourceX = Math.floorMod(x - plane.horizontalScroll, plane.widthTiles * 8);
        int sourceY = Math.floorMod(y + plane.verticalScroll, plane.heightTiles * 8);
        int word = plane.words[sourceY / 8 * plane.widthTiles + sourceX / 8];
        int pixel = patternPixel(patterns, word & 0x7FF, sourceX & 7, sourceY & 7,
                (word & 0x800) != 0, (word & 0x1000) != 0);
        return pixel | ((word >>> 9) & 0x30) | ((word >>> 8) & 0x80);
    }

    private static int[] spritePixels(Pattern[] patterns, List<SpriteSatEntry> entries) {
        int[] result = new int[WIDTH * HEIGHT];
        Arrays.fill(result, -1);
        var admitted = SpriteSatMaskPostProcessor.process(List.copyOf(entries), false,
                SpriteSatMaskPostProcessor.Raster.h40(0, 0, HEIGHT));
        for (var entry : admitted) {
            entry.piece().forEachTile(
                    (pattern, hFlip, vFlip, palette, drawX, drawY) -> {
                        int top = entry.visibleTileRowStart(drawY), end = entry.visibleTileRowEnd(drawY);
                        for (int row = top; row < end; row++) for (int col = 0; col < 8; col++) {
                            int x = drawX + col, y = drawY + row;
                            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) continue;
                            int at = y * WIDTH + x;
                            if (result[at] >= 0) continue;
                            int color = patternPixel(patterns, pattern & 0x7FF, col, row, hFlip, vFlip);
                            if (color != 0) result[at] = color | (palette & 3) * 16
                                    | (entry.piece().effectiveHighPriority() ? 0x80 : 0);
                        }
                    });
        }
        return result;
    }

    private static int patternPixel(Pattern[] patterns, int pattern, int x, int y, boolean hFlip, boolean vFlip) {
        if (pattern >= patterns.length || patterns[pattern] == null)
            throw new IllegalArgumentException("Native scene references unavailable ROM pattern " + pattern);
        return patterns[pattern].getPixel(hFlip ? 7 - x : x, vFlip ? 7 - y : y) & 15;
    }
}

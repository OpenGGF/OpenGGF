package com.openggf.level;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.color.DisplayColorConverter;

import static org.lwjgl.opengl.GL11.glClearColor;

/**
 * Palette colours shown outside the palette texture (the CRAM colour-0 backdrop
 * used for GL clears and the parallax backdrop uniform). They take the same
 * colour profile and expansion as palette uploads, so a Limited display never
 * shows a full-range backdrop behind limited-range tiles. Allocation-free.
 */
public final class PaletteDisplayColor {
    private PaletteDisplayColor() {
    }

    public static int packed(Palette.Color color, GraphicsManager graphics) {
        var fade = com.openggf.graphics.PaletteUploadPresentation.displayedFade(graphics);
        int r = color.r & 255, g = color.g & 255, b = color.b & 255;
        if (fade.affects(0) && (fade.mode() == com.openggf.graphics.PaletteFadePresentation.Mode.FROM_WHITE
                || fade.mode() == com.openggf.graphics.PaletteFadePresentation.Mode.TO_WHITE)) {
            int rgb = fade.fadeRgb(r, g, b);
            r = (rgb >>> 16) & 255;
            g = (rgb >>> 8) & 255;
            b = rgb & 255;
        }
        return DisplayColorConverter.toPackedRgb(r, g, b,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    /** LevelManager has already resolved CRAM's line mask and displayed fade. */
    static int packedDisplayed(Palette.Color color, GraphicsManager graphics) {
        return DisplayColorConverter.toPackedRgb(color.r & 255, color.g & 255, color.b & 255,
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
    }

    public static float red(int packed) { return ((packed >>> 16) & 0xFF) / 255.0f; }

    public static float green(int packed) { return ((packed >>> 8) & 0xFF) / 255.0f; }

    public static float blue(int packed) { return (packed & 0xFF) / 255.0f; }

    /** {@code glClearColor} with the displayed form of a palette backdrop colour. */
    public static void glClear(Palette.Color color, GraphicsManager graphics) {
        int packed = packed(color, graphics);
        glClearColor(red(packed), green(packed), blue(packed), 1.0f);
    }
}

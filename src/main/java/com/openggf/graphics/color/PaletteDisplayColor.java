package com.openggf.graphics.color;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;

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
        return DisplayColorConverter.toPackedRgb(color.r & 0xFF, color.g & 0xFF, color.b & 0xFF,
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

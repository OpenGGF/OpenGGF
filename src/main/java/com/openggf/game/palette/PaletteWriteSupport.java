package com.openggf.game.palette;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;

/**
 * Small shared helper for systems that can submit normal-palette writes through
 * {@link PaletteOwnershipRegistry}, while preserving direct-write fallback for
 * older contexts that do not own a registry yet.
 */
public final class PaletteWriteSupport {
    private PaletteWriteSupport() {
    }

    public static void applyColor(PaletteOwnershipRegistry registry,
                                  Level level,
                                  GraphicsManager graphics,
                                  String ownerId,
                                  int priority,
                                  int paletteIndex,
                                  int colorIndex,
                                  int segaWord) {
        if (registry != null) {
            registry.submit(PaletteWrite.normal(
                    ownerId,
                    priority,
                    paletteIndex,
                    colorIndex,
                    segaWordBytes(segaWord)));
            return;
        }

        Palette palette = paletteOrNull(level, paletteIndex);
        if (palette == null) {
            return;
        }
        palette.getColor(colorIndex).fromSegaFormat(segaWordBytes(segaWord), 0);
        cachePaletteTextureIfReady(graphics, palette, paletteIndex);
    }

    public static int segaWordFromColor(Palette.Color color) {
        int r3 = quantizeSegaComponent(color.r);
        int g3 = quantizeSegaComponent(color.g);
        int b3 = quantizeSegaComponent(color.b);
        return (b3 << 9) | (g3 << 5) | (r3 << 1);
    }

    private static Palette paletteOrNull(Level level, int paletteIndex) {
        if (level == null || paletteIndex < 0 || paletteIndex >= level.getPaletteCount()) {
            return null;
        }
        return level.getPalette(paletteIndex);
    }

    private static void cachePaletteTextureIfReady(GraphicsManager graphics, Palette palette, int paletteIndex) {
        if (graphics != null && graphics.isGlInitialized()) {
            graphics.cachePaletteTexture(palette, paletteIndex);
        }
    }

    private static int quantizeSegaComponent(byte component) {
        return (((component & 0xFF) * 7) + 127) / 255;
    }

    private static byte[] segaWordBytes(int segaWord) {
        return new byte[] {
                (byte) ((segaWord >>> 8) & 0xFF),
                (byte) (segaWord & 0xFF)
        };
    }
}

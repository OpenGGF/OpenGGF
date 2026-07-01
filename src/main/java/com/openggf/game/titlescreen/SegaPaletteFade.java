package com.openggf.game.titlescreen;

import com.openggf.level.Palette;

/**
 * Mega Drive palette fade helpers matching the Sonic 1/2 Pal_FadeFromBlack and
 * Pal_FadeToBlack channel order.
 */
public final class SegaPaletteFade {
    public enum Mode {
        NONE,
        FROM_BLACK,
        TO_BLACK
    }

    public static final int ROM_FADE_FRAMES = 22;
    private static final int COLOR_STEPS = 21;

    private SegaPaletteFade() {
    }

    public static Palette apply(Palette target, Mode mode, int steps) {
        if (target == null || mode == null || mode == Mode.NONE) {
            return target;
        }
        return switch (mode) {
            case FROM_BLACK -> fromBlack(target, steps);
            case TO_BLACK -> toBlack(target, steps);
            case NONE -> target;
        };
    }

    public static Palette fromBlack(Palette target, int steps) {
        Palette faded = new Palette();
        int clampedSteps = clampSteps(steps);
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            int targetR = toGenesisChannel(target.colors[i].r);
            int targetG = toGenesisChannel(target.colors[i].g);
            int targetB = toGenesisChannel(target.colors[i].b);
            int r = 0;
            int g = 0;
            int b = 0;
            for (int step = 0; step < clampedSteps; step++) {
                if (r == targetR && g == targetG && b == targetB) {
                    break;
                }
                if (b < targetB) {
                    b++;
                } else if (g < targetG) {
                    g++;
                } else if (r < targetR) {
                    r++;
                }
            }
            writeGenesisChannels(faded.colors[i], r, g, b);
        }
        return faded;
    }

    public static Palette toBlack(Palette target, int steps) {
        Palette faded = new Palette();
        int clampedSteps = clampSteps(steps);
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            int r = toGenesisChannel(target.colors[i].r);
            int g = toGenesisChannel(target.colors[i].g);
            int b = toGenesisChannel(target.colors[i].b);
            for (int step = 0; step < clampedSteps; step++) {
                if (r == 0 && g == 0 && b == 0) {
                    break;
                }
                if (r > 0) {
                    r--;
                } else if (g > 0) {
                    g--;
                } else if (b > 0) {
                    b--;
                }
            }
            writeGenesisChannels(faded.colors[i], r, g, b);
        }
        return faded;
    }

    private static int clampSteps(int steps) {
        return Math.max(0, Math.min(COLOR_STEPS, steps));
    }

    private static int toGenesisChannel(byte rgb) {
        return (Byte.toUnsignedInt(rgb) * 7 + 127) / 255;
    }

    private static void writeGenesisChannels(Palette.Color color, int r, int g, int b) {
        color.r = toRgb(r);
        color.g = toRgb(g);
        color.b = toRgb(b);
    }

    private static byte toRgb(int channel) {
        return (byte) ((channel * 255 + 3) / 7);
    }
}

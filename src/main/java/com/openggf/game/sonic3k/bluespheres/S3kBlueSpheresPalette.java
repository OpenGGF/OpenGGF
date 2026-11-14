package com.openggf.game.sonic3k.bluespheres;

import com.openggf.level.Palette;

import java.util.Arrays;

/**
 * {@code Normal_palette} and {@code Target_palette} for the Blue Spheres
 * screens, with the S3K blocking fade steps applied to the native words.
 * The screens own their palette RAM; the engine fade overlay stays idle
 * except across the special stage's own entry fade.
 */
final class S3kBlueSpheresPalette {
    static final int COLOURS = 64;
    /** {@code move.w #$15,d4}: frames in each blocking fade. */
    static final int FADE_FRAMES = 22;

    private final int[] normal = new int[COLOURS];
    private final int[] target = new int[COLOURS];

    void setTarget(int offset, int[] words) {
        System.arraycopy(words, 0, target, offset, Math.min(words.length, COLOURS - offset));
    }

    void setNormal(int offset, int[] words) {
        System.arraycopy(words, 0, normal, offset, Math.min(words.length, COLOURS - offset));
    }

    int[] target() {
        return target.clone();
    }

    void clearTarget() {
        Arrays.fill(target, 0);
    }

    /** {@code Pal_FillBlack} before {@code Pal_FadeFromBlack}'s steps. */
    void fillBlack() {
        Arrays.fill(normal, 0);
    }

    private int fadeDelay2;

    /** {@code Pal_FillWhite}: also clears {@code Pal_fade_delay2}. */
    void fillWhite() {
        Arrays.fill(normal, 0x0EEE);
        fadeDelay2 = 0;
    }

    void copyTargetToNormal() {
        System.arraycopy(target, 0, normal, 0, COLOURS);
    }

    /**
     * {@code Pal_FromBlack}/{@code Pal_AddColor} (sonic3k.asm:4987-5009): whole
     * words compare, so blue rises while {@code +$200} stays at or below the
     * target, then green, and otherwise red.
     */
    void stepFromBlack() {
        for (int i = 0; i < COLOURS; i++) {
            int c = normal[i];
            int t = target[i];
            if (c == t) continue;
            if (c + 0x200 <= t) c += 0x200;
            else if (c + 0x20 <= t) c += 0x20;
            else c += 2;
            normal[i] = c & 0xFFFF;
        }
    }

    /** {@code Pal_ToBlack}/{@code Pal_DecColor}: red, else green, else blue, one step. */
    void stepToBlack() {
        for (int i = 0; i < COLOURS; i++) {
            int c = normal[i];
            if ((c & 0x00E) != 0) c -= 0x002;
            else if ((c & 0x0E0) != 0) c -= 0x020;
            else if ((c & 0xE00) != 0) c -= 0x200;
            normal[i] = c;
        }
    }

    /**
     * {@code Pal_FromWhite}/{@code Pal_DecColor2}: every third frame
     * ({@code Pal_fade_delay2}), each component above its target drops a step.
     */
    void stepFromWhite() {
        if (--fadeDelay2 >= 0) return;
        fadeDelay2 = 2;
        for (int i = 0; i < COLOURS; i++) {
            int c = normal[i];
            int t = target[i];
            if ((c & 0x00E) > (t & 0x00E)) c -= 0x002;
            if ((c & 0x0E0) > (t & 0x0E0)) c -= 0x020;
            if ((c & 0xE00) > (t & 0xE00)) c -= 0x200;
            normal[i] = c;
        }
    }

    int normalWord(int index) {
        return normal[index];
    }

    void setNormalWord(int index, int word) {
        normal[index] = word & 0x0EEE;
    }

    /** The four CRAM lines as engine palettes. */
    Palette[] cram() {
        Palette[] lines = new Palette[4];
        for (int line = 0; line < 4; line++) {
            lines[line] = new Palette();
            for (int colour = 0; colour < 16; colour++) {
                lines[line].getColor(colour).fromSegaFormat(normal[line * 16 + colour]);
            }
        }
        return lines;
    }
}

package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;

/**
 * ROM {@code Normal_palette}/{@code Target_palette} pair for a Super Emerald results screen.
 *
 * <p>{@code SpecialStage_Results} copies {@code Pal_Results} into both buffers, repeats line 1
 * over line 2 and applies {@code sub_2E2C0} (sonic3k.asm:63115-63142), then leaves lines 3-4
 * at {@code $CCC} with {@code Pal_HPZIntro+$20} as their target (loc_2E150, :63144-63148).
 * The HPZ controller retargets lines 2-4 on its first pass (loc_90998-loc_909B0, :197754-197770), and
 * a cleared stage fades the whole screen from white when the tally wait ends
 * (loc_2E410, :63339-63364) through {@code Pal_FromWhite} (:5157-5188).
 */
final class S3kSanctuaryResultsPalette {
    private static final int LINES = 4;
    private static final int COLORS = 16;
    /** loc_2E150: {@code move.l #$CCC0CCC,(a1)+}. */
    private static final int HPZ_WASH = 0x0CCC;
    /** loc_2E410: {@code move.w #$16,(Palette_fade_timer).w}. */
    private static final int FADE_CALLS = 0x16;
    /**
     * {@code Pal_fade_delay2} is not written by the results setup. Every fade that reaches
     * this screen ({@code Pal_FadeFromWhite}, or the {@code Pal_FillWhite} + $16-call
     * {@code Animate_Palette} entry of $1701) starts it from 0 and runs 22 calls, ending at 2.
     */
    private static final int INHERITED_FADE_DELAY = 2;
    /** loc_2E410: offsets $42,$44,$4C,$62,$66,$6E,$70,$7C,$7E of Normal_palette. */
    private static final int[][] FADE_START_WHITE = {
            {2, 1}, {2, 2}, {2, 6}, {3, 1}, {3, 3}, {3, 7}, {3, 8}, {3, 14}, {3, 15}
    };

    private final int[][] normal = new int[LINES][COLORS];
    private final int[][] target = new int[LINES][COLORS];
    private int fadeTimer;
    private int fadeDelay = INHERITED_FADE_DELAY;
    private final Palette[] uploadScratch = new Palette[LINES];

    S3kSanctuaryResultsPalette(int[][] resultsPalette, int[][] hpzIntro, PlayerCharacter character) {
        for (int line = 0; line < LINES; line++) {
            normal[line] = resultsPalette[line].clone();
        }
        // loc_2E104: Normal and Target both start as Pal_Results.
        applyPlayerModeColors(character);
        // loc_2E12C: Pal_Results line 1 is written over lines 1 and 2 of both buffers.
        normal[1] = resultsPalette[0].clone();
        normal[0] = resultsPalette[0].clone();
        applyPlayerModeColors(character);
        for (int line = 2; line < LINES; line++) {
            java.util.Arrays.fill(normal[line], HPZ_WASH);
        }
        for (int line = 0; line < 2; line++) {
            target[line] = normal[line].clone();
        }
        target[2] = hpzIntro[1].clone();
        target[3] = hpzIntro[2].clone();
        for (int line = 0; line < LINES; line++) {
            uploadScratch[line] = new Palette();
        }
    }

    /**
     * {@code sub_2E2C0} on a Super Emerald stage: Knuckles gets $84E,$40C,$206,$EE at line 1
     * colours 2-5 ({@code move.w #$EE,d0} replaces only the low word of $2060080); everyone
     * else gets $EE at line 2 colour 5.
     */
    private void applyPlayerModeColors(PlayerCharacter character) {
        if (character == PlayerCharacter.KNUCKLES) {
            normal[0][2] = 0x084E;
            normal[0][3] = 0x040C;
            normal[0][4] = 0x0206;
            normal[0][5] = 0x00EE;
        } else {
            normal[1][5] = 0x00EE;
        }
    }

    /**
     * {@code Obj_HPZSSEntryControl} init: target line 2 becomes the character palette
     * ({@code Pal_CutsceneKnux}, or {@code Pal_SonicTails} for Knuckles), lines 3-4 become
     * {@code Pal_HPZ+$20}, and line 4 colours 1-2 become $6A0,$660.
     */
    void applyControllerTargets(int[] characterLine, int[][] hpzMain) {
        target[1] = characterLine.clone();
        target[2] = hpzMain[1].clone();
        target[3] = hpzMain[2].clone();
        target[3][1] = 0x06A0;
        target[3][2] = 0x0660;
    }

    /** loc_2E410, cleared Super Emerald stage: whiten nine colours and arm the fade. */
    void beginFadeFromWhite() {
        for (int[] entry : FADE_START_WHITE) {
            normal[entry[0]][entry[1]] = 0x0EEE;
        }
        fadeTimer = FADE_CALLS;
    }

    /** loc_2E24C tail: {@code subq.w #1,(Palette_fade_timer).w / jsr Pal_FromWhite}. */
    void endOfFrame() {
        if (fadeTimer <= 0) {
            return;
        }
        fadeTimer--;
        if (--fadeDelay >= 0) {
            return;
        }
        fadeDelay = 2;
        for (int line = 0; line < LINES; line++) {
            for (int color = 0; color < COLORS; color++) {
                normal[line][color] = decColor2(normal[line][color], target[line][color]);
            }
        }
    }

    /** {@code Pal_DecColor2}: each channel steps down by 2 while above its target. */
    static int decColor2(int normalWord, int targetWord) {
        int blue = normalWord & 0x0E00;
        if (blue > (targetWord & 0x0E00)) {
            blue -= 0x0200;
        }
        int green = normalWord & 0x00E0;
        if (green > (targetWord & 0x00E0)) {
            green -= 0x0020;
        }
        int red = normalWord & 0x000E;
        if (red > (targetWord & 0x000E)) {
            red -= 0x0002;
        }
        return blue | green | red;
    }

    /**
     * Applies this frame's object palette writes (the Master Emerald owns line 4 colours
     * 1-2 through {@code loc_90700}) to {@code Normal_palette}.
     */
    void resolveObjectWrites(PaletteOwnershipRegistry registry) {
        if (registry == null) {
            return;
        }
        for (int line = 0; line < LINES; line++) {
            writeLine(uploadScratch[line], normal[line]);
        }
        registry.resolveInto(uploadScratch, null, null, null);
        for (int line = 0; line < LINES; line++) {
            for (int color = 0; color < COLORS; color++) {
                int word = toSegaWord(uploadScratch[line].getColor(color));
                if (word != normal[line][color]) {
                    normal[line][color] = word;
                            }
            }
        }
    }

    /** Uploads {@code Normal_palette} every drawn frame, after the level pass cached its own lines. */
    void upload(GraphicsManager graphics) {
        if (graphics == null) {
            return;
        }
        for (int line = 0; line < LINES; line++) {
            writeLine(uploadScratch[line], normal[line]);
            graphics.cachePaletteTexture(uploadScratch[line], line);
        }
    }

    int normalColor(int line, int color) {
        return normal[line][color];
    }

    int targetColor(int line, int color) {
        return target[line][color];
    }

    int fadeTimer() {
        return fadeTimer;
    }

    static int[][] linesFromSegaBytes(byte[] data, int lineCount) {
        int[][] lines = new int[lineCount][COLORS];
        for (int line = 0; line < lineCount; line++) {
            for (int color = 0; color < COLORS; color++) {
                int offset = (line * COLORS + color) * 2;
                lines[line][color] = offset + 1 < data.length
                        ? ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF) : 0;
            }
        }
        return lines;
    }

    private static void writeLine(Palette palette, int[] words) {
        for (int color = 0; color < COLORS; color++) {
            palette.getColor(color).fromSegaFormat(words[color]);
        }
    }

    /** Inverse of {@link Palette.Color#fromSegaFormat(int)}'s 3-bit to 8-bit scaling. */
    private static int toSegaWord(Palette.Color color) {
        int red = Math.round((color.r & 0xFF) * 7 / 255f);
        int green = Math.round((color.g & 0xFF) * 7 / 255f);
        int blue = Math.round((color.b & 0xFF) * 7 / 255f);
        return (blue << 9) | (green << 5) | (red << 1);
    }
}

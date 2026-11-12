package com.openggf.game.sonic3k.bluespheres;

import com.openggf.game.PlayerCharacter;

/**
 * The Blue Spheres RAM that survives between its title, stage and results:
 * {@code Blue_spheres_*} (sonic3k.constants.asm:934-942) plus the player choice.
 * Only the full game (a Sonic 1 lock-on) is supported, so
 * {@code Blue_spheres_mode} is always 1.
 */
final class S3kBlueSpheresSession {
    /** {@code Blue_spheres_menu_flag}: 0 = NO WAY!, 1 = menu, bit 7 = entering a code. */
    int menuFlag;
    /** {@code Blue_spheres_option}: 0 = level, 1 = start, 2 = code. */
    int option;
    /** {@code Blue_spheres_progress_flag}: 0, or {@code $FF} for single-stage codes. */
    int progressFlag;
    /** {@code Blue_spheres_current_level}. */
    int level;
    /** {@code Blue_spheres_current_stage}. */
    int[] seed = new int[4];
    /** {@code Player_option}: 1 = Sonic, 3 = Knuckles. */
    int playerOption = 1;
    /** {@code Special_stage_spheres_left} as the title reads it. */
    int spheresLeft;
    /** {@code Special_stage_rings_left} from the last stage, read by the results. */
    int ringsLeft;
    /** {@code Blue_spheres_difficulty} word: shown byte high, target byte low. */
    int difficultyWord;
    /**
     * {@code Palette_frame} ($F65C): no Blue Spheres screen clears it, so the
     * Super Sonic fade of a later perfect screen starts where the last one's
     * cycle stopped. Zero after the cold-boot RAM clear.
     */
    int paletteFrame;

    /**
     * sub_4CCA6's full-game branch (loc_4CCDC, sonic3k.asm:100497-100507):
     * the first title visit starts at level 0 with seed $00,$01,$02,$03.
     */
    void startFullGame() {
        if (menuFlag != 0) return;
        progressFlag = 0;
        level = 0;
        seed = new int[] {0, 1, 2, 3};
    }

    boolean progressDisabled() {
        return (progressFlag & 0x80) != 0;
    }

    PlayerCharacter character() {
        return playerOption == 3 ? PlayerCharacter.KNUCKLES : PlayerCharacter.SONIC_ALONE;
    }

    /** Applies a decoded level code (loc_4D4FC). */
    void applyCode(S3kBlueSpheresLevels.DecodedCode code) {
        progressFlag = code.progressFlag() ? 0xFF : 0;
        level = code.level();
        seed = S3kBlueSpheresLevels.stageSeed(level);
    }

    /** One results advance: {@code addq.l #1} on the level and the seed walk. */
    void advance() {
        level++;
        seed = S3kBlueSpheresLevels.advanceSeed(seed);
    }
}

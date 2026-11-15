package com.openggf.game.soniccd.special;

/**
 * What the program-flow owner hands the special stage: the stage (0-based {@code specStageID},
 * 0-6 Time Stone stages, 7 the secret stage), the mode flags and the Time Stones already held
 * ({@code timeStonesCmd}, _Global Variables.inc:10). IPX.asm builds these before
 * {@code SCMD_SPECSTAGE}: normal play {@code .SpecialStage} (IPX.asm:586-591), stage-select and
 * attract demos {@code SpecStage1Demo}/{@code SpecStage6Demo} (IPX.asm:111-130), Time Attack
 * {@code TimeAttack_SS} (IPX.asm:1287-1297), Special Stage 8 {@code SoundTest_SpecStg8}
 * (IPX.asm:1064-1078).
 */
public record CdSpecialStageEntry(int stageId, CdSpecialStageFlags flags, int timeStones) {
    public CdSpecialStageEntry {
        if (stageId < 0 || stageId >= CdSpecialStageAssets.STAGE_COUNT) {
            throw new IllegalArgumentException("Special stage " + stageId + " outside 0-7");
        }
        if (flags == null) throw new IllegalArgumentException("flags");
        if ((timeStones & ~0x7F) != 0) throw new IllegalArgumentException("Seven Time Stone bits");
    }

    /** Normal play: {@code curSpecStage} with the held stones. */
    public static CdSpecialStageEntry normal(int stageId, int timeStones) {
        return new CdSpecialStageEntry(stageId, CdSpecialStageFlags.NORMAL, timeStones);
    }

    /** The stage-select / attract demo of Stage 1 or 6 (stones reset to none). */
    public static CdSpecialStageEntry demo(int stageId) {
        return new CdSpecialStageEntry(stageId, CdSpecialStageFlags.DEMO, 0);
    }

    /** Special Stage 8 from the sound-test secret (stones reset to none). */
    public static CdSpecialStageEntry secret() {
        return new CdSpecialStageEntry(7, CdSpecialStageFlags.SECRET, 0);
    }
}

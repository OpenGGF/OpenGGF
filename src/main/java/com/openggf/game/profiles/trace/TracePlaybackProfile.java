package com.openggf.game.profiles.trace;

/**
 * Per-game ROM timing facts used when a whole-movie trace crosses gameplay
 * segments that the engine models with shorter transition choreography.
 *
 * <p>A negative row count disables the corresponding alignment. This keeps a
 * game opt-in: values are added only after a movie/ROM measurement establishes
 * them, rather than assuming that all games share Sonic 1's lifecycle timing.
 */
public record TracePlaybackProfile(
        int interLevelNonAdvancingMovieRows,
        int stageResultsEntryNonAdvancingMovieRows,
        boolean alignUncomparedInteriorReturnVblank,
        boolean reinitializeOscillationAtLoadedLevelAttach,
        RecordedLevelIdentityProfile recordedLevelIdentityProfile) {

    public static final TracePlaybackProfile DISABLED = new TracePlaybackProfile(
			-1, -1, false, false, RecordedLevelIdentityProfile.DIRECT);
    public static final TracePlaybackProfile SONIC_1 = new TracePlaybackProfile(
			6, 7, true, true, RecordedLevelIdentityProfile.SONIC_1_ROM);

    public TracePlaybackProfile {
        if (interLevelNonAdvancingMovieRows < -1) {
            throw new IllegalArgumentException(
                    "interLevelNonAdvancingMovieRows must be -1 or non-negative");
        }
        if (stageResultsEntryNonAdvancingMovieRows < -1) {
            throw new IllegalArgumentException(
                    "stageResultsEntryNonAdvancingMovieRows must be -1 or non-negative");
        }
    }

    public boolean alignsInterLevelVblank() {
        return interLevelNonAdvancingMovieRows >= 0;
    }

    /**
     * True when the game declares how many V-blank periods its special-stage
     * results screen builds itself through with interrupts disabled.
     *
     * <p>Sonic 1 spends seven of them inside {@code SS_Finish}'s
     * {@code disable_ints ... ClearScreen / NemDec Nem_TitleCard / Hud_Base ...
     * enable_ints} block (docs/s1disasm/sonic.asm:3369-3383). The V-int that
     * would have run in each of them never reaches
     * {@code VBlank_Exit}'s unconditional {@code addq.l #1,(v_vblank_count).w}
     * (docs/s1disasm/sonic.asm:684), so the ROM's object-visible clock loses
     * exactly that many ticks while the movie keeps advancing. The count is
     * fixed by the block's decompression payload, not by the route that
     * reached it.
     */
    public boolean alignsStageResultsPresentationVblank() {
        return stageResultsEntryNonAdvancingMovieRows >= 0;
    }

    /** Converts recorder-native zone/act bytes into engine progression identity. */
    public LevelIdentity resolveRecordedLevel(int recordedZone, int oneBasedAct) {
        int zeroBasedAct = Math.max(0, oneBasedAct - 1);
        if (recordedLevelIdentityProfile != RecordedLevelIdentityProfile.SONIC_1_ROM) {
            return new LevelIdentity(recordedZone, zeroBasedAct);
        }
        // S1's two aliased late-game identities precede the normal ROM-order map:
        // LZ act 4 is SBZ3, while SBZ act 3 is Final Zone.
        if (recordedZone == 1 && oneBasedAct == 4) {
            return new LevelIdentity(5, 2);
        }
        if (recordedZone == 5 && oneBasedAct == 3) {
            return new LevelIdentity(6, 0);
        }
        int progressionZone = switch (recordedZone) {
            case 0 -> 0; // GHZ
            case 1 -> 3; // LZ
            case 2 -> 1; // MZ
            case 3 -> 4; // SLZ
            case 4 -> 2; // SYZ
            default -> recordedZone; // SBZ/FZ and non-level identities
        };
        return new LevelIdentity(progressionZone, zeroBasedAct);
    }

    public enum RecordedLevelIdentityProfile {
        DIRECT,
        SONIC_1_ROM
    }

    public record LevelIdentity(int zone, int act) {
    }
}

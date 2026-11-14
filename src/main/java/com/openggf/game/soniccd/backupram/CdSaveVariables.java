package com.openggf.game.soniccd.backupram;

/**
 * The IPX variables that {@code ReadSaveData}/{@code WriteSaveData} exchange with the save record
 * (IPX.asm:1420-1475; Main CPU Variables.inc:25-39). {@code savedLevel} is a word: {@code ReadSaveData}
 * reads it from {@code svZone} as {@code move.w}, so its low byte is the stored {@code svTmAtkUnlock}.
 *
 * <p>Time-travel state the save keeps is here and nowhere else: {@code goodFutures} (one bit per zone),
 * {@code timeStones} (one bit per stone) and {@code curSpecStage}. The time-travel and special-stage lanes
 * read and write these through {@code CdProgramFlow}; they add no save fields of their own.
 */
public record CdSaveVariables(int savedLevel, int goodFutures, int titleFlags, int timeAttackUnlock,
                              int unknown, int curSpecStage, int timeStones) {
    public CdSaveVariables {
        if (savedLevel < 0 || savedLevel > 0xFFFF) throw new IllegalArgumentException("savedLevel is a word");
        for (int b : new int[] {goodFutures, titleFlags, timeAttackUnlock, unknown, curSpecStage, timeStones}) {
            if (b < 0 || b > 0xFF) throw new IllegalArgumentException("save variable is a byte: " + b);
        }
    }
}

package com.openggf.game.soniccd.program;

import com.openggf.game.GameStateManager;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.internal.ScoreAdditionPolicy;

import java.util.function.IntConsumer;

/**
 * Sonic CD's {@code AddPoints} (HUD and Points/Main.asm:157-180): the score (units of ten; the engine
 * counts points) caps at 999999 units, and reaching {@code nextLifeScore} adds a life, raises the threshold
 * by 5000 units and plays {@code SCMD_YESSFX}. {@code LevelStart} sets the threshold to 5000 units on a
 * run's first load ({@code move.l #5000,nextLifeScore}, Level/Main.asm:42).
 */
public final class CdScorePolicy implements ScoreAdditionPolicy {
    /** {@code move.l #999999,d1} (HUD and Points/Main.asm:162), in points. */
    static final int MAXIMUM = 999_999 * 10;
    /** {@code move.l #5000,nextLifeScore} and {@code addi.l #5000,nextLifeScore}, in points. */
    static final int LIFE_STEP = 5000 * 10;

    private final IntConsumer subCpuCommand;
    private int nextLifeScore = LIFE_STEP;

    public CdScorePolicy(IntConsumer subCpuCommand) {
        this.subCpuCommand = subCpuCommand;
    }

    @Override public int maximumScore() { return MAXIMUM; }

    @Override
    public void scoreAdded(GameStateManager state, int score) {
        if (score < nextLifeScore) return;                   // cmp.l nextLifeScore,d0; bcs.s .End
        nextLifeScore += LIFE_STEP;
        awardLevelLife(state);
        subCpuCommand.accept(com.openggf.game.soniccd.audio.CdSoundRequests.YES_VOICE);
    }

    /** The uncapped byte increment in {@code AddPoints} (HUD and Points/Main.asm:173-174). */
    public static void awardLevelLife(GameStateManager state) {
        NativeGameStateOps.restoreLives(state, (state.getLives() + 1) & 0xFF);
    }

    /**
     * Wraps an incremented life count to a byte, then caps it as in
     * {@code AddScore} (Special Stage/Main.asm:437-446).
     */
    public static int capSpecialStageLives(int incrementedLives) {
        return Math.min(incrementedLives & 0xFF, 249);
    }

    /**
     * Carries back the special stage's completed tally ({@code AddScore}, Special Stage/Main.asm:433-455).
     * The score and next threshold arrive in engine points.
     */
    public void commitSpecialStageResults(GameStateManager state, int score, int nextLifeScore, int lives) {
        NativeGameStateOps.restoreScore(state, Math.min(MAXIMUM, score + state.getScore() % 10));
        NativeGameStateOps.restoreLives(state, lives);
        this.nextLifeScore = nextLifeScore;
    }

    /** {@code LevelStart}'s first load. */
    void resetForRun() { nextLifeScore = LIFE_STEP; }

    public int nextLifeScore() { return nextLifeScore; }

    void restore(int nextLifeScore) { this.nextLifeScore = nextLifeScore; }
}

package com.openggf.game.soniccd.program;

import com.openggf.game.GameStateManager;
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
    private boolean specialStageResults;

    public CdScorePolicy(IntConsumer subCpuCommand) {
        this.subCpuCommand = subCpuCommand;
    }

    @Override public int maximumScore() { return MAXIMUM; }

    @Override
    public void scoreAdded(GameStateManager state, int score) {
        if (specialStageResults) {
            while (score >= nextLifeScore) {                 // .Check1UP loops (Special Stage/Main.asm:437-446)
                nextLifeScore += LIFE_STEP;
                state.addLife();
            }
            return;
        }
        if (score < nextLifeScore) return;                   // cmp.l nextLifeScore,d0; bcs.s .End
        nextLifeScore += LIFE_STEP;
        state.addLife();
        subCpuCommand.accept(com.openggf.game.soniccd.audio.CdSoundRequests.YES_VOICE);
    }

    /**
     * Points the special stage results added ({@code AddScore}, scddisasm/src/Special Stage/Main.asm:433-455):
     * every threshold crossed adds a life, with no voice. The 249-life cap is not modelled; the engine's
     * lives counter has no cap.
     */
    public void awardSpecialStageResults(GameStateManager state, int points) {
        specialStageResults = true;
        state.addScore(points);
        specialStageResults = false;
    }

    /** {@code LevelStart}'s first load. */
    void resetForRun() { nextLifeScore = LIFE_STEP; }

    public int nextLifeScore() { return nextLifeScore; }

    void restore(int nextLifeScore) { this.nextLifeScore = nextLifeScore; }
}

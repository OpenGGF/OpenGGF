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

    public CdScorePolicy(IntConsumer subCpuCommand) {
        this.subCpuCommand = subCpuCommand;
    }

    @Override public int maximumScore() { return MAXIMUM; }

    @Override
    public void scoreAdded(GameStateManager state, int score) {
        if (score < nextLifeScore) return;                   // cmp.l nextLifeScore,d0; bcs.s .End
        nextLifeScore += LIFE_STEP;
        state.addLife();
        subCpuCommand.accept(com.openggf.game.soniccd.audio.CdSoundRequests.YES_VOICE);
    }

    /** {@code LevelStart}'s first load. */
    void resetForRun() { nextLifeScore = LIFE_STEP; }

    public int nextLifeScore() { return nextLifeScore; }

    void restore(int nextLifeScore) { this.nextLifeScore = nextLifeScore; }
}

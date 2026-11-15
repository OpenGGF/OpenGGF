package com.openggf.game.soniccd;

/**
 * The frame a Sonic CD stage load runs in. Native: the load ends with {@code Level_MainLoop}'s first iteration
 * (its {@code VSync}, {@code addq.w #1,levelFrames}, the object pass; Level/Main.asm:333-335, 400-403), and the
 * recorder's row 0 is that iteration completed ({@code levelFrames} 1). The engine's load runs the same iteration
 * itself ({@link CdFirstMainLoopIteration}), so in the production loop the frame that ran the load must not also
 * run iteration 2: without this the first frame of every stage showed {@code levelFrames} 2 and the stage ran
 * one level frame ahead of native for its whole length. A load outside the loop (a trace harness, a headless
 * boot) is not affected: the loop forgets the mark when its next iteration begins.
 */
final class CdLoadFrame implements com.openggf.game.internal.LevelLoadRunsFirstIteration {
    private boolean ranFirstIteration;

    /** The load has run iteration 1 ({@link CdFirstMainLoopIteration#run}). */
    void markFirstIterationRun() {
        ranFirstIteration = true;
    }

    @Override
    public boolean consumeIterationRunByLoad() {
        boolean ran = ranFirstIteration;
        ranFirstIteration = false;
        return ran;
    }

    @Override
    public void forgetIterationRunByLoad() {
        ranFirstIteration = false;
    }
}

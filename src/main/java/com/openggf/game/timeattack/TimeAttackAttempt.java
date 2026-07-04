package com.openggf.game.timeattack;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure attempt state machine (main spec §6.1): frame counting is spawn-anchored,
 * the displayed timer starts at first input, authoritative time =
 * finishFrame - firstInputFrame. One onFrame call per gameplay frame.
 */
public final class TimeAttackAttempt {
    public enum Phase { ARMED, RUNNING, FINISHED, VOID }

    private Phase phase = Phase.ARMED;
    private int frameCount = -1;      // becomes 0 on the spawn frame's onFrame call
    private int firstInputFrame = -1;
    private int finishFrame = -1;
    private int highestCheckpoint = -1;
    private final List<Integer> splits = new ArrayList<>();

    public void onFrame(int heldMask, boolean endOfLevelActive, int checkpointIndex) {
        if (phase == Phase.FINISHED || phase == Phase.VOID) {
            return;
        }
        frameCount++;
        if (phase == Phase.ARMED && heldMask != 0) {
            phase = Phase.RUNNING;
            firstInputFrame = frameCount;
        }
        // RUNNING-gated: splits before the first input (ARMED) are deliberately not recorded.
        if (phase == Phase.RUNNING && checkpointIndex > highestCheckpoint && checkpointIndex >= 0) {
            highestCheckpoint = checkpointIndex;
            splits.add(frameCount);
        }
        if (phase == Phase.RUNNING && endOfLevelActive) {
            phase = Phase.FINISHED;
            finishFrame = frameCount;
        }
    }

    public void voidAttempt() {
        // A finished attempt's result must not be silently discarded.
        if (phase == Phase.FINISHED) {
            return;
        }
        phase = Phase.VOID;
    }

    public Phase phase() { return phase; }
    public int frameCount() { return Math.max(frameCount, 0); }
    public int firstInputFrame() { return firstInputFrame; }
    public int finishFrame() { return finishFrame; }
    public int finalTimeFrames() { return finishFrame - firstInputFrame; }

    public int elapsedDisplayFrames() {
        if (phase == Phase.RUNNING) return frameCount - firstInputFrame;
        if (phase == Phase.FINISHED) return finalTimeFrames();
        return 0;
    }

    public int[] splitFrames() {
        return splits.stream().mapToInt(Integer::intValue).toArray();
    }
}

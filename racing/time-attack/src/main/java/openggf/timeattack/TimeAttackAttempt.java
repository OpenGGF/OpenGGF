package openggf.timeattack;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure attempt state machine (main spec §6.1): frame counting is spawn-anchored,
 * the clock starts at first input, release or native GO according to the frozen rule.
 * Authoritative time = finishFrame - clockStartFrame. One call per executed step.
 */
public final class TimeAttackAttempt {
    public enum TimingRule { FIRST_INPUT, RELEASE, RACE_CLOCK }

    public enum Phase { ARMED, RUNNING, FINISHED, VOID }

    private final TimingRule timingRule;
    private int completedLaps;
    private int lapBoundary = -1;
    private final List<Integer> laps = new ArrayList<>();
    private com.openggf.mods.run.RaceClock.Failure failure = com.openggf.mods.run.RaceClock.Failure.NONE;

    public TimeAttackAttempt() { this(TimingRule.FIRST_INPUT); }
    public TimeAttackAttempt(TimingRule timingRule) {
        this.timingRule = java.util.Objects.requireNonNull(timingRule);
    }

    private Phase phase = Phase.ARMED;
    private int frameCount = -1;      // becomes 0 on the spawn frame's onFrame call
    private int firstInputFrame = -1;
    private int finishFrame = -1;
    private int highestCheckpoint = -1;
    private final List<Integer> splits = new ArrayList<>();

    public void onFrame(int heldMask, boolean endOfLevelActive, int checkpointIndex) {
        onFrame(heldMask, endOfLevelActive, checkpointIndex, com.openggf.mods.run.RaceClock.ACT);
    }

    public void onFrame(int heldMask, boolean endOfLevelActive, int checkpointIndex,
                        com.openggf.mods.run.RaceClock race) {
        if (phase == Phase.FINISHED || phase == Phase.VOID) {
            return;
        }
        frameCount++;
        if (race.failure() != com.openggf.mods.run.RaceClock.Failure.NONE) {
            failure = race.failure();
            voidAttempt();
            return;
        }
        if (phase == Phase.ARMED && switch (timingRule) {
            case FIRST_INPUT -> heldMask != 0;
            case RELEASE -> true;
            case RACE_CLOCK -> race.running();
        }) {
            phase = Phase.RUNNING;
            firstInputFrame = frameCount;
            lapBoundary = frameCount;
        }
        // completedLaps is the session's highest lap, never the rollback-prone current lap.
        if (phase == Phase.RUNNING && race.completedLaps() > completedLaps) {
            if (race.completedLaps() != completedLaps + 1 || frameCount <= lapBoundary) {
                voidAttempt();
                return;
            }
            laps.add(frameCount - lapBoundary);
            lapBoundary = frameCount;
            completedLaps = race.completedLaps();
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

    public int clockStartFrame() { return firstInputFrame; }
    public int[] lapFrames() { return laps.stream().mapToInt(Integer::intValue).toArray(); }
    public int bestLapFrames() { return laps.stream().mapToInt(Integer::intValue).min().orElse(-1); }
    public int currentLapFrames() { return phase == Phase.RUNNING ? frameCount - lapBoundary : 0; }
    public com.openggf.mods.run.RaceClock.Failure failure() { return failure; }
    public boolean continuesAcross(com.openggf.mods.run.LevelStartCause cause) {
        return timingRule != TimingRule.FIRST_INPUT
                && cause == com.openggf.mods.run.LevelStartCause.CHECKPOINT_RESPAWN
                && (phase == Phase.ARMED || phase == Phase.RUNNING);
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

package com.openggf.trace.replay.runs;

import com.openggf.level.LevelManager;

import java.util.Objects;
import java.util.Optional;

/**
 * Gameplay-session-owned structural receipt for completed level loads.
 * It records what production already loaded and why; it cannot request a load
 * or carry trace/gameplay values into the engine.
 */
public final class RunLevelLoadTracker {
    public record Receipt(
            RunLevelLoadCause cause,
            RunPlaybackObservation.LevelIdentity identity) {
        public Receipt {
            cause = Objects.requireNonNull(cause, "cause");
            identity = Objects.requireNonNull(identity, "identity");
        }
    }

    private RunLevelLoadCause nextCause = RunLevelLoadCause.ORDINARY;
    private Object lastLevel;
    private long generation;
    private Receipt latest;

    public void prime(Object currentLevel) {
        lastLevel = currentLevel;
        latest = null;
    }

    public void markNext(RunLevelLoadCause cause) {
        nextCause = Objects.requireNonNull(cause, "cause");
    }

    public Optional<Receipt> observeLoaded(LevelManager levelManager) {
        Objects.requireNonNull(levelManager, "levelManager");
        Object current = levelManager.getCurrentLevel();
        if (current == null || current == lastLevel) {
            return Optional.empty();
        }
        lastLevel = current;
        latest = new Receipt(nextCause,
                new RunPlaybackObservation.LevelIdentity(
                        ++generation,
                        levelManager.getCurrentZone(),
                        levelManager.getRomZoneId(),
                        levelManager.getCurrentAct()));
        nextCause = RunLevelLoadCause.ORDINARY;
        return Optional.of(latest);
    }

    public Optional<Receipt> latest() {
        return Optional.ofNullable(latest);
    }

    public long generation() {
        return generation;
    }
}

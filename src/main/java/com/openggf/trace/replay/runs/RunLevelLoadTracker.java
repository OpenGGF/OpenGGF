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
    private long lastCompletedLoadGeneration = -1;
    private Receipt latest;

    public void prime(LevelManager levelManager) {
        Objects.requireNonNull(levelManager, "levelManager");
        lastCompletedLoadGeneration =
                levelManager.getCompletedProductionLoadGeneration();
        latest = null;
    }

    public void markNext(RunLevelLoadCause cause) {
        nextCause = Objects.requireNonNull(cause, "cause");
    }

    public Optional<Receipt> observeLoaded(LevelManager levelManager) {
        Objects.requireNonNull(levelManager, "levelManager");
        Object current = levelManager.getCurrentLevel();
        long completedLoadGeneration =
                levelManager.getCompletedProductionLoadGeneration();
        if (current == null
                || completedLoadGeneration == lastCompletedLoadGeneration) {
            return Optional.empty();
        }
        lastCompletedLoadGeneration = completedLoadGeneration;
        latest = new Receipt(nextCause,
                new RunPlaybackObservation.LevelIdentity(
                        completedLoadGeneration,
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
        return Math.max(0, lastCompletedLoadGeneration);
    }
}

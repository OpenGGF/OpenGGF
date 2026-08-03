package com.openggf.trace.replay.runs;

import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;

import java.util.Objects;

/**
 * Immutable structural snapshot consumed by whole-run playback policy.
 * It deliberately contains no mutable gameplay owner or trace comparison row.
 */
public record RunPlaybackObservation(
        GameMode mode,
        int sharedBk2Cursor,
        long admittedStepOrdinal,
        LevelIdentity level,
        boolean initialTitleCardPending,
        BonusIdentity bonus,
        Integer specialStageIndex,
        boolean productionOpen,
        boolean currentSegmentExhausted,
        int destinationRowsConsumed,
        boolean lagOnlySameLevelContinuation,
        long timingScheduleGeneration,
        long dynamicArtGeneration) {

    public RunPlaybackObservation {
        mode = Objects.requireNonNull(mode, "mode");
        if (sharedBk2Cursor < 0) {
            throw new IllegalArgumentException("sharedBk2Cursor must be non-negative");
        }
        if (admittedStepOrdinal < 0) {
            throw new IllegalArgumentException("admittedStepOrdinal must be non-negative");
        }
    }

    /** Typed identity assigned by the production level-load lifecycle. */
    public record LevelIdentity(
            long loadGeneration,
            int progressionZone,
            int romZone,
            int act) {
        public LevelIdentity {
            if (loadGeneration < 0) {
                throw new IllegalArgumentException("loadGeneration must be non-negative");
            }
        }
    }

    /** Active bonus-stage identity, separate from the coarse game mode. */
    public record BonusIdentity(int romZone, int act, BonusStageType type) {
        public BonusIdentity {
            type = Objects.requireNonNull(type, "type");
        }
    }
}

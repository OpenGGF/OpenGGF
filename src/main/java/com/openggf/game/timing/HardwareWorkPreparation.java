package com.openggf.game.timing;

/**
 * Runtime-owned preparation for a hardware job.
 *
 * <p>One call to {@link #stepOneWorkUnit()} consumes one integer work unit.
 * Implementations define that unit from ROM decoder or queue semantics; elapsed
 * host time is never an input.
 */
public interface HardwareWorkPreparation {
    boolean stepOneWorkUnit();

    boolean isPrepared();

    byte[] preparedPayload();

    HardwareWorkPreparationSnapshot snapshot();

    void restore(HardwareWorkPreparationSnapshot snapshot);
}

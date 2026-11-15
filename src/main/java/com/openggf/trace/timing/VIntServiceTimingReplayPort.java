package com.openggf.trace.timing;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Admission-only replay input for the approved lost-V-INT extension of the hardware-timing contract
 * (2026-11-13): on a row carrying {@code vint_not_serviced} the replay services no V-INT at all, the inverse of
 * the lag row's one service. It answers one question per row in order and supplies nothing else: no counter
 * value, no handler choice, no decoder or PLC progress and no gameplay state, and it reads no physics or
 * auxiliary comparison data. Which rows are VBlank-only comes from the caller's existing execution-phase rule.
 *
 * <p>Installation fails when the trace carries the stream but its game does not declare the admission, or when
 * an event names a row that runs a main loop (only a VBlank-only row can have lost its V-INT).
 */
public final class VIntServiceTimingReplayPort {
    private final VIntServiceTimingSchedule schedule;
    private final int[] rows;
    private int cursor;
    private int lastRow = -1;

    private VIntServiceTimingReplayPort(VIntServiceTimingSchedule schedule) {
        this.schedule = schedule;
        this.rows = schedule.unservicedRows();
    }

    /**
     * @param gameDeclaresAdmission whether the running game declares the unserviced-VBlank admission
     * @param vblankOnlyRow         the replay's own execution phase: true for a row that runs no main loop
     */
    public static VIntServiceTimingReplayPort install(VIntServiceTimingSchedule schedule,
                                                      boolean gameDeclaresAdmission, IntPredicate vblankOnlyRow) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(vblankOnlyRow, "vblankOnlyRow");
        if (schedule.observed() && !gameDeclaresAdmission) {
            throw new IllegalStateException("trace carries vint_not_serviced timing, which its game does not declare");
        }
        for (int row : schedule.unservicedRows()) {
            if (!vblankOnlyRow.test(row)) {
                throw new IllegalStateException("vint_not_serviced on raw_frame " + row
                        + ", which runs a main loop; only a VBlank-only row can lose its V-INT");
            }
        }
        return new VIntServiceTimingReplayPort(schedule);
    }

    /** Whether the trace carries the stream (observed V-INT entry), even with no event. */
    public boolean recorded() {
        return schedule.observed();
    }

    /**
     * Whether row {@code rawFrame}'s VBlank is serviced. Rows are asked in increasing order, each once; a row
     * carrying the event answers false and consumes it, and skipping past an unconsumed event fails.
     */
    public boolean vBlankServiced(int rawFrame) {
        if (rawFrame <= lastRow) {
            throw new IllegalStateException("V-INT service asked for raw_frame " + rawFrame + " after " + lastRow);
        }
        if (cursor < rows.length && rows[cursor] < rawFrame) {
            throw new IllegalStateException("vint_not_serviced on raw_frame " + rows[cursor] + " was never applied");
        }
        lastRow = rawFrame;
        if (cursor < rows.length && rows[cursor] == rawFrame) {
            cursor++;
            return false;
        }
        return true;
    }
}

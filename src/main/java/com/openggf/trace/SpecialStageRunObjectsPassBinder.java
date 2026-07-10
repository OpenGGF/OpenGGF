package com.openggf.trace;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Comparison-only cursor that binds atomic ROM {@code RunObjects} results to
 * executed replay updates.
 *
 * <p>A 68K object pass may straddle a VBlank sample, and more than one pass may
 * therefore carry the same physical observation frame. The recorder assigns a
 * monotonic pass sequence plus the first trace row at which that pass can be
 * observed. When several completed passes bind to one replay observation, the
 * latest atomic result is authoritative; intermediate results were never a
 * separately comparable engine observation. Lag rows never call this cursor.
 */
public final class SpecialStageRunObjectsPassBinder {

    private final List<TraceEvent.StateSnapshot> passes;
    private int nextIndex;

    public SpecialStageRunObjectsPassBinder(List<TraceEvent.StateSnapshot> passes) {
        this.passes = passes.stream()
                .sorted(Comparator.comparingInt(SpecialStageRunObjectsPassBinder::sequence))
                .toList();
        for (int i = 0; i < this.passes.size(); i++) {
            int actual = sequence(this.passes.get(i));
            if (actual != i) {
                throw new IllegalArgumentException(
                        "run_objects_end sequence discontinuity: expected " + i
                                + ", got " + actual);
            }
            if (number(this.passes.get(i), "first_eligible_frame")
                    > this.passes.get(i).frame()) {
                throw new IllegalArgumentException(
                        "run_objects_end frame precedes first eligible frame for sequence "
                                + actual);
            }
        }
    }

    public Optional<TraceEvent.StateSnapshot> nextForExecutedFrame(int traceFrame) {
        TraceEvent.StateSnapshot latest = null;
        while (hasRemaining() && passes.get(nextIndex).frame() <= traceFrame) {
            latest = passes.get(nextIndex++);
        }
        return Optional.ofNullable(latest);
    }

    public boolean hasRemaining() {
        return nextIndex < passes.size();
    }

    private static int sequence(TraceEvent.StateSnapshot snapshot) {
        return number(snapshot, "pass_sequence");
    }

    private static int number(TraceEvent.StateSnapshot snapshot, String field) {
        Object raw = snapshot.fields().get(field);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            throw new IllegalArgumentException("run_objects_end missing " + field);
        }
        return Integer.parseInt(raw.toString());
    }
}

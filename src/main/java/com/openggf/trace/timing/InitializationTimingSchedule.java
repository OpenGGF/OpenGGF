package com.openggf.trace.timing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable ordered loading-phase input, distinct from absence of a stream. */
public final class InitializationTimingSchedule {
    private static final InitializationTimingSchedule EMPTY = new InitializationTimingSchedule(false, List.of());
    private final boolean recordedInput;
    private final List<InitializationTimingEvent> events;
    private final Map<Integer, List<InitializationTimingEvent>> eventsByFrame;
    private record LoadRows(int first, int last) { }
    private final Map<Long, LoadRows> rowsByLoad;

    public InitializationTimingSchedule(List<InitializationTimingEvent> events) {
        this(true, events);
    }

    private InitializationTimingSchedule(boolean recordedInput, List<InitializationTimingEvent> events) {
        this.recordedInput = recordedInput;
        this.events = List.copyOf(events);
        InitializationTimingEvent previous = null;
        Map<Integer, List<InitializationTimingEvent>> indexed = new HashMap<>();
        for (InitializationTimingEvent event : this.events) {
            Objects.requireNonNull(event, "initialization timing event");
            validateNext(previous, event);
            indexed.computeIfAbsent(event.rawFrame(), ignored -> new ArrayList<>()).add(event);
            previous = event;
        }
        if (previous != null && previous.operation() == InitializationTimingEvent.Operation.VINT_0) {
            throw new IllegalArgumentException("initialization row must end with HOLD or RESUME");
        }
        Map<Integer, List<InitializationTimingEvent>> immutable = new HashMap<>();
        indexed.forEach((frame, rows) -> immutable.put(frame, List.copyOf(rows)));
        eventsByFrame = Map.copyOf(immutable);
        Map<Long, LoadRows> ranges = new HashMap<>();
        for (var event : this.events) {
            var before = ranges.get(event.loadOrdinal());
            ranges.put(event.loadOrdinal(), new LoadRows(before == null ? event.rawFrame() : before.first(), event.rawFrame()));
        }
        rowsByLoad = Map.copyOf(ranges);
    }

    private static void validateNext(InitializationTimingEvent previous, InitializationTimingEvent event) {
        if (event.sequence() >= 64) throw new IllegalArgumentException("initialization row observation overflow");
        if (previous != null && previous.rawFrame() == event.rawFrame()
                && previous.operation() == InitializationTimingEvent.Operation.HOLD) {
            throw new IllegalArgumentException("HOLD must end the physical initialization row");
        }
        if (previous != null && previous.rawFrame() != event.rawFrame()
                && previous.operation() == InitializationTimingEvent.Operation.VINT_0) {
            throw new IllegalArgumentException("initialization row must end with HOLD or RESUME");
        }
        if (previous == null || event.rawFrame() > previous.rawFrame()) {
            if (event.sequence() != 0) throw new IllegalArgumentException("sequence must start at zero per raw_frame");
        } else if (event.rawFrame() < previous.rawFrame()
                || (long) event.sequence() != (long) previous.sequence() + 1) {
            throw new IllegalArgumentException("raw_frame must be nondecreasing and sequence contiguous");
        }
        if (previous == null) return;
        boolean samePhase = event.loadOrdinal() == previous.loadOrdinal()
                && event.phaseOrdinal() == previous.phaseOrdinal();
        if (samePhase) {
            if (previous.operation() == InitializationTimingEvent.Operation.RESUME) {
                throw new IllegalArgumentException("operation after RESUME for the same phase");
            }
            if (event.phase() != previous.phase()
                    || !event.submissionFingerprint().equals(previous.submissionFingerprint())) {
                throw new IllegalArgumentException("phase identity and submission_fingerprint must remain stable");
            }
        } else {
            if (previous.operation() != InitializationTimingEvent.Operation.RESUME) {
                throw new IllegalArgumentException("phase must RESUME before a new phase");
            }
            if (event.loadOrdinal() < previous.loadOrdinal()
                    || (event.loadOrdinal() == previous.loadOrdinal()
                            && event.phaseOrdinal() <= previous.phaseOrdinal())) {
                throw new IllegalArgumentException("load_ordinal and phase_ordinal must increase");
            }
        }
    }

    public boolean coversLoadRow(long load, int frame) {
        var rows = rowsByLoad.get(load);
        return rows != null && frame >= rows.first() && frame <= rows.last();
    }

    public static InitializationTimingSchedule empty() { return EMPTY; }
    public static InitializationTimingSchedule recordedEmpty() { return new InitializationTimingSchedule(List.of()); }
    public boolean hasRecordedInput() { return recordedInput; }
    public List<InitializationTimingEvent> events() { return events; }
    public List<InitializationTimingEvent> eventsAt(int rawFrame) {
        return eventsByFrame.getOrDefault(rawFrame, List.of());
    }
}

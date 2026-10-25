package com.openggf.audio.rewind;

import com.openggf.audio.presentation.AudioPresentationForwardService;

import java.util.List;
import java.util.Objects;

/** Engine-only projection of the timeline's ordered public and native events. */
public final class AudioTimelineInternalAccess {
    private AudioTimelineInternalAccess() { }

    /**
     * An immutable retained event. Exactly one of command/nativePause is present.
     * Cursor is this event's absolute index; publicEntryIndex is the public entry
     * index, or the next public index at native commit. Native events retain the
     * forward service's immutable committed snapshot for held-rewind staging.
     */
    public record Event(int cursor, long frame, int publicEntryIndex,
                        AudioCommand command, Boolean nativePause,
                        AudioPresentationForwardService.Snapshot forwardSnapshot) { }

    /** Prepared ownership remains local to the forward transaction until commit. */
    public static final class PreparedNativePause {
        private final Runnable append;
        private boolean committed;

        PreparedNativePause(Runnable append) {
            this.append = append;
        }

        public void commit() {
            if (!committed) {
                append.run();
                committed = true;
            }
        }
    }

    /** Absolute index of the next event; public command counts remain separate. */
    public static int cursor(AudioCommandTimeline timeline) {
        return Objects.requireNonNull(timeline, "timeline").eventCursor();
    }

    public static int firstRetainedCursor(AudioCommandTimeline timeline) {
        return Objects.requireNonNull(timeline, "timeline").firstRetainedEventCursor();
    }

    public static PreparedNativePause prepareNativePause(
            AudioCommandTimeline timeline, boolean paused,
            AudioPresentationForwardService.Snapshot forwardSnapshot) {
        return prepareNativePause(timeline, paused, forwardSnapshot, 0);
    }

    /** Reserve the complete transaction's capacity before either prepared append commits. */
    public static PreparedNativePause prepareNativePause(
            AudioCommandTimeline timeline, boolean paused,
            AudioPresentationForwardService.Snapshot forwardSnapshot,
            int additionalPreparedPublicEntries) {
        return Objects.requireNonNull(timeline, "timeline")
                .prepareNativePause(paused, forwardSnapshot, additionalPreparedPublicEntries);
    }

    /** Copied ordered range, clamped to the retained floor and inclusive maxFrame. */
    public static List<Event> eventsFrom(AudioCommandTimeline timeline, int cursor, long maxFrame) {
        return Objects.requireNonNull(timeline, "timeline").eventsFrom(cursor, maxFrame);
    }

    /** Prune only to the actual internal cursor of the earliest retained snapshot. */
    public static void pruneBefore(AudioCommandTimeline timeline, int internalCursor) {
        Objects.requireNonNull(timeline, "timeline").pruneBeforeEvent(internalCursor);
    }
}

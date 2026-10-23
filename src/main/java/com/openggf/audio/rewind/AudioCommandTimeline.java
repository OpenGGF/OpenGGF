package com.openggf.audio.rewind;

import com.openggf.audio.presentation.AudioPresentationForwardService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Append-only command history indexed by absolute entry position.
 *
 * <p>Entries are appended in non-decreasing frame order: forward stepping only
 * moves {@code currentFrame} forward, and every rewind path truncates the tail
 * with {@link #discardAfter(long)} before moving the cursor backward. Frame-local
 * operations therefore walk from the tail instead of scanning the full list.
 *
 * <p>Indices handed out to snapshots ({@link #entryCount()}) are ABSOLUTE: they
 * keep their meaning after old history is pruned via {@link #pruneBefore(int)},
 * which only advances {@link #firstRetainedEntryIndex()}.
 */
@com.openggf.game.ModApi
public final class AudioCommandTimeline {
    // One history owns both public commands and engine-internal native controls.
    // Public indices/orders remain a projection containing only AudioCommand.
    // Explicit zero capacity lets ensureCapacity reserve even the first append.
    private final ArrayList<HistoryEvent> history = new ArrayList<>(0);
    private int prunedEntryCount;
    private int nextPublicEntryIndex;
    private int prunedEventCount;
    private long currentFrame;
    private int nextOrder;

    private static final class HistoryEvent {
        private final long frame;
        private final AudioTimelineEntry publicEntry;
        private final Boolean nativePause;
        private final AudioPresentationForwardService.Snapshot forwardSnapshot;
        // Assigned at commit, so preparing another event does not reserve a cursor.
        private int publicEntryIndex;

        private HistoryEvent(long frame, AudioTimelineEntry publicEntry,
                             Boolean nativePause,
                             AudioPresentationForwardService.Snapshot forwardSnapshot) {
            this.frame = frame;
            this.publicEntry = publicEntry;
            this.nativePause = nativePause;
            this.forwardSnapshot = forwardSnapshot;
        }
    }

    public final class PreparedAppend {
        private final long frame = currentFrame;
        private final int order = nextOrder;
        private final List<HistoryEvent> prepared;
        private boolean committed;

        private PreparedAppend(List<AudioCommand> commands) {
            ArrayList<HistoryEvent> entries =
                    new ArrayList<>(commands.size());
            int candidateOrder = order;
            for (AudioCommand command : commands) {
                entries.add(new HistoryEvent(frame, new AudioTimelineEntry(
                        frame, candidateOrder++, command), null, null));
            }
            prepared = List.copyOf(entries);
            history.ensureCapacity(
                    history.size()
                            + prepared.size());
        }

        public void commit() {
            if (committed) {
                return;
            }
            for (int i = 0; i < prepared.size(); i++) {
                append(prepared.get(i));
            }
            nextOrder = order + prepared.size();
            committed = true;
        }
    }

    public PreparedAppend prepareAppend(List<AudioCommand> commands) {
        return new PreparedAppend(List.copyOf(
                Objects.requireNonNull(commands, "commands")));
    }

    public void beginFrame(long frame) {
        if (frame != currentFrame) {
            currentFrame = frame;
            nextOrder = entriesOnFrame(frame);
        }
    }

    public AudioTimelineEntry record(AudioCommand command) {
        Objects.requireNonNull(command, "command");
        assert history.isEmpty() || history.getLast().frame <= currentFrame
                : "timeline entries must be appended in non-decreasing frame order";
        AudioTimelineEntry entry = new AudioTimelineEntry(currentFrame, nextOrder++, command);
        append(new HistoryEvent(currentFrame, entry, null, null));
        return entry;
    }

    public void discardAfter(long frame) {
        for (int i = history.size() - 1; i >= 0 && history.get(i).frame > frame; i--) {
            HistoryEvent removed = history.remove(i);
            if (removed.publicEntry != null) {
                nextPublicEntryIndex = Math.max(prunedEntryCount, nextPublicEntryIndex - 1);
            }
        }
        if (currentFrame > frame) {
            currentFrame = frame;
            nextOrder = entriesOnFrame(frame);
        }
    }

    /**
     * Drops retained entries with absolute index below {@code entryIndex}.
     * Later entries keep their absolute indices; callers key this off the
     * earliest retained audio keyframe's {@code commandEntryCount} so every
     * replayable keyframe still resolves its replay range.
     */
    public void pruneBefore(int entryIndex) {
        if (entryIndex <= prunedEntryCount) {
            return;
        }
        int clamped = Math.min(entryIndex, entryCount());
        prunedEntryCount = clamped;
        // A public index cannot distinguish native events on either side of a
        // keyframe with the same public command count. Only the internal cursor
        // may prune those events. Public-only history keeps its original pruning.
        int prefix = 0;
        while (prefix < history.size()) {
            HistoryEvent event = history.get(prefix);
            if (event.publicEntry == null || event.publicEntryIndex >= clamped) {
                break;
            }
            prefix++;
        }
        removePrefix(prefix);
    }

    /** Total entries ever recorded (absolute), including pruned ones. */
    public int entryCount() {
        return nextPublicEntryIndex;
    }

    /** Absolute index of the oldest retained entry. */
    public int firstRetainedEntryIndex() {
        return prunedEntryCount;
    }

    /** Returns the retained entry at the given ABSOLUTE index. */
    public AudioTimelineEntry entryAt(int entryIndex) {
        if (entryIndex < prunedEntryCount) {
            throw new IndexOutOfBoundsException(
                    "entry " + entryIndex + " was pruned (first retained: " + prunedEntryCount + ")");
        }
        if (entryIndex >= nextPublicEntryIndex) {
            throw new IndexOutOfBoundsException("entry " + entryIndex);
        }
        // Event anchors are non-decreasing; native entries may share the next
        // public entry's anchor without changing its absolute public index.
        int low = 0;
        int high = history.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (history.get(middle).publicEntryIndex < entryIndex) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        for (int i = low; i < history.size(); i++) {
            HistoryEvent event = history.get(i);
            if (event.publicEntryIndex != entryIndex) {
                break;
            }
            if (event.publicEntry != null) {
                return event.publicEntry;
            }
        }
        throw new IndexOutOfBoundsException("entry " + entryIndex);
    }

    /** Copied view of the retained entries; index 0 is {@link #firstRetainedEntryIndex()}. */
    public List<AudioTimelineEntry> entries() {
        ArrayList<AudioTimelineEntry> entries = new ArrayList<>();
        for (HistoryEvent event : history) {
            if (event.publicEntry != null && event.publicEntryIndex >= prunedEntryCount) {
                entries.add(event.publicEntry);
            }
        }
        return List.copyOf(entries);
    }

    public long currentFrame() {
        return currentFrame;
    }

    public int nextOrder() {
        return nextOrder;
    }

    public void restoreCursor(long frame, int nextOrder) {
        if (nextOrder < 0) {
            throw new IllegalArgumentException("nextOrder must be non-negative");
        }
        this.currentFrame = frame;
        this.nextOrder = nextOrder;
    }

    public void clear() {
        history.clear();
        prunedEntryCount = 0;
        nextPublicEntryIndex = 0;
        prunedEventCount = 0;
        currentFrame = 0;
        nextOrder = 0;
    }

    /**
     * Counts retained entries on {@code frame} by walking back from the tail;
     * the non-decreasing frame invariant means matches are contiguous there.
     */
    int entriesOnFrame(long frame) {
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryEvent event = history.get(i);
            long entryFrame = event.frame;
            if (entryFrame == frame) {
                if (event.publicEntry != null && event.publicEntryIndex >= prunedEntryCount) {
                    count++;
                }
            } else if (entryFrame < frame) {
                break;
            }
        }
        return count;
    }

    int eventCursor() {
        return prunedEventCount + history.size();
    }

    int firstRetainedEventCursor() {
        return prunedEventCount;
    }

    AudioTimelineInternalAccess.PreparedNativePause prepareNativePause(
            boolean paused, AudioPresentationForwardService.Snapshot forwardSnapshot,
            int additionalPreparedPublicEntries) {
        if (additionalPreparedPublicEntries < 0) {
            throw new IllegalArgumentException("additionalPreparedPublicEntries must be non-negative");
        }
        HistoryEvent event = new HistoryEvent(currentFrame, null, paused,
                Objects.requireNonNull(forwardSnapshot, "forwardSnapshot"));
        history.ensureCapacity(Math.addExact(history.size(),
                Math.addExact(1, additionalPreparedPublicEntries)));
        return new AudioTimelineInternalAccess.PreparedNativePause(() -> append(event));
    }

    List<AudioTimelineInternalAccess.Event> eventsFrom(int cursor, long maxFrame) {
        ArrayList<AudioTimelineInternalAccess.Event> events = new ArrayList<>();
        int start = Math.max(cursor, prunedEventCount) - prunedEventCount;
        for (int i = start; i < history.size(); i++) {
            HistoryEvent event = history.get(i);
            if (event.frame > maxFrame) {
                break;
            }
            events.add(new AudioTimelineInternalAccess.Event(
                    prunedEventCount + i, event.frame, event.publicEntryIndex,
                    event.publicEntry != null ? event.publicEntry.command() : null,
                    event.nativePause, event.forwardSnapshot));
        }
        return List.copyOf(events);
    }

    void pruneBeforeEvent(int cursor) {
        if (cursor > prunedEventCount) {
            removePrefix(Math.min(cursor, eventCursor()) - prunedEventCount);
        }
    }

    private void append(HistoryEvent event) {
        event.publicEntryIndex = nextPublicEntryIndex;
        history.add(event);
        if (event.publicEntry != null) {
            nextPublicEntryIndex++;
        }
    }

    private void removePrefix(int count) {
        for (int i = 0; i < count; i++) {
            HistoryEvent event = history.get(i);
            if (event.publicEntry != null) {
                prunedEntryCount = Math.max(prunedEntryCount, event.publicEntryIndex + 1);
            }
        }
        history.subList(0, count).clear();
        prunedEventCount += count;
    }
}

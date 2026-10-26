package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioNativePauseAccess;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public final class AudioKeyframeStore {
    private final NavigableMap<Long, AudioLogicalSnapshot> keyframes = new TreeMap<>();

    public void capture(long frame, AudioManager audio) {
        Objects.requireNonNull(audio, "audio");
        keyframes.put(frame, audio.captureLogicalSnapshot());
    }

    public AudioLogicalSnapshot keyframeAtOrBefore(long frame) {
        Map.Entry<Long, AudioLogicalSnapshot> entry = keyframes.floorEntry(frame);
        return entry != null ? entry.getValue() : null;
    }

    public int replayTo(AudioManager audio, long targetFrame, AudioReplayReason reason) {
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(reason, "reason");
        Map.Entry<Long, AudioLogicalSnapshot> keyframe = keyframes.floorEntry(targetFrame);
        if (keyframe == null) {
            return 0;
        }

        AudioLogicalSnapshot snapshot = keyframe.getValue();
        int replayed = 0;
        audio.restoreLogicalSnapshot(snapshot);
        try (AudioReplayScope ignored = audio.beginRewindReplay(
                Math.toIntExact(keyframe.getKey()),
                Math.toIntExact(targetFrame),
                reason)) {
            replayed = replayHistory(audio, snapshot, targetFrame, false);
        }
        return replayed;
    }

    public int replayToLogicalState(AudioManager audio, long targetFrame) {
        return replayToLogicalState(audio, targetFrame, targetFrame);
    }

    /** Gameplay keyframes and externally owned audio clocks may have different origins. */
    public int replayToLogicalState(AudioManager audio, long targetFrame, long commandTimelineFrame) {
        Objects.requireNonNull(audio, "audio");
        Map.Entry<Long, AudioLogicalSnapshot> keyframe = keyframes.floorEntry(targetFrame);
        if (keyframe == null) {
            return 0;
        }

        AudioLogicalSnapshot snapshot = keyframe.getValue();
        audio.restoreLogicalSnapshot(snapshot);
        return replayHistory(audio, snapshot, commandTimelineFrame, true);
    }

    private int replayHistory(AudioManager audio, AudioLogicalSnapshot snapshot,
                              long commandTimelineFrame, boolean logicalOnly) {
        AudioCommandTimeline timeline = audio.commandTimeline();
        var session = snapshot.presentation().smpsSession();
        int snapshotCursor = session != null ? session.nativeHistoryCursor() : 0;
        // Retention normally keeps these floors aligned with the earliest
        // keyframe. Clamps retain the previous skip-don't-crash public behavior.
        int cursor = Math.max(snapshotCursor,
                AudioTimelineInternalAccess.firstRetainedCursor(timeline));
        int firstPublic = Math.max(snapshot.commandEntryCount(),
                timeline.firstRetainedEntryIndex());
        int replayed = 0;
        for (AudioTimelineInternalAccess.Event event :
                AudioTimelineInternalAccess.eventsFrom(timeline, cursor, commandTimelineFrame)) {
            if (event.command() != null) {
                // Pending public commands have always been covered by the
                // snapshot's public count; a native cursor must not replay them.
                if (event.publicEntryIndex() >= firstPublic) {
                    if (logicalOnly) {
                        audio.replayTimelineCommandLogically(event.command());
                    } else {
                        audio.replayTimelineCommand(event.command());
                    }
                    replayed++;
                }
            } else {
                AudioNativePauseAccess.replay(audio, event.nativePause(), event.forwardSnapshot());
            }
            cursor = event.cursor() + 1;
        }
        AudioNativePauseAccess.advanceHistoryCursor(audio, cursor);
        // Existing callers count public commands, not internal native controls.
        return replayed;
    }

    public void discardAfter(long frame) {
        keyframes.tailMap(frame, false).clear();
    }

    public void discardBefore(long frame) {
        keyframes.headMap(frame, false).clear();
    }

    /** Keep the audio base needed by a gameplay checkpoint between audio boundaries. */
    public void discardBeforeRetainingFloor(long frame) {
        Long floor = keyframes.floorKey(frame);
        if (floor != null) {
            keyframes.headMap(floor, false).clear();
        }
    }

    /** Earliest retained keyframe snapshot, or {@code null} when empty. */
    public AudioLogicalSnapshot earliestSnapshot() {
        Map.Entry<Long, AudioLogicalSnapshot> first = keyframes.firstEntry();
        return first != null ? first.getValue() : null;
    }

    public void clear() {
        keyframes.clear();
    }
}

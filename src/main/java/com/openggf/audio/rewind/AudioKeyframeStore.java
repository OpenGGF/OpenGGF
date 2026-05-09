package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;

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
            replayed = audio.commandTimeline().forEachEntryFrom(
                    snapshot.commandEntryCount(),
                    targetFrame,
                    entry -> audio.replayTimelineCommand(entry.command()));
        }
        return replayed;
    }

    public int replayToLogicalState(AudioManager audio, long targetFrame) {
        Objects.requireNonNull(audio, "audio");
        Map.Entry<Long, AudioLogicalSnapshot> keyframe = keyframes.floorEntry(targetFrame);
        if (keyframe == null) {
            return 0;
        }

        AudioLogicalSnapshot snapshot = keyframe.getValue();
        audio.restoreLogicalSnapshot(snapshot);
        return audio.commandTimeline().forEachEntryFrom(
                snapshot.commandEntryCount(),
                targetFrame,
                entry -> audio.replayTimelineCommandLogically(entry.command()));
    }

    public void discardAfter(long frame) {
        keyframes.tailMap(frame, false).clear();
    }

    public void clear() {
        keyframes.clear();
    }
}

package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.List;

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
        try (AudioReplayScope ignored = audio.beginRewindReplay(
                Math.toIntExact(keyframe.getKey()),
                Math.toIntExact(targetFrame),
                reason)) {
            List<AudioTimelineEntry> entries = audio.commandTimeline().entries();
            for (int i = snapshot.commandEntryCount(); i < entries.size(); i++) {
                AudioTimelineEntry entry = entries.get(i);
                if (entry.frame() <= targetFrame) {
                    audio.replayTimelineCommand(entry.command());
                    replayed++;
                }
            }
        }
        return replayed;
    }

    public void clear() {
        keyframes.clear();
    }
}

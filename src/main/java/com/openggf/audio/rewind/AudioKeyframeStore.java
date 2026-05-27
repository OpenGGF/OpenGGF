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
        audio.restoreLogicalSnapshot(snapshot);
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

    public int replayToLogicalState(AudioManager audio, long targetFrame) {
        Objects.requireNonNull(audio, "audio");
        Map.Entry<Long, AudioLogicalSnapshot> keyframe = keyframes.floorEntry(targetFrame);
        if (keyframe == null) {
            return 0;
        }

        AudioLogicalSnapshot snapshot = keyframe.getValue();
        audio.restoreLogicalSnapshot(snapshot);
        int replayed = 0;
        List<AudioTimelineEntry> entries = audio.commandTimeline().entries();
        for (int i = snapshot.commandEntryCount(); i < entries.size(); i++) {
            AudioTimelineEntry entry = entries.get(i);
            if (entry.frame() <= targetFrame) {
                audio.replayTimelineCommandLogically(entry.command());
                replayed++;
            }
        }
        return replayed;
    }

    /**
     * Returns the latest keyframe whose {@code backend().clockSnapshot()}
     * places its captured audio-frame index at or before {@code audioFrame}.
     * Returns null if no keyframe in the store has a clock snapshot, or if
     * the earliest captured keyframe sits at a higher audio-frame index than
     * the requested one. Audio-frame indices are read from each keyframe's
     * {@code AudioFrameClock.Snapshot.totalSamplesProduced()} value.
     */
    public AudioLogicalSnapshot keyframeAtOrBeforeAudioFrame(long audioFrame) {
        AudioLogicalSnapshot best = null;
        long bestAudio = Long.MIN_VALUE;
        for (AudioLogicalSnapshot snapshot : keyframes.values()) {
            if (snapshot == null || snapshot.backend() == null
                    || snapshot.backend().clockSnapshot() == null) {
                continue;
            }
            long candidate = snapshot.backend().clockSnapshot().totalSamplesProduced();
            if (candidate <= audioFrame && candidate >= bestAudio) {
                best = snapshot;
                bestAudio = candidate;
            }
        }
        return best;
    }

    /**
     * Dispatches any timeline entries whose game-frame equals
     * {@code atGameFrame}, in submission order, via
     * {@link AudioManager#replayTimelineCommand} under the
     * {@link AudioReplayReason#REVERSE_RESYNTH} scope. Returns the number of
     * commands replayed.
     *
     * <p>Caller contract: this method is invoked once per game-frame inside
     * the burst loop with monotonically increasing {@code atGameFrame}
     * values. It must not re-dispatch entries at earlier frames — the
     * burst loop relies on this single-frame semantic to avoid duplicating
     * SFX commands that already mutated the chip on a prior iteration. The
     * keyframe state is restored once at burst start; from there, this
     * method walks forward command-by-command exactly as the live game did.
     *
     * <p>Entries are stored in {@code AudioCommandTimeline} sorted by frame
     * (then by order within a frame). The early-exit on
     * {@code entry.frame() > atGameFrame} relies on that invariant.
     */
    public int replayCommandsAtGameFrame(AudioManager audio,
                                          AudioLogicalSnapshot keyframe,
                                          long atGameFrame) {
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(keyframe, "keyframe");
        if (keyframe.commandTimelineFrame() > atGameFrame) {
            return 0;
        }
        int replayed = 0;
        try (AudioReplayScope ignored = audio.beginRewindReplay(
                Math.toIntExact(keyframe.commandTimelineFrame()),
                Math.toIntExact(atGameFrame),
                AudioReplayReason.REVERSE_RESYNTH)) {
            List<AudioTimelineEntry> entries = audio.commandTimeline().entries();
            for (int i = keyframe.commandEntryCount(); i < entries.size(); i++) {
                AudioTimelineEntry entry = entries.get(i);
                if (entry.frame() < atGameFrame) {
                    continue;
                }
                if (entry.frame() > atGameFrame) {
                    break;
                }
                audio.replayTimelineCommand(entry.command());
                replayed++;
            }
        }
        return replayed;
    }

    public void discardAfter(long frame) {
        keyframes.tailMap(frame, false).clear();
    }

    public void clear() {
        keyframes.clear();
    }
}

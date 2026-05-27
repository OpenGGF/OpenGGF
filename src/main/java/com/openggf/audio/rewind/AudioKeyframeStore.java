package com.openggf.audio.rewind;

import com.openggf.audio.AudioManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Tracks {@link AudioLogicalSnapshot}s indexed by game-frame for held-rewind.
 *
 * <h2>Threading</h2>
 *
 * <p>The store's small mutator and lookup methods are {@code synchronized}
 * on {@code this} so concurrent capture/discard/clear from the game thread
 * is safe alongside lookups from the reverse-resynth worker. The replay
 * methods (e.g. {@link #replayTo}, {@link #replayToLogicalState}) do
 * potentially-slow audio work and only acquire the monitor for the brief
 * keyframe lookup — they release before calling into {@link AudioManager}.
 *
 * <p>For the worker's session, prefer {@link #frozenView}: it returns an
 * immutable snapshot of the keyframes at session start, removing any need
 * for the worker to synchronise with live capture/discard. Internal layout
 * is a {@link TreeMap}; the copy is O(n) for n ≈ a few dozen keyframes per
 * session, which is well below noise.
 */
public final class AudioKeyframeStore {
    private final NavigableMap<Long, AudioLogicalSnapshot> keyframes = new TreeMap<>();

    public synchronized void capture(long frame, AudioManager audio) {
        Objects.requireNonNull(audio, "audio");
        keyframes.put(frame, audio.captureLogicalSnapshot());
    }

    public synchronized AudioLogicalSnapshot keyframeAtOrBefore(long frame) {
        Map.Entry<Long, AudioLogicalSnapshot> entry = keyframes.floorEntry(frame);
        return entry != null ? entry.getValue() : null;
    }

    public int replayTo(AudioManager audio, long targetFrame, AudioReplayReason reason) {
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(reason, "reason");
        Map.Entry<Long, AudioLogicalSnapshot> keyframe;
        synchronized (this) {
            keyframe = keyframes.floorEntry(targetFrame);
        }
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
        Map.Entry<Long, AudioLogicalSnapshot> keyframe;
        synchronized (this) {
            keyframe = keyframes.floorEntry(targetFrame);
        }
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
     *
     * <p>Holds the monitor while iterating the map. The worker should prefer
     * {@link FrozenView#keyframeAtOrBeforeAudioFrame} via the session.
     */
    public synchronized AudioLogicalSnapshot keyframeAtOrBeforeAudioFrame(long audioFrame) {
        return scanKeyframeAtOrBeforeAudioFrame(keyframes, audioFrame);
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
     *
     * <p>No locking is required against this store: the method's only input
     * map read is the {@code keyframe} parameter (already held by the
     * caller). The timeline iteration goes against {@code audio}'s timeline,
     * not this store.
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

    public synchronized void discardAfter(long frame) {
        keyframes.tailMap(frame, false).clear();
    }

    public synchronized void clear() {
        keyframes.clear();
    }

    /**
     * Returns an immutable, point-in-time snapshot of the keyframes for
     * use by code that needs to read the store without synchronising
     * against live capture/discard — primarily the reverse-resynth worker
     * thread for the duration of a held-rewind session.
     *
     * <p>The snapshot copies the underlying {@link TreeMap} structure (O(n)
     * for n keyframes, typically a few dozen per session). The
     * {@link AudioLogicalSnapshot} values themselves are immutable records,
     * so they are shared rather than deep-copied.
     */
    public synchronized FrozenView frozenView() {
        return new FrozenView(Collections.unmodifiableNavigableMap(new TreeMap<>(keyframes)));
    }

    private static AudioLogicalSnapshot scanKeyframeAtOrBeforeAudioFrame(
            NavigableMap<Long, AudioLogicalSnapshot> source,
            long audioFrame) {
        AudioLogicalSnapshot best = null;
        long bestAudio = Long.MIN_VALUE;
        for (AudioLogicalSnapshot snapshot : source.values()) {
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
     * Immutable point-in-time view of an {@link AudioKeyframeStore}. The
     * reverse-resynth worker holds a {@code FrozenView} for the lifetime of
     * a held-rewind session so subsequent {@code capture} or
     * {@code discardAfter} calls on the live store don't perturb the
     * worker's read model.
     *
     * <p>Backed by an unmodifiable copy of the keyframes map; iteration is
     * lock-free.
     */
    public static final class FrozenView {
        private final NavigableMap<Long, AudioLogicalSnapshot> snapshot;

        private FrozenView(NavigableMap<Long, AudioLogicalSnapshot> snapshot) {
            this.snapshot = snapshot;
        }

        /** Same semantics as
         *  {@link AudioKeyframeStore#keyframeAtOrBeforeAudioFrame}, against
         *  the frozen snapshot. */
        public AudioLogicalSnapshot keyframeAtOrBeforeAudioFrame(long audioFrame) {
            return scanKeyframeAtOrBeforeAudioFrame(snapshot, audioFrame);
        }

        /**
         * Returns the earliest captured audio-frame across keyframes that
         * have a clock snapshot — i.e. the floor below which the worker
         * cannot legitimately synthesize audio because no chip state was
         * captured earlier than this point. Returns {@code 0} when no
         * keyframe has a clock snapshot (the worker shouldn't burst at
         * all in that case).
         *
         * <p>The reverse-resynth worker uses this to refuse bursts whose
         * target audio-frame range falls below the earliest available
         * keyframe — without the clamp, the consumer can keep draining
         * past the game state's rewind floor and the worker would keep
         * synthesizing audio at frames the game can't actually rewind to.
         */
        public long earliestKeyframeAudioFrame() {
            long earliest = Long.MAX_VALUE;
            boolean found = false;
            for (AudioLogicalSnapshot snap : snapshot.values()) {
                if (snap == null || snap.backend() == null
                        || snap.backend().clockSnapshot() == null) {
                    continue;
                }
                long candidate = snap.backend().clockSnapshot().totalSamplesProduced();
                if (candidate < earliest) {
                    earliest = candidate;
                    found = true;
                }
            }
            return found ? earliest : 0L;
        }

        /** Count of keyframes in the frozen view. */
        public int size() {
            return snapshot.size();
        }

        /** True when no keyframes were captured at session start. */
        public boolean isEmpty() {
            return snapshot.isEmpty();
        }
    }
}

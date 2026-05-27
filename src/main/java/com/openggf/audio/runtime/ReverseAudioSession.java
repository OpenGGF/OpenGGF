package com.openggf.audio.runtime;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.rewind.SmpsDriverSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Frozen per-session inputs for the reverse-resynth worker. Built once at
 * {@code AudioManager.beginReverseAudioPresentation} and never mutated for
 * the lifetime of the held-rewind session.
 *
 * <p>The worker thread operates entirely on this session record + its
 * private {@code SmpsPresentationState}; it never touches live
 * {@code AudioManager}, {@code AudioBackend}, {@code AudioCommandTimeline},
 * or live {@code AudioKeyframeStore} state. That isolation is the basis of
 * the worker's correctness — the game thread is free to keep mutating
 * those live objects while the worker reads from this frozen snapshot.
 *
 * <p>Per-field threading notes:
 * <ul>
 *   <li>{@link #ring} — shared between consumer (audio drain on the game
 *       thread) and producer (worker thread). The ring's own intrinsic
 *       lock provides the SPSC contract.</li>
 *   <li>{@link #keyframes} — an immutable {@code FrozenView} obtained from
 *       {@code AudioKeyframeStore.frozenView()} at session start.</li>
 *   <li>{@link #frozenTimeline} — an immutable {@code List.copyOf} of the
 *       live timeline at session start.</li>
 *   <li>{@link #replayDependencies} — provided by {@code AudioManager} for
 *       the duration of the session; its dependency-resolution callbacks
 *       are safe to call from the worker thread.</li>
 *   <li>{@link #presentationDriverFactory} — supplies newly-configured
 *       {@code SmpsDriver} instances for the worker to use when restoring
 *       state from keyframes; the factory must be safe to call from the
 *       worker thread (i.e. not depend on live backend state).</li>
 * </ul>
 *
 * <p>Sizing fields ({@link #sampleRate}, {@link #frameRate},
 * {@link #burstAudioFrames}, {@link #headroomThresholdFrames}) are
 * captured here so the worker doesn't reach back to the runtime for them;
 * the runtime can rebind streams during the session and shouldn't change
 * the worker's perception of its size budget.
 */
public record ReverseAudioSession(
        PcmHistoryRing ring,
        AudioKeyframeStore.FrozenView keyframes,
        List<AudioTimelineEntry> frozenTimeline,
        int sampleRate,
        int frameRate,
        int burstAudioFrames,
        int headroomThresholdFrames,
        SmpsDriverSnapshot.DependencyResolver replayDependencies,
        Supplier<SmpsDriver> presentationDriverFactory) {

    public ReverseAudioSession {
        Objects.requireNonNull(ring, "ring");
        Objects.requireNonNull(keyframes, "keyframes");
        Objects.requireNonNull(frozenTimeline, "frozenTimeline");
        Objects.requireNonNull(replayDependencies, "replayDependencies");
        Objects.requireNonNull(presentationDriverFactory, "presentationDriverFactory");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive, got " + sampleRate);
        }
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive, got " + frameRate);
        }
        if (burstAudioFrames <= 0) {
            throw new IllegalArgumentException(
                    "burstAudioFrames must be positive, got " + burstAudioFrames);
        }
        if (headroomThresholdFrames < 0) {
            throw new IllegalArgumentException(
                    "headroomThresholdFrames must be non-negative, got "
                            + headroomThresholdFrames);
        }
        // Force-copy the timeline to be safe even if the caller passes a
        // mutable list. List.copyOf is a no-op when already immutable.
        frozenTimeline = List.copyOf(frozenTimeline);
    }
}

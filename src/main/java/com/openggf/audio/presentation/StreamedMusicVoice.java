package com.openggf.audio.presentation;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.audio.rewind.AudioSourceDescriptor;

import java.util.Objects;

/**
 * Hosts a creator-supplied streamed music override as an ordinary presentation
 * voice, so mod music participates in the presentation clock, history, reverse
 * cursor, capture leases, and rewind snapshots exactly like SMPS and sample
 * music do.
 *
 * <p>The logical playback state stays owned by the {@link StreamedMusicPort}:
 * loop points, fade ramps, the pause mask, and speed-shoes tempo are all
 * port-side concepts with their own validation, and re-expressing them as a
 * plain Q32.32 source cursor (as {@link SampleBackedVoice} does) would drop
 * them. This voice is therefore an adapter over the port, not a
 * reimplementation of its state machine.
 *
 * <p>Like every other voice this is confined to the presentation thread, which
 * is the confinement the port already requires.
 */
public final class StreamedMusicVoice implements PresentationVoice {

    /** Music voices mix at the base priority, matching {@code loopingMusic}. */
    private static final int MUSIC_PRIORITY = 0;

    private final long voiceId;
    private final int priority;
    private final StreamedMusicPort port;
    private final AudioSourceDescriptor sourceDescriptor;
    private short[] scratch = new short[0];
    private boolean stopped;

    public StreamedMusicVoice(long voiceId, StreamedMusicPort port,
            AudioSourceDescriptor sourceDescriptor) {
        this(voiceId, MUSIC_PRIORITY, port, sourceDescriptor, false);
    }

    private StreamedMusicVoice(long voiceId, int priority, StreamedMusicPort port,
            AudioSourceDescriptor sourceDescriptor, boolean stopped) {
        this.voiceId = voiceId;
        this.priority = priority;
        this.port = Objects.requireNonNull(port, "port");
        this.sourceDescriptor = Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
        this.stopped = stopped;
    }

    /**
     * Rebuilds a streamed voice from a captured snapshot against the installed
     * port. A creator track's identity is its owner-scoped {@code TrackRef}, so
     * only the port that prepared it can vouch for the restore; a port that no
     * longer holds the track rejects it rather than resuming silence.
     */
    public static StreamedMusicVoice restore(PresentationVoiceSnapshot.Streamed snapshot,
            StreamedMusicPort port) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(port, "port");
        if (!port.restoreState(snapshot.playback())) {
            throw new IllegalStateException(
                    "installed streamed port cannot restore "
                            + snapshot.playback().track());
        }
        return new StreamedMusicVoice(snapshot.voiceId(), snapshot.priority(), port,
                snapshot.sourceDescriptor(), snapshot.stopped());
    }

    public StreamedMusicPort port() {
        return port;
    }

    @Override
    public long voiceId() {
        return voiceId;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public void mixInto(long[] accumulation, int stereoFrames) {
        Objects.requireNonNull(accumulation, "accumulation");
        if (stereoFrames < 0 || accumulation.length < (long) stereoFrames * 2) {
            throw new IllegalArgumentException("accumulation cannot hold requested stereo frames");
        }
        if (isComplete() || stereoFrames == 0) {
            return;
        }
        int samples = stereoFrames * 2;
        if (scratch.length < samples) {
            // Grown only when the packet size grows, never per frame: this is the
            // presentation hot path and the port's own mixInto is allocation-free.
            scratch = new short[samples];
        }
        int mixedFrames = port.mixInto(scratch, stereoFrames);
        for (int frame = 0; frame < mixedFrames; frame++) {
            int index = frame * 2;
            accumulation[index] += scratch[index];
            accumulation[index + 1] += scratch[index + 1];
        }
    }

    /**
     * Advances the fade ramp by one presentation frame. Fades step per frame
     * rather than per sample, so the frame boundary drives this and not
     * {@link #mixInto}.
     */
    public void advanceFade() {
        if (!isComplete()) {
            port.advanceFade();
        }
    }

    @Override
    public boolean isComplete() {
        return stopped || !port.hasSource();
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        port.stop();
    }

    @Override
    public PresentationVoiceSnapshot snapshot() {
        StreamedMusicPort.State playback = port.captureState().orElse(null);
        if (playback == null) {
            // A voice with no current source is already complete, so the
            // registry's completion sweep owes us its removal before any
            // snapshot. Failing loudly beats contributing a null the snapshot
            // list would reject anyway, or inventing state never captured.
            throw new IllegalStateException(
                    "streamed music voice " + voiceId + " has no source to capture");
        }
        return new PresentationVoiceSnapshot.Streamed(
                voiceId, priority, sourceDescriptor, playback, stopped);
    }
}

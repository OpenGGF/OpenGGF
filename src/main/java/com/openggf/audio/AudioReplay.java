package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.game.ModApi;

/**
 * Bounded recording of the actual presented audio, independent of developer rewind.
 * Create through the controlled-mode CourseControl.recordAudioReplay capability before the first replayable row.
 * Reverse it at the same rate as the view, then close it before restoring the world.
 * Silence while paused does not advance its cursor. Release preserves live sound-driver state.
 * This presentation resource is not a gameplay snapshot or a network payload.
 */
@ModApi
public final class AudioReplay implements AutoCloseable {
    private final AudioPresentationProducer.RecordedReplay recording;

    AudioReplay(AudioPresentationProducer.RecordedReplay recording) {
        this.recording = java.util.Objects.requireNonNull(recording);
    }

    /** Seals the recording and starts reverse playback at a finite rate greater than zero and at most 64. */
    public void beginReverse(double speed) { requireSpeed(speed); recording.beginReverse(speed); }

    /** Changes the active reverse rate without restarting its cursor. */
    public void setRate(double speed) { requireSpeed(speed); recording.setRate(speed); }

    private static void requireSpeed(double speed) {
        if (!Double.isFinite(speed) || speed <= 0 || speed > 64)
            throw new IllegalArgumentException("Audio replay speed must be finite, positive and at most 64");
    }

    /** Stops reverse playback without changing the world, live voices or developer history. */
    public void stop() { recording.stop(); }

    public boolean isClosed() { return recording.isClosed(); }

    /**
     * Idempotently releases this recording, even if flushing a failed audio sink throws.
     * CourseControl's session teardown also closes forgotten recordings.
     */
    @Override public void close() { recording.dispose(); }
}

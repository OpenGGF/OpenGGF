package com.openggf.audio.runtime;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;

import java.util.Arrays;
import java.util.function.Consumer;

public interface DeterministicAudioRuntime {
    void advanceFrame(long frame, FrameAudioMode mode);

    default boolean consumesSubmittedCommands() {
        return false;
    }

    default void setCommandHandler(Consumer<AudioCommand> commandHandler) {
    }

    default void submit(AudioTimelineEntry entry) {
    }

    default void discardSubmittedCommandsAfter(long frame) {
    }

    default void clearSubmittedCommands() {
    }

    default boolean providesPresentationPcm() {
        return false;
    }

    default int drainPcm(short[] target, int frames) {
        Arrays.fill(target, 0, frames * 2, (short) 0);
        return 0;
    }

    default void flushPresentationFifo() {
    }

    default void beginReversePresentation() {
    }

    default void endReversePresentation() {
    }

    default void clearPcmHistory() {
    }

    default void setMusicStream(AudioStream stream) {
    }

    default void setSfxStream(AudioStream stream) {
    }

    default void clearMusicStream() {
        setMusicStream(null);
    }

    default void clearSfxStream() {
        setSfxStream(null);
    }

    default boolean hasActivePresentation() {
        return false;
    }

    default AudioFrameClock.Snapshot captureClockSnapshot() {
        return null;
    }

    default void restoreClockSnapshot(AudioFrameClock.Snapshot snapshot) {
    }

    /** Re-anchors any in-memory PCM history ring at the given audio
     *  frame with zero stored frames, so the next reverse cursor sees
     *  an empty readable window. No-op for runtimes without a ring. */
    default void invalidatePcmHistoryAt(long nextAudioFrame) {
    }

    /** Resets the audio frame clock to 0 samples produced. Used by
     *  AudioManager.resetForLevelRewindSegment to give the level a
     *  clean audio-frame origin. No-op for runtimes without a clock. */
    default void resetClock() {
    }
}

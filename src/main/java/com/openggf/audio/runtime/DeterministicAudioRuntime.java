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

    /**
     * Sets the playback rate while reverse presentation is active. A value
     * of 1.0 walks the PCM history one stored frame per output frame.
     * Values >1 pitch the rewound audio up; values in (0, 1) slow it down.
     */
    default void setReversePlaybackRate(double rate) {
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
}

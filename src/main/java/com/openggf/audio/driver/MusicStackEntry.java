package com.openggf.audio.driver;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.SmpsSequencer;

/**
 * One frame on the music override stack: a snapshot of the active music
 * driver/sequencer/stream when an override (e.g. 1-up, invincibility) pushes
 * the current music aside. Restored when the override ends.
 *
 * <p>Lives in the SMPS driver package so both the live backend and the
 * reverse-resynth worker can share the same stack representation through
 * {@link SmpsPresentationState#musicStack}.
 */
public record MusicStackEntry(
        AudioStream stream,
        SmpsSequencer sequencer,
        SmpsDriver driver,
        int musicId,
        AudioSourceDescriptor descriptor) {
}

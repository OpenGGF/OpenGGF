package com.openggf.capture;

import com.openggf.audio.AudioManager;

/** {@link AudioFrameTap} over {@link AudioManager#drainCaptureFrame(short[])}. */
public final class DrainPcmAudioTap implements AudioFrameTap {

    private final AudioManager audio;

    public DrainPcmAudioTap(AudioManager audio) {
        this.audio = audio;
    }

    @Override
    public int drain(short[] target) {
        return audio.drainCaptureFrame(target);
    }
}

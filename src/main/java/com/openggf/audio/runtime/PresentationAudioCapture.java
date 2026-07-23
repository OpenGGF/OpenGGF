package com.openggf.audio.runtime;

public interface PresentationAudioCapture extends AutoCloseable {
    int sampleRate();

    int frameRate();

    int maxStereoFramesPerPacket();

    int drainPresentationFrame(short[] target);

    long totalStereoFrames();

    AudioFrameClock.Snapshot clockSnapshot();

    @Override
    void close();
}

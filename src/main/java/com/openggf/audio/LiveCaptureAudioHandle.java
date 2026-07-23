package com.openggf.audio;

public interface LiveCaptureAudioHandle extends AutoCloseable {
    int sampleRate();

    int frameRate();

    int maxStereoFramesPerPacket();

    int drainPresentationFrame(short[] target);

    @Override
    void close();
}

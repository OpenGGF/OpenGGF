package com.openggf.audio;

import com.openggf.audio.runtime.AudioFrameClock;

public interface LiveCaptureAudioHandle extends AutoCloseable {
    int sampleRate();

    int frameRate();

    int maxStereoFramesPerPacket();

    /**
     * Drains one capture-clock packet on the producer's owner thread. Published
     * PCM may span producer packets; missing samples are silence. This never
     * drives synthesis. A failed drain must not consume capture-clock phase.
     */
    int drainPresentationFrame(short[] target);

    long totalStereoFrames();

    AudioFrameClock.Snapshot clockSnapshot();

    @Override
    void close();
}

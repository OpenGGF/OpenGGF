package com.openggf.audio.runtime;

import java.util.Arrays;
import java.util.Objects;

final class LiveAudioCaptureTap {
    private static final int CHANNELS = 2;

    private final int sampleRate;
    private final int frameRate;
    private final int maxStereoFramesPerPacket;
    private final AudioFrameClock frameClock;
    private short[] forwardPcm = new short[0];
    private int forwardStereoFrames;
    private boolean forwardPcmFresh;
    private PcmHistoryRing.ReverseCursor reverseCursor;
    private boolean reversePresentation;

    LiveAudioCaptureTap(int sampleRate, int frameRate) {
        this.frameClock = new AudioFrameClock(sampleRate, frameRate);
        this.sampleRate = sampleRate;
        this.frameRate = frameRate;
        this.maxStereoFramesPerPacket =
                (int) ((sampleRate + (long) frameRate - 1) / frameRate);
    }

    int sampleRate() {
        return sampleRate;
    }

    int frameRate() {
        return frameRate;
    }

    int maxStereoFramesPerPacket() {
        return maxStereoFramesPerPacket;
    }

    void acceptForwardPcm(short[] pcm, int stereoFrames) {
        Objects.requireNonNull(pcm, "pcm");
        if (stereoFrames < 0) {
            throw new IllegalArgumentException("stereoFrames must be non-negative");
        }
        int sampleCount = Math.multiplyExact(stereoFrames, CHANNELS);
        if (pcm.length < sampleCount) {
            throw new IllegalArgumentException("pcm is too small for stereoFrames");
        }
        if (forwardPcm.length < sampleCount) {
            forwardPcm = new short[sampleCount];
        }
        System.arraycopy(pcm, 0, forwardPcm, 0, sampleCount);
        forwardStereoFrames = stereoFrames;
        forwardPcmFresh = true;
    }

    void clearForwardPcm() {
        forwardStereoFrames = 0;
        forwardPcmFresh = false;
    }

    void beginReversePresentation(PcmHistoryRing.ReverseCursor cursor) {
        clearForwardPcm();
        reverseCursor = cursor;
        reversePresentation = true;
    }

    void setReversePlaybackRate(double rate) {
        if (reverseCursor != null) {
            reverseCursor.setRate(rate);
        }
    }

    void endReversePresentation() {
        reverseCursor = null;
        reversePresentation = false;
    }

    void clearPcmHistory() {
        endReversePresentation();
        clearForwardPcm();
    }

    int drainPresentationFrame(short[] target) {
        Objects.requireNonNull(target, "target");
        int requiredCapacity = Math.multiplyExact(maxStereoFramesPerPacket, CHANNELS);
        if (target.length < requiredCapacity) {
            throw new IllegalArgumentException(
                    "target is too small for the maximum presentation packet");
        }

        int requestedFrames = frameClock.samplesForNextFrame();
        int requestedSamples = requestedFrames * CHANNELS;
        Arrays.fill(target, 0, requestedSamples, (short) 0);
        if (reversePresentation) {
            if (reverseCursor != null) {
                reverseCursor.readPrevious(target, requestedFrames);
            }
        } else if (forwardPcmFresh) {
            int copiedFrames = Math.min(requestedFrames, forwardStereoFrames);
            System.arraycopy(forwardPcm, 0, target, 0, copiedFrames * CHANNELS);
        }
        clearForwardPcm();
        return requestedFrames;
    }

    long totalStereoFrames() {
        return frameClock.totalSamplesProduced();
    }

    AudioFrameClock.Snapshot clockSnapshot() {
        return frameClock.captureSnapshot();
    }
}

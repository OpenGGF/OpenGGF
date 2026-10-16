package com.openggf.audio.presentation;

import com.openggf.audio.output.AudioPresentationSink;

import java.util.Objects;

/** Presentation-only quiet low-pass and dialog ducking for the hub's ROM theme. */
public final class HubMusicPresentationSink implements AudioPresentationSink {
    private final AudioPresentationSink delegate;
    private final AudioPresentationFrameView output = new AudioPresentationFrameView(new short[0]);
    private short[] samples = new short[0];
    private volatile double targetGain;
    private double gain;
    private double left;
    private double right;

    public HubMusicPresentationSink(AudioPresentationSink delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public void setVolume(int percent, boolean ducked) {
        targetGain = Math.clamp(percent, 0, 100) / 100.0 * (ducked ? .35 : 1.0);
    }

    @Override public int sampleRate() { return delegate.sampleRate(); }

    @Override public void accept(AudioPresentationFrameView frame) {
        int count = frame.stereoFrames();
        if (samples.length < count * 2) samples = new short[count * 2];
        // One-pole 1800 Hz low-pass, then a 20 ms gain ramp to avoid clicks on focus changes.
        double alpha = 1 - Math.exp(-2 * Math.PI * 1800 / sampleRate());
        double ramp = 1 - Math.exp(-1.0 / (.020 * sampleRate()));
        for (int i = 0; i < count; i++) {
            gain += (targetGain - gain) * ramp;
            left += alpha * (frame.sampleAt(i, 0) - left);
            right += alpha * (frame.sampleAt(i, 1) - right);
            samples[i * 2] = (short) Math.round(left * gain);
            samples[i * 2 + 1] = (short) Math.round(right * gain);
        }
        output.update(samples, count, frame.presentationFrame(), frame.mode());
        delegate.accept(output);
    }

    @Override public void onReverseBoundary() { delegate.onReverseBoundary(); }
    @Override public void close() { delegate.close(); }
}

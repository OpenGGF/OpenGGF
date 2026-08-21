package com.openggf.tools.audio.playback;

import com.openggf.audio.synth.ChipWriteObserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Disabled-by-default in-memory observer for short, deterministic playback
 * scenarios. Bounds are fixed at construction so diagnostics cannot grow into
 * an accidental complete-run recorder.
 */
public final class BoundedAudioPlaybackTrace implements ChipWriteObserver {
    private final int maxEvents;
    private final int maxStereoFrames;
    private final List<AudioPlaybackTraceEvent> events = new ArrayList<>();
    private short[] pcm = new short[0];
    private int stereoFrames;

    public BoundedAudioPlaybackTrace(int maxEvents, int maxStereoFrames) {
        if (maxEvents <= 0 || maxStereoFrames <= 0) {
            throw new IllegalArgumentException("trace bounds must be positive");
        }
        this.maxEvents = maxEvents;
        this.maxStereoFrames = maxStereoFrames;
    }

    public void mark(String name) {
        addEvent(new AudioPlaybackTraceEvent.Marker(name));
    }

    @Override
    public void onYm2612Write(int port, int register, int value) {
        addEvent(new AudioPlaybackTraceEvent.Ym2612Write(
                port, register, value));
    }

    @Override
    public void onPsgWrite(int value) {
        addEvent(new AudioPlaybackTraceEvent.PsgWrite(value));
    }

    public void recordPcm(short[] samples, int frames) {
        if (frames < 0 || samples.length < frames * 2) {
            throw new IllegalArgumentException("PCM buffer is smaller than requested frames");
        }
        if ((long) stereoFrames + frames > maxStereoFrames) {
            throw new IllegalStateException("audio playback PCM bound exceeded");
        }
        int previousSamples = stereoFrames * 2;
        short[] extended = Arrays.copyOf(pcm, previousSamples + frames * 2);
        System.arraycopy(samples, 0, extended, previousSamples, frames * 2);
        pcm = extended;
        stereoFrames += frames;
    }

    public AudioPlaybackTraceSnapshot snapshot() {
        return new AudioPlaybackTraceSnapshot(events, pcm);
    }

    private void addEvent(AudioPlaybackTraceEvent event) {
        if (events.size() == maxEvents) {
            throw new IllegalStateException("audio playback event bound exceeded");
        }
        events.add(event);
    }
}

package com.openggf.tools.audio.playback;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.audio.synth.ChipWriteObserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disabled-by-default in-memory observer for short, deterministic playback
 * scenarios. Bounds are fixed at construction so diagnostics cannot grow into
 * an accidental complete-run recorder.
 */
public final class BoundedAudioPlaybackTrace
        implements ChipWriteObserver, AudioRequestObserver {
    private final int maxEvents;
    private final int maxStereoFrames;
    private final int ym2612ChannelSampleMask;
    private final int maxYm2612ChannelSamples;
    private final List<AudioPlaybackTraceEvent> events = new ArrayList<>();
    private final List<AudioPlaybackTraceSnapshot.Ym2612ChannelSample>
            ym2612ChannelSamples = new ArrayList<>();
    private final List<AudioPlaybackTraceSnapshot.TimedYm2612Write>
            timedYm2612Writes = new ArrayList<>();
    private final List<AudioPlaybackTraceSnapshot.TimedYm2612KeyOn>
            timedYm2612KeyOns = new ArrayList<>();
    private final List<AudioPlaybackTraceSnapshot.TimedAudioRequest>
            timedAudioRequests = new ArrayList<>();
    private final Map<String, Integer> markerYm2612SampleOffsets =
            new LinkedHashMap<>();
    private short[] pcm = new short[0];
    private int stereoFrames;

    public BoundedAudioPlaybackTrace(int maxEvents, int maxStereoFrames) {
        this(maxEvents, maxStereoFrames, 0, 0);
    }

    public BoundedAudioPlaybackTrace(
            int maxEvents,
            int maxStereoFrames,
            int ym2612ChannelSampleMask,
            int maxYm2612ChannelSamples) {
        if (maxEvents <= 0 || maxStereoFrames <= 0) {
            throw new IllegalArgumentException("trace bounds must be positive");
        }
        if ((ym2612ChannelSampleMask & ~0x3F) != 0
                || Integer.bitCount(ym2612ChannelSampleMask) > 1
                || (ym2612ChannelSampleMask != 0
                && maxYm2612ChannelSamples <= 0)
                || (ym2612ChannelSampleMask == 0
                && maxYm2612ChannelSamples != 0)) {
            throw new IllegalArgumentException(
                    "YM2612 channel sample bounds must match a six-channel mask");
        }
        this.maxEvents = maxEvents;
        this.maxStereoFrames = maxStereoFrames;
        this.ym2612ChannelSampleMask = ym2612ChannelSampleMask;
        this.maxYm2612ChannelSamples = maxYm2612ChannelSamples;
    }

    public void mark(String name) {
        addEvent(new AudioPlaybackTraceEvent.Marker(name));
        markerYm2612SampleOffsets.put(name, ym2612ChannelSamples.size());
    }

    @Override
    public void onYm2612Write(int port, int register, int value) {
        addEvent(new AudioPlaybackTraceEvent.Ym2612Write(
                port, register, value));
        if (ym2612ChannelSampleMask != 0) {
            timedYm2612Writes.add(
                    new AudioPlaybackTraceSnapshot.TimedYm2612Write(
                            ym2612ChannelSamples.size(), port, register, value));
        }
    }

    @Override
    public void onPsgWrite(int value) {
        addEvent(new AudioPlaybackTraceEvent.PsgWrite(value));
    }

    @Override
    public int ym2612ChannelSampleMask() {
        return ym2612ChannelSampleMask;
    }

    @Override
    public void onYm2612ChannelSample(int channel, int output) {
        if ((ym2612ChannelSampleMask & (1 << channel)) == 0) {
            return;
        }
        if (ym2612ChannelSamples.size() == maxYm2612ChannelSamples) {
            throw new IllegalStateException(
                    "audio playback YM2612 channel sample bound exceeded");
        }
        ym2612ChannelSamples.add(
                new AudioPlaybackTraceSnapshot.Ym2612ChannelSample(channel, output));
    }

    @Override
    public void onYm2612KeyOn(
            int channel, int operator, int attenuation) {
        addEvent(new AudioPlaybackTraceEvent.Ym2612KeyOn(
                channel, operator, attenuation));
        if ((ym2612ChannelSampleMask & (1 << channel)) != 0) {
            timedYm2612KeyOns.add(
                    new AudioPlaybackTraceSnapshot.TimedYm2612KeyOn(
                            ym2612ChannelSamples.size(), channel, operator,
                            attenuation));
        }
    }

    @Override
    public void onRequested(RequestClass requestClass, int rawSoundId) {
        addEvent(new AudioPlaybackTraceEvent.AudioRequest(
                requestClass, rawSoundId));
        if (ym2612ChannelSampleMask != 0) {
            timedAudioRequests.add(
                    new AudioPlaybackTraceSnapshot.TimedAudioRequest(
                            ym2612ChannelSamples.size(), requestClass,
                            rawSoundId));
        }
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
        return new AudioPlaybackTraceSnapshot(
                events, pcm, ym2612ChannelSampleMask,
                ym2612ChannelSamples, timedYm2612Writes,
                timedYm2612KeyOns,
                timedAudioRequests,
                markerYm2612SampleOffsets);
    }

    private void addEvent(AudioPlaybackTraceEvent event) {
        if (events.size() == maxEvents) {
            throw new IllegalStateException("audio playback event bound exceeded");
        }
        events.add(event);
    }
}

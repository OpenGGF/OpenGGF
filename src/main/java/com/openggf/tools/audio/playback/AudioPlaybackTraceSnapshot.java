package com.openggf.tools.audio.playback;

import com.openggf.audio.AudioRequestObserver;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Immutable result of one bounded audio playback observation. */
public final class AudioPlaybackTraceSnapshot {
    private final List<AudioPlaybackTraceEvent> events;
    private final short[] pcm;
    private final PcmSummary pcmSummary;
    private final int ym2612ChannelSampleMask;
    private final List<Ym2612ChannelSample> ym2612ChannelSamples;
    private final List<TimedYm2612Write> timedYm2612Writes;
    private final List<TimedYm2612KeyOn> timedYm2612KeyOns;
    private final List<TimedAudioRequest> timedAudioRequests;
    private final Map<String, Integer> markerYm2612SampleOffsets;

    AudioPlaybackTraceSnapshot(
            List<AudioPlaybackTraceEvent> events, short[] pcm) {
        this(events, pcm, 0, List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    AudioPlaybackTraceSnapshot(
            List<AudioPlaybackTraceEvent> events,
            short[] pcm,
            int ym2612ChannelSampleMask,
            List<Ym2612ChannelSample> ym2612ChannelSamples,
            List<TimedYm2612Write> timedYm2612Writes,
            List<TimedYm2612KeyOn> timedYm2612KeyOns,
            List<TimedAudioRequest> timedAudioRequests,
            Map<String, Integer> markerYm2612SampleOffsets) {
        this.events = List.copyOf(events);
        this.pcm = pcm.clone();
        this.ym2612ChannelSampleMask = ym2612ChannelSampleMask;
        this.ym2612ChannelSamples = List.copyOf(ym2612ChannelSamples);
        this.timedYm2612Writes = List.copyOf(timedYm2612Writes);
        this.timedYm2612KeyOns = List.copyOf(timedYm2612KeyOns);
        this.timedAudioRequests = List.copyOf(timedAudioRequests);
        this.markerYm2612SampleOffsets = Map.copyOf(markerYm2612SampleOffsets);
        pcmSummary = summarize(this.pcm);
    }

    public List<AudioPlaybackTraceEvent> events() {
        return events;
    }

    public short[] pcm() {
        return pcm.clone();
    }

    public PcmSummary pcmSummary() {
        return pcmSummary;
    }

    public List<Integer> ym2612ChannelSamplesAfter(
            String markerName, int channel) {
        if (channel < 0 || channel >= 6
                || (ym2612ChannelSampleMask & (1 << channel)) == 0) {
            throw new IllegalArgumentException(
                    "YM2612 channel was not selected: " + channel);
        }
        int markerEventIndex = uniqueMarkerIndex(markerName);
        int start = markerYm2612SampleOffsets.get(markerName);
        int end = ym2612ChannelSamples.size();
        for (int index = markerEventIndex + 1; index < events.size(); index++) {
            if (events.get(index) instanceof AudioPlaybackTraceEvent.Marker marker) {
                end = markerYm2612SampleOffsets.get(marker.name());
                break;
            }
        }
        return ym2612ChannelSamples.subList(start, end).stream()
                .filter(sample -> sample.channel() == channel)
                .map(Ym2612ChannelSample::output)
                .toList();
    }

    public List<TimedYm2612Write> timedYm2612Writes() {
        return timedYm2612Writes;
    }

    public List<Ym2612ChannelSample> ym2612ChannelSamples() {
        return ym2612ChannelSamples;
    }

    public List<TimedYm2612KeyOn> timedYm2612KeyOns() {
        return timedYm2612KeyOns;
    }

    public List<TimedAudioRequest> timedAudioRequests() {
        return timedAudioRequests;
    }

    public int ym2612ChannelSampleOffset(String markerName) {
        uniqueMarkerIndex(markerName);
        return markerYm2612SampleOffsets.get(markerName);
    }

    public List<Integer> ym2612ChannelSamplesBetween(
            String startMarkerName, String endMarkerName, int channel) {
        if (channel < 0 || channel >= 6
                || (ym2612ChannelSampleMask & (1 << channel)) == 0) {
            throw new IllegalArgumentException(
                    "YM2612 channel was not selected: " + channel);
        }
        int startEvent = uniqueMarkerIndex(startMarkerName);
        int endEvent = uniqueMarkerIndex(endMarkerName);
        if (endEvent <= startEvent) {
            throw new IllegalArgumentException(
                    "end marker must follow start marker");
        }
        int start = markerYm2612SampleOffsets.get(startMarkerName);
        int end = markerYm2612SampleOffsets.get(endMarkerName);
        return ym2612ChannelSamples.subList(start, end).stream()
                .filter(sample -> sample.channel() == channel)
                .map(Ym2612ChannelSample::output)
                .toList();
    }

    /** Events after the named marker, stopping before the next marker. */
    public List<AudioPlaybackTraceEvent> eventsAfter(String markerName) {
        int markerIndex = uniqueMarkerIndex(markerName);
        int end = events.size();
        for (int index = markerIndex + 1; index < events.size(); index++) {
            if (events.get(index) instanceof AudioPlaybackTraceEvent.Marker) {
                end = index;
                break;
            }
        }
        return List.copyOf(events.subList(markerIndex + 1, end));
    }

    private int uniqueMarkerIndex(String markerName) {
        int markerIndex = -1;
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index) instanceof AudioPlaybackTraceEvent.Marker marker
                    && marker.name().equals(markerName)) {
                if (markerIndex >= 0) {
                    throw new IllegalArgumentException(
                            "marker is not unique: " + markerName);
                }
                markerIndex = index;
            }
        }
        if (markerIndex < 0) {
            throw new IllegalArgumentException("marker is absent: " + markerName);
        }
        return markerIndex;
    }

    public record Ym2612ChannelSample(int channel, int output) {
        public Ym2612ChannelSample {
            if (channel < 0 || channel >= 6) {
                throw new IllegalArgumentException(
                        "YM2612 channel must be between 0 and 5");
            }
        }
    }

    public record TimedYm2612Write(
            int sampleOrdinal, int port, int register, int value) {
        public TimedYm2612Write {
            if (sampleOrdinal < 0) {
                throw new IllegalArgumentException(
                        "YM2612 sample ordinal must not be negative");
            }
            if (port < 0 || port > 1
                    || register < 0 || register > 0xFF
                    || value < 0 || value > 0xFF) {
                throw new IllegalArgumentException(
                        "timed YM2612 write must contain byte values");
            }
        }
    }

    public record TimedAudioRequest(
            int sampleOrdinal,
            AudioRequestObserver.RequestClass requestClass,
            int rawSoundId) {
        public TimedAudioRequest {
            if (sampleOrdinal < 0) {
                throw new IllegalArgumentException(
                        "YM2612 sample ordinal must not be negative");
            }
            java.util.Objects.requireNonNull(requestClass, "requestClass");
            if (rawSoundId < 0 || rawSoundId > 0xFF) {
                throw new IllegalArgumentException(
                        "raw sound id must be an unsigned byte");
            }
        }
    }

    public record TimedYm2612KeyOn(
            int sampleOrdinal, int channel, int operator, int attenuation) {
        public TimedYm2612KeyOn {
            if (sampleOrdinal < 0) {
                throw new IllegalArgumentException(
                        "YM2612 sample ordinal must not be negative");
            }
            if (channel < 0 || channel >= 6 || operator < 0 || operator >= 4
                    || attenuation < 0 || attenuation > 1023) {
                throw new IllegalArgumentException(
                        "timed YM2612 key-on fields are outside their bounds");
            }
        }
    }

    public record PcmSummary(
            int stereoFrames,
            int leftPeak,
            int rightPeak,
            double leftRms,
            double rightRms,
            String sha256) {
    }

    private static PcmSummary summarize(short[] samples) {
        long leftSquares = 0;
        long rightSquares = 0;
        int leftPeak = 0;
        int rightPeak = 0;
        for (int index = 0; index < samples.length; index += 2) {
            int left = samples[index];
            int right = samples[index + 1];
            leftPeak = Math.max(leftPeak, Math.abs(left));
            rightPeak = Math.max(rightPeak, Math.abs(right));
            leftSquares += (long) left * left;
            rightSquares += (long) right * right;
        }
        int frames = samples.length / 2;
        return new PcmSummary(
                frames,
                leftPeak,
                rightPeak,
                frames == 0 ? 0.0 : Math.sqrt((double) leftSquares / frames),
                frames == 0 ? 0.0 : Math.sqrt((double) rightSquares / frames),
                sha256(samples));
    }

    private static String sha256(short[] samples) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (short sample : samples) {
                digest.update((byte) sample);
                digest.update((byte) (sample >>> 8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}

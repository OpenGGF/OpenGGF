package com.openggf.tools.audio.playback;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Immutable result of one bounded audio playback observation. */
public final class AudioPlaybackTraceSnapshot {
    private final List<AudioPlaybackTraceEvent> events;
    private final short[] pcm;
    private final PcmSummary pcmSummary;

    AudioPlaybackTraceSnapshot(
            List<AudioPlaybackTraceEvent> events, short[] pcm) {
        this.events = List.copyOf(events);
        this.pcm = pcm.clone();
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

    /** Events after the named marker, stopping before the next marker. */
    public List<AudioPlaybackTraceEvent> eventsAfter(String markerName) {
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
        int end = events.size();
        for (int index = markerIndex + 1; index < events.size(); index++) {
            if (events.get(index) instanceof AudioPlaybackTraceEvent.Marker) {
                end = index;
                break;
            }
        }
        return List.copyOf(events.subList(markerIndex + 1, end));
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

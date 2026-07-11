package com.openggf.mods;

import java.util.Objects;

/** Caller-confined mutable source-frame cursor and allocation-free stereo mixer for one prepared track. */
public final class StreamedTrack {
    private final StreamedTrackData data;
    private double position;
    private boolean ended;

    public StreamedTrack(StreamedTrackData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    public StreamedTrackData data() { return data; }
    public double position() { return position; }
    public boolean ended() { return ended; }

    public void restorePosition(double restoredPosition) {
        if (!Double.isFinite(restoredPosition) || restoredPosition < 0
                || restoredPosition >= canonicalEnd()) {
            throw new IllegalArgumentException("Streamed position is outside active source frames");
        }
        position = restoredPosition;
        ended = false;
    }

    /**
     * Adds up to {@code frames} stereo frames into {@code output}. The caller owns and
     * may reuse the output array; this method performs no allocation or I/O.
     */
    public int mixInto(short[] output, int frames, float fadeGain, double rate) {
        Objects.requireNonNull(output, "output");
        if (frames < 0 || frames > output.length / 2) {
            throw new IllegalArgumentException("Stereo output is too small for requested frames");
        }
        if (!Float.isFinite(fadeGain) || fadeGain < 0 || fadeGain > 1) {
            throw new IllegalArgumentException("Fade gain must be finite in 0..1");
        }
        if (!Double.isFinite(rate) || rate <= 0 || rate > 4.0) {
            throw new IllegalArgumentException("Playback rate must be finite in (0,4]");
        }
        if (ended || frames == 0) return 0;

        int mixed = 0;
        while (mixed < frames) {
            canonicalize();
            if (ended) break;
            int left = (int) position;
            double fraction = position - left;
            int right = rightNeighbor(left);
            double combinedGain = data.gain() * (double) fadeGain;
            int outputIndex = mixed * 2;
            for (int channel = 0; channel < 2; channel++) {
                int a = data.sampleAt(left, channel);
                int b = data.sampleAt(right, channel);
                double interpolated = a + (b - (double) a) * fraction;
                int contribution = symmetricRound(interpolated * combinedGain);
                output[outputIndex + channel] = saturatingAdd(output[outputIndex + channel], contribution);
            }
            mixed++;
            position += rate;
            canonicalize();
        }
        return mixed;
    }

    private double canonicalEnd() {
        return data.looping() ? data.loopEndFrame() : data.frameCount();
    }

    private void canonicalize() {
        if (data.looping()) {
            double end = data.loopEndFrame();
            if (position >= end) {
                double loopLength = end - data.loopStartFrame();
                position = data.loopStartFrame() + (position - end) % loopLength;
            }
        } else if (position >= data.frameCount()) {
            position = data.frameCount();
            ended = true;
        }
    }

    private int rightNeighbor(int left) {
        if (data.looping() && left + 1 >= data.loopEndFrame()) {
            return (int) data.loopStartFrame();
        }
        return Math.min(left + 1, data.frameCount() - 1);
    }

    private static int symmetricRound(double value) {
        long rounded = value >= 0 ? (long) Math.floor(value + 0.5) : (long) Math.ceil(value - 0.5);
        if (rounded > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (rounded < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) rounded;
    }

    private static short saturatingAdd(short existing, int contribution) {
        long mixed = existing + (long) contribution;
        if (mixed > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (mixed < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) mixed;
    }
}

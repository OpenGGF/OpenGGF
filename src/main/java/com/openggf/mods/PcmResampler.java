package com.openggf.mods;

import com.openggf.io.ModInputLimits;

import java.util.Objects;

/** Deterministic interleaved linear PCM resampling with half-up rational frame conversion. */
public final class PcmResampler {
    public PcmData resample(PcmData source, int outputRate, ModInputLimits limits) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(limits, "limits");
        if (outputRate < limits.minAudioSampleRate() || outputRate > limits.maxAudioSampleRate()) {
            throw new IllegalArgumentException("Output sample rate is outside configured limits");
        }
        if (outputRate == source.sampleRate()) return source;
        long outputFramesLong = outputFrameCount(source.frameCount(), source.sampleRate(), outputRate);
        if (outputFramesLong <= 0 || outputFramesLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Resampled frame count is outside Java array limits");
        }
        long outputSamplesLong = Math.multiplyExact(outputFramesLong, source.channels());
        long outputBytes = Math.multiplyExact(outputSamplesLong, Short.BYTES);
        if (outputBytes > limits.maxAudioTrackPcmBytes()
                || Math.addExact(source.byteSize(), outputBytes) > limits.maxAudioCacheBytes()) {
            throw new IllegalArgumentException("Resample output exceeds audio budgets");
        }
        short[] output = new short[Math.toIntExact(outputSamplesLong)];
        int outputFrames = (int) outputFramesLong;
        for (int frame = 0; frame < outputFrames; frame++) {
            long numerator = Math.multiplyExact((long) frame, source.sampleRate());
            int left = (int) Math.min(numerator / outputRate, source.frameCount() - 1L);
            int right = Math.min(left + 1, source.frameCount() - 1);
            long remainder = numerator % outputRate;
            for (int channel = 0; channel < source.channels(); channel++) {
                int a = source.sampleAt(left * source.channels() + channel);
                int b = source.sampleAt(right * source.channels() + channel);
                long weighted = Math.addExact(Math.multiplyExact((long) a, outputRate - remainder),
                        Math.multiplyExact((long) b, remainder));
                long rounded = weighted >= 0 ? weighted + outputRate / 2L : weighted - outputRate / 2L;
                output[frame * source.channels() + channel] = (short) (rounded / outputRate);
            }
        }
        return PcmData.takeOwnership(outputRate, source.channels(), output);
    }

    public long convertFrame(long sourceFrame, int sourceRate, int outputRate) {
        if (sourceFrame < 0 || sourceRate <= 0 || outputRate <= 0) {
            throw new IllegalArgumentException("Frame and rates must be nonnegative/positive");
        }
        long scaled = Math.multiplyExact(sourceFrame, (long) outputRate);
        return Math.addExact(scaled, sourceRate / 2L) / sourceRate;
    }

    public LoopBounds convertLoop(long sourceStart, long sourceEnd, PcmData source, int outputRate) {
        Objects.requireNonNull(source, "source");
        if (sourceStart < 0 || sourceEnd <= sourceStart || sourceEnd > source.frameCount()) {
            throw new IllegalArgumentException("Loop must be a nonempty half-open range within decoded frames");
        }
        long convertedEnd = sourceEnd == source.frameCount()
                ? outputFrameCount(source.frameCount(), source.sampleRate(), outputRate)
                : convertFrame(sourceEnd, source.sampleRate(), outputRate);
        return new LoopBounds(convertFrame(sourceStart, source.sampleRate(), outputRate), convertedEnd);
    }

    long outputFrameCount(long sourceFrames, int sourceRate, int outputRate) {
        if (sourceFrames <= 0 || sourceRate <= 0 || outputRate <= 0) {
            throw new IllegalArgumentException("Frames and rates must be positive");
        }
        long scaled = Math.multiplyExact(sourceFrames, (long) outputRate);
        long quotient = scaled / sourceRate;
        return Math.addExact(quotient, scaled % sourceRate == 0 ? 0 : 1);
    }

    public record LoopBounds(long startFrame, long endFrame) {
        public LoopBounds {
            if (startFrame < 0 || endFrame <= startFrame) throw new IllegalArgumentException("Invalid loop bounds");
        }
    }
}

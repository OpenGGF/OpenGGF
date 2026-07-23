package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioSourceDescriptor;

import java.util.Objects;

/**
 * A deterministic, decoded PCM presentation voice with a Q32.32 source cursor.
 */
public final class SampleBackedVoice implements PresentationVoice {
    private static final long Q32_ONE = 1L << 32;
    private static final int YM_DAC_GAIN_Q16 = 1 << 14;

    private final long voiceId;
    private final int priority;
    private final DecodedPcm pcm;
    private final Integer musicId;
    private final AudioSourceDescriptor sourceDescriptor;
    private final long sourceStepQ32;
    private final int gainQ16;
    private final boolean looping;
    private final long endPositionQ32;
    private long sourcePositionQ32;
    private boolean stopped;

    public SampleBackedVoice(long voiceId, int priority, DecodedPcm pcm, int outputSampleRate,
                             double pitch, int gainQ16, boolean looping) {
        this(voiceId, priority, pcm, null, null, 0L,
                calculateSourceStepQ32(pcm, outputSampleRate, pitch), gainQ16, looping, false);
    }

    private SampleBackedVoice(long voiceId, int priority, DecodedPcm pcm, Integer musicId,
                              AudioSourceDescriptor sourceDescriptor, long sourcePositionQ32,
                              long sourceStepQ32, int gainQ16, boolean looping, boolean stopped) {
        this.voiceId = voiceId;
        this.priority = priority;
        this.pcm = Objects.requireNonNull(pcm, "pcm");
        this.musicId = musicId;
        this.sourceDescriptor = sourceDescriptor;
        this.endPositionQ32 = ((long) pcm.sourceFrames()) << 32;
        if (sourcePositionQ32 < 0 || sourcePositionQ32 > endPositionQ32) {
            throw new IllegalArgumentException("sourcePositionQ32 is outside the sample");
        }
        if (sourceStepQ32 <= 0) {
            throw new IllegalArgumentException("sourceStepQ32 must be positive");
        }
        this.sourcePositionQ32 = sourcePositionQ32;
        this.sourceStepQ32 = sourceStepQ32;
        this.gainQ16 = gainQ16;
        this.looping = looping;
        this.stopped = stopped;
    }

    public static SampleBackedVoice rawSegaPcm(long voiceId, int priority, DecodedPcm pcm,
                                                int outputSampleRate) {
        return new SampleBackedVoice(voiceId, priority, pcm, outputSampleRate, 1.0,
                YM_DAC_GAIN_Q16, false);
    }

    public static SampleBackedVoice restore(PresentationVoiceSnapshot.Sample snapshot, DecodedPcm pcm) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.assetId().equals(Objects.requireNonNull(pcm, "pcm").assetId())) {
            throw new IllegalArgumentException("snapshot assetId does not match PCM");
        }
        return new SampleBackedVoice(snapshot.voiceId(), snapshot.priority(), pcm, snapshot.musicId(),
                snapshot.sourceDescriptor(), snapshot.sourcePositionQ32(), snapshot.sourceStepQ32(),
                snapshot.gainQ16(), snapshot.looping(), snapshot.stopped());
    }

    public static SampleBackedVoice restore(PresentationVoiceSnapshot.Sample snapshot, DecodedPcmCache cache) {
        Objects.requireNonNull(snapshot, "snapshot");
        DecodedPcm pcm = Objects.requireNonNull(cache, "cache").get(snapshot.assetId());
        if (pcm == null) {
            throw new IllegalArgumentException("no cached PCM for " + snapshot.assetId());
        }
        return restore(snapshot, pcm);
    }

    @Override
    public long voiceId() {
        return voiceId;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public void mixInto(long[] accumulation, int stereoFrames) {
        Objects.requireNonNull(accumulation, "accumulation");
        if (stereoFrames < 0 || accumulation.length < (long) stereoFrames * 2) {
            throw new IllegalArgumentException("accumulation cannot hold requested stereo frames");
        }
        for (int frame = 0; frame < stereoFrames && !isComplete(); frame++) {
            int sourceFrame = (int) (sourcePositionQ32 >>> 32);
            long fraction = sourcePositionQ32 & 0xFFFF_FFFFL;
            int nextFrame = sourceFrame + 1;
            if (nextFrame >= pcm.sourceFrames()) {
                nextFrame = looping ? 0 : sourceFrame;
            }
            int left = interpolate(sourceFrame, nextFrame, 0, fraction);
            int right = pcm.channels() == 1 ? left : interpolate(sourceFrame, nextFrame, 1, fraction);
            int outputIndex = frame * 2;
            accumulation[outputIndex] += ((long) left * gainQ16) >> 16;
            accumulation[outputIndex + 1] += ((long) right * gainQ16) >> 16;
            advance();
        }
    }

    @Override
    public boolean isComplete() {
        return stopped || endPositionQ32 == 0 || (!looping && sourcePositionQ32 >= endPositionQ32);
    }

    @Override
    public void stop() {
        stopped = true;
    }

    @Override
    public PresentationVoiceSnapshot snapshot() {
        return new PresentationVoiceSnapshot.Sample(voiceId, priority, pcm.assetId(), musicId, sourceDescriptor,
                sourcePositionQ32, sourceStepQ32, gainQ16, looping, stopped);
    }

    private int interpolate(int sourceFrame, int nextFrame, int channel, long fraction) {
        int left = pcm.sample(sourceFrame, channel);
        int right = pcm.sample(nextFrame, channel);
        return left + (int) (((long) (right - left) * fraction) >> 32);
    }

    private void advance() {
        if (looping) {
            if (endPositionQ32 == 0) {
                return;
            }
            long advance = sourceStepQ32 % endPositionQ32;
            long remaining = endPositionQ32 - sourcePositionQ32;
            sourcePositionQ32 = advance >= remaining ? advance - remaining : sourcePositionQ32 + advance;
            return;
        }
        long remaining = endPositionQ32 - sourcePositionQ32;
        if (sourceStepQ32 >= remaining) {
            sourcePositionQ32 = endPositionQ32;
        } else {
            sourcePositionQ32 += sourceStepQ32;
        }
    }

    private static long calculateSourceStepQ32(DecodedPcm pcm, int outputSampleRate, double pitch) {
        Objects.requireNonNull(pcm, "pcm");
        if (outputSampleRate <= 0) {
            throw new IllegalArgumentException("outputSampleRate must be positive");
        }
        if (!Double.isFinite(pitch) || pitch <= 0.0) {
            throw new IllegalArgumentException("pitch must be finite and positive");
        }
        double step = (pcm.sampleRate() * pitch / outputSampleRate) * Q32_ONE;
        if (step > Long.MAX_VALUE) {
            throw new IllegalArgumentException("source step is too large");
        }
        return Math.max(1L, Math.round(step));
    }
}

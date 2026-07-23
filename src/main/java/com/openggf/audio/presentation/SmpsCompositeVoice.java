package com.openggf.audio.presentation;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;

import java.util.Arrays;
import java.util.Objects;

/**
 * Adapts one complete SMPS driver to the presentation mixer without separating
 * its music and SFX sequencers. Channel arbitration remains owned by the driver.
 */
public final class SmpsCompositeVoice implements PresentationVoice {
    private final long voiceId;
    private final int priority;
    private final Integer musicId;
    private final AudioSourceDescriptor sourceDescriptor;
    private final int maxStereoFrames;
    private final SmpsDriver driver;
    private final short[] scratch;

    public SmpsCompositeVoice(long voiceId, int priority, Integer musicId,
                              AudioSourceDescriptor sourceDescriptor, int maxStereoFrames,
                              SmpsDriver driver) {
        if (maxStereoFrames < 0 || maxStereoFrames > Integer.MAX_VALUE / 2) {
            throw new IllegalArgumentException("maxStereoFrames must fit an interleaved stereo buffer");
        }
        this.voiceId = voiceId;
        this.priority = priority;
        this.musicId = musicId;
        this.sourceDescriptor = sourceDescriptor;
        this.maxStereoFrames = maxStereoFrames;
        this.driver = Objects.requireNonNull(driver, "driver");
        scratch = new short[maxStereoFrames * 2];
    }

    public SmpsDriver driver() {
        return driver;
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
        if (stereoFrames < 0 || stereoFrames > maxStereoFrames
                || accumulation.length < (long) stereoFrames * 2) {
            throw new IllegalArgumentException("requested stereo frames exceed composite capacity");
        }
        int samples = stereoFrames * 2;
        Arrays.fill(scratch, 0, samples, (short) 0);
        int renderedSamples = driver.read(scratch, samples);
        if (renderedSamples < 0 || renderedSamples > samples) {
            throw new IllegalStateException("SmpsDriver returned an invalid sample count");
        }
        for (int sample = 0; sample < renderedSamples; sample++) {
            accumulation[sample] += scratch[sample];
        }
    }

    @Override
    public boolean isComplete() {
        return driver.isComplete();
    }

    @Override
    public void stop() {
        driver.stopAll();
    }

    @Override
    public PresentationVoiceSnapshot snapshot() {
        return new PresentationVoiceSnapshot.Smps(voiceId, priority, musicId, sourceDescriptor,
                maxStereoFrames, driver.captureSnapshot());
    }

    public void restore(PresentationVoiceSnapshot.Smps snapshot,
                        SmpsDriverSnapshot.DependencyResolver resolver) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.voiceId() != voiceId || snapshot.priority() != priority
                || snapshot.maxStereoFrames() != maxStereoFrames
                || !Objects.equals(snapshot.musicId(), musicId)
                || !Objects.equals(snapshot.sourceDescriptor(), sourceDescriptor)) {
            throw new IllegalArgumentException("snapshot identity does not match composite voice");
        }
        driver.restoreSnapshot(snapshot.driver(), Objects.requireNonNull(resolver, "resolver"));
    }
}

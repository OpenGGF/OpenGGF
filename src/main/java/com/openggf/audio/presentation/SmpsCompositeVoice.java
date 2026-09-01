package com.openggf.audio.presentation;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import java.util.Objects;

/**
 * Adapts one complete SMPS driver to the presentation mixer without separating
 * its music and SFX sequencers. Channel arbitration remains owned by the driver.
 */
@Deprecated(forRemoval = true)
public final class SmpsCompositeVoice implements PresentationVoice {
    private final long voiceId;
    private final int priority;
    private final Integer musicId;
    private final AudioSourceDescriptor sourceDescriptor;
    private final int maxStereoFrames;
    private final SmpsDriver driver;

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
        throw new UnsupportedOperationException(
                "standalone SMPS presentation voices were removed");
    }

    /** Runs the driver's one frame-locked V-blank service before PCM mixing. */
    public void serviceOuterFrame() {
        driver.serviceOuterFrame();
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
        throw new UnsupportedOperationException(
                "standalone SMPS presentation snapshots were removed");
    }

    public static final class LiveCommandMutationToken {
        private final SmpsCompositeVoice owner;
        private final SmpsDriver.LiveCommandMutationToken driverToken;

        private LiveCommandMutationToken(
                SmpsCompositeVoice owner,
                SmpsDriver.LiveCommandMutationToken driverToken) {
            this.owner = owner;
            this.driverToken = driverToken;
        }
    }

    public LiveCommandMutationToken captureLiveCommandMutation() {
        return new LiveCommandMutationToken(
                this, driver.captureLiveCommandMutation());
    }

    public void rollbackLiveCommandMutation(
            LiveCommandMutationToken token) {
        Objects.requireNonNull(token, "token");
        if (token.owner != this) {
            throw new IllegalArgumentException(
                    "live command token belongs to another composite voice");
        }
        driver.rollbackLiveCommandMutation(token.driverToken);
    }

}

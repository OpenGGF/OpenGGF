package com.openggf.audio.session;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.VirtualSynthesizer;

import java.util.Objects;

public final class SmpsPhysicalDevice {
    public record Settings(
            double outputSampleRate,
            boolean dacInterpolate,
            boolean psgNoiseShiftEveryToggle) {
        public Settings {
            if (!Double.isFinite(outputSampleRate)
                    || outputSampleRate <= 0.0) {
                throw new IllegalArgumentException(
                        "outputSampleRate must be positive and finite");
            }
        }
    }

    public record Snapshot(
            VirtualSynthesizer.Snapshot synth,
            Settings settings) {
        public Snapshot {
            Objects.requireNonNull(synth, "synth");
            Objects.requireNonNull(settings, "settings");
        }
    }

    interface LiveMutationToken {
    }

    private final class LiveMutationState implements LiveMutationToken {
        private final Object deviceIdentity;
        private final Snapshot snapshot;
        private final DacData selectedDac;
        private boolean consumed;

        private LiveMutationState(
                Snapshot snapshot,
                DacData selectedDac) {
            deviceIdentity = identity;
            this.snapshot = snapshot;
            this.selectedDac = selectedDac;
        }
    }

    private final Thread ownerThread;
    private final Object identity = new Object();
    private final Settings settings;
    private final VirtualSynthesizer synth;
    private boolean closed;

    SmpsPhysicalDevice(Settings settings, ChipWriteObserver observer) {
        ownerThread = Thread.currentThread();
        this.settings = Objects.requireNonNull(settings, "settings");
        synth = new VirtualSynthesizer(
                settings.outputSampleRate(),
                Objects.requireNonNull(observer, "observer"),
                VirtualSynthesizer.Initialization.DEFERRED);
        synth.setDacInterpolate(settings.dacInterpolate());
        synth.setPsgNoiseShiftOnEveryToggle(
                settings.psgNoiseShiftEveryToggle());
    }

    void apply(SmpsWriteProgram program) {
        requireActive();
        for (SmpsChipWrite write : Objects.requireNonNull(
                program, "program").writes()) {
            if (write instanceof SmpsChipWrite.Ym2612 ym) {
                synth.writeFm(this, ym.port(), ym.register(), ym.value());
            } else if (write instanceof SmpsChipWrite.Psg psg) {
                synth.writePsg(this, psg.value());
            }
        }
    }

    int renderFrames(
            short[] target,
            int offsetSamples,
            int stereoFrames) {
        requireActive();
        Objects.requireNonNull(target, "target");
        if (offsetSamples < 0 || (offsetSamples & 1) != 0) {
            throw new IllegalArgumentException(
                    "offsetSamples must be non-negative and stereo-aligned");
        }
        if (stereoFrames < 0) {
            throw new IllegalArgumentException(
                    "stereoFrames must be non-negative");
        }
        long requiredSamples = (long) offsetSamples
                + (long) stereoFrames * 2;
        if (requiredSamples > target.length) {
            throw new IllegalArgumentException(
                    "target does not contain the requested frame range");
        }
        synth.renderFrames(target, offsetSamples / 2, stereoFrames);
        return stereoFrames;
    }

    Snapshot captureSnapshot() {
        requireActive();
        return new Snapshot(synth.captureSynthSnapshot(), settings);
    }

    void restoreSnapshot(Snapshot snapshot, DacData resolvedDac) {
        requireActive();
        Snapshot resolved = Objects.requireNonNull(snapshot, "snapshot");
        if (!settings.equals(resolved.settings())) {
            throw new IllegalArgumentException(
                    "physical snapshot settings do not match the device");
        }
        synth.restoreSelectedDacData(resolvedDac);
        synth.restoreSynthSnapshot(resolved.synth());
    }

    LiveMutationToken captureLiveMutation() {
        requireActive();
        return new LiveMutationState(
                captureSnapshot(), synth.selectedDacDataForSnapshot());
    }

    void rollbackLiveMutation(LiveMutationToken token) {
        requireActive();
        LiveMutationState state = requireLiveMutation(token);
        if (state.consumed) {
            throw new IllegalStateException(
                    "physical mutation token has already been consumed");
        }
        synth.restoreSelectedDacData(state.selectedDac);
        synth.restoreSynthSnapshot(state.snapshot.synth());
        state.consumed = true;
    }

    void applyChannelMasks(int fmMask, int psgMask) {
        requireActive();
        for (int channel = 0; channel < 6; channel++) {
            synth.setFmMute(channel, (fmMask & (1 << channel)) != 0);
        }
        for (int channel = 0; channel < 4; channel++) {
            synth.setPsgMute(channel, (psgMask & (1 << channel)) != 0);
        }
    }

    void writeFm(int port, int register, int value) {
        requireActive();
        synth.writeFm(this, port, register, value);
    }

    void writePsg(int value) {
        requireActive();
        synth.writePsg(this, value);
    }

    void setInstrument(int channelId, byte[] voice) {
        requireActive();
        synth.setInstrument(this, channelId,
                Objects.requireNonNull(voice, "voice"));
    }

    void playDac(int note) {
        requireActive();
        synth.playDac(this, note);
    }

    void stopDac() {
        requireActive();
        synth.stopDac(this);
    }

    void selectDac(DacData data) {
        requireActive();
        synth.setDacData(Objects.requireNonNull(data, "data"));
    }

    void forceSilenceFmChannel(int channelId) {
        requireActive();
        synth.forceSilenceChannel(channelId);
    }

    VirtualSynthesizer.SfxAdmissionState captureAdmissionState(
            int fmMask, int psgMask) {
        requireActive();
        return synth.captureSfxAdmissionState(fmMask, psgMask);
    }

    void restoreAdmissionState(
            VirtualSynthesizer.SfxAdmissionState state) {
        requireActive();
        synth.restoreSfxAdmissionState(
                Objects.requireNonNull(state, "state"));
    }

    void close() {
        requireOwnerThread();
        if (closed) {
            return;
        }
        closed = true;
    }

    private LiveMutationState requireLiveMutation(
            LiveMutationToken token) {
        if (!(token instanceof SmpsPhysicalDevice.LiveMutationState state)
                || state.deviceIdentity != identity) {
            throw new IllegalArgumentException(
                    "physical mutation token belongs to another device");
        }
        return state;
    }

    private void requireActive() {
        requireOwnerThread();
        if (closed) {
            throw new IllegalStateException(
                    "SMPS physical device is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "SMPS physical device accessed off its owner thread");
        }
    }
}

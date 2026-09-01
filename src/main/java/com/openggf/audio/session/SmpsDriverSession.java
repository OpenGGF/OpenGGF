package com.openggf.audio.session;

import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.VirtualSynthesizer;

import java.util.Objects;

public final class SmpsDriverSession implements AutoCloseable {
    public interface DacDependencyResolver {
        DacData resolve(SmpsSourceDescriptor source);
    }

    public record PreparedRestore(
            SmpsDriverSessionSnapshot session,
            SmpsDriverSnapshot logical,
            DacData resolvedDac) {
        public PreparedRestore {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(logical, "logical");
        }
    }

    public interface LiveMutationToken {
    }

    private final class SessionLiveMutation implements LiveMutationToken {
        private final Object ownerIdentity;
        private final SmpsPhysicalDevice.LiveMutationToken physical;
        private final boolean initialized;
        private final SmpsPendingGlobalCommand pendingGlobalCommand;
        private final SmpsSourceDescriptor selectedDacSource;
        private boolean consumed;

        private SessionLiveMutation(
                SmpsPhysicalDevice.LiveMutationToken physical) {
            ownerIdentity = sessionIdentity;
            this.physical = physical;
            initialized = SmpsDriverSession.this.initialized;
            pendingGlobalCommand =
                    SmpsDriverSession.this.pendingGlobalCommand;
            selectedDacSource =
                    SmpsDriverSession.this.selectedDacSource;
        }
    }

    private final class PortCapability implements SmpsPhysicalPort {
        private final Object ownerSessionIdentity;
        private final SmpsDriverServiceObserver.DriverIdentity owner;
        private final long epoch;

        private PortCapability(
                SmpsDriverServiceObserver.DriverIdentity owner,
                long epoch) {
            ownerSessionIdentity = sessionIdentity;
            this.owner = owner;
            this.epoch = epoch;
        }

        @Override
        public SmpsDriverServiceObserver.DriverIdentity owner() {
            return owner;
        }

        @Override
        public long epoch() {
            return epoch;
        }

        @Override
        public void writeFm(int port, int register, int value) {
            requireOpen(this);
            device.writeFm(port, register, value);
        }

        @Override
        public void writePsg(int value) {
            requireOpen(this);
            device.writePsg(value);
        }

        @Override
        public void setInstrument(int channelId, byte[] voice) {
            requireOpen(this);
            device.setInstrument(channelId, voice);
        }

        @Override
        public void playDac(int note) {
            requireOpen(this);
            device.playDac(note);
        }

        @Override
        public void stopDac() {
            requireOpen(this);
            device.stopDac();
        }

        @Override
        public void selectDac(SmpsDacSelection selection) {
            requireOpen(this);
            SmpsDacSelection resolved = Objects.requireNonNull(
                    selection, "selection");
            device.selectDac(resolved.data());
            selectedDacSource = resolved.source();
        }

        @Override
        public void forceSilenceFmChannel(int channelId) {
            requireOpen(this);
            device.forceSilenceFmChannel(channelId);
        }

        @Override
        public AdmissionToken captureAdmissionState(
                int fmMask, int psgMask) {
            requireOpen(this);
            return new AdmissionState(this,
                    device.captureAdmissionState(fmMask, psgMask));
        }

        @Override
        public void restoreAdmissionState(AdmissionToken token) {
            requireOpen(this);
            AdmissionState state = requireAdmissionToken(token);
            if (state.consumed) {
                throw new IllegalStateException(
                        "SMPS admission token has already been consumed");
            }
            device.restoreAdmissionState(state.physical);
            state.consumed = true;
        }
    }

    private final class AdmissionState
            implements SmpsPhysicalPort.AdmissionToken {
        private final Object ownerSessionIdentity;
        private final SmpsPhysicalDevice ownerDevice;
        private final SmpsDriverServiceObserver.DriverIdentity owner;
        private final long epoch;
        private final VirtualSynthesizer.SfxAdmissionState physical;
        private boolean consumed;

        private AdmissionState(
                PortCapability capability,
                VirtualSynthesizer.SfxAdmissionState physical) {
            ownerSessionIdentity = sessionIdentity;
            ownerDevice = device;
            owner = capability.owner;
            epoch = capability.epoch;
            this.physical = physical;
        }
    }

    private final Thread ownerThread;
    private final Object sessionIdentity = new Object();
    private final SmpsPhysicalDevice device;
    private final SmpsPhysicalPolicy policy;
    private final SmpsSessionProfileFingerprint profile;
    private boolean initialized;
    private SmpsPendingGlobalCommand pendingGlobalCommand =
            SmpsPendingGlobalCommand.NONE;
    private SmpsSourceDescriptor selectedDacSource;
    private SmpsDriverServiceObserver.DriverIdentity openOwner;
    private long openEpoch;
    private long nextEpoch;
    private boolean closed;

    public SmpsDriverSession(
            SmpsPhysicalDevice.Settings settings,
            SmpsPhysicalPolicy policy,
            ChipWriteObserver observer,
            SmpsSessionProfileFingerprint profile) {
        ownerThread = Thread.currentThread();
        SmpsPhysicalDevice.Settings resolvedSettings =
                Objects.requireNonNull(settings, "settings");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.profile = Objects.requireNonNull(profile, "profile");
        if (!resolvedSettings.equals(profile.settings())) {
            throw new IllegalArgumentException(
                    "session settings do not match the profile fingerprint");
        }
        if (!policy.identity().equals(profile.physicalPolicyId())) {
            throw new IllegalArgumentException(
                    "physical policy does not match the profile fingerprint");
        }
        device = new SmpsPhysicalDevice(resolvedSettings,
                Objects.requireNonNull(observer, "observer"));
    }

    public boolean installed() {
        requireActive();
        return false;
    }

    public SmpsDriverSessionSnapshot captureSnapshot() {
        requireActive();
        return new SmpsDriverSessionSnapshot(
                initialized,
                pendingGlobalCommand,
                profile,
                selectedDacSource,
                device.captureSnapshot());
    }

    public LiveMutationToken captureLiveMutation() {
        requireActive();
        return new SessionLiveMutation(device.captureLiveMutation());
    }

    public void commitLiveMutation(LiveMutationToken token) {
        requireActive();
        SessionLiveMutation state = requireLiveMutation(token);
        if (state.consumed) {
            throw new IllegalStateException(
                    "session mutation token has already been consumed");
        }
        state.consumed = true;
    }

    public void rollbackLiveMutation(LiveMutationToken token) {
        requireActive();
        SessionLiveMutation state = requireLiveMutation(token);
        if (state.consumed) {
            throw new IllegalStateException(
                    "session mutation token has already been consumed");
        }
        device.rollbackLiveMutation(state.physical);
        initialized = state.initialized;
        pendingGlobalCommand = state.pendingGlobalCommand;
        selectedDacSource = state.selectedDacSource;
        state.consumed = true;
    }

    public void applyChannelMasks(int fmMask, int psgMask) {
        requireActive();
        device.applyChannelMasks(fmMask, psgMask);
    }

    SmpsPhysicalPort openTestEpoch(
            SmpsDriverServiceObserver.DriverIdentity owner) {
        requireActive();
        if (openOwner != null) {
            throw new IllegalStateException(
                    "an SMPS physical capability epoch is already open");
        }
        openOwner = Objects.requireNonNull(owner, "owner");
        openEpoch = Math.incrementExact(nextEpoch);
        nextEpoch = openEpoch;
        return new PortCapability(openOwner, openEpoch);
    }

    void closeTestEpoch(long epoch) {
        requireActive();
        if (openOwner == null || epoch != openEpoch) {
            throw new IllegalStateException(
                    "SMPS physical capability epoch is not active");
        }
        openOwner = null;
        openEpoch = 0;
    }

    @Override
    public void close() {
        requireOwnerThread();
        if (closed) {
            return;
        }
        openOwner = null;
        openEpoch = 0;
        device.close();
        closed = true;
    }

    private void requireOpen(PortCapability capability) {
        requireActive();
        if (capability.ownerSessionIdentity != sessionIdentity
                || capability.epoch != openEpoch
                || !capability.owner.equals(openOwner)) {
            throw new IllegalStateException(
                    "SMPS physical capability is not active");
        }
    }

    private AdmissionState requireAdmissionToken(
            SmpsPhysicalPort.AdmissionToken token) {
        if (!(token instanceof SmpsDriverSession.AdmissionState state)
                || state.ownerSessionIdentity != sessionIdentity
                || state.ownerDevice != device
                || state.epoch != openEpoch
                || !state.owner.equals(openOwner)) {
            throw new IllegalArgumentException(
                    "SMPS admission token does not belong to this capability");
        }
        return state;
    }

    private SessionLiveMutation requireLiveMutation(
            LiveMutationToken token) {
        if (!(token instanceof SmpsDriverSession.SessionLiveMutation state)
                || state.ownerIdentity != sessionIdentity) {
            throw new IllegalArgumentException(
                    "session mutation token belongs to another session");
        }
        return state;
    }

    private void requireActive() {
        requireOwnerThread();
        if (closed) {
            throw new IllegalStateException("SMPS driver session is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "SMPS driver session accessed off its owner thread");
        }
    }
}

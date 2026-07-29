package com.openggf.game.timing;

import java.util.Objects;
import java.util.Set;

/** Mutable runtime state for one submitted job; ownership stays inside the timing service. */
public final class HardwareTimingJob {
    private final HardwareWorkSubmission submission;
    private final HardwareWorkHandle handle;
    private byte[] preparedPayload;
    private boolean ready;
    private boolean claimed;
    private boolean profileActive;
    private boolean physicallyRetired;
    private int assignedServiceFrames;
    private int remainingServiceFrames;
    private Set<HardwareServiceBoundary> eligibleBoundaries = Set.of();
    private LoadTimeDecisionSource decisionSource = LoadTimeDecisionSource.IMMEDIATE;
    private String serviceModel = "unassigned";

    HardwareTimingJob(HardwareWorkSubmission submission, HardwareWorkHandle handle) {
        this.submission = Objects.requireNonNull(submission, "submission");
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    private HardwareTimingJob(Snapshot snapshot) {
        HardwareWorkPreparation preparation = Objects.requireNonNull(
                snapshot.preparationSnapshot().recreatePreparation(),
                "recreated preparation");
        this.submission = new HardwareWorkSubmission(
                snapshot.kind(),
                snapshot.romSourceAddress(),
                snapshot.compressedLength(),
                snapshot.destinationAddress(),
                snapshot.destinationLength(),
                snapshot.compressionVariant(),
                snapshot.moduleCount(),
                snapshot.exportableAcrossSegment(),
                preparation);
        this.handle = snapshot.handle();
        this.preparedPayload = snapshot.preparedPayload();
        this.ready = snapshot.ready();
        this.claimed = snapshot.claimed();
        this.profileActive = snapshot.profileActive();
        this.physicallyRetired = snapshot.physicallyRetired();
        this.assignedServiceFrames = snapshot.assignedServiceFrames();
        this.remainingServiceFrames = snapshot.remainingServiceFrames();
        this.eligibleBoundaries = snapshot.eligibleBoundaries();
        this.decisionSource = snapshot.decisionSource();
        this.serviceModel = snapshot.serviceModel();
    }

    HardwareWorkPreparation preparation() {
        return submission.preparation();
    }

    HardwareWorkSubmission submission() {
        return submission;
    }

    HardwareWorkHandle handle() {
        return handle;
    }

    boolean hasPreparedPayload() {
        return preparedPayload != null;
    }

    boolean isReady() {
        return ready;
    }

    boolean isClaimed() {
        return claimed;
    }

    boolean isPhysicallyRetired() {
        return physicallyRetired;
    }

    boolean isProfileActive() {
        return profileActive;
    }

    void activateProfile(LoadTimeDecision decision) {
        if (profileActive || physicallyRetired) {
            throw new IllegalStateException("hardware profile already activated");
        }
        Objects.requireNonNull(decision, "decision");
        profileActive = true;
        assignedServiceFrames = decision.serviceFrames();
        remainingServiceFrames = decision.serviceFrames();
        eligibleBoundaries = decision.eligibleBoundaries();
        decisionSource = decision.source();
        serviceModel = decision.serviceModel();
    }

    void advanceProfile(HardwareServiceBoundary boundary) {
        if (profileActive && remainingServiceFrames > 0
                && eligibleBoundaries.contains(boundary)) {
            remainingServiceFrames--;
        }
    }

    boolean isProfileComplete() {
        return profileActive && remainingServiceFrames == 0;
    }

    void capturePreparedPayload() {
        if (preparedPayload != null) {
            return;
        }
        if (!preparation().isPrepared()) {
            throw new IllegalStateException(
                    "hardware preparation is not complete: " + describe(handle));
        }
        byte[] payload = Objects.requireNonNull(
                preparation().preparedPayload(), "preparedPayload");
        preparedPayload = payload.clone();
    }

    void admitReadiness() {
        if (claimed) {
            throw new IllegalStateException(
                    "hardware work was already claimed: " + describe(handle));
        }
        if (ready) {
            throw new IllegalStateException(
                    "hardware work was already released: " + describe(handle));
        }
        if (preparedPayload == null || !preparation().isPrepared()) {
            throw new IllegalStateException(
                    "hardware work is not prepared: " + describe(handle));
        }
        ready = true;
        physicallyRetired = true;
    }

    byte[] claim() {
        if (claimed) {
            throw new IllegalStateException(
                    "hardware work was already claimed: " + describe(handle));
        }
        if (!ready) {
            throw new IllegalStateException(
                    "hardware work is not ready: " + describe(handle));
        }
        claimed = true;
        ready = false;
        return preparedPayload.clone();
    }

    byte[] claimedPayload() {
        if (!claimed) {
            throw new IllegalStateException(
                    "hardware work has not been claimed: " + describe(handle));
        }
        return preparedPayload.clone();
    }

    Snapshot snapshot() {
        return new Snapshot(
                submission.kind(),
                submission.romSourceAddress(),
                submission.compressedLength(),
                submission.destinationAddress(),
                submission.destinationLength(),
                submission.compressionVariant(),
                submission.moduleCount(),
                submission.exportableAcrossSegment(),
                handle,
                Objects.requireNonNull(
                        preparation().snapshot(), "preparation snapshot"),
                preparedPayload,
                ready,
                claimed,
                profileActive,
                physicallyRetired,
                assignedServiceFrames,
                remainingServiceFrames,
                eligibleBoundaries,
                decisionSource,
                serviceModel);
    }

    static HardwareTimingJob restore(Snapshot snapshot) {
        return new HardwareTimingJob(Objects.requireNonNull(snapshot, "snapshot"));
    }

    static String describe(HardwareWorkHandle handle) {
        return handle.kind() + "#" + handle.ordinal()
                + " " + handle.submissionFingerprint();
    }

    /** Complete rewind state for one queued job, including resumable preparation. */
    public record Snapshot(
            HardwareWorkKind kind,
            int romSourceAddress,
            int compressedLength,
            int destinationAddress,
            int destinationLength,
            String compressionVariant,
            int moduleCount,
            boolean exportableAcrossSegment,
            HardwareWorkHandle handle,
            HardwareWorkPreparationSnapshot preparationSnapshot,
            byte[] preparedPayload,
            boolean ready,
            boolean claimed,
            boolean profileActive,
            boolean physicallyRetired,
            int assignedServiceFrames,
            int remainingServiceFrames,
            Set<HardwareServiceBoundary> eligibleBoundaries,
            LoadTimeDecisionSource decisionSource,
            String serviceModel) {

        public Snapshot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(compressionVariant, "compressionVariant");
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(preparationSnapshot, "preparationSnapshot");
            eligibleBoundaries = Set.copyOf(Objects.requireNonNull(
                    eligibleBoundaries, "eligibleBoundaries"));
            Objects.requireNonNull(decisionSource, "decisionSource");
            Objects.requireNonNull(serviceModel, "serviceModel");
            if (assignedServiceFrames < 0 || remainingServiceFrames < 0
                    || remainingServiceFrames > assignedServiceFrames) {
                throw new IllegalArgumentException("invalid profile frame state");
            }
            if (physicallyRetired && !ready && !claimed) {
                throw new IllegalArgumentException(
                        "retired hardware work must be ready or claimed");
            }
            preparedPayload = preparedPayload != null ? preparedPayload.clone() : null;
        }

        @Override
        public byte[] preparedPayload() {
            return preparedPayload != null ? preparedPayload.clone() : null;
        }
    }
}

package com.openggf.game.timing;

import java.util.Objects;

/** Mutable runtime state for one submitted job; ownership stays inside the timing service. */
public final class HardwareTimingJob {
    private final HardwareWorkSubmission submission;
    private final HardwareWorkHandle handle;
    private byte[] preparedPayload;
    private boolean ready;
    private boolean claimed;

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
                claimed);
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
            boolean claimed) {

        public Snapshot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(compressionVariant, "compressionVariant");
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(preparationSnapshot, "preparationSnapshot");
            preparedPayload = preparedPayload != null ? preparedPayload.clone() : null;
        }

        @Override
        public byte[] preparedPayload() {
            return preparedPayload != null ? preparedPayload.clone() : null;
        }
    }
}

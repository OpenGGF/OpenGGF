package com.openggf.game.timing;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Session-owned FIFO for deterministic preparation and observable hardware readiness.
 */
public final class HardwareTimingService
        implements RewindSnapshottable<HardwareTimingSnapshot> {
    public static final String REWIND_KEY = "hardware-timing";

    private final RomWorkBudgetScheduler scheduler;
    private final RecordedAuthority recordedAuthority = new RecordedAuthority();
    private final EnumMap<HardwareWorkKind, Long> nextOrdinals =
            new EnumMap<>(HardwareWorkKind.class);
    private final List<HardwareTimingJob> jobs = new ArrayList<>();

    private HardwareReadinessAdmissionPolicy admissionPolicy =
            HardwareReadinessAdmissionPolicy.LIVE;
    private boolean hasSubmitted;
    private HardwareServiceBoundary lastServicedBoundary;

    public HardwareTimingService() {
        this(RomWorkBudgetScheduler.oneWorkUnitAt(
                HardwareServiceBoundary.POST_OBJECTS));
    }

    public HardwareTimingService(RomWorkBudgetScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public HardwareWorkHandle submit(HardwareWorkSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        long ordinal = nextOrdinals.getOrDefault(submission.kind(), 0L);
        if (ordinal == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "hardware work ordinal exhausted for " + submission.kind());
        }
        HardwareWorkHandle handle = new HardwareWorkHandle(
                submission.kind(),
                ordinal,
                HardwareSubmissionFingerprint.compute(submission));
        nextOrdinals.put(submission.kind(), ordinal + 1);
        jobs.add(new HardwareTimingJob(submission, handle));
        hasSubmitted = true;
        return handle;
    }

    public void service(HardwareServiceBoundary boundary) {
        Objects.requireNonNull(boundary, "boundary");
        serviceBoundaryDrivenHead(boundary);
        scheduler.service(boundary, jobs);
        if (admissionPolicy == HardwareReadinessAdmissionPolicy.LIVE) {
            releasePreparedInFifoOrder();
        }
        lastServicedBoundary = boundary;
    }

    public boolean isPending(HardwareWorkHandle handle) {
        HardwareTimingJob job = find(handle);
        return job != null && !job.isClaimed();
    }

    public boolean isReady(HardwareWorkHandle handle) {
        HardwareTimingJob job = find(handle);
        return job != null && !job.isClaimed() && job.isReady();
    }

    public byte[] claim(HardwareWorkHandle handle) {
        HardwareTimingJob job = requireKnown(handle);
        return job.claim();
    }

    public List<HardwareWorkHandle> pendingHandles() {
        return jobs.stream()
                .filter(job -> !job.isClaimed())
                .map(HardwareTimingJob::handle)
                .toList();
    }

    /**
     * Resolves an unclaimed handle by its session-stable identity.
     *
     * <p>Rewind owners use this after the timing ledger has been restored so
     * their transient queue facade can bind to the original job without
     * submitting replacement work.
     */
    public Optional<HardwareWorkHandle> pendingHandle(
            HardwareWorkKind kind,
            long ordinal) {
        Objects.requireNonNull(kind, "kind");
        if (ordinal < 0) {
            return Optional.empty();
        }
        return jobs.stream()
                .filter(job -> !job.isClaimed())
                .map(HardwareTimingJob::handle)
                .filter(handle -> handle.kind() == kind && handle.ordinal() == ordinal)
                .findFirst();
    }

    public int incompleteCount(HardwareWorkKind kind) {
        Objects.requireNonNull(kind, "kind");
        int count = 0;
        for (HardwareTimingJob job : jobs) {
            if (job.handle().kind() == kind
                    && !job.isClaimed()
                    && !job.isReady()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Switches only the final prepared-to-ready admission to recorded authority.
     *
     * <p>The returned capability cannot submit work, advance preparation, or
     * provide payload bytes.
     */
    public RecordedCompletionAuthority beginRecordedAdmission() {
        if (admissionPolicy != HardwareReadinessAdmissionPolicy.LIVE) {
            throw new IllegalStateException("recorded hardware admission is already active");
        }
        if (hasSubmitted) {
            throw new IllegalStateException(
                    "recorded hardware admission must begin before the first submission");
        }
        admissionPolicy = HardwareReadinessAdmissionPolicy.RECORDED;
        return recordedAuthority;
    }

    public HardwareReadinessAdmissionPolicy admissionPolicy() {
        return admissionPolicy;
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public HardwareTimingSnapshot capture() {
        EnumMap<HardwareWorkKind, Long> ordinalSnapshot =
                new EnumMap<>(HardwareWorkKind.class);
        ordinalSnapshot.putAll(nextOrdinals);
        List<HardwareTimingJob.Snapshot> jobSnapshots =
                jobs.stream().map(HardwareTimingJob::snapshot).toList();
        return new HardwareTimingSnapshot(
                ordinalSnapshot,
                jobSnapshots,
                admissionPolicy,
                hasSubmitted,
                lastServicedBoundary);
    }

    @Override
    public void restore(HardwareTimingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        nextOrdinals.clear();
        nextOrdinals.putAll(snapshot.nextOrdinals());
        jobs.clear();
        for (HardwareTimingJob.Snapshot jobSnapshot : snapshot.jobs()) {
            jobs.add(HardwareTimingJob.restore(jobSnapshot));
        }
        admissionPolicy = snapshot.admissionPolicy();
        hasSubmitted = snapshot.hasSubmitted();
        lastServicedBoundary = snapshot.lastServicedBoundary();
    }

    @Override
    public void resetForMissingSnapshot() {
        nextOrdinals.clear();
        jobs.clear();
        admissionPolicy = HardwareReadinessAdmissionPolicy.LIVE;
        hasSubmitted = false;
        lastServicedBoundary = null;
    }

    private void releasePreparedInFifoOrder() {
        for (HardwareTimingJob job : jobs) {
            if (job.isClaimed() || job.isReady()) {
                continue;
            }
            if (!job.hasPreparedPayload()) {
                return;
            }
            job.admitReadiness();
        }
    }

    private void serviceBoundaryDrivenHead(HardwareServiceBoundary boundary) {
        for (HardwareTimingJob job : jobs) {
            if (job.isClaimed() || job.hasPreparedPayload()) {
                continue;
            }
            HardwareWorkPreparation preparation = job.preparation();
            if (!preparation.isBoundaryDriven()) {
                return;
            }
            preparation.serviceBoundary(boundary);
            if (preparation.isPrepared()) {
                job.capturePreparedPayload();
            }
            return;
        }
    }

    private HardwareTimingJob firstAwaitingAdmission(HardwareWorkKind kind) {
        for (HardwareTimingJob job : jobs) {
            if (job.handle().kind() == kind
                    && !job.isClaimed()
                    && !job.isReady()) {
                return job;
            }
        }
        return null;
    }

    private HardwareTimingJob find(HardwareWorkHandle handle) {
        if (handle == null) {
            return null;
        }
        for (HardwareTimingJob job : jobs) {
            if (job.handle().equals(handle)) {
                return job;
            }
        }
        return null;
    }

    private HardwareTimingJob requireKnown(HardwareWorkHandle handle) {
        Objects.requireNonNull(handle, "handle");
        HardwareTimingJob job = find(handle);
        if (job == null) {
            throw new IllegalArgumentException(
                    "unknown hardware work handle: " + HardwareTimingJob.describe(handle));
        }
        return job;
    }

    private List<PendingRecordedSubmission> recordedPendingSubmissions() {
        return jobs.stream()
                .filter(job -> !job.isClaimed())
                .map(job -> new PendingRecordedSubmission(
                        job.handle(),
                        job.submission().exportableAcrossSegment()))
                .toList();
    }

    private void requireRecordedAdmission() {
        if (admissionPolicy != HardwareReadinessAdmissionPolicy.RECORDED) {
            throw new IllegalStateException("recorded hardware admission is not active");
        }
    }

    private String pendingDescription() {
        List<HardwareWorkHandle> pending = pendingHandles();
        if (pending.isEmpty()) {
            return "<none>";
        }
        return pending.stream()
                .map(HardwareTimingJob::describe)
                .toList()
                .toString();
    }

    private final class RecordedAuthority implements RecordedCompletionAuthority {
        @Override
        public void admitRecordedCompletion(
                HardwareServiceBoundary boundary,
                HardwareWorkKind kind,
                long ordinal,
                String submissionFingerprint) {
            requireRecordedAdmission();
            Objects.requireNonNull(boundary, "boundary");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(submissionFingerprint, "submissionFingerprint");
            if (lastServicedBoundary != boundary) {
                throw new IllegalStateException(
                        "recorded completion boundary mismatch: expected " + boundary
                                + ", production serviced " + lastServicedBoundary);
            }

            HardwareTimingJob engineHead = firstAwaitingAdmission(kind);
            String expected = kind + "#" + ordinal + " " + submissionFingerprint;
            if (engineHead == null) {
                throw new IllegalStateException(
                        "expected completion: " + expected
                                + "; engine pending: " + pendingDescription());
            }
            if (engineHead.handle().ordinal() != ordinal
                    || !engineHead.handle().submissionFingerprint()
                    .equals(submissionFingerprint)) {
                throw new IllegalStateException(
                        "expected completion: " + expected
                                + "; engine pending: "
                                + HardwareTimingJob.describe(engineHead.handle()));
            }
            if (!engineHead.hasPreparedPayload()
                    || !engineHead.preparation().isPrepared()) {
                throw new IllegalStateException(
                        "expected completion: " + expected
                                + "; engine job is not prepared");
            }
            engineHead.admitReadiness();
        }

        @Override
        public List<PendingRecordedSubmission> pendingSubmissions() {
            requireRecordedAdmission();
            return recordedPendingSubmissions();
        }

        @Override
        public void endRecordedAdmission() {
            requireRecordedAdmission();
            List<PendingRecordedSubmission> pending = recordedPendingSubmissions();
            if (!pending.isEmpty()) {
                throw new IllegalStateException(
                        "unexpected pending hardware submissions at final run: " + pending);
            }
            admissionPolicy = HardwareReadinessAdmissionPolicy.LIVE;
            lastServicedBoundary = null;
        }
    }
}

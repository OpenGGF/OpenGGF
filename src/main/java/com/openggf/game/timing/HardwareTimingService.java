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
    private final LoadTimeProfile loadTimeProfile;
    private final RecordedAuthority recordedAuthority = new RecordedAuthority();
    private final EnumMap<HardwareWorkKind, Long> nextOrdinals =
            new EnumMap<>(HardwareWorkKind.class);
    private final List<HardwareTimingJob> jobs = new ArrayList<>();

    private final EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy>
            admissionPolicies = liveAdmissionPolicies();
    private boolean recordedAdmissionActive;
    private boolean hasSubmitted;
    private HardwareServiceBoundary lastServicedBoundary;

    public HardwareTimingService() {
        this(RomWorkBudgetScheduler.oneWorkUnitAt(
                HardwareServiceBoundary.POST_OBJECTS));
    }

    public HardwareTimingService(RomWorkBudgetScheduler scheduler) {
        this(scheduler, LoadTimeProfile.IMMEDIATE);
    }

    public HardwareTimingService(
            RomWorkBudgetScheduler scheduler,
            LoadTimeProfile loadTimeProfile) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.loadTimeProfile = Objects.requireNonNull(loadTimeProfile, "loadTimeProfile");
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
        for (HardwareWorkKind kind : HardwareWorkKind.values()) {
            if (admissionPolicyFor(kind) == HardwareReadinessAdmissionPolicy.LIVE) {
                activateAndAdvanceHead(boundary, kind);
            }
            serviceBoundaryDrivenHead(boundary, kind);
            scheduler.service(boundary, jobsOfKind(kind));
            if (admissionPolicyFor(kind) == HardwareReadinessAdmissionPolicy.LIVE) {
                releasePreparedInFifoOrder(kind);
            }
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

    /**
     * Reads the preserved result of already-claimed work during rewind-owner
     * reconstruction. This does not change the job lifecycle.
     */
    public byte[] claimedPayload(HardwareWorkKind kind, long ordinal) {
        Objects.requireNonNull(kind, "kind");
        HardwareTimingJob job = jobs.stream()
                .filter(candidate -> candidate.handle().kind() == kind
                        && candidate.handle().ordinal() == ordinal)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown hardware work identity: " + kind + "#" + ordinal));
        return job.claimedPayload();
    }

    /**
     * Captures payload prepared by a production coordinator at the boundary
     * just serviced. Recorded authority still controls final readiness.
     */
    public void captureCoordinatorPreparation(
            HardwareWorkHandle handle,
            HardwareServiceBoundary boundary) {
        Objects.requireNonNull(boundary, "boundary");
        if (lastServicedBoundary != boundary) {
            throw new IllegalStateException(
                    "coordinator boundary mismatch: expected " + boundary
                            + ", production serviced " + lastServicedBoundary);
        }
        HardwareTimingJob job = requireKnown(handle);
        job.capturePreparedPayload();
        if (admissionPolicyFor(handle.kind())
                == HardwareReadinessAdmissionPolicy.LIVE) {
            releasePreparedInFifoOrder(handle.kind());
        }
    }

    /** Returns a pending preparation to its production-owned coordinator. */
    public HardwareWorkPreparation coordinatorPreparation(
            HardwareWorkHandle handle) {
        HardwareTimingJob job = requireKnown(handle);
        if (job.isClaimed()) {
            throw new IllegalStateException(
                    "hardware work was already claimed: "
                            + HardwareTimingJob.describe(handle));
        }
        return job.preparation();
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
        return beginRecordedAdmission(schemaOneAdmissionPolicies());
    }

    /** Begins recorded readiness with one complete policy for every known work kind. */
    public RecordedCompletionAuthority beginRecordedAdmission(
            Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies) {
        if (recordedAdmissionActive) {
            throw new IllegalStateException("recorded hardware admission is already active");
        }
        if (hasSubmitted) {
            throw new IllegalStateException(
                    "recorded hardware admission must begin before the first submission");
        }
        installAdmissionPolicies(policies);
        recordedAdmissionActive = true;
        return recordedAuthority;
    }

    public HardwareReadinessAdmissionPolicy admissionPolicy() {
        return recordedAdmissionActive
                ? HardwareReadinessAdmissionPolicy.RECORDED
                : HardwareReadinessAdmissionPolicy.LIVE;
    }

    public HardwareReadinessAdmissionPolicy admissionPolicyFor(HardwareWorkKind kind) {
        return admissionPolicies.get(Objects.requireNonNull(kind, "kind"));
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
                admissionPolicies,
                recordedAdmissionActive,
                hasSubmitted,
                lastServicedBoundary);
    }

    @Override
    public void restore(HardwareTimingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        HardwareTimingSnapshot.validateAdmissionPolicies(
                snapshot.admissionPolicies(), snapshot.recordedAdmissionActive());
        nextOrdinals.clear();
        nextOrdinals.putAll(snapshot.nextOrdinals());
        jobs.clear();
        for (HardwareTimingJob.Snapshot jobSnapshot : snapshot.jobs()) {
            jobs.add(HardwareTimingJob.restore(jobSnapshot));
        }
        admissionPolicies.clear();
        admissionPolicies.putAll(snapshot.admissionPolicies());
        recordedAdmissionActive = snapshot.recordedAdmissionActive();
        hasSubmitted = snapshot.hasSubmitted();
        lastServicedBoundary = snapshot.lastServicedBoundary();
    }

    @Override
    public void resetForMissingSnapshot() {
        nextOrdinals.clear();
        jobs.clear();
        admissionPolicies.clear();
        admissionPolicies.putAll(liveAdmissionPolicies());
        recordedAdmissionActive = false;
        hasSubmitted = false;
        lastServicedBoundary = null;
    }

    private void releasePreparedInFifoOrder(HardwareWorkKind kind) {
        for (HardwareTimingJob job : jobs) {
            if (job.handle().kind() != kind) {
                continue;
            }
            if (job.isClaimed() || job.isReady()) {
                continue;
            }
            if (!job.hasPreparedPayload()) {
                return;
            }
            if (!job.isProfileActive()) {
                job.activateProfile(loadTimeProfile.assign(
                        job.submission(), job.handle()));
            }
            if (!job.isProfileComplete()) {
                return;
            }
            job.admitReadiness();
        }
    }

    private void activateAndAdvanceHead(
            HardwareServiceBoundary boundary, HardwareWorkKind kind) {
        for (HardwareTimingJob job : jobs) {
            if (job.handle().kind() != kind || job.isPhysicallyRetired()) {
                continue;
            }
            if (!job.isProfileActive()) {
                job.activateProfile(loadTimeProfile.assign(
                        job.submission(), job.handle()));
            }
            job.advanceProfile(boundary);
            return;
        }
    }

    private void serviceBoundaryDrivenHead(
            HardwareServiceBoundary boundary, HardwareWorkKind kind) {
        for (HardwareTimingJob job : jobs) {
            if (job.handle().kind() != kind) {
                continue;
            }
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
                .filter(job -> !job.isClaimed()
                        && admissionPolicyFor(job.handle().kind())
                        == HardwareReadinessAdmissionPolicy.RECORDED)
                .map(job -> new PendingRecordedSubmission(
                        job.handle(),
                        job.submission().exportableAcrossSegment()))
                .toList();
    }

    private void requireRecordedAdmission() {
        if (!recordedAdmissionActive) {
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
        public void configureAdmissionPolicies(
                Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies) {
            requireRecordedAdmission();
            if (hasSubmitted && !admissionPolicies.equals(policies)) {
                throw new IllegalStateException(
                        "recorded hardware admission policy must be configured before the first submission");
            }
            if (hasSubmitted) {
                return;
            }
            installAdmissionPolicies(policies);
        }

        @Override
        public void initializeOrdinalBases(
                Map<HardwareWorkKind, Long> firstOrdinals) {
            requireRecordedAdmission();
            Objects.requireNonNull(firstOrdinals, "firstOrdinals");
            for (Map.Entry<HardwareWorkKind, Long> entry
                    : firstOrdinals.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "hardware work kind");
                Long ordinal = Objects.requireNonNull(
                        entry.getValue(), "first hardware work ordinal");
                if (ordinal < 0) {
                    throw new IllegalArgumentException(
                            "hardware work ordinal base must be non-negative: "
                                    + entry.getKey() + "#" + ordinal);
                }
            }
            for (Map.Entry<HardwareWorkKind, Long> entry
                    : firstOrdinals.entrySet()) {
                HardwareWorkKind kind = entry.getKey();
                if (!nextOrdinals.containsKey(kind)) {
                    nextOrdinals.put(kind, entry.getValue());
                }
            }
        }

        @Override
        public void admitRecordedCompletion(
                HardwareServiceBoundary boundary,
                HardwareWorkKind kind,
                long ordinal,
                String submissionFingerprint) {
            admitRecordedCompletion(
                    boundary, kind, ordinal, submissionFingerprint, true);
        }

        @Override
        public void admitRecordedSuppressedRowCompletion(
                HardwareServiceBoundary boundary,
                HardwareWorkKind kind,
                long ordinal,
                String submissionFingerprint) {
            if (boundary != HardwareServiceBoundary.PRE_MAIN_LOOP) {
                throw new IllegalArgumentException(
                        "suppressed-row completion requires PRE_MAIN_LOOP: "
                                + boundary);
            }
            admitRecordedCompletion(
                    boundary, kind, ordinal, submissionFingerprint, false);
        }

        private void admitRecordedCompletion(
                HardwareServiceBoundary boundary,
                HardwareWorkKind kind,
                long ordinal,
                String submissionFingerprint,
                boolean requireServicedBoundary) {
            requireRecordedAdmission();
            Objects.requireNonNull(boundary, "boundary");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(submissionFingerprint, "submissionFingerprint");
            if (admissionPolicyFor(kind) != HardwareReadinessAdmissionPolicy.RECORDED) {
                throw new IllegalStateException(
                        "recorded completion kind is not recorded by this stream: " + kind);
            }
            if (requireServicedBoundary && lastServicedBoundary != boundary) {
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
            admissionPolicies.clear();
            admissionPolicies.putAll(liveAdmissionPolicies());
            recordedAdmissionActive = false;
            lastServicedBoundary = null;
        }
    }

    private List<HardwareTimingJob> jobsOfKind(HardwareWorkKind kind) {
        return jobs.stream().filter(job -> job.handle().kind() == kind).toList();
    }

    private void installAdmissionPolicies(
            Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies) {
        Objects.requireNonNull(policies, "admissionPolicies");
        EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy> checked =
                new EnumMap<>(HardwareWorkKind.class);
        for (HardwareWorkKind kind : HardwareWorkKind.values()) {
            HardwareReadinessAdmissionPolicy policy = policies.get(kind);
            if (policy == null) {
                throw new IllegalArgumentException(
                        "recorded admission policy is missing kind " + kind);
            }
            checked.put(kind, policy);
        }
        if (checked.values().stream().noneMatch(
                policy -> policy == HardwareReadinessAdmissionPolicy.RECORDED)) {
            throw new IllegalArgumentException(
                    "recorded admission policy cannot leave every kind live");
        }
        admissionPolicies.clear();
        admissionPolicies.putAll(checked);
    }

    private static EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy>
            liveAdmissionPolicies() {
        EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies =
                new EnumMap<>(HardwareWorkKind.class);
        for (HardwareWorkKind kind : HardwareWorkKind.values()) {
            policies.put(kind, HardwareReadinessAdmissionPolicy.LIVE);
        }
        return policies;
    }

    private static Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy>
            schemaOneAdmissionPolicies() {
        EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies =
                liveAdmissionPolicies();
        policies.put(HardwareWorkKind.KOS_MODULE_QUEUE,
                HardwareReadinessAdmissionPolicy.RECORDED);
        return Map.copyOf(policies);
    }
}

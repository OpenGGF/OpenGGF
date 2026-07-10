package com.openggf.net.master;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/** Broker-loop verification queue with upload and worker-lease ownership. */
public final class VerificationJobQueue {
    public enum State { AWAITING_UPLOAD, QUEUED, LEASED, DONE, VOID }

    public record Job(String jobId, String roomId, int slot,
                      String identityFingerprint, String attemptRef,
                      String determinismFingerprint, String trackKey,
                      String character, int claimedTimeFrames,
                      int firstInputFrame, int finishFrame,
                      String inputRecordingHashHex, String ghostStreamHashHex,
                      boolean spotCheck, long createdAtMillis) {
    }

    private static final class Entry {
        private final Job job;
        private final long uploadDeadlineAtMillis;
        private State state = State.AWAITING_UPLOAD;
        private String leasedWorkerId;
        private long leaseExpiresAtMillis;

        private Entry(Job job, long uploadDeadlineAtMillis) {
            this.job = job;
            this.uploadDeadlineAtMillis = uploadDeadlineAtMillis;
        }
    }

    private final LongSupplier clock;
    private final long leaseMillis;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long counter;

    public VerificationJobQueue(LongSupplier clock, long leaseMillis) {
        this.clock = clock;
        this.leaseMillis = leaseMillis;
    }

    public String submit(Job candidate, long uploadDeadlineAtMillis) {
        String id = "vj-" + (++counter);
        Job job = new Job(id, candidate.roomId(), candidate.slot(),
                candidate.identityFingerprint(), candidate.attemptRef(),
                candidate.determinismFingerprint(), candidate.trackKey(),
                candidate.character(), candidate.claimedTimeFrames(),
                candidate.firstInputFrame(), candidate.finishFrame(),
                candidate.inputRecordingHashHex(), candidate.ghostStreamHashHex(),
                candidate.spotCheck(), clock.getAsLong());
        entries.put(id, new Entry(job, uploadDeadlineAtMillis));
        return id;
    }

    public void onRecordingUploaded(String recordingHashHex) {
        entries.values().stream()
                .filter(entry -> entry.state == State.AWAITING_UPLOAD)
                .filter(entry -> entry.job.inputRecordingHashHex()
                        .equalsIgnoreCase(recordingHashHex))
                .forEach(entry -> entry.state = State.QUEUED);
    }

    public Optional<Job> lease(String workerId, Set<String> supportedFingerprints) {
        for (Entry entry : entries.values()) {
            if (entry.state == State.QUEUED
                    && supportedFingerprints.contains(
                    entry.job.determinismFingerprint())) {
                entry.state = State.LEASED;
                entry.leasedWorkerId = workerId;
                entry.leaseExpiresAtMillis = clock.getAsLong() + leaseMillis;
                return Optional.of(entry.job);
            }
        }
        return Optional.empty();
    }

    public Optional<Job> complete(String jobId, String workerId) {
        Entry entry = entries.get(jobId);
        if (entry == null || entry.state != State.LEASED
                || !java.util.Objects.equals(entry.leasedWorkerId, workerId)) {
            return Optional.empty();
        }
        entry.state = State.DONE;
        entry.leasedWorkerId = null;
        return Optional.of(entry.job);
    }

    public List<Job> voidExpiredUploads() {
        long now = clock.getAsLong();
        java.util.ArrayList<Job> expired = new java.util.ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.state == State.AWAITING_UPLOAD
                    && now >= entry.uploadDeadlineAtMillis) {
                entry.state = State.VOID;
                expired.add(entry.job);
            }
        }
        return List.copyOf(expired);
    }

    public int requeueExpiredLeases() {
        long now = clock.getAsLong();
        int requeued = 0;
        for (Entry entry : entries.values()) {
            if (entry.state == State.LEASED && now >= entry.leaseExpiresAtMillis) {
                entry.state = State.QUEUED;
                entry.leasedWorkerId = null;
                requeued++;
            }
        }
        return requeued;
    }

    public State stateOf(String jobId) {
        Entry entry = entries.get(jobId);
        return entry == null ? null : entry.state;
    }
}

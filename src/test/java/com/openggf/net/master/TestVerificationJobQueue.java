package com.openggf.net.master;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVerificationJobQueue {
    private static VerificationJobQueue.Job job(String fingerprint, String hash) {
        return new VerificationJobQueue.Job(null, "r-1", 2, "player",
                "r-1#2#3", fingerprint, "s3k:0:0", "sonic",
                100, 10, 110, hash, "bb", false, 0);
    }

    @Test
    void submitAwaitsUploadThenQueuesOnMatchingHash() {
        VerificationJobQueue queue = new VerificationJobQueue(() -> 0, 100);
        String id = queue.submit(job("0.6:cafe", "aa"), 1000);
        assertEquals(VerificationJobQueue.State.AWAITING_UPLOAD, queue.stateOf(id));
        queue.onRecordingUploaded("aa");
        assertEquals(VerificationJobQueue.State.QUEUED, queue.stateOf(id));
    }

    @Test
    void leaseOnlyMatchingFingerprintAndOnlyQueued() {
        VerificationJobQueue queue = new VerificationJobQueue(() -> 0, 100);
        queue.submit(job("0.6:cafe", "aa"), 1000);
        queue.onRecordingUploaded("aa");
        assertTrue(queue.lease("wrong", Set.of("0.7:beef")).isEmpty());
        assertTrue(queue.lease("right", Set.of("0.6:cafe")).isPresent());
        assertTrue(queue.lease("other", Set.of("0.6:cafe")).isEmpty());
    }

    @Test
    void perJobUploadDeadlinesVoidIndependentlyAndOnlyOnce() {
        long[] now = {0};
        VerificationJobQueue queue = new VerificationJobQueue(() -> now[0], 100);
        String early = queue.submit(job("fp", "aa"), 10);
        String late = queue.submit(job("fp", "bb"), 20);
        now[0] = 11;
        assertEquals(1, queue.voidExpiredUploads().size());
        assertEquals(VerificationJobQueue.State.VOID, queue.stateOf(early));
        assertEquals(VerificationJobQueue.State.AWAITING_UPLOAD, queue.stateOf(late));
        assertTrue(queue.voidExpiredUploads().isEmpty());
    }

    @Test
    void expiredLeaseRequeuesAndCanBeCompletedOnlyByNewLeaseholder() {
        long[] now = {0};
        VerificationJobQueue queue = new VerificationJobQueue(() -> now[0], 10);
        String id = queue.submit(job("fp", "aa"), 100);
        queue.onRecordingUploaded("aa");
        queue.lease("w1", Set.of("fp")).orElseThrow();
        now[0] = 11;
        assertEquals(1, queue.requeueExpiredLeases());
        queue.lease("w2", Set.of("fp")).orElseThrow();
        assertTrue(queue.complete(id, "w1").isEmpty());
        assertTrue(queue.complete(id, "w2").isPresent());
    }

    @Test
    void completeRequiresLeaseholderAndIsIdempotent() {
        VerificationJobQueue queue = new VerificationJobQueue(() -> 0, 100);
        String id = queue.submit(job("fp", "aa"), 1000);
        queue.onRecordingUploaded("aa");
        assertTrue(queue.complete(id, "w1").isEmpty());
        queue.lease("w1", Set.of("fp")).orElseThrow();
        assertTrue(queue.complete(id, "w2").isEmpty());
        assertEquals(VerificationJobQueue.State.LEASED, queue.stateOf(id));
        assertTrue(queue.complete(id, "w1").isPresent());
        assertTrue(queue.complete(id, "w1").isEmpty());
    }
}

package openggf.racing.server.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import openggf.timeattack.AttemptInputRecording;
import openggf.racing.server.verifier.AttemptReplayHarness;
import openggf.timeattack.AttemptStartDescriptor;
import com.openggf.net.identity.PlayerIdentity;
import openggf.racing.server.master.VerificationJobQueue;
import openggf.racing.server.master.VerifierRegistry;
import com.openggf.net.protocol.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVerifierWorker {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path dir;

    @Test
    void noJobReturnsFalse() throws Exception {
        FakeApi api = new FakeApi(null, new byte[0]);
        VerifierWorker worker = new VerifierWorker(api,
                new FakeReplayer(passReplay()), identity("none"));
        assertFalse(worker.pollOnce());
    }

    @Test
    void tamperedBlobFailsDivergentAndSigns() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        VerificationJobQueue.Job job = job(recording, "0".repeat(64),
                "s3k:0:0", "sonic", "fp", "gg");
        assertDecision(job, recording.encode(), passReplay(),
                VerdictCodec.RESULT_FAIL_DIVERGENT, false);
    }

    @Test
    void trackCharacterAndFingerprintMismatchSkipReplay() throws Exception {
        AttemptInputRecording recording = recording("s3k", 1, 0, "tails", "fp-a");
        String hash = HexFormat.of().formatHex(recording.sha256());
        assertDecision(job(recording, hash, "s3k:0:0", "tails", "fp-a", "gg"),
                recording.encode(), passReplay(), VerdictCodec.RESULT_FAIL_TRACK_MISMATCH, false);
        assertDecision(job(recording, hash, "s3k:1:0", "sonic", "fp-a", "gg"),
                recording.encode(), passReplay(), VerdictCodec.RESULT_FAIL_TRACK_MISMATCH, false);
        assertDecision(job(recording, hash, "s3k:1:0", "tails", "fp-b", "gg"),
                recording.encode(), passReplay(), VerdictCodec.RESULT_FAIL_TRACK_MISMATCH, false);
    }

    @Test
    void unfinishedReplayFailsDivergent() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        assertDecision(matchingJob(recording, "gg"), recording.encode(),
                new AttemptReplayHarness.Result(false, 1, -1, -1,
                        "gg", 10, null), VerdictCodec.RESULT_FAIL_DIVERGENT, true);
    }

    @Test
    void timingMismatchHasSpecificResult() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        assertDecision(matchingJob(recording, "gg"), recording.encode(),
                new AttemptReplayHarness.Result(true, 2, 101, 99,
                        "gg", 10, null), VerdictCodec.RESULT_FAIL_TIME_MISMATCH, true);
    }

    @Test
    void ghostMismatchHasSpecificResult() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        assertDecision(matchingJob(recording, "claimed"), recording.encode(),
                new AttemptReplayHarness.Result(true, 1, 101, 100,
                        "actual", 10, null), VerdictCodec.RESULT_FAIL_GHOST_HASH, true);
    }

    @Test
    void matchingReplayPassesWithVerifiableSignature() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        assertDecision(matchingJob(recording, "gg"), recording.encode(),
                passReplay(), VerdictCodec.RESULT_PASS, true);
    }

    @Test
    void heartbeatsKeepLongReplayAuthorizedWithinItsOriginalLease() throws Exception {
        AtomicLong now = new AtomicLong();
        PlayerIdentity identity = identity("long-replay");
        VerifierRegistry registry = new VerifierRegistry(now::get, 120_000);
        var registered = registry.register(identity.publicKeyEncoded(), Set.of("fp"));
        VerificationJobQueue queue = new VerificationJobQueue(now::get, 300_000);
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        var candidate = matchingJob(recording, "gg");
        String jobId = queue.submit(candidate, 10_000);
        queue.onRecordingUploaded(candidate.inputRecordingHashHex(), "player");
        var leased = queue.lease(registered.workerId(), Set.of("fp")).orElseThrow();
        Semaphore observedHeartbeat = new Semaphore(0);
        FakeApi api = new FakeApi(JSON.writeValueAsString(leased), recording.encode()) {
            @Override public void heartbeat() {
                assertTrue(registry.authenticate(registered.workerId(),
                        registered.workerToken()).isPresent());
                observedHeartbeat.release();
            }

            @Override public boolean postVerdict(String id, String result, byte[] signature) {
                assertTrue(registry.authenticate(registered.workerId(),
                        registered.workerToken()).isPresent(), "worker must remain authorized");
                assertTrue(queue.complete(id, registered.workerId()).isPresent(),
                        "heartbeat must preserve the original lease owner");
                return super.postVerdict(id, result, signature);
            }
        };
        VerifierWorker worker = new VerifierWorker(api, (inputs, fingerprint) -> {
            // Four virtual minutes exceed worker staleness but stay inside the 5-minute lease.
            for (int step = 0; step < 4; step++) {
                observedHeartbeat.drainPermits();
                now.addAndGet(60_000);
                try {
                    assertTrue(observedHeartbeat.tryAcquire(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return passReplay();
        }, identity, Duration.ofMillis(1));

        assertTrue(worker.pollOnce());
        assertEquals(VerdictCodec.RESULT_PASS, api.result);
        assertEquals(VerificationJobQueue.State.DONE, queue.stateOf(jobId));
    }

    @Test
    void endingCycleInterruptsAnInFlightHeartbeat() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        FakeApi api = new FakeApi(JSON.writeValueAsString(matchingJob(recording, "gg")),
                recording.encode()) {
            @Override public void heartbeat() {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException expected) {
                    Thread.currentThread().interrupt();
                    interrupted.countDown();
                }
            }
        };
        VerifierWorker worker = new VerifierWorker(api, (inputs, fingerprint) -> {
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                throw new IllegalStateException(failure);
            }
            return passReplay();
        }, identity("heartbeat-cleanup"), Duration.ofMillis(1));
        assertTrue(worker.pollOnce());
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }

    @Test
    void refusedVerdictStillCountsAsProcessedCycle() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        FakeApi api = new FakeApi(JSON.writeValueAsString(matchingJob(recording, "gg")),
                recording.encode());
        api.verdictAccepted = false;
        VerifierWorker worker = new VerifierWorker(api,
                new FakeReplayer(passReplay()), identity("refused-verdict"));
        List<Long> sleeps = new ArrayList<>();
        try {
            // The voided/re-leased job is consumed: the loop polls again at once
            // instead of backing off, then idles once the queue is empty.
            new VerifierLoop(worker::pollOnce, millis -> {
                sleeps.add(millis);
                Thread.currentThread().interrupt();
            }).run();
        } finally {
            Thread.interrupted();
        }
        assertEquals(VerdictCodec.RESULT_PASS, api.result);
        assertEquals(2, api.polls);
        assertEquals(List.of(VerifierLoop.IDLE_POLL_MILLIS), sleeps);
    }

    @Test
    void jobScopedErrorInReplayFailsOnlyThatCycleWithoutAVerdict() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        for (Error fault : List.of(new StackOverflowError("deep replay recursion"),
                new NoClassDefFoundError("openggf/LazilyLinkedZone"),
                new ExceptionInInitializerError("zone tables failed to initialise"))) {
            FakeApi api = new FakeApi(JSON.writeValueAsString(matchingJob(recording, "gg")),
                    recording.encode());
            VerifierWorker worker = new VerifierWorker(api, (inputs, fingerprint) -> {
                throw fault;
            }, identity("fault-" + fault.getClass().getSimpleName()));

            IllegalStateException failed = assertThrows(IllegalStateException.class,
                    worker::pollOnce, fault.getClass().getSimpleName());
            assertSame(fault, failed.getCause());
            assertTrue(api.verdictJobIds.isEmpty(),
                    "a verifier-side fault is no evidence against the recording");
        }
    }

    @Test
    void jvmFaultInReplayStaysFatal() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        FakeApi api = new FakeApi(JSON.writeValueAsString(matchingJob(recording, "gg")),
                recording.encode());
        OutOfMemoryError exhausted = new OutOfMemoryError("Java heap space");
        VerifierWorker worker = new VerifierWorker(api, (inputs, fingerprint) -> {
            throw exhausted;
        }, identity("fatal-fault"));

        assertSame(exhausted, assertThrows(OutOfMemoryError.class, worker::pollOnce));
        assertTrue(api.verdictJobIds.isEmpty());
    }

    @Test
    void loopSurvivesAStackOverflowingReplayAndVerifiesTheNextJob() throws Exception {
        AttemptInputRecording recording = recording("s3k", 0, 0, "sonic", "fp");
        VerificationJobQueue.Job poisoned = matchingJob(recording, "gg");
        VerificationJobQueue.Job healthy = withJobId(poisoned, "vj-2");
        FakeApi api = new FakeApi(JSON.writeValueAsString(poisoned), recording.encode());
        api.jobs.add(JSON.writeValueAsString(healthy));
        int[] replays = {0};
        VerifierWorker worker = new VerifierWorker(api, (inputs, fingerprint) -> {
            if (replays[0]++ == 0) {
                throw new StackOverflowError("deep replay recursion");
            }
            return passReplay();
        }, identity("survives-overflow"));
        Logger loopLog = Logger.getLogger(VerifierLoop.class.getName());
        boolean parentHandlers = loopLog.getUseParentHandlers();
        // The expected failed cycle would otherwise print its stack trace into the test log.
        loopLog.setUseParentHandlers(false);
        List<Long> sleeps = new ArrayList<>();
        try {
            new VerifierLoop(worker::pollOnce, millis -> {
                sleeps.add(millis);
                if (sleeps.size() == 2) {
                    Thread.currentThread().interrupt();
                }
            }).run();
        } finally {
            Thread.interrupted();
            loopLog.setUseParentHandlers(parentHandlers);
        }
        // Back-off after the poisoned job, none after the verified one, then idle.
        assertEquals(List.of(VerifierLoop.INITIAL_BACKOFF_MILLIS,
                VerifierLoop.IDLE_POLL_MILLIS), sleeps);
        assertEquals(3, api.polls);
        assertEquals(List.of("vj-2"), api.verdictJobIds);
        assertEquals(VerdictCodec.RESULT_PASS, api.result);
    }

    private void assertDecision(VerificationJobQueue.Job job, byte[] bytes,
                                AttemptReplayHarness.Result replay,
                                String expected, boolean expectedReplay) throws Exception {
        FakeApi api = new FakeApi(JSON.writeValueAsString(job), bytes);
        FakeReplayer replayer = new FakeReplayer(replay);
        PlayerIdentity identity = identity("worker-" + Math.abs(job.hashCode())
                + "-" + expected + "-" + System.nanoTime());
        VerifierWorker worker = new VerifierWorker(api, replayer, identity);
        assertTrue(worker.pollOnce());
        assertEquals(expected, api.result);
        assertEquals(expectedReplay, replayer.called);
        assertTrue(PlayerIdentity.verify(identity.publicKeyEncoded(),
                VerdictCodec.canonicalBytes(job.jobId(), job.attemptRef(),
                        job.inputRecordingHashHex(), expected), api.signature));
    }

    private PlayerIdentity identity(String name) throws Exception {
        return PlayerIdentity.loadOrCreate(dir.resolve(name));
    }

    private static AttemptInputRecording recording(String game, int zone, int act,
                                                    String character, String fingerprint) {
        AttemptInputRecording recording = new AttemptInputRecording(
                new AttemptStartDescriptor(game, zone, act, character, fingerprint));
        recording.appendFrame(0, false);
        recording.appendFrame(8, false);
        return recording;
    }

    private static VerificationJobQueue.Job matchingJob(
            AttemptInputRecording recording, String ghostHash) {
        return job(recording, HexFormat.of().formatHex(recording.sha256()),
                "s3k:0:0", "sonic", "fp", ghostHash);
    }

    private static VerificationJobQueue.Job job(AttemptInputRecording recording,
                                                 String hash, String track,
                                                 String character, String fingerprint,
                                                 String ghostHash) {
        return new VerificationJobQueue.Job("vj-1", "r", 1, "player",
                "r#1#1", fingerprint, track, character,
                100, 1, 101, hash, ghostHash, false, 0);
    }

    private static VerificationJobQueue.Job withJobId(VerificationJobQueue.Job job,
                                                       String jobId) {
        return new VerificationJobQueue.Job(jobId, job.roomId(), job.slot(),
                job.identityFingerprint(), job.attemptRef(), job.determinismFingerprint(),
                job.trackKey(), job.character(), job.claimedTimeFrames(),
                job.firstInputFrame(), job.finishFrame(), job.inputRecordingHashHex(),
                job.ghostStreamHashHex(), job.spotCheck(), job.createdAtMillis());
    }

    private static AttemptReplayHarness.Result passReplay() {
        return new AttemptReplayHarness.Result(true, 1, 101, 100,
                "gg", 102, null);
    }

    private static class FakeApi implements VerifierWorker.MasterApi {
        private final Deque<String> jobs = new ArrayDeque<>();
        private final byte[] recording;
        private final List<String> verdictJobIds = new ArrayList<>();
        private String result;
        private byte[] signature;
        private boolean verdictAccepted = true;
        private int polls;

        private FakeApi(String jobJson, byte[] recording) {
            if (jobJson != null) {
                jobs.add(jobJson);
            }
            this.recording = recording;
        }

        @Override public String register(byte[] key, Set<String> fingerprints) {
            return "token";
        }
        @Override public Optional<String> pollJobJson() {
            polls++;
            return Optional.ofNullable(jobs.poll());
        }
        @Override public void heartbeat() { }
        @Override public byte[] fetchRecording(String hashHex) { return recording; }
        @Override public boolean postVerdict(String jobId, String result, byte[] signature) {
            verdictJobIds.add(jobId);
            this.result = result;
            this.signature = signature;
            return verdictAccepted;
        }
    }

    private static final class FakeReplayer implements VerifierWorker.Replayer {
        private final AttemptReplayHarness.Result result;
        private boolean called;

        private FakeReplayer(AttemptReplayHarness.Result result) { this.result = result; }
        @Override public AttemptReplayHarness.Result replay(
                AttemptInputRecording recording, String fingerprint) {
            called = true;
            return result;
        }
    }
}

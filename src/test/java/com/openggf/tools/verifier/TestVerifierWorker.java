package com.openggf.tools.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.timeattack.AttemptInputRecording;
import com.openggf.game.timeattack.AttemptReplayHarness;
import com.openggf.game.timeattack.AttemptStartDescriptor;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.master.VerificationJobQueue;
import com.openggf.net.protocol.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static AttemptReplayHarness.Result passReplay() {
        return new AttemptReplayHarness.Result(true, 1, 101, 100,
                "gg", 102, null);
    }

    private static final class FakeApi implements VerifierWorker.MasterApi {
        private String jobJson;
        private final byte[] recording;
        private String result;
        private byte[] signature;

        private FakeApi(String jobJson, byte[] recording) {
            this.jobJson = jobJson;
            this.recording = recording;
        }

        @Override public String register(byte[] key, Set<String> fingerprints) {
            return "token";
        }
        @Override public Optional<String> pollJobJson() {
            String result = jobJson;
            jobJson = null;
            return Optional.ofNullable(result);
        }
        @Override public byte[] fetchRecording(String hashHex) { return recording; }
        @Override public void postVerdict(String jobId, String result, byte[] signature) {
            this.result = result;
            this.signature = signature;
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

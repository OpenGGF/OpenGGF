package com.openggf.tools.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.timeattack.AttemptInputRecording;
import com.openggf.game.timeattack.AttemptReplayHarness;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.master.VerificationJobQueue;
import com.openggf.net.protocol.VerdictCodec;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/** One-job-at-a-time verifier decision engine with injectable transport and replay. */
public final class VerifierWorker {
    private static final ObjectMapper JSON = new ObjectMapper();

    public interface MasterApi {
        String register(byte[] publicKeyEncoded, Set<String> fingerprints);
        Optional<String> pollJobJson();
        byte[] fetchRecording(String hashHex);
        void postVerdict(String jobId, String result, byte[] signature);
    }

    public interface Replayer {
        AttemptReplayHarness.Result replay(AttemptInputRecording recording,
                                           String determinismFingerprint);
    }

    private final MasterApi api;
    private final Replayer replayer;
    private final PlayerIdentity identity;

    public VerifierWorker(MasterApi api, Replayer replayer, PlayerIdentity identity) {
        this.api = api;
        this.replayer = replayer;
        this.identity = identity;
    }

    public boolean pollOnce() {
        Optional<String> body = api.pollJobJson();
        if (body.isEmpty()) {
            return false;
        }
        try {
            VerificationJobQueue.Job job = JSON.readValue(
                    body.orElseThrow(), VerificationJobQueue.Job.class);
            byte[] encoded = api.fetchRecording(job.inputRecordingHashHex());
            String result = decide(job, encoded);
            byte[] signature = identity.sign(VerdictCodec.canonicalBytes(
                    job.jobId(), job.attemptRef(), job.inputRecordingHashHex(), result));
            api.postVerdict(job.jobId(), result, signature);
            return true;
        } catch (Exception failure) {
            throw new IllegalStateException("verification cycle failed", failure);
        }
    }

    private String decide(VerificationJobQueue.Job job, byte[] encoded) {
        if (!sha256Hex(encoded).equalsIgnoreCase(job.inputRecordingHashHex())) {
            return VerdictCodec.RESULT_FAIL_DIVERGENT;
        }
        final AttemptInputRecording recording;
        try {
            recording = AttemptInputRecording.decode(encoded);
        } catch (RuntimeException invalid) {
            return VerdictCodec.RESULT_FAIL_DIVERGENT;
        }
        String recordedTrack = recording.start().gameId() + ":"
                + recording.start().zone() + ":" + recording.start().act();
        if (!recordedTrack.equals(job.trackKey())
                || !recording.start().character().equals(job.character())
                || !recording.start().fingerprint().equals(
                job.determinismFingerprint())) {
            return VerdictCodec.RESULT_FAIL_TRACK_MISMATCH;
        }
        AttemptReplayHarness.Result replay = replayer.replay(
                recording, job.determinismFingerprint());
        if (replay.failureReason() != null || !replay.finished()) {
            return VerdictCodec.RESULT_FAIL_DIVERGENT;
        }
        if (replay.finalTimeFrames() != job.claimedTimeFrames()
                || replay.firstInputFrame() != job.firstInputFrame()
                || replay.finishFrame() != job.finishFrame()) {
            return VerdictCodec.RESULT_FAIL_TIME_MISMATCH;
        }
        if (!replay.ghostStreamHashHex().equalsIgnoreCase(
                job.ghostStreamHashHex())) {
            return VerdictCodec.RESULT_FAIL_GHOST_HASH;
        }
        return VerdictCodec.RESULT_PASS;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

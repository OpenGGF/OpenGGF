package openggf.racing.server.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import openggf.timeattack.AttemptInputRecording;
import openggf.racing.server.verifier.AttemptReplayHarness;
import com.openggf.net.identity.PlayerIdentity;
import openggf.racing.server.master.VerificationJobQueue;
import com.openggf.net.protocol.VerdictCodec;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One-job-at-a-time verifier decision engine with injectable transport and replay. */
public final class VerifierWorker {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(VerifierWorker.class.getName());
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    public interface MasterApi {
        String register(byte[] publicKeyEncoded, Set<String> fingerprints);
        Optional<String> pollJobJson();
        void heartbeat();
        byte[] fetchRecording(String hashHex);

        /**
         * Submits a signed verdict. Returns {@code false} when the master refuses it
         * because the job was voided, superseded or re-leased; other failures throw.
         */
        boolean postVerdict(String jobId, String result, byte[] signature);
    }

    public interface Replayer {
        AttemptReplayHarness.Result replay(AttemptInputRecording recording,
                                           String determinismFingerprint);
    }

    private final MasterApi api;
    private final Replayer replayer;
    private final PlayerIdentity identity;
    private final long heartbeatIntervalMillis;

    public VerifierWorker(MasterApi api, Replayer replayer, PlayerIdentity identity) {
        this(api, replayer, identity, HEARTBEAT_INTERVAL);
    }

    VerifierWorker(MasterApi api, Replayer replayer, PlayerIdentity identity,
                   Duration heartbeatInterval) {
        this.api = api;
        this.replayer = replayer;
        this.identity = identity;
        heartbeatIntervalMillis = heartbeatInterval.toMillis();
        if (heartbeatIntervalMillis < 1) {
            throw new IllegalArgumentException("heartbeat interval must be at least 1ms");
        }
    }

    public boolean pollOnce() {
        Optional<String> body = api.pollJobJson();
        if (body.isEmpty()) {
            return false;
        }
        // Replay is synchronous and can outlast the master's worker-liveness window.
        // Refresh only worker liveness: the existing fixed job lease remains authoritative.
        var heartbeats = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "verifier-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeats.scheduleWithFixedDelay(() -> {
            try {
                api.heartbeat();
            } catch (RuntimeException failure) {
                // A transient HTTP failure must not permanently cancel future heartbeats.
                LOG.log(System.Logger.Level.WARNING, "verifier heartbeat failed", failure);
            }
        }, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
        try {
            VerificationJobQueue.Job job = JSON.readValue(
                    body.orElseThrow(), VerificationJobQueue.Job.class);
            String result;
            try {
                result = decide(job, api.fetchRecording(job.inputRecordingHashHex()));
            } catch (RuntimeException unavailable) {
                // Fetch failure is a completed, non-sanctioning VOID decision; only a
                // worker failure before a decision lets the fixed lease lapse.
                LOG.log(System.Logger.Level.WARNING, "verification inputs unavailable", unavailable);
                result = VerdictCodec.RESULT_VOID_VERIFIER_UNAVAILABLE;
            }
            byte[] signature = identity.sign(VerdictCodec.canonicalBytes(
                    job.jobId(), job.attemptRef(), job.inputRecordingHashHex(), result));
            if (!api.postVerdict(job.jobId(), result, signature)) {
                // Nothing is left to retry: the master already moved the job on, so the
                // leased job still counts as processed.
                LOG.log(System.Logger.Level.INFO, "verdict " + result + " for job "
                        + job.jobId() + " refused: job voided, superseded or re-leased");
            }
            return true;
        } catch (Exception | StackOverflowError | LinkageError failure) {
            // A StackOverflowError (a recording driving deep replay recursion) or a
            // LinkageError (a lazily linked class failing to load or initialise) leaves
            // the JVM usable and poisons only this job, so it fails this cycle and the
            // loop survives it. No verdict is posted: a verifier-side fault is no evidence
            // against the recording, and the lapsed lease returns the job to the master.
            // Any other Error (OutOfMemoryError, InternalError, ...) stays fatal.
            throw new IllegalStateException("verification cycle failed", failure);
        } finally {
            heartbeats.shutdownNow();
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
        if (replay.failureReason() != null) {
            return VerdictCodec.RESULT_VOID_VERIFIER_UNAVAILABLE;
        }
        if (!replay.finished()) {
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

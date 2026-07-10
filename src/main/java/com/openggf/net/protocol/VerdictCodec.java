package com.openggf.net.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Canonical result vocabulary and signed payload encoding for replay verdicts. */
public final class VerdictCodec {
    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL_DIVERGENT = "FAIL_DIVERGENT";
    public static final String RESULT_FAIL_TIME_MISMATCH = "FAIL_TIME_MISMATCH";
    public static final String RESULT_FAIL_GHOST_HASH = "FAIL_GHOST_HASH";
    public static final String RESULT_FAIL_TRACK_MISMATCH = "FAIL_TRACK_MISMATCH";
    public static final String RESULT_VOID_NO_UPLOAD = "VOID_NO_UPLOAD";

    private VerdictCodec() {
    }

    public static byte[] canonicalBytes(String jobId, String attemptRef,
                                         String recordingHashHex, String result) {
        return String.join("\n", Objects.requireNonNull(jobId),
                        Objects.requireNonNull(attemptRef),
                        Objects.requireNonNull(recordingHashHex),
                        Objects.requireNonNull(result))
                .getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isPass(String result) {
        return RESULT_PASS.equals(result);
    }

    public static boolean isFail(String result) {
        return RESULT_FAIL_DIVERGENT.equals(result)
                || RESULT_FAIL_TIME_MISMATCH.equals(result)
                || RESULT_FAIL_GHOST_HASH.equals(result)
                || RESULT_FAIL_TRACK_MISMATCH.equals(result);
    }

    public static boolean isWorkerResult(String result) {
        return isPass(result) || isFail(result);
    }
}

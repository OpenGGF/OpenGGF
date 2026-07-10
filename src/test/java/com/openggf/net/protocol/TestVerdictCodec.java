package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVerdictCodec {

    @Test
    void canonicalBytesAreStableAndNewlineDelimited() {
        byte[] bytes = VerdictCodec.canonicalBytes(
                "job-1", "r-7#3#12", "abcd", VerdictCodec.RESULT_PASS);
        assertEquals("job-1\nr-7#3#12\nabcd\nPASS",
                new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void resultClassification() {
        assertTrue(VerdictCodec.isPass(VerdictCodec.RESULT_PASS));
        assertTrue(VerdictCodec.isFail(VerdictCodec.RESULT_FAIL_DIVERGENT));
        assertTrue(VerdictCodec.isFail(VerdictCodec.RESULT_FAIL_TIME_MISMATCH));
        assertTrue(VerdictCodec.isFail(VerdictCodec.RESULT_FAIL_GHOST_HASH));
        assertTrue(VerdictCodec.isFail(VerdictCodec.RESULT_FAIL_TRACK_MISMATCH));
        assertFalse(VerdictCodec.isFail(VerdictCodec.RESULT_VOID_NO_UPLOAD));
        assertFalse(VerdictCodec.isPass(VerdictCodec.RESULT_VOID_NO_UPLOAD));
        assertTrue(VerdictCodec.isWorkerResult(VerdictCodec.RESULT_PASS));
        assertTrue(VerdictCodec.isWorkerResult(VerdictCodec.RESULT_FAIL_DIVERGENT));
        assertFalse(VerdictCodec.isWorkerResult(VerdictCodec.RESULT_VOID_NO_UPLOAD));
        assertFalse(VerdictCodec.isWorkerResult("BOGUS"));
        assertFalse(VerdictCodec.isWorkerResult(null));
    }

    @Test
    void standingsRowCarriesVerifyState() throws Exception {
        ControlMessage.StandingsRow row = new ControlMessage.StandingsRow(
                1, "ana", "sonic", 1885, 1, "PENDING");
        ControlMessage message = new ControlMessage.StandingsDelta(List.of(row));
        assertEquals(message,
                ControlCodec.decode(ControlCodec.encode("t", message)).message());
    }
}

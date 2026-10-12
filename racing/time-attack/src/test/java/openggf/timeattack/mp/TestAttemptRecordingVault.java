package openggf.timeattack.mp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAttemptRecordingVault {
    @Test
    void unstampedBurstHasBoundedCustodyWithoutEvictingAcceptedRecordings() {
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> Long.MAX_VALUE / 2);
        for (int i = 0; i < 256; i++) vault.put("key" + i, new byte[]{(byte) i});
        assertTrue(vault.size() <= 128, "unfinished rounds must not retain an unbounded attempt count");
        assertTrue(vault.get("key0").isPresent(), "UNSTAMPED records remain retained until explicit round end");
        assertEquals(0, vault.evictExpired());
    }

    @Test
    void oversizedBodiesAndAggregateBytesDoNotEnterCustody() {
        AttemptRecordingVault oversized = new AttemptRecordingVault(() -> 0);
        oversized.put("too-big", new byte[openggf.timeattack.AttemptInputRecording.MAX_ENCODED_BYTES + 1]);
        assertEquals(0, oversized.size());
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> 0);
        for (int i = 0; i < 80; i++) vault.put("key" + i, new byte[32768]);
        long retained = 0;
        for (int i = 0; i < 80; i++) retained += vault.get("key" + i).map(bytes -> bytes.length).orElse(0);
        assertTrue(retained <= 2L * 1024 * 1024, "aggregate custody bytes must be bounded before copy");
    }

    @Test
    void duplicateLegacyPutCannotRemoveAnExistingExpiryStamp() {
        long[] now = {0};
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> now[0]);
        vault.put("aa", new byte[]{1});
        vault.onRoundEnd();
        now[0] = AttemptRecordingVault.GRACE_MILLIS - 1;
        vault.put("aa", new byte[]{1});
        now[0] += 2;
        assertEquals(1, vault.evictExpired(), "a duplicate caller must not retag stamped custody as UNSTAMPED");
    }

    @Test
    void explicitRejectionPreservesBodyExpiryAndAllowsExpiredCapacityToBeReused() {
        long[] now = {0};
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> now[0]);
        byte[] body = {1};
        assertEquals(AttemptRecordingVault.PutResult.STORED, vault.put("first", body));
        body[0] = 9;
        vault.onRoundEnd();
        assertEquals(AttemptRecordingVault.PutResult.CONFLICT, vault.put("first", new byte[]{2}));
        assertEquals(1, vault.get("first").orElseThrow()[0]);
        byte[] retrieved = vault.get("first").orElseThrow();
        retrieved[0] = 8;
        assertEquals(1, vault.get("first").orElseThrow()[0]);
        for (int i = 1; i < 128; i++) vault.put("entry" + i, new byte[]{1});
        assertEquals(AttemptRecordingVault.PutResult.FULL, vault.put("rejected", new byte[]{1}));
        now[0] = AttemptRecordingVault.GRACE_MILLIS + 1;
        assertEquals(AttemptRecordingVault.PutResult.STORED, vault.put("replacement", new byte[]{1}));
        assertTrue(vault.get("first").isEmpty());
        assertTrue(vault.get("entry1").isPresent(), "unstamped records retain their original custody");
        assertEquals(128, vault.retainedBytes());
    }

    @Test
    void graceExpirySaturatesInsteadOfWrappingIntoAnExpiredStamp() {
        long[] now = {Long.MAX_VALUE - 1};
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> now[0]);
        vault.put("first", new byte[]{1});
        vault.onRoundEnd();
        assertEquals(0, vault.evictExpired());
        now[0] = Long.MAX_VALUE;
        assertEquals(1, vault.evictExpired());
    }

    @Test
    void keepsUntilRoundEndPlusGraceThenEvicts() {
        long[] now = {0};
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> now[0]);
        vault.put("aa", new byte[]{1});
        vault.put("bb", new byte[]{2});
        now[0] += 3_600_000;
        assertEquals(0, vault.evictExpired());
        vault.onRoundEnd();
        now[0] += AttemptRecordingVault.GRACE_MILLIS - 1;
        assertEquals(0, vault.evictExpired());
        assertTrue(vault.get("aa").isPresent());
        now[0] += 2;
        assertEquals(2, vault.evictExpired());
        assertTrue(vault.get("aa").isEmpty());
    }

    @Test
    void newAttemptAfterRoundEndIsNotStampedByOldRound() {
        long[] now = {0};
        AttemptRecordingVault vault = new AttemptRecordingVault(() -> now[0]);
        vault.put("aa", new byte[]{1});
        vault.onRoundEnd();
        vault.put("bb", new byte[]{2});
        now[0] += AttemptRecordingVault.GRACE_MILLIS + 1;
        assertEquals(1, vault.evictExpired());
        assertTrue(vault.get("bb").isPresent());
    }
}

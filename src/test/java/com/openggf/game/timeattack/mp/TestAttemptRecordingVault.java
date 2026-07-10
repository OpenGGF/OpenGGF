package com.openggf.game.timeattack.mp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAttemptRecordingVault {

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

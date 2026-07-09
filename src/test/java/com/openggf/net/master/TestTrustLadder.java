package com.openggf.net.master;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTrustLadder {
    private long now = 1_000_000;
    private final List<IdentityStore> stores = new ArrayList<>();

    private TrustLadder ladder(Path dir) {
        IdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"));
        stores.add(store);
        NewIdentityCache cache = new NewIdentityCache(1000, 3_600_000, () -> now);
        return new TrustLadder(store, cache, TrustLadder.Thresholds.defaults(), () -> now);
    }

    @AfterEach
    void closeStores() {
        stores.forEach(IdentityStore::close);
        stores.clear();
    }

    @Test
    void freshIdentityIsNewAndCannotCreateRooms(@TempDir Path dir) {
        TrustLadder ladder = ladder(dir);
        assertEquals(TrustLadder.Tier.NEW, ladder.tierOf("fp"));
        assertFalse(ladder.canCreateRoom("fp"));
        assertFalse(ladder.canChatYet("fp", now));
        assertTrue(ladder.canChatYet("fp", now - TrustLadder.NEW_CHAT_MUTE_MILLIS - 1));
    }

    @Test
    void ageAloneNeverPromotesRoundsAloneNeitherBothDo(@TempDir Path dir) {
        TrustLadder ladder = ladder(dir);
        ladder.onCleanRound("fp");
        now += TrustLadder.Thresholds.defaults().establishedAgeMillis() + 1;
        assertEquals(TrustLadder.Tier.NEW, ladder.tierOf("fp"));

        for (int i = 0; i < 9; i++) {
            now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
            ladder.onCleanRound("fp");
        }
        assertEquals(TrustLadder.Tier.ESTABLISHED, ladder.tierOf("fp"));
        assertTrue(ladder.canCreateRoom("fp"));

        TrustLadder fastFarm = ladder(dir.resolve("sub"));
        for (int i = 0; i < 60; i++) {
            now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
            fastFarm.onCleanRound("farmer");
        }
        assertEquals(TrustLadder.Tier.NEW, fastFarm.tierOf("farmer"));
    }

    @Test
    void trustedRequiresBothLongAgeAndFiftyRounds(@TempDir Path dir) {
        TrustLadder ladder = ladder(dir);
        ladder.onCleanRound("fp");
        now += TrustLadder.Thresholds.defaults().trustedAgeMillis() + 1;
        for (int i = 0; i < 49; i++) {
            now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
            ladder.onCleanRound("fp");
        }
        assertEquals(TrustLadder.Tier.TRUSTED, ladder.tierOf("fp"));
    }

    @Test
    void banRejectsAtHandshakeAndDestroysStanding(@TempDir Path dir) {
        TrustLadder ladder = ladder(dir);
        ladder.onCleanRound("fp");
        now += TrustLadder.Thresholds.defaults().establishedAgeMillis() + 1;
        for (int i = 0; i < 9; i++) {
            now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
            ladder.onCleanRound("fp");
        }
        assertEquals(TrustLadder.Tier.ESTABLISHED, ladder.tierOf("fp"));

        ladder.sanction(new IdentityStore.SanctionRecord("fp", "BAN", "cheating",
                "operator", now, Long.MAX_VALUE));
        assertEquals(TrustLadder.Tier.SANCTIONED, ladder.tierOf("fp"));
        assertTrue(ladder.isBanned("fp"));
        assertFalse(ladder.canCreateRoom("fp"));
    }

    @Test
    void timeoutSanctionExpires(@TempDir Path dir) {
        TrustLadder ladder = ladder(dir);
        ladder.sanction(new IdentityStore.SanctionRecord("fp", "BAN", "spam",
                "operator", now, now + 10_000));
        assertTrue(ladder.isBanned("fp"));
        now += 10_001;
        assertFalse(ladder.isBanned("fp"));
        assertEquals(TrustLadder.Tier.NEW, ladder.tierOf("fp"));
    }

    @Test
    void accrualPacingIgnoresBackToBackRounds(@TempDir Path dir) {
        TrustLadder ladder = ladder(dir);
        ladder.onCleanRound("fp");
        ladder.onCleanRound("fp");
        now += TrustLadder.Thresholds.defaults().establishedAgeMillis() + 1;
        for (int i = 0; i < 8; i++) {
            now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
            ladder.onCleanRound("fp");
        }
        assertEquals(TrustLadder.Tier.NEW, ladder.tierOf("fp"));
        now += TrustLadder.ACCRUAL_MIN_INTERVAL_MILLIS + 1;
        ladder.onCleanRound("fp");
        assertEquals(TrustLadder.Tier.ESTABLISHED, ladder.tierOf("fp"));
    }
}

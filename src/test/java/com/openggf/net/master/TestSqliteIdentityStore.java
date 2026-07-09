package com.openggf.net.master;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSqliteIdentityStore {
    @Test
    void writeOnMeritPersistsAndFinds(@TempDir Path dir) throws Exception {
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            assertTrue(store.find("fp1").isEmpty());
            store.persistOnDurableEvent("fp1", 1000, 2000);
            IdentityStore.IdentityRecord record = store.find("fp1").orElseThrow();
            assertEquals(1000, record.firstSeenMillis());
            assertEquals("NEW", record.tier());
            assertEquals(0, record.cleanRounds());

            store.persistOnDurableEvent("fp1", 999_999, 3000);
            assertEquals(1000, store.find("fp1").orElseThrow().firstSeenMillis());
            assertEquals(3000, store.find("fp1").orElseThrow().lastSeenMillis());
        }
    }

    @Test
    void cleanRoundsTierAndNamePersistAcrossReopen(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("ids.db");
        try (SqliteIdentityStore store = new SqliteIdentityStore(db)) {
            store.persistOnDurableEvent("fp1", 1000, 1000);
            store.recordCleanRound("fp1", 2000);
            store.recordCleanRound("fp1", 3000);
            store.setDisplayName("fp1", "Farrell");
            store.setTier("fp1", "ESTABLISHED");
        }
        try (SqliteIdentityStore store = new SqliteIdentityStore(db)) {
            IdentityStore.IdentityRecord record = store.find("fp1").orElseThrow();
            assertEquals(2, record.cleanRounds());
            assertEquals("Farrell", record.displayName());
            assertEquals("ESTABLISHED", record.tier());
        }
    }

    @Test
    void sanctionsSurviveIdentityGcAndExpire(@TempDir Path dir) throws Exception {
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            store.persistOnDurableEvent("banned", 1000, 1000);
            store.addSanction(new IdentityStore.SanctionRecord(
                    "banned", "BAN", "cheating", "operator", 1000, Long.MAX_VALUE));
            store.addSanction(new IdentityStore.SanctionRecord(
                    "banned", "TIMEOUT", "spam", "operator", 1000, 5000));

            assertEquals(2, store.activeSanctions("banned", 2000).size());
            assertEquals(1, store.activeSanctions("banned", 6000).size());
            assertEquals(1, store.gcInactiveNewIdentities(999_999));
            assertTrue(store.find("banned").isEmpty());
            assertEquals(1, store.activeSanctions("banned", 6000).size());
        }
    }

    @Test
    void gcSparesNonNewTiers(@TempDir Path dir) throws Exception {
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            store.persistOnDurableEvent("vet", 1000, 1000);
            store.setTier("vet", "TRUSTED");
            store.persistOnDurableEvent("noob", 1000, 1000);
            assertEquals(1, store.gcInactiveNewIdentities(999_999));
            assertTrue(store.find("vet").isPresent());
        }
    }

    @Test
    void newIdentityCacheBoundsAndResetsOnEviction() {
        long[] now = {1000};
        NewIdentityCache cache = new NewIdentityCache(2, 10_000, () -> now[0]);
        assertEquals(1000, cache.firstSeenOf("a"));
        now[0] = 2000;
        assertEquals(2000, cache.firstSeenOf("b"));
        assertEquals(1000, cache.firstSeenOf("a"));
        now[0] = 3000;
        cache.firstSeenOf("c");
        assertEquals(2, cache.size());
        now[0] = 4000;
        assertEquals(4000, cache.firstSeenOf("b"));

        now[0] = 20_000;
        assertEquals(20_000, cache.firstSeenOf("a"));
    }
}

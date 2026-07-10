package com.openggf.net.master;

import com.openggf.net.protocol.VerdictCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestVerdictPersistence {

    @Test
    void addVerdictRoundTripsThroughSqlite(@TempDir Path dir) {
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            store.persistOnDurableEvent("fp1", 0, 0);
            store.addVerdict(new IdentityStore.VerdictRecord("fp1", "r-1#2#3",
                    "abcd", VerdictCodec.RESULT_PASS, "sigB64", 42));
            var verdicts = store.verdictsFor("fp1");
            assertEquals(1, verdicts.size());
            assertEquals("r-1#2#3", verdicts.getFirst().attemptRef());
        }
    }

    @Test
    void failVerdictSanctionsAndDemotes(@TempDir Path dir) {
        long[] now = {1_000_000};
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            store.establishForTest("fp", 0, now[0], 10);
            TrustLadder ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 60_000, () -> now[0]),
                    new TrustLadder.Thresholds(1, 1, Long.MAX_VALUE, Integer.MAX_VALUE),
                    () -> now[0]);
            assertEquals(TrustLadder.Tier.ESTABLISHED, ladder.tierOf("fp"));
            VerdictConsequences consequences = new VerdictConsequences(
                    store, ladder, () -> now[0], 0);
            assertFalse(consequences.apply(new IdentityStore.VerdictRecord(
                    "fp", "r#1#1", "aa", VerdictCodec.RESULT_FAIL_DIVERGENT,
                    "sig", now[0]), "worker-1"));
            assertEquals(TrustLadder.Tier.SANCTIONED, ladder.tierOf("fp"));
            assertEquals(1, store.verdictsFor("fp").size());
        }
    }

    @Test
    void voidNoUploadPersistsWithoutSanction(@TempDir Path dir) {
        long[] now = {100};
        try (SqliteIdentityStore store = new SqliteIdentityStore(dir.resolve("ids.db"))) {
            store.persistOnDurableEvent("fp", 0, now[0]);
            TrustLadder ladder = new TrustLadder(store,
                    new NewIdentityCache(100, 60_000, () -> now[0]),
                    TrustLadder.Thresholds.defaults(), () -> now[0]);
            VerdictConsequences consequences = new VerdictConsequences(
                    store, ladder, () -> now[0], 0);
            assertFalse(consequences.apply(new IdentityStore.VerdictRecord(
                    "fp", "r#1#1", "aa", VerdictCodec.RESULT_VOID_NO_UPLOAD,
                    null, now[0]), "master"));
            assertEquals(TrustLadder.Tier.NEW, ladder.tierOf("fp"));
            assertEquals(1, store.verdictsFor("fp").size());
            assertEquals(0, store.activeSanctions("fp", now[0]).size());
        }
    }
}

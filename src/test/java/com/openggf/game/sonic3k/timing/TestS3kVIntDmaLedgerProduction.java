package com.openggf.game.sonic3k.timing;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.FixedTransfers;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Producer;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.QueuedEntry;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Record;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production ledger records the engine's own submitted DMA work on a fresh HCZ1 start: KosM module completions
 * and Animate_Tiles uploads as the S3K V-int drains them, VInt_8's fixed transfers, and no total for a routine it does
 * not model. Rewind capture/restore replays the same records.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kVIntDmaLedgerProduction {
    @AfterEach
    void reset() {
        SessionManager.clear();
    }

    private static S3kVIntDmaLedger ledger() {
        return S3kRuntimeArtCoordinator.from(GameServices.level().getObjectManager().getObjectServices()).vintDmaLedger();
    }

    private static List<Record> step(HeadlessTestFixture fixture, int frames) {
        List<Record> records = new ArrayList<>();
        for (int frame = 0; frame < frames; frame++) {
            Record before = ledger().lastRecord();
            fixture.stepFrame(false, false, false, false, false);
            Record after = ledger().lastRecord();
            long advanced = (after == null ? 0 : after.ordinal()) - (before == null ? 0 : before.ordinal());
            assertTrue(advanced == 0 || advanced == 1, "at most one draining V-int per frame");
            if (advanced == 1) {
                records.add(after);
            }
        }
        return records;
    }

    @Test
    void recordsTheEngineSubmittedWorkAndReplaysItAfterRestore() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0)
                .withFreshLevelStartLifecycle().build();
        List<Record> records = step(fixture, 240);
        assertTrue(records.size() > 150, "a draining V-int on nearly every frame: " + records.size());
        boolean animateTiles = false;
        for (Record record : records) {
            if (record.fixed() == FixedTransfers.VINT_8) {
                assertEquals(26_855 + record.queueFreezeMclk(), record.freezeMclk(), record.toString());
                assertTrue(S3kVIntDmaLedger.runsVInt8(record.phase()));
            } else {
                // negative control: no total for VInt_A_C / title-card or other unmodelled routines
                assertEquals(-1, record.freezeMclk(), record.toString());
                assertTrue(!S3kVIntDmaLedger.runsVInt8(record.phase()) || record.phase() == null,
                        "VInt_8 phases are modelled outside the Hyper flash: " + record);
            }
            for (QueuedEntry entry : record.drained()) {
                assertEquals(0, entry.words() % 0x10, "whole tiles: " + entry);
                assertEquals(0, entry.vramAddress() % 0x20, "tile-aligned: " + entry);
                animateTiles |= entry.producer() == Producer.ANIMATE_TILES;
                if (entry.producer() == Producer.KOS_MODULE) {
                    assertTrue(entry.words() <= 0x800, "one KosM module at most: " + entry);
                }
            }
            assertEquals(0, record.droppedEntries(), record.toString());
        }
        assertTrue(animateTiles, "HCZ1 AniPLC uploads reach the drained queue");
        assertTrue(records.stream().anyMatch(r -> r.phase() == PlcLifecyclePhase.ORDINARY_LEVEL
                && r.fixed() == FixedTransfers.VINT_8));

        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var capture = registry.capture();
        List<Record> forward = step(fixture, 40);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(capture);
            assertEquals(forward, step(fixture, 40), "forward replay from the capture");
            var replayed = registry.capture();
            assertTrue(RewindSnapshotDiff.diffKey(S3kVIntDmaLedger.REWIND_KEY,
                    after.get(S3kVIntDmaLedger.REWIND_KEY), replayed.get(S3kVIntDmaLedger.REWIND_KEY)).isEmpty());
        }
    }
}

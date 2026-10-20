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
                assertEquals(S3kVIntDmaLedger.FreezeStatus.CONDITIONAL_ON_VINT_START, record.freezeStatus(),
                        record.toString());
                assertEquals(26_855 + record.queueFreezeMclk(), record.conditionalFreezeMclk(), record.toString());
                assertTrue(S3kVIntDmaLedger.runsVInt8(record.phase()));
            } else {
                // negative control: no total for VInt_A_C / title-card or other unmodelled routines
                assertEquals(-1, record.conditionalFreezeMclk(), record.toString());
                assertEquals(S3kVIntDmaLedger.FreezeStatus.FIXED_UNMODELLED, record.freezeStatus(), record.toString());
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

        // capture only once DMA is actually queued, so restore and replay cross a positive drain
        int guard = 0;
        while (ledger().pendingObjectEntries().isEmpty()) {
            assertTrue(++guard < 240, "no queued object DMA to capture");
            fixture.stepFrame(false, false, false, true, false);
        }
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var capture = registry.capture();
        assertTrue(!((S3kVIntDmaLedger.State) capture.get(S3kVIntDmaLedger.REWIND_KEY)).objectPending().isEmpty());
        List<Record> forward = step(fixture, 40);
        assertTrue(!forward.getFirst().drained().isEmpty(), "the first replayed V-int drains the captured entries");
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(capture);
            assertTrue(!ledger().pendingObjectEntries().isEmpty(), "restored with DMA queued");
            assertEquals(forward, step(fixture, 40), "forward replay from the capture");
            var replayed = registry.capture();
            assertTrue(RewindSnapshotDiff.diffKey(S3kVIntDmaLedger.REWIND_KEY,
                    after.get(S3kVIntDmaLedger.REWIND_KEY), replayed.get(S3kVIntDmaLedger.REWIND_KEY)).isEmpty());
        }
    }

    @Test
    void freshLevelResetRunsOnlyForANativeLevelEntryAndBeforeTheTitleCardOwner() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0).build();
        for (int frame = 0; frame < 30; frame++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        var profile = (com.openggf.game.sonic3k.Sonic3kLevelInitProfile)
                com.openggf.game.GameModuleRegistry.getCurrent().getLevelInitProfile();
        var fresh = new com.openggf.game.LevelLoadContext();
        fresh.setLoadMode(com.openggf.game.LevelLoadMode.FULL);
        fresh.setIncludePostLoadAssembly(true);
        fresh.setAssemblyKind(com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        List<String> names = profile.levelLoadSteps(fresh).stream().map(com.openggf.game.InitStep::name).toList();
        assertEquals(List.of("ClearFreshPhysicalDisplayRam", "ClearFreshLevelRng",
                        "ResetFreshLevelDmaQueue", "PrepareFreshTitleCardSst"),
                names.subList(0, 4), "loc_60DE clears physical display RAM before RNG; $61C6 precedes loc_62B6");
        for (var kind : List.of(com.openggf.game.LevelAssemblyKind.STATE_RESTORATION,
                com.openggf.game.LevelAssemblyKind.DECODE_ONLY)) {
            var other = new com.openggf.game.LevelLoadContext();
            other.setLoadMode(com.openggf.game.LevelLoadMode.FULL);
            other.setIncludePostLoadAssembly(true);
            other.setAssemblyKind(kind);
            assertTrue(profile.levelLoadSteps(other).stream().noneMatch(s -> s.name().equals("ResetFreshLevelDmaQueue")),
                    kind + " never runs Level");
        }
        var preview = new com.openggf.game.LevelLoadContext();
        preview.setLoadMode(com.openggf.game.LevelLoadMode.PREVIEW_CAPTURE);
        preview.setIncludePostLoadAssembly(true);
        preview.setAssemblyKind(com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        assertTrue(profile.levelLoadSteps(preview).stream().noneMatch(s -> s.name().equals("ResetFreshLevelDmaQueue")));
        // positive control: queued DMA and the previous-frame bytes are cleared only by the fresh step
        int guard = 0;
        while (ledger().pendingObjectEntries().isEmpty()) {
            assertTrue(++guard < 240, "no queued object DMA");
            fixture.stepFrame(false, false, false, true, false);
        }
        var pending = ledger().pendingObjectEntries();
        profile.levelLoadSteps(fresh).stream().filter(s -> s.name().equals("ResetFreshLevelDmaQueue"))
                .findFirst().orElseThrow().action().run();
        assertEquals(List.of(), ledger().pendingObjectEntries());
        assertTrue(!pending.isEmpty());
        assertEquals(0, ((S3kVIntDmaLedger.State) ledger().capture()).player1PreviousFrame());
        assertEquals(0, ledger().capture().tailPreviousFrame());
    }
}

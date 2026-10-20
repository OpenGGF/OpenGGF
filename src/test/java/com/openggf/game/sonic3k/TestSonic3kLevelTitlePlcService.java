package com.openggf.game.sonic3k;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kLevelTitlePlcService {
    @Test
    void titleVblankServicesOnlyThePreparedHeadAndLagDoesNoWork() throws Exception {
        var plc = new Sonic3kLevelTitlePlcService(TestEnvironment.currentRom());
        plc.beginFreshZoneTitle(8, 0, "sonic");
        var queued = plc.capture();
        assertFalse(queued.queuedEntries().isEmpty());
        assertNull(queued.activeEntry());
        plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(queued, plc.capture(), "VBlank cannot prepare a queued descriptor");
        plc.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        var prepared = plc.capture();
        plc.serviceVBlank(PlcLifecyclePhase.LAG);
        plc.prepareAfterLoop(PlcLifecyclePhase.LAG);
        assertEquals(prepared, plc.capture());
        plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(prepared.activeEntry().remainingPatterns() - 6,
                plc.capture().activeEntry().remainingPatterns());
        assertEquals(prepared.queuedEntries(), plc.capture().queuedEntries());
    }

    @Test
    void queueDrainReplaysFromAnInFlightSnapshotAndFreshLoadReplacesIt() throws Exception {
        var plc = new Sonic3kLevelTitlePlcService(TestEnvironment.currentRom());
        plc.beginFreshZoneTitle(8, 0, "sonic");
        plc.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        var snapshot = plc.capture();
        var recipes = plc.recipeState().capture();
        int first = drain(plc);
        plc.restore(snapshot);
        plc.recipeState().restore(recipes);
        assertEquals(snapshot, plc.capture());
        assertEquals(first, drain(plc));
        plc.restore(snapshot);
        plc.beginFreshZoneTitle(8, 0, "knuckles");
        assertNull(plc.capture().activeEntry());
        assertNotEquals(snapshot.queuedEntries(), plc.capture().queuedEntries());
        assertTrue(drain(plc) > 0);
    }


    @Test
    void identicalRawDescriptorsKeepCapturedPurposesAndCannotUseReadinessAsPhysicalService() throws Exception {
        var plc = new Sonic3kLevelTitlePlcService(TestEnvironment.currentRom());
        var readiness = Sonic3kLevelTitlePlcService.RecipePurpose.READINESS;
        var physical = Sonic3kLevelTitlePlcService.RecipePurpose.PHYSICAL_PATTERNS;
        // Same exact original descriptor appears in both roles: source/ID cannot classify it.
        plc.appendRaw(0x083D74, readiness);
        plc.appendRaw(0x083D74);
        plc.appendRaw(0x083D74, readiness);
        var counts = plc.capture();
        var recipes = plc.recipeState().capture();
        assertEquals(java.util.List.of(readiness, physical, readiness), recipes.queuedPurposes());
        plc.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(counts, plc.capture(), "ordinary Init cannot skip a readiness predecessor");
        plc.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        int total = plc.capture().activeEntry().totalPatterns();
        for (int left = total; left > 0; left -= 6) plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertNull(plc.capture().activeEntry());
        var waitingPhysical = plc.capture();
        plc.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        plc.prepareAfterLoop(PlcLifecyclePhase.SPECIAL_STAGE_RESULTS);
        assertEquals(waitingPhysical, plc.capture(), "title/results cannot arm a physical raw head");
        plc.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        var armed = plc.capture();
        for (var phase : PlcLifecyclePhase.values()) plc.serviceVBlank(phase);
        assertEquals(armed, plc.capture(), "no legacy phase claim can retire physical work");
        var armedRecipes = plc.recipeState().capture();
        assertEquals(physical, armedRecipes.activePurpose());
        for (int cycle = 0; cycle < 2; cycle++) {
            plc.restore(counts);
            assertThrows(IllegalStateException.class,
                    () -> plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD),
                    "count-only restore cannot reclassify physical entries as readiness");
            plc.recipeState().restore(recipes);
            assertEquals(recipes, plc.recipeState().capture());
            plc.restore(armed);
            plc.recipeState().restore(armedRecipes);
            assertEquals(armedRecipes, plc.recipeState().capture());
            plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            assertEquals(armed, plc.capture());
        }
        plc.restore(armed);
        assertThrows(IllegalStateException.class, () -> plc.recipeState().resetForMissingSnapshot());
        plc.recipeState().restore(armedRecipes);
        assertEquals(armedRecipes, plc.recipeState().capture());
    }


    @Test
    void heldOrdinaryInitRunsAfterTheClosingVblankAndOmittedAnimationNeverArms() throws Exception {
        var plc = new Sonic3kLevelTitlePlcService(TestEnvironment.currentRom());
        plc.appendRaw(0x083D74);
        var queued = plc.capture();
        var lifecycle = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(plc);
        lifecycle.markRepresentedIterationDefersLoopTailPreparation();
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
            frame.dispatchGameVBlank(() -> { }, null, null);
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            assertEquals(queued, plc.capture(), "a held tail has not reached Init");
            return null;
        });
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.LAG));
            assertTrue(frame.consumedHeldLoopTailPreparation());
            assertEquals(queued, plc.capture(), "claim cannot arm ahead of the physical VBlank");
            frame.dispatchGameVBlank(() -> assertEquals(queued, plc.capture()), null, null);
            assertNotNull(plc.capture().activeEntry());
            assertEquals(queued.queuedEntries().getFirst(), plc.capture().activeEntry());
            return null;
        });
        var prepared = plc.capture();
        lifecycle.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.NORMAL_PAUSE));
            frame.dispatchGameVBlank(() -> { }, null, null);
            assertEquals(prepared, plc.capture(), "pause has no qualified physical Nemesis schedule");
            return null;
        });
        var fresh = new Sonic3kLevelTitlePlcService(TestEnvironment.currentRom());
        fresh.appendRaw(0x083D74);
        var freshQueued = fresh.capture();
        new com.openggf.game.resources.PlcFrameLifecycleCoordinator(fresh)
                .runLogicalIteration(() -> { }, frame -> {
                    assertTrue(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
                    frame.dispatchGameVBlank(() -> { }, null, null);
                    frame.omitUnreachedLevelAnimationTail();
                    assertEquals(freshQueued, fresh.capture(), "an early transition does not invent Init");
                    return null;
                });
    }

    private static int drain(Sonic3kLevelTitlePlcService plc) {
        int frames = 0;
        while (plc.isBusy() && frames < 1000) {
            plc.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            frames++;
        }
        assertFalse(plc.isBusy(), "ROM startup descriptors must drain within the bounded probe");
        return frames;
    }
}

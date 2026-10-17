package com.openggf.game.sonic3k.timing;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Record;
import com.openggf.game.sonic3k.timing.S3kVIntInstructionCycles.MissingInput;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production witness for the V-int instruction figure in a dry level (MGZ1, Sonic alone, real fresh-level lifecycle).
 * Each VInt_8 record is priced from engine-owned inputs only: the drained DMA count, the configured region, the
 * physical Kos queue, the level's water owner and the Demo_timer mirror (1800 at Level, one count per VInt_8). The
 * expected figure is the ROM path term sum counted by the test itself; the 1801st VInt_8 loses the Demo_timer term.
 * Records that cannot be priced name only genuinely missing inputs, and the unpriced callees are always listed.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kVIntInstructionProduction {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static S3kVIntDmaLedger ledger() {
        return S3kRuntimeArtCoordinator.from(GameServices.level().getObjectManager().getObjectServices())
                .vintDmaLedger();
    }

    @Test
    void dryLevelVInt8FiguresFollowEngineInputsAcrossTheDemoTimerBoundaryAndReplay() {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(2, 0)
                .withFreshLevelStartLifecycle().build();
        var level = GameServices.level();
        assertFalse(level.getObjectManager().getObjectServices().waterSystem()
                .hasWater(level.getCurrentZone(), level.getCurrentAct()), "precondition: a dry level");
        Set<MissingInput> allowed = EnumSet.of(MissingInput.ROUTINE_NOT_CLOSED, MissingInput.KOS_BUSY_PC_UNKNOWN,
                MissingInput.DMA_PRODUCER_STATE_UNKNOWN);
        int vint8 = 0;
        int closedWithDemo = 0;
        int closedWithoutDemo = 0;
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        com.openggf.game.rewind.CompositeSnapshot nearBoundary = null;
        int boundaryCaptureVInt8 = -1;
        List<Record> afterCapture = new ArrayList<>();
        for (int frame = 0; frame < 2400 && vint8 < 1830; frame++) {
            Record before = ledger().lastRecord();
            fixture.stepFrame(false, false, false, false, false);
            Record record = ledger().lastRecord();
            if (record == null || record == before) {
                continue;
            }
            if (nearBoundary != null && afterCapture.size() < 12) {
                afterCapture.add(record);
            }
            if (record.phase() == PlcLifecyclePhase.ORDINARY_LEVEL) {
                vint8++;
            }
            var term = record.instructions();
            assertEquals(3, term.unpricedCallees().size(), "DrawLevel, UpdateHUD, Process_Nem_Queue_2 unpriced");
            if (term.missing().isEmpty()) {
                boolean demo = vint8 <= 1800;
                assertEquals(1804 + 116 * record.drained().size() + (demo ? 18 : 0), term.closedHandlerCycles(),
                        "VInt_8 #" + vint8 + " " + record);
                if (demo) {
                    closedWithDemo++;
                } else {
                    closedWithoutDemo++;
                }
            } else {
                assertTrue(allowed.containsAll(term.missing()), "only genuinely missing inputs: " + term.missing());
            }
            if (nearBoundary == null && vint8 == 1795) {
                nearBoundary = registry.capture();
                boundaryCaptureVInt8 = vint8;
            }
        }
        assertTrue(closedWithDemo >= 100, "priced VInt_8 records while Demo_timer counts: " + closedWithDemo);
        assertTrue(closedWithoutDemo >= 10, "priced VInt_8 records after Demo_timer reached 0: " + closedWithoutDemo);
        assertEquals(1795, boundaryCaptureVInt8);
        assertEquals(12, afterCapture.size());
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(nearBoundary);
            assertEquals(5, ledger().capture().demoTimer(), "restored mirror: 1800 - 1795");
            List<Record> replay = new ArrayList<>();
            while (replay.size() < afterCapture.size()) {
                Record before = ledger().lastRecord();
                fixture.stepFrame(false, false, false, false, false);
                Record record = ledger().lastRecord();
                if (record != null && record != before) {
                    replay.add(record);
                }
            }
            assertEquals(afterCapture, replay, "replay across the Demo_timer boundary");
        }
    }
}

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
        int first = drain(plc);
        plc.restore(snapshot);
        assertEquals(snapshot, plc.capture());
        assertEquals(first, drain(plc));
        plc.restore(snapshot);
        plc.beginFreshZoneTitle(8, 0, "knuckles");
        assertNull(plc.capture().activeEntry());
        assertNotEquals(snapshot.queuedEntries(), plc.capture().queuedEntries());
        assertTrue(drain(plc) > 0);
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

package com.openggf.game.sonic2.competition;

import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionP1CheckpointEnvironment {
    @Test void supplierReadsTheLivePrimaryCameraAndTheActualBankPortEachTime() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            w.session.camera(0).setX((short)0x1234);w.session.camera(0).setY((short)0x234);
            w.session.camera(0).setMaxY((short)0x567);
            var e=w.host.checkpointEnvironment();
            assertEquals(0x1234,e.cameraX());assertEquals(0x234,e.cameraY());assertEquals(0x567,e.cameraMaxY());
            assertEquals(1,e.bgX());assertEquals(2,e.bgY());assertEquals(3,e.bg2X());
            assertEquals(4,e.bg2Y());assertEquals(5,e.bg3X());assertEquals(6,e.bg3Y());
            assertEquals(0,e.dynamicResizeRoutine());assertEquals(0,e.waterLevel());
            assertEquals(0,e.waterRoutine());assertEquals(0,e.waterFullscreen());
            w.backgrounds.swapPlayers();w.session.camera(0).setX((short)0x4321);
            var newer=w.host.checkpointEnvironment();
            assertEquals(0x4321,newer.cameraX());assertEquals(11,newer.bgX());assertEquals(16,newer.bg3Y());
            var lifecycle=w.host.context().getWorldSession().getGameModule().getGameService(
                    com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle.class);
            lifecycle.saveCheckpoint(0,1,0x456,0x789);
            lifecycle.saveCheckpoint(1,1,0x456,0x789);
            assertEquals(newer,lifecycle.checkpoint(0).primaryEnvironment());
            assertNull(lifecycle.checkpoint(1).primaryEnvironment());
        });
    }
}

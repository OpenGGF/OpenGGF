package com.openggf.tools;

import com.openggf.game.soniccd.*;
import com.openggf.route.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdRouteTape {
    @Test void recordedRowsBootTheExactPastStageThroughTheDiscPipeline() throws Exception {
        var path = CdTestMedia.usaCue();
        var launch = LaunchContract.coldLevel(RomIdentity.ofFile("scd", path), new LevelId("scd", 0, 1),
                new Roster("sonic", ""), 320, 0);
        try (var world = EngineRouteHosts.withRoms(Map.of("scd", path)).openColdReplay(launch)) {
            assertEquals(CdStagePayload.R11B, assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class,
                    com.openggf.game.GameServices.level().getCurrentLevel()).payload());
            assertEquals(0, world.rowsOffered());
            world.step(PadRow.NEUTRAL);
            assertEquals(1, world.rowsOffered());
        }
    }
}

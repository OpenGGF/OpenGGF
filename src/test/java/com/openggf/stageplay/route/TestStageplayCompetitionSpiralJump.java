package com.openggf.stageplay.route;

import com.openggf.route.*;
import com.openggf.tools.StageplayCompetitionRouteHosts;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Cold two-human EHZ input prefix; a regression draft, not a qualified full route. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestStageplayCompetitionSpiralJump {
    @Test void jumpingOffSpiralPreservesNativeAirborneVelocity() throws Exception {
        TestEnvironment.resetEngineOnly();
        var route = RouteFiles.readDraft(Path.of(getClass().getResource(
                "/routes/s2/competition/ehz-spiral-jump-regression.route.json").toURI()));
        boolean[] checked = {false};
        var result = new RouteReplay(new StageplayCompetitionRouteHosts(
                Path.of(System.getProperty("sonic2.rom.path")))).replay(route, Cancellation.NONE, (row, sample) -> {
            if (row == 2227) {
                checked[0] = true;
                assertTrue(sample.p1().air(), "loc_215C0 must release a jumping rider");
                assertFalse(sample.p1().onObject());
                assertEquals(-0x680, sample.p1().ySpeed());
                assertEquals(4604, sample.p1().centreX());
                assertEquals(677, sample.p1().centreY());
                assertTrue(sample.p2().isPresent());
            }
            // End-of-row native frame = row + 69 for this act-1 input prefix.
            if (row == 2228) {
                assertEquals(670, sample.p1().centreY());
                assertTrue(sample.p1().air());
            }
        });
        assertInstanceOf(ReplayResult.Passed.class, result, result.toString());
        assertTrue(checked[0]);
    }
}

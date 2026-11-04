package com.openggf.trace.replay;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Load_Starpost_Settings restores the saved mean, not Target_water_level. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTraceSegmentCarriedWaterRestore {
    @AfterEach
    void reset() {
        SessionManager.clear();
    }

    @Test
    void recordedSaved2MeanLeavesTheFreshWaterTargetIntact() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(1, 0).withSkippedZoneIntro().build();
        var trace = TraceData.loadMetadataOnly(Path.of(
                "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/hcz_2"));
        var carried = TraceSegmentCarriedStateImport.resolve(trace);
        assertEquals("Saved2", carried.entryRestore().block());
        var water = GameServices.water();
        int freshTarget = water.getWaterLevelTarget(1, 0);
        assertNotEquals(freshTarget, carried.entryRestore().meanWaterLevel(),
                "the recorded mean and ROM-initialized target must discriminate the restore");

        TraceSegmentCarriedStateImport.importAfterLevelInit(carried);

        assertEquals(carried.entryRestore().meanWaterLevel(), water.getWaterLevelY(1, 0));
        assertEquals(freshTarget, water.getWaterLevelTarget(1, 0),
                "loc_2D2C2 restores Mean_water_level while retaining the initialized target");
    }
}

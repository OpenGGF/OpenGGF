package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.sonic1.objects.bosses.Sonic1SYZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2CPZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2EHZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestServiceRequiredBossRootRewindRoundTrip {

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void wfzBossRootRoundTripsThroughObjectConstructionContext() {
        RoundTripSweepResult result = RewindRoundTripHarness.probeClass(
                Sonic2WFZBossInstance.class.getName());

        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                "WFZ boss root must recreate with restore-time ObjectServices");
    }

    @Test
    void constructorSpawningBossRootsStayGraphOnlyInTheIsolatedSweep() {
        for (String className : constructorSpawningBossRoots()) {
            RoundTripSweepResult result = RewindRoundTripHarness.probeClass(className);

            assertInstanceOf(RoundTripSweepResult.Unprobed.class, result,
                    className + " needs the graph harness because its constructor spawns children");
        }
    }

    private static List<String> constructorSpawningBossRoots() {
        return List.of(
                Sonic1SYZBossInstance.class.getName(),
                Sonic2CPZBossInstance.class.getName(),
                Sonic2EHZBossInstance.class.getName());
    }
}

package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestGraphCoveredIsolatedProbeClassification {
    private static final String DER_GRAPH_TEST =
            "com.openggf.game.sonic2.objects.bosses.TestS2DeathEggRobotGraphRewind";
    private static final String DER_BOMB_GRAPH_TEST =
            "TestS2DezBombGraphRewind";
    private static final String AIZ_END_BOSS_GRAPH_TEST =
            "TestS3kAizEndBossGraphRewind";
    private static final String MECHA_SONIC_GRAPH_TEST =
            "com.openggf.game.sonic2.objects.bosses.TestS2MechaSonicGraphRewind";
    private static final String MTZ_BOSS_GRAPH_TEST =
            "com.openggf.game.sonic2.objects.bosses.TestS2MTZBossGraphRewind";
    private static final String WFZ_BOSS_GRAPH_TEST =
            "com.openggf.game.sonic2.objects.bosses.TestS2WfzBossGraphRewind";

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void deathEggRobotNoProbeAndParentDependentChildrenAreReportedAsGraphCovered() {
        Map<String, String> expected = Map.of(
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$ArticulatedChild",
                DER_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$ForearmChild",
                DER_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$HeadChild",
                DER_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$JetChild",
                DER_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$SensorChild",
                DER_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$BombChild",
                DER_BOMB_GRAPH_TEST);

        expected.forEach(this::assertGraphCovered);
    }

    @Test
    void aizEndBossNoProbeAndParentDependentChildrenAreReportedAsGraphCovered() {
        Map<String, String> expected = Map.of(
                "com.openggf.game.sonic3k.objects.AizEndBossArmChild",
                AIZ_END_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic3k.objects.AizEndBossPropellerChild",
                AIZ_END_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic3k.objects.AizEndBossFlameChild",
                AIZ_END_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic3k.objects.AizEndBossBombChild",
                AIZ_END_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic3k.objects.AizEndBossSmokeChild",
                AIZ_END_BOSS_GRAPH_TEST);

        expected.forEach(this::assertGraphCovered);
    }

    @Test
    void mechaSonicNoProbeAndParentDependentChildrenAreReportedAsGraphCovered() {
        Map<String, String> expected = Map.of(
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicDEZWindow",
                MECHA_SONIC_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicLEDWindow",
                MECHA_SONIC_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicTargetingSensor",
                MECHA_SONIC_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance$MechaSonicSpikeball",
                MECHA_SONIC_GRAPH_TEST);

        expected.forEach(this::assertGraphCovered);
    }

    @Test
    void mtzBossNoProbeAndParentDependentChildrenAreReportedAsGraphCovered() {
        Map<String, String> expected = Map.of(
                "com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance$MTZBossOrb",
                MTZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance$MTZLaserShooter",
                MTZ_BOSS_GRAPH_TEST);

        expected.forEach(this::assertGraphCovered);
    }

    @Test
    void wfzBossNoProbeAndParentDependentChildrenAreReportedAsGraphCovered() {
        Map<String, String> expected = Map.of(
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZFloatingPlatform",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZLaser",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZLaserShooter",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZLaserWall",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZPlatformHurt",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZPlatformReleaser",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZRobotnik",
                WFZ_BOSS_GRAPH_TEST,
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZRobotnikPlatform",
                WFZ_BOSS_GRAPH_TEST);

        expected.forEach(this::assertGraphCovered);
    }

    private void assertGraphCovered(String className, String evidence) {
        RoundTripSweepResult result = RewindRoundTripHarness.probeClass(className);
        RoundTripSweepResult.GraphCovered covered =
                assertInstanceOf(RoundTripSweepResult.GraphCovered.class, result);
        assertEquals(evidence, covered.evidence());
    }
}

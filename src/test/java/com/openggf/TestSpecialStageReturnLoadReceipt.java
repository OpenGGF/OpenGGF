package com.openggf;

import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@RequiresRom(SonicGame.SONIC_2)
class TestSpecialStageReturnLoadReceipt {
    @Test
    void synchronousReturnPublishesItsProductionLoadBeforePlaybackActivation() {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var level = GameServices.level();
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        tracker.prime(level);
        long generation = level.getCompletedProductionLoadGeneration();

        assertFalse(SpecialStageTransitionSupport.loadSpecialStageReturnLevel(
                level, EmeraldRewardKind.CHAOS_EMERALD, 0, true));

        var receipt = tracker.latest().orElseThrow();
        assertEquals(RunLevelLoadCause.INTERIOR_RETURN, receipt.cause());
        assertEquals(generation + 1, receipt.identity().loadGeneration());
        assertEquals(level.getCurrentZone(), receipt.identity().progressionZone());
        assertEquals(level.getRomZoneId(), receipt.identity().romZone());
        assertEquals(level.getCurrentAct(), receipt.identity().act());
    }
}

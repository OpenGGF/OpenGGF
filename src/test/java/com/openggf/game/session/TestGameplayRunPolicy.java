package com.openggf.game.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGameplayRunPolicy {
    @Test
    void stockAndIsolatedActKeepTheShippedDeath() {
        for (GameplayRunPolicy policy : new GameplayRunPolicy[] {
                GameplayRunPolicy.stock(), GameplayRunPolicy.isolatedAct()}) {
            assertEquals(GameplayRunPolicy.DeathRestart.STOCK, policy.deathRestart());
            assertTrue(policy.livesCounter());
            assertFalse(policy.fastDeathRestart());
            assertTrue(policy.zoneIntros());
        }
    }

    @Test
    void fastRestartsChangeOnlyTheDeathAndTheLivesCounter() {
        GameplayRunPolicy base = GameplayRunPolicy.isolatedAct();
        GameplayRunPolicy fast = base.withFastRestarts();
        assertEquals(new GameplayRunPolicy(base.specialStageEntry(), base.bonusStageEntry(),
                base.actCompletion(), base.liveRewind(), base.editorEntry(),
                GameplayRunPolicy.DeathRestart.FAST_FROM_START, false, true), fast);
        assertTrue(fast.fastDeathRestart());
        assertEquals(30, GameplayRunPolicy.FAST_RESTART_SKIP_FRAMES, "half a second of executed steps");
    }

    @Test
    void withoutZoneIntrosChangesOnlyTheIntros() {
        GameplayRunPolicy fast = GameplayRunPolicy.isolatedAct().withFastRestarts();
        GameplayRunPolicy skipping = fast.withoutZoneIntros();
        assertFalse(skipping.zoneIntros());
        assertEquals(fast, new GameplayRunPolicy(skipping.specialStageEntry(), skipping.bonusStageEntry(),
                skipping.actCompletion(), skipping.liveRewind(), skipping.editorEntry(),
                skipping.deathRestart(), skipping.livesCounter(), true));
    }

    @Test
    void theDeathRestartIsRequired() {
        assertThrows(NullPointerException.class, () -> new GameplayRunPolicy(true, true,
                GameplayRunPolicy.ActCompletion.CONTINUE, true, true, null, true, true));
    }
}

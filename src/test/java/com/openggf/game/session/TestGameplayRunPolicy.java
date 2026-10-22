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
        }
    }

    @Test
    void fastRestartsChangeOnlyTheDeathAndTheLivesCounter() {
        GameplayRunPolicy base = GameplayRunPolicy.isolatedAct();
        GameplayRunPolicy fast = base.withFastRestarts();
        assertEquals(new GameplayRunPolicy(base.specialStageEntry(), base.bonusStageEntry(),
                base.actCompletion(), base.liveRewind(), base.editorEntry(),
                GameplayRunPolicy.DeathRestart.FAST_FROM_START, false), fast);
        assertTrue(fast.fastDeathRestart());
        assertEquals(30, GameplayRunPolicy.FAST_RESTART_SKIP_FRAMES, "half a second of executed steps");
    }

    @Test
    void theDeathRestartIsRequired() {
        assertThrows(NullPointerException.class, () -> new GameplayRunPolicy(true, true,
                GameplayRunPolicy.ActCompletion.CONTINUE, true, true, null, true));
    }
}

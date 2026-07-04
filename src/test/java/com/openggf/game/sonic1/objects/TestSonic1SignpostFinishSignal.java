package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S1 signpost must flag the end-of-level sequence ({@code Level_end_flag})
 * when the player passes it, the same way S3K signposts/capsules do. Shared
 * runtime code keys off that flag (strict right level boundary, and the
 * time-attack finish signal), so S1 leaving it unset stalled timed attempts.
 */
class TestSonic1SignpostFinishSignal {
    @Test
    void passingSignpostFlagsEndOfLevel() {
        GameStateManager gameState = new GameStateManager();
        TestablePlayableSprite player =
                new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x0300);
        int centreX = player.getCentreX();
        ObjectSpawn spawn = new ObjectSpawn(centreX, 0x0300, 0x0D, 0x00, 0, true, 0);
        Sonic1SignpostObjectInstance signpost = new Sonic1SignpostObjectInstance(spawn);
        signpost.setServices(new StubObjectServices() {
            @Override
            public GameStateManager gameState() {
                return gameState;
            }
        });

        assertFalse(gameState.isEndOfLevelActive());
        signpost.update(0, player); // player centred on the signpost -> Sign_Touch
        assertTrue(gameState.isEndOfLevelActive(),
                "S1 signpost must flag the end-of-level sequence when the player passes it");
    }
}

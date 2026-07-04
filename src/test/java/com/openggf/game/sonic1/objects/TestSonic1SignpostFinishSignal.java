package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S1 signpost must raise the game-agnostic act-completion signal when the
 * player passes it, so observers such as time attack can detect act completion.
 * It must NOT set the ROM {@code Level_end_flag} ({@code endOfLevelActive}): shared
 * physics reads that flag for the strict right-boundary clamp, but the S1 ROM keeps
 * the player running past the signpost with no such clamp.
 */
class TestSonic1SignpostFinishSignal {
    @Test
    void passingSignpostRaisesActCompletionSignal() {
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

        assertFalse(gameState.isActCompletionSignalActive());
        signpost.update(0, player); // player centred on the signpost -> Sign_Touch
        assertTrue(gameState.isActCompletionSignalActive(),
                "S1 signpost must raise the act-completion signal when the player passes it");
        assertFalse(gameState.isEndOfLevelActive(),
                "S1 signpost must NOT set the ROM Level_end_flag (physics reads it)");
    }
}

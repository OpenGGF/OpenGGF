package com.openggf.game.sonic2.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameStateManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The S2 signpost (Obj0D) must flag the end-of-level sequence ({@code Level_end_flag})
 * when the player passes it, the same way S3K signposts/capsules do. Shared runtime
 * code keys off that flag (strict right level boundary, and the time-attack finish
 * signal), so S2 leaving it unset stalled timed attempts.
 */
class TestSonic2SignpostFinishSignal {
    @Test
    void passingSignpostFlagsEndOfLevel() {
        GameStateManager gameState = new GameStateManager();
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("sonic");

        TestablePlayableSprite player =
                new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x0300);
        int centreX = player.getCentreX();
        ObjectSpawn spawn = new ObjectSpawn(centreX, 0x0300, 0x0D, 0x00, 0, true, 0);
        SignpostObjectInstance signpost = new SignpostObjectInstance(spawn, "Signpost");
        signpost.setServices(new StubObjectServices() {
            @Override
            public GameStateManager gameState() {
                return gameState;
            }

            @Override
            public SonicConfigurationService configuration() {
                return config;
            }
        });

        assertFalse(gameState.isEndOfLevelActive());
        signpost.update(0, player); // player centred on the signpost -> Obj0D_Main activation
        assertTrue(gameState.isEndOfLevelActive(),
                "S2 signpost must flag the end-of-level sequence when the player passes it");
    }
}

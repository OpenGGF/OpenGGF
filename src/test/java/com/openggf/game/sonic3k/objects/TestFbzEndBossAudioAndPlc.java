package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzEndBossAudioAndPlc {
    @Test
    void entryAndExitPlcsUseLockedOnIdsAndKosmOrder() {
        assertEquals(0x6F, Sonic3kPlcLoader.fbzEndBossPlcId());
        assertEquals(java.util.List.of(
                new Sonic3kPlcLoader.KosmQueueEntry(
                        Sonic3kConstants.ART_KOSM_FBZ_EXIT_DOOR_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR * 32),
                new Sonic3kPlcLoader.KosmQueueEntry(
                        Sonic3kConstants.ART_KOSM_FBZ_EXIT_HALL_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_EXIT_HALL * 32)),
                Sonic3kPlcLoader.fbzEndBossExitKosmEntries());
    }

    @Test
    void combatAudioIdsAndPalettePatchMatchTheDisassembly() {
        assertEquals(0x19, FbzEndBossInstance.BOSS_MUSIC_ID);
        assertEquals(0x6E, FbzEndBossInstance.BOSS_HIT_SFX_ID);
        assertEquals(0xC9, FbzEndBossInstance.ARM_ROTATE_SFX_ID);
        assertEquals(0x4F, FbzEndBossFlameChild.CONTINUOUS_SFX_ID);
        assertEquals(0x70F94, Sonic3kConstants.PAL_FBZ_END_BOSS_ADDR);
    }

    @Test
    void exitPublicationUsesTheCanonicalRewindCapturedUnkFaa8Word() {
        var gameState = new com.openggf.game.GameStateManager();
        gameState.setEndOfLevelActive(true);
        var snapshot = gameState.capture();
        gameState.setEndOfLevelActive(false);
        gameState.restore(snapshot);
        assertTrue(gameState.isEndOfLevelActive());
        assertFalse(gameState.isEndOfLevelFlag(), "End_of_level_flag is a different native word");
    }
}

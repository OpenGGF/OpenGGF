package openggf.timeattack.mp;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.RunArea;
import com.openggf.mods.run.RunSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The per-game lobby levels: each game's own act, fenced, with its spawn inside the fence. */
class TestLobbyAreas {
    @Test
    void everyGameHasAFencedLobbyInItsOwnFirstStage() {
        assertLobby("s1", 0, 0);
        assertLobby("s2", 0, 0);
        assertLobby("s3k", 22, 1);
        assertNull(LobbyAreas.forGame("kis2"));
        assertNull(LobbyAreas.forGame(null));
    }

    private static void assertLobby(String gameId, int zone, int act) {
        LobbyAreas.LobbyArea lobby = LobbyAreas.forGame(gameId);
        assertNotNull(lobby, gameId);
        assertEquals(gameId, lobby.gameId());
        assertEquals(zone, lobby.zone());
        assertEquals(act, lobby.act());
        RunArea area = lobby.area();
        int rightWall = area.maxX() + (gameId.equals("s3k") ? 0x128 : 0x168);
        assertTrue(area.spawnX() >= area.minX() + 0x10 && area.spawnX() <= rightWall, gameId + " spawn X");
        assertTrue(area.spawnY() >= area.minY() && area.spawnY() < area.maxY() + 0xE0, gameId + " spawn Y");
        RunSpec spec = LobbyAreas.runSpec(lobby, "sonic");
        assertEquals(GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros(), spec.policy(),
                "no stages, rewind or editor; deaths reload at once at the lobby spawn");
        assertSame(area, spec.area());
    }

    @Test
    void theHiddenPalaceFenceStaysLeftOfTheKnucklesFightTrigger() {
        // word_63CEC starts the fight once the camera X reaches 0xFE0 (sonic3k.asm:131316-131319).
        assertTrue(LobbyAreas.forGame("s3k").area().maxX() < 0xFE0);
    }

    @Test
    void charactersFallBackToSonicWhereTheGameHasNoSuchStockCharacter() {
        assertEquals("sonic", LobbyAreas.characterFor("s1", "tails"));
        assertEquals("tails", LobbyAreas.characterFor("s2", "tails"));
        assertEquals("sonic", LobbyAreas.characterFor("s2", "knuckles"));
        assertEquals("knuckles", LobbyAreas.characterFor("s3k", "knuckles"));
        assertEquals("sonic", LobbyAreas.characterFor("s3k", null));
    }
}

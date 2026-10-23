package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStockGameCapabilityMembership {
    @Test void discIdentityDoesNotAcquireCartridgeOrStandaloneCapabilities() {
        assertEquals(GameId.SCD, GameId.fromCode("scd"));
        assertEquals(GameId.SCD, GameId.fromCode("SCD"));
        assertEquals("scd", GameId.SCD.code());
        assertTrue(GameId.SCD.cartridgeGame().isEmpty());
        assertThrows(IllegalStateException.class, GameId.SCD::romGame);
        assertThrows(IllegalArgumentException.class, () -> BuiltInRomDetectors.forGame(GameId.SCD));
        assertEquals(3, RomGame.values().length, "disc media must not enter cartridge address-space resolution");
        var first = GameModuleRegistry.createStockModule(GameId.SCD);
        var second = GameModuleRegistry.createStockModule(GameId.SCD);
        assertNotSame(first, second, "each world receives its own stock CD module");
        assertEquals(GameId.SCD, first.getGameId());
        assertFalse(GameModuleRouting.isStandalone(first));
    }

    @Test void existingCartridgeMappingsRemainExplicitAndStandaloneHasNone() {
        assertEquals(RomGame.S1, GameId.S1.cartridgeGame().orElseThrow());
        assertEquals(RomGame.S2, GameId.S2.cartridgeGame().orElseThrow());
        assertEquals(RomGame.S3K, GameId.S3K.cartridgeGame().orElseThrow());
        assertTrue(GameId.STANDALONE.cartridgeGame().isEmpty());
    }

    @Test void unsupportedDiscMenuAudioNeverFallsBackToAnotherGame() {
        assertThrows(IllegalArgumentException.class, () -> GameModuleRegistry.createStockMenuAudio(GameId.SCD));
        var configuration = SonicConfigurationService.createStandalone();
        assertThrows(IllegalArgumentException.class, () -> HubMusicController.openRomSession("scd", configuration, true));
    }
}

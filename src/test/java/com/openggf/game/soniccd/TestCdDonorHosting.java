package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Disc host plus cartridge-owned character sheets, through the production boot. */
class TestCdDonorHosting {
    @ParameterizedTest
    @CsvSource({"s1,tails", "s1,knuckles", "s2,knuckles"})
    void absentCartridgeCharacterRefusesTheLaunch(String donor, String character) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue()); var boot = new HeadlessGameBoot(320, 224)) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> boot.boot(disc, new SonicCdGameModule(), 0, 0)).getMessage().contains("does not supply"));
        } finally {
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        }
    }

    @ParameterizedTest
    @CsvSource({"s1,sonic", "s2,sonic", "s2,tails", "s3k,sonic", "s3k,tails", "s3k,knuckles"})
    void cartridgeCharactersRunInDiscStage(String donor, String character) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            assertTrue(CrossGameFeatureProvider.isActive());
            var player = GameServices.camera().getFocusedSprite();
            assertNotNull(player);
            assertNotNull(GameServices.crossGameFeatures().loadPlayerSpriteArt(character));
            assertSame(com.openggf.game.soniccd.player.SonicCdRules.RULES.playerMovement(), player.getGameRules().playerMovement());
            assertEquals(donor.equals("s3k"), player.getGameRules().playerCapability().elementalShieldsEnabled());
            if (donor.equals("s3k")) {
                assertNotNull(GameServices.crossGameFeatures().getDonorShieldFactory());
                assertNotNull(GameServices.crossGameFeatures().getDonorInstaShieldFactory());
            }
            if (donor.equals("s1")) {
                assertTrue(assertThrows(IllegalArgumentException.class,
                        () -> GameServices.crossGameFeatures().loadPlayerSpriteArt("tails")).getMessage().contains("does not supply"));
            }
            if (!donor.equals("s3k")) {
                assertTrue(assertThrows(IllegalArgumentException.class,
                        () -> GameServices.crossGameFeatures().loadPlayerSpriteArt("knuckles")).getMessage().contains("does not supply"));
            }
            assertEquals(CharacterKey.parsePersisted(character), player.characterKey());
            assertEquals("knuckles".equals(character) ? PhysicsModifiers.KNUCKLES : PhysicsModifiers.STANDARD,
                    player.getPhysicsModifiers());
            player.setAnimationId(com.openggf.game.soniccd.player.CdSonicAnimation.SPRING);
            assertEquals(player.resolveAnimationId(CanonicalAnimation.SPRING), player.getAnimationId());
            for (int i = 0; i < 240; i++) loop.step();
            int startX = player.getCentreX();
            loop.getInputHandler().setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                    new com.openggf.control.PlayerInputState(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT, 0, 0, 0, false, false),
                    com.openggf.control.PlayerInputState.neutral()));
            for (int i = 0; i < 120; i++) loop.step();
            assertTrue(player.getCentreX() > startX + 64, "donated character runs beyond the entry");
            assertFalse(player.getDead());
            if (donor.equals("s3k")) {
                loop.getInputHandler().clearLogicalOverride();
                for (var shield : new ShieldType[] {ShieldType.FIRE, ShieldType.LIGHTNING, ShieldType.BUBBLE}) {
                    player.giveShield(shield);
                    for (int i = 0; i < 10; i++) loop.step();
                    assertEquals(shield, player.getShieldType());
                }
            }
            assertTrue(assertThrows(java.io.IOException.class,
                    () -> GameServices.crossGameFeatures().initialize("scd")).getMessage().contains("cartridge-backed"));
            assertFalse(CrossGameFeatureProvider.isActive());
        } finally {
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        }
    }
}

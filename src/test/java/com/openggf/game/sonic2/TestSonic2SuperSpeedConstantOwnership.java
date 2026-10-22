package com.openggf.game.sonic2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Speed-word ownership is separate from underwater status and its gravity. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SuperSpeedConstantOwnership {
    @AfterEach
    void clearConfiguration() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private static HeadlessTestFixture transformWhileSubmerged() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(2, 0)
                .withFreshLevelStartLifecycle().build();
        var player = fixture.sprite();
        var controller = assertInstanceOf(Sonic2SuperStateController.class,
                player.getSuperStateController());
        GameServices.gameState().configureSpecialStageProgress(7, 7);
        for (int emerald = 0; emerald < 7; emerald++) {
            GameServices.gameState().markEmeraldCollected(emerald);
        }
        player.setRingCount(100);
        player.setInWater(true);
        player.setAir(true);
        player.setJumping(true);
        player.setYSpeed((short) 0x20);
        player.currentLevelState().resumeTimer();
        assertSpeeds(player, 0x300, 6, 0x40);

        controller.checkTransformationBeforeMove();

        assertEquals(SuperState.TRANSFORMING, controller.getState(), "ordinary apex gate transforms");
        assertEquals(PhysicsProfile.SONIC_2_SUPER_SONIC, player.getPhysicsProfile());
        return fixture;
    }

    private static void assertSpeeds(AbstractPlayableSprite player, int maximum, int acceleration,
                                     int deceleration) {
        assertEquals(maximum, player.getMax());
        assertEquals(acceleration, player.getRunAccel());
        assertEquals(deceleration, player.getRunDecel());
    }

    private static void assertSubmergedWithFullSuperSpeeds(AbstractPlayableSprite player) {
        assertTrue(player.isInWater(), "speed-word writes preserve Status_Underwater");
        assertFalse(player.captureRewindState().playerExtra().waterPhysicsActive(),
                "the native speed bank retains the transformation's dry constants");
        assertSpeeds(player, 0xA00, 0x30, 0x100);
        assertEquals(0x380, player.getJump(), "water status still selects underwater jump force");
    }

    @Test
    void underwaterTransformationKeepsFullSpeedWordsAcrossAlreadyUnderwaterChecks() throws Exception {
        var player = transformWhileSubmerged().sprite();
        assertSubmergedWithFullSuperSpeeds(player);
        // Sonic_Water's bset/bne returns when the status bit was already set.
        // Repeating that real owner must not reapply a water multiplier.
        for (int pass = 0; pass < 4; pass++) {
            player.updateWaterState(player.getCentreY() - 1);
            assertSubmergedWithFullSuperSpeeds(player);
        }
    }

    @Test
    void exitReentryAndReversionSelectTheirNativeBanksAndRewindRestoresTheWriter() throws Exception {
        var fixture = transformWhileSubmerged();
        var player = fixture.sprite();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            player.setYSpeed((short) 0);
            player.updateWaterState(player.getCentreY());
            assertFalse(player.isInWater());
            assertSpeeds(player, 0xA00, 0x30, 0x100);

            player.updateWaterState(player.getCentreY() - 1);
            assertTrue(player.isInWater());
            assertSpeeds(player, 0x500, 0x18, 0x80);
            player.getSuperStateController().debugDeactivate();
            assertEquals(SuperState.NORMAL, player.getSuperStateController().getState());
            assertTrue(player.isInWater());
            assertSpeeds(player, 0x300, 6, 0x40);

            registry.restore(before);
            assertEquals(SuperState.TRANSFORMING, player.getSuperStateController().getState());
            assertSubmergedWithFullSuperSpeeds(player);
            player.updateWaterState(player.getCentreY() - 1);
            assertSubmergedWithFullSuperSpeeds(player);
        }
    }
}

package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Positioned approach to the actual $B3/$2D exit, including its receiving load. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzExitHeadless {
    @AfterEach void reset() {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "tails", "knuckles"})
    void actualExitRequestsAndLoadsHiddenPalaceForEveryReachingCharacter(String character) {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short)0x3FC8, (short)0xD0).startPositionIsCentre().build();
        assertEquals(character, fixture.sprite().getCode());
        var loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int i = 0; i < 90 && !GameServices.level().isLevelInactiveForTransition(); i++) {
                fixture.stepFrame(false, false, false, true, false);
            }
            assertTrue(GameServices.level().isLevelInactiveForTransition(), "actual exit must request StartNewLevel");
            for (int i = 0; i < 120 && GameServices.level().getCurrentZone() == 9; i++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
            }
            assertEquals(0x16, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
        } finally {
            loop.closePresence();
        }
    }
}

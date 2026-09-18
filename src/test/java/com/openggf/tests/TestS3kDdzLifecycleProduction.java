package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.DdzFlightControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Doomsday ($C00) ring-out death and restart through the production loop. With no rings the powered form
 * ends ({@code Sonic_Super} drain), {@code sub_82742} sends the controller to {@code loc_8179E}, which
 * drops Player 1 until it passes {@code Camera_Y + $F0} and calls {@code Kill_Character}; the death
 * countdown then reloads the zone, whose {@code DDZ_ScreenInit} allocates a fresh controller.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzLifecycleProduction {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void ringOutEndsTheFormFallsAndReloadsDoomsday(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle()
                .build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var manager = GameServices.level().getObjectManager();
        var player = fixture.sprite();
        fixture.stepIdleFrames(60);
        assertTrue(player.isSuperSonic(), "transformed and released");

        player.setRingCount(0);
        int fallFrame = -1;
        for (int frame = 0; frame < 240 && !player.getDead(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (fallFrame < 0 && !player.isSuperSonic()) {
                fallFrame = frame;
            }
        }
        assertTrue(fallFrame >= 0, "Sonic_Super ends the form with no rings");
        assertTrue(player.getDead(), "loc_8179E calls Kill_Character below Camera_Y + $F0");
        assertTrue((player.getCentreY() & 0xFFFF) > ((GameServices.camera().getY() & 0xFFFF) + 0xF0),
                "the fall passes the camera bottom before the kill");

        var loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            boolean reloaded = false;
            for (int i = 0; i < 1200 && !reloaded; i++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                reloaded = GameServices.level().getObjectManager() != manager;
            }
            var p = GameServices.camera().getFocusedSprite();
            String where = "mode=" + loop.getCurrentGameMode() + " dead=" + p.getDead() + " y=" + Integer.toHexString(p.getCentreY() & 0xFFFF)
                    + " camY=" + Integer.toHexString(GameServices.camera().getY() & 0xFFFF) + " ctl=" + p.isObjectControlled()
                    + " inactive=" + GameServices.level().isLevelInactiveForTransition() + " yspd=" + p.getYSpeed()
                    + " lives=" + GameServices.gameState().getLives();
            assertTrue(reloaded, "the death countdown reaches the GameLoop reload: " + where);
            assertEquals(Sonic3kZoneIds.ZONE_DDZ, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            var focused = GameServices.camera().getFocusedSprite();
            assertFalse(focused.getDead());
            assertEquals(width, GameServices.camera().getWidth() & 0xFFFF);
            assertTrue(S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).isPresent(),
                    "the reload installs fresh Doomsday runtime words");
            assertEquals(1, GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o instanceof DdzFlightControllerObjectInstance).count(),
                    "DDZ_ScreenInit allocates exactly one flight controller");
        } finally {
            loop.closePresence();
        }
    }
}

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
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
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
        var input = new InputHandler();
        var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
        input.setLogicalOverride(
                com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            // Drive the live loop before ring-out so the history being discarded is real.
            for (int frame = 0; frame < 60; frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
            }
            assertTrue(player.isSuperSonic(), "transformed and released");
            var outgoingRewind = fixture.gameplayMode().getRewindController();
            assertTrue(outgoingRewind != null && outgoingRewind.currentFrame() > 10,
                    "flight built live rewind history before ring-out");

            player.setRingCount(0);
            int fallFrame = -1;
            for (int frame = 0; frame < 240 && !player.getDead(); frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                if (fallFrame < 0 && !player.isSuperSonic()) {
                    fallFrame = frame;
                }
            }
            assertTrue(fallFrame >= 0, "Sonic_Super ends the form with no rings");
            assertTrue(player.getDead(), "loc_8179E calls Kill_Character below Camera_Y + $F0");
            assertTrue((player.getCentreY() & 0xFFFF) > ((GameServices.camera().getY() & 0xFFFF) + 0xF0),
                    "the fall passes the camera bottom before the kill");

            boolean reloaded = false;
            int largestOutgoingFrame = outgoingRewind.currentFrame();
            for (int i = 0; i < 1200 && !reloaded; i++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                reloaded = GameServices.level().getObjectManager() != manager;
                var rewind = fixture.gameplayMode().getRewindController();
                if (reloaded) {
                    assertTrue(largestOutgoingFrame > 10,
                            "the outgoing death countdown built a real timeline to isolate");
                    assertTrue(rewind == null || rewind.currentFrame() < largestOutgoingFrame,
                            "Doomsday reload must discard the outgoing death timeline");
                } else if (rewind != null) {
                    largestOutgoingFrame = Math.max(largestOutgoingFrame, rewind.currentFrame());
                }
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
    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void endingRequestSavesProgressAndStartsAnIsolatedTimeline(int width) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle().build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var input = new InputHandler();
        var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
        input.setLogicalOverride(
                com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int frame = 0; frame < 60; frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
            }
            var rewind = fixture.gameplayMode().getRewindController();
            assertTrue(rewind != null && rewind.currentFrame() > 10,
                    "outgoing DDZ flight has live history");
            int outgoingFrame = rewind.currentFrame();
            var objects = GameServices.level().getObjectManager();
            // Isolate loc_81CA4 after the white fade. Full controller routes own the
            // preceding fight; this declared setup tests the real save/load boundary.
            var boss = new com.openggf.game.sonic3k.objects.DdzEndBossObjectInstance(
                    new com.openggf.level.objects.ObjectSpawn(0x500, 0x80, 0, 0, 0, false, 0));
            objects.addDynamicObject(boss);
            var services = org.mockito.Mockito.spy(TestEnvironment.objectServices());
            boss.setServices(services);
            var exit = boss.getClass().getDeclaredField("exitMode");
            exit.setAccessible(true);
            exit.setBoolean(boss, true);
            var routine = boss.getClass().getDeclaredField("routine");
            routine.setAccessible(true);
            routine.setInt(boss, 6);
            boolean loaded = false;
            for (int frame = 0; frame < 240 && !loaded; frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                loaded = GameServices.level().getObjectManager() != objects;
            }
            org.mockito.Mockito.verify(services).requestSessionSave(
                    com.openggf.game.save.SaveReason.PROGRESSION_SAVE);
            assertTrue(loaded, "loc_81CA4's ending request is consumed by GameLoop");
            assertEquals(0x0D, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            var endingRewind = fixture.gameplayMode().getRewindController();
            assertTrue(endingRewind == null || endingRewind.currentFrame() < outgoingFrame,
                    "the ending load cannot continue the outgoing DDZ timeline");
        } finally {
            loop.closePresence();
        }
    }

}

package com.openggf.configuration;

import com.openggf.game.launch.LiveSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.openggf.configuration.SettingTiming.NEXT_GAME;
import static com.openggf.configuration.SettingTiming.NOW;
import static com.openggf.configuration.SettingTiming.RESTART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSettingTiming {
    @TempDir Path directory;

    @AfterEach
    void forgetSession() {
        PendingRestart.clearForTest();
    }

    @Test
    void readersDecideWhenEachSettingTakesEffect() {
        // Read once at start-up: display initialisation and GameLoop's presence.
        for (SonicConfiguration key : List.of(SonicConfiguration.DISPLAY_SHADER_SELECTION,
                SonicConfiguration.DISPLAY_COLOR_PROFILE, SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,
                SonicConfiguration.LIVE_REWIND_VHS_EFFECT, SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP,
                SonicConfiguration.AUDIO_ENABLED)) {
            assertEquals(RESTART, SettingTiming.of(key), key.name());
        }
        // Read when a game starts: a new audio backend per launch, the team, the launch profile.
        for (SonicConfiguration key : List.of(SonicConfiguration.REGION, SonicConfiguration.AUDIO_FM_CORE,
                SonicConfiguration.MAIN_CHARACTER_CODE, SonicConfiguration.CROSS_GAME_SOURCE,
                SonicConfiguration.DISPLAY_ASPECT, SonicConfiguration.DISPLAY_HUD_ANCHOR,
                SonicConfiguration.LIVE_REWIND_ENABLED)) {
            assertEquals(NEXT_GAME, SettingTiming.of(key), key.name());
        }
        // Read when used.
        for (SonicConfiguration key : List.of(SonicConfiguration.FPS, SonicConfiguration.UP, SonicConfiguration.CONTROLLER_DEADZONE,
                SonicConfiguration.PAUSE_KEY, SonicConfiguration.SONIC_2_ROM, SonicConfiguration.CAPTURE_CODEC)) {
            assertEquals(NOW, SettingTiming.of(key), key.name());
        }
    }

    @Test
    void aPausedGameNeverWaitsOnARestartAndAlwaysTakesImmediateSettings() {
        for (SonicConfiguration key : ConfigCatalog.emitOrder()) {
            if (key == SonicConfiguration.DISPLAY_ASPECT) continue;
            if (key == SonicConfiguration.DISPLAY_HUD_ANCHOR) {
                assertFalse(LiveSettings.appliesOnResume(key, true),
                        "HUD anchoring is installed when a level loads, not when a paused game resumes");
            }
            if (SettingTiming.of(key) == RESTART) assertFalse(LiveSettings.appliesOnResume(key, true), key.name());
            if (SettingTiming.of(key) == NOW) assertTrue(LiveSettings.appliesOnResume(key, false), key.name());
        }
    }

    @Test
    void restartIsPendingOnlyForSavedChangesThatDifferFromStartUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        PendingRestart.captureAtStart(config);
        int width = config.getInt(SonicConfiguration.SCREEN_WIDTH);

        save(config, SonicConfiguration.CONTROLLER_DEADZONE, "0.3");
        assertTrue(PendingRestart.current(config).isEmpty(), "an immediate setting needs no restart");

        save(config, SonicConfiguration.FPS, "75");
        assertTrue(PendingRestart.current(config).isEmpty(), "display FPS applies without restart");
        save(config, SonicConfiguration.SCREEN_WIDTH, Integer.toString(width + 1));
        assertEquals(List.of(SonicConfiguration.SCREEN_WIDTH), PendingRestart.current(config));

        save(config, SonicConfiguration.SCREEN_WIDTH, Integer.toString(width));
        assertTrue(PendingRestart.current(config).isEmpty(), "saving the start-up value back needs no restart");
    }

    @Test
    void rowBadgesCompareDraftsToTheRunningValueEvenAfterSaving() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        PendingRestart.captureAtStart(config);
        var key = SonicConfiguration.SCREEN_WIDTH;
        int width = config.getInt(key);
        assertFalse(PendingRestart.awaiting(key, width, false));
        assertTrue(PendingRestart.awaiting(key, width + 1, true));
        assertFalse(PendingRestart.awaiting(key, width, true));
        save(config, key, Integer.toString(width + 1));
        assertTrue(PendingRestart.awaiting(key, width + 1, false), "saved still awaits restart");
        assertFalse(PendingRestart.awaiting(key, width, true), "draft reverting to running value clears badge");
        save(config, key, Integer.toString(width));
        assertFalse(PendingRestart.awaiting(key, width, false));
        assertFalse(PendingRestart.awaiting(SonicConfiguration.DISPLAY_ASPECT, "16:9", true));
    }

    @Test
    void engineWritesDoNotAskForARestart() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        PendingRestart.captureAtStart(config);

        config.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s3k"); // as every hub launch does

        assertTrue(PendingRestart.current(config).isEmpty());
    }

    @Test
    void withoutAnEngineStartNothingIsPending() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        assertTrue(PendingRestart.current(config).isEmpty());
    }

    private static void save(SonicConfigurationService config, SonicConfiguration key, String value) throws Exception {
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        draft.set(key, value);
        draft.apply();
    }
}

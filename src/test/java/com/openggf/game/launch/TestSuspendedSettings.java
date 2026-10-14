package com.openggf.game.launch;

import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSuspendedSettings {
    @TempDir Path directory;

    @Test
    void hubRunsOnSavedPreferencesWhileTheGameIsSuspended() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");

        SuspendedSettings.suspend(config);

        assertFalse(config.hasSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE),
                "the hub must not show the paused game's per-game overrides as preferences");
    }

    @Test
    void launchOnlySettingsSavedInTheHubWaitForTheNextLaunch() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        boolean audioBefore = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        save(config, SonicConfiguration.AUDIO_ENABLED, Boolean.toString(!audioBefore));
        paused.resume(config, SONIC_2, true);

        assertEquals(audioBefore, config.getBoolean(SonicConfiguration.AUDIO_ENABLED),
                "the audio backend was built at launch, so the resumed game keeps its value");
        config.clearSessionOverrides(); // the next launch, as MasterTitleLaunchCoordinator.prepareExit does
        assertEquals(!audioBefore, config.getBoolean(SonicConfiguration.AUDIO_ENABLED));
    }

    @Test
    void rewindAndDebugSavedInSettingsReachTheResumedGame() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        assertFalse(config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED), "fixture default");
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        save(config, SonicConfiguration.LIVE_REWIND_ENABLED, "true");
        save(config, SonicConfiguration.DEBUG_VIEW_ENABLED, "true");
        paused.resume(config, SONIC_2, false);

        assertTrue(config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
        assertTrue(config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED));
    }

    @Test
    void gameSetupForThePausedGameReachesItOnResume() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        LaunchProfileStore store = new LaunchProfileStore(config);
        store.save(SONIC_2, LaunchProfile.stockFor(SONIC_2).withNext(LaunchProfile.Row.DEBUG_TOOLS, SONIC_2));
        paused.resume(config, SONIC_2, false);

        assertTrue(config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED), "Debug tools: On for Sonic 2");
    }

    @Test
    void aspectChangesInALevelAndWaitsElsewhere() {
        SonicConfigurationService config = launchedAt("WIDE_16_9");
        int gameWidth = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        assertTrue(gameWidth > 320, "the widescreen fixture must change the width");

        SuspendedSettings paused = SuspendedSettings.suspend(config);
        assertEquals(320, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS), "the hub uses the saved 4:3");
        new LaunchProfileStore(config).save(SONIC_2,
                LaunchProfile.stockFor(SONIC_2).withNext(LaunchProfile.Row.WIDESCREEN, SONIC_2)); // 4:3 for Sonic 2

        paused.resume(config, SONIC_2, false);
        assertEquals(gameWidth, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                "outside a level the screens were sized on entry, so the aspect waits");

        SuspendedSettings again = SuspendedSettings.suspend(config);
        again.resume(config, SONIC_2, true);
        assertEquals(320, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS), "in a level the new aspect applies");
        assertFalse(config.hasSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                "derived picture sizes come from the aspect, never a pin of their own");
    }

    @Test
    void charactersStayAsTheGameWasStartedEvenWhenGameSetupChanged() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        paused.resume(config, SONIC_2, true); // the stored Sonic 2 profile says Sonic

        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                "the player object was built at launch; a new character waits for the next start");
    }

    @Test
    void unchangedSettingsAddNoOverrides() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        java.util.Set<SonicConfiguration> before = overridden(config);
        SuspendedSettings paused = SuspendedSettings.suspend(config);

        paused.resume(config, SONIC_2, true);

        assertEquals(before, paused.overridesForTest().keySet());
        assertEquals(before, overridden(config), "resume adds no overrides when nothing changed");
    }

    @Test
    void liveSettingsAreTheOnesTheirReadersTakeWithoutAReload() {
        assertTrue(LiveSettings.appliesOnResume(SonicConfiguration.LIVE_REWIND_ENABLED, false));
        assertTrue(LiveSettings.appliesOnResume(SonicConfiguration.DEBUG_VIEW_ENABLED, false));
        assertTrue(LiveSettings.appliesOnResume(SonicConfiguration.P1_A, false), "key bindings are read when pressed");
        assertTrue(LiveSettings.appliesOnResume(SonicConfiguration.DISPLAY_ASPECT, true));
        assertFalse(LiveSettings.appliesOnResume(SonicConfiguration.DISPLAY_ASPECT, false));
        for (SonicConfiguration launchOnly : new SonicConfiguration[] {
                SonicConfiguration.MAIN_CHARACTER_CODE, SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, SonicConfiguration.CROSS_GAME_SOURCE,
                SonicConfiguration.REGION, SonicConfiguration.AUDIO_ENABLED, SonicConfiguration.FPS,
                SonicConfiguration.SONIC_2_ROM}) {
            assertFalse(LiveSettings.appliesOnResume(launchOnly, true), launchOnly.name());
        }
    }

    /** A Sonic 2 launch with Game Setup's aspect, as MasterTitleLaunchCoordinator.prepareExit applies it. */
    private SonicConfigurationService launchedAt(String aspect) {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        LaunchProfileStore store = new LaunchProfileStore(config);
        LaunchProfile profile = LaunchProfile.stockFor(SONIC_2);
        while (!profile.aspect().equals(aspect)) profile = profile.withNext(LaunchProfile.Row.WIDESCREEN, SONIC_2);
        store.save(SONIC_2, profile);
        config.clearSessionOverrides();
        new LaunchProfileApplier(config).apply(store.load(SONIC_2), SONIC_2, store);
        config.resolveDisplayAspect();
        return config;
    }

    private static void save(SonicConfigurationService config, SonicConfiguration key, String value) throws Exception {
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        draft.set(key, value);
        draft.apply();
    }

    private static java.util.Set<SonicConfiguration> overridden(SonicConfigurationService config) {
        java.util.Set<SonicConfiguration> keys = java.util.EnumSet.noneOf(SonicConfiguration.class);
        for (SonicConfiguration key : SonicConfiguration.values()) if (config.hasSessionOverride(key)) keys.add(key);
        return keys;
    }
}

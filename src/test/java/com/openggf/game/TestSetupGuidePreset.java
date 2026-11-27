package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each play style writes exactly its listed all-games settings, through the real configuration file. */
class TestSetupGuidePreset {
    @TempDir Path dir;

    private SonicConfigurationService fresh() { return SonicConfigurationService.createStandalone(dir); }

    private SonicConfigurationService reloaded() { return SonicConfigurationService.createStandalone(dir); }

    @Test void balancedWritesSixteenByNineWithNativeWidescreenAndNothingElse() throws Exception {
        var config = fresh();
        assertEquals(List.of(SonicConfiguration.DISPLAY_ASPECT, SonicConfiguration.WIDESCREEN_DRAW_MODE),
                SetupGuidePreset.BALANCED.changes(config).stream().map(SetupGuidePreset.Change::key).toList());
        SetupGuidePreset.BALANCED.finish(config);
        var saved = reloaded();
        assertEquals("WIDE_16_9", saved.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals("NATIVE", saved.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE));
        assertEquals(SetupGuidePreset.DONE, saved.getString(SonicConfiguration.SETUP_GUIDE));
        assertFalse(saved.getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION), "interpolation keeps its value");
        assertTrue(saved.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED), "rewind keeps its default");
        assertEquals("act", saved.getString(SonicConfiguration.REWIND_BUDGET_SCOPE));
    }

    @Test void enhancedSharesBalancedFramingAndAddsInterpolationAndRewind() throws Exception {
        var config = fresh();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.REWIND_BUDGET_SCOPE, "off"));
        assertFalse(SetupGuidePreset.rewindOn(config));
        SetupGuidePreset.ENHANCED.finish(config);
        var saved = reloaded();
        assertEquals("WIDE_16_9", saved.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertEquals("NATIVE", saved.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE));
        assertTrue(saved.getBoolean(SonicConfiguration.DISPLAY_INTERPOLATION));
        assertTrue(saved.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED), "rewind turned back on");
        assertEquals("act", saved.getString(SonicConfiguration.REWIND_BUDGET_SCOPE), "the default scope returns");
    }

    @Test void enhancedKeepsAChosenRewindScope() throws Exception {
        var config = fresh();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.REWIND_BUDGET_SCOPE, "zone"));
        assertTrue(SetupGuidePreset.ENHANCED.changes(config).stream()
                .noneMatch(c -> c.key() == SonicConfiguration.REWIND_BUDGET_SCOPE));
        SetupGuidePreset.ENHANCED.finish(config);
        assertEquals("zone", reloaded().getString(SonicConfiguration.REWIND_BUDGET_SCOPE));
    }

    @Test void stockIsFourByThreeWithRewindOffAndTheOriginalTimingChoices() throws Exception {
        var config = fresh();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9"));
        SetupGuidePreset.STOCK.finish(config);
        var saved = reloaded();
        assertEquals("NATIVE_4_3", saved.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertFalse(saved.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
        assertEquals("off", saved.getString(SonicConfiguration.REWIND_BUDGET_SCOPE));
        assertTrue(saved.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT));
        assertEquals("accurate", saved.getString(SonicConfiguration.AUDIO_FM_CORE));
        assertEquals("PROFILED", saved.getString(SonicConfiguration.LOAD_TIME_SIMULATION));
        assertEquals("EDGE", saved.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE), "Stock never writes the draw mode");
    }

    @Test void keepAndMultiplayerWriteNoSettingsAndGameSetupProfilesAreNeverTouched() throws Exception {
        var config = fresh();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.DISPLAY_ASPECT, "ULTRA_21_9",
                SonicConfiguration.LAUNCH_S2_ASPECT, "NATIVE_4_3"));
        for (SetupGuidePreset style : List.of(SetupGuidePreset.KEEP, SetupGuidePreset.MULTIPLAYER)) {
            assertTrue(style.changes(config).isEmpty(), style + " is not a settings preset");
            style.finish(config);
            assertEquals("ULTRA_21_9", reloaded().getString(SonicConfiguration.DISPLAY_ASPECT));
        }
        SetupGuidePreset.ENHANCED.finish(config);
        assertEquals("NATIVE_4_3", reloaded().getString(SonicConfiguration.LAUNCH_S2_ASPECT), "per-game choice kept");
        for (SetupGuidePreset style : SetupGuidePreset.values())
            assertTrue(style.writes().keySet().stream().noneMatch(k -> k.name().startsWith("LAUNCH_")), style.name());
    }

    @Test void upgradeDiffListsOnlyTheValuesThatChange() throws Exception {
        var config = fresh();
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9"));
        var changes = SetupGuidePreset.BALANCED.changes(config);
        assertEquals(1, changes.size());
        assertEquals(new SetupGuidePreset.Change(SonicConfiguration.WIDESCREEN_DRAW_MODE, "EDGE", "NATIVE"), changes.getFirst());
    }

    @Test void skipRecordsOnlyTheGuideStateAndSettlesIt() throws Exception {
        var config = fresh();
        assertFalse(SetupGuidePreset.settled(config));
        SetupGuidePreset.skip(config);
        var saved = reloaded();
        assertEquals(SetupGuidePreset.SKIPPED, saved.getString(SonicConfiguration.SETUP_GUIDE));
        assertTrue(SetupGuidePreset.settled(saved));
        assertEquals("NATIVE_4_3", saved.getString(SonicConfiguration.DISPLAY_ASPECT));
        assertTrue(Files.readString(dir.resolve("config.yaml")).contains("setupGuide"));
    }
}

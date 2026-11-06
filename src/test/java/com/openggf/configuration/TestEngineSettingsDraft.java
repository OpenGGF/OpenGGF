package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static com.openggf.configuration.SonicConfiguration.*;
import static org.junit.jupiter.api.Assertions.*;

class TestEngineSettingsDraft {
    @TempDir Path directory;

    @Test
    void rewindSelectorPersistsOffAndOrderedScopesWithoutASecondSwitch() throws IOException {
        var config = service();
        var draft = new EngineSettingsDraft(config);
        assertEquals("act", draft.text(REWIND_BUDGET_SCOPE));
        assertFalse(draft.keys(EngineSettingsDraft.Category.GAMEPLAY).contains(LIVE_REWIND_ENABLED));
        assertTrue(draft.keys(EngineSettingsDraft.Category.GAMEPLAY).contains(REWIND_SPECIAL_STAGES));
        draft.set(REWIND_BUDGET_SCOPE, "off");
        draft.apply();
        assertFalse(config.getBoolean(LIVE_REWIND_ENABLED));
        assertEquals("off", new EngineSettingsDraft(service()).text(REWIND_BUDGET_SCOPE));
        draft.step(REWIND_BUDGET_SCOPE, 1);
        assertEquals("checkpoint", draft.text(REWIND_BUDGET_SCOPE));
        draft.apply();
        assertTrue(config.getBoolean(LIVE_REWIND_ENABLED));
        draft.set(REWIND_BUDGET_SECONDS, "unlimited");
        draft.apply();
        assertEquals("unlimited", service().getString(REWIND_BUDGET_SECONDS));
        assertThrows(IllegalArgumentException.class, () -> draft.set(REWIND_BUDGET_SECONDS, "0"));
    }

    @Test
    void legacyDisabledRewindRemainsOffUntilSelectorChanges() throws IOException {
        var config = service();
        config.setConfigValue(LIVE_REWIND_ENABLED, false);
        var draft = new EngineSettingsDraft(config);
        assertEquals("off", draft.text(REWIND_BUDGET_SCOPE));
        assertFalse(draft.dirty());
        draft.set(REWIND_BUDGET_SCOPE, "zone");
        draft.apply();
        assertTrue(config.getBoolean(LIVE_REWIND_ENABLED));
        assertEquals("zone", config.getString(REWIND_BUDGET_SCOPE));
    }

    @Test
    void colourExpansionHasItsOwnSavedDisplayAxisAndCancelsWithoutChangingProfile() throws IOException {
        SonicConfigurationService config = service();
        assertEquals("FULL", config.getDefaultValue(DISPLAY_COLOR_EXPANSION));
        var draft = new EngineSettingsDraft(config);
        assertTrue(draft.keys(EngineSettingsDraft.Category.DISPLAY).contains(DISPLAY_COLOR_EXPANSION));
        draft.set(DISPLAY_COLOR_EXPANSION, "LIMITED");
        assertEquals("FULL", config.getString(DISPLAY_COLOR_EXPANSION));
        assertEquals("FULL", new EngineSettingsDraft(config).text(DISPLAY_COLOR_EXPANSION));
        draft.apply();
        assertEquals("LIMITED", service().getString(DISPLAY_COLOR_EXPANSION));
        assertEquals("RAW_RGB", service().getString(DISPLAY_COLOR_PROFILE));
        draft.reset(DISPLAY_COLOR_EXPANSION);
        draft.apply();
        assertEquals("FULL", service().getString(DISPLAY_COLOR_EXPANSION));
    }

    @Test
    void presentationRevisionChangesOnlyWithDraftOrSavedState() throws IOException {
        EngineSettingsDraft draft = new EngineSettingsDraft(service());
        long initial = draft.revision();
        assertSame(draft.keys(EngineSettingsDraft.Category.INPUT), draft.keys(EngineSettingsDraft.Category.INPUT));
        draft.set(AUDIO_ENABLED, draft.text(AUDIO_ENABLED));
        assertEquals(initial, draft.revision());
        draft.step(AUDIO_ENABLED, 1);
        assertTrue(draft.revision() > initial);
        long edited = draft.revision();
        assertTrue(draft.changed(AUDIO_ENABLED));
        assertTrue(draft.dirty());
        draft.apply();
        assertTrue(draft.revision() > edited);
        assertFalse(draft.dirty());
        assertFalse(draft.changed(AUDIO_ENABLED));
        assertTrue(draft.nonDefault(AUDIO_ENABLED));
    }

    @Test
    void everyPersistedPreferenceHasExactlyOneVisibleCategory() {
        EngineSettingsDraft draft = new EngineSettingsDraft(service());
        Set<SonicConfiguration> reachable = new HashSet<>();
        for (EngineSettingsDraft.Category category : EngineSettingsDraft.Category.values()) {
            assertFalse(draft.keys(category).isEmpty(), category.name());
            for (SonicConfiguration key : draft.keys(category)) assertTrue(reachable.add(key), key.name());
        }
        assertEquals(new HashSet<>(ConfigCatalog.emitOrder().stream().filter(key -> key != LIVE_REWIND_ENABLED && !ConfigCatalog.meta(key).section().equals("hub")).toList()), reachable);
        assertFalse(reachable.contains(SCREEN_WIDTH_PIXELS));
        assertFalse(reachable.contains(SCREEN_HEIGHT_PIXELS));
    }

    @Test
    void cancelDoesNotMutatePreferencesOrCreateAFile() throws IOException {
        SonicConfigurationService config = service();
        boolean before = config.getBoolean(AUDIO_ENABLED);
        Path file = directory.resolve("config.yaml");
        String beforeFile = Files.exists(file) ? Files.readString(file) : null;
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        draft.set(AUDIO_ENABLED, Boolean.toString(!before));
        assertTrue(draft.dirty());
        assertEquals(before, config.getBoolean(AUDIO_ENABLED));
        assertEquals(beforeFile, Files.exists(file) ? Files.readString(file) : null);
        assertFalse(new EngineSettingsDraft(config).dirty());
    }

    @Test
    void applyingPersistsOnlyChangesAndNonDefaultRemainsAmberAfterSave() throws IOException {
        SonicConfigurationService config = service();
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        boolean defaultAudio = (Boolean) config.getDefaultValue(AUDIO_ENABLED);
        draft.set(AUDIO_ENABLED, Boolean.toString(!defaultAudio));
        assertTrue(draft.nonDefault(AUDIO_ENABLED));
        draft.apply();
        assertFalse(draft.dirty());
        assertTrue(draft.nonDefault(AUDIO_ENABLED));
        SonicConfigurationService reloaded = service();
        assertEquals(!defaultAudio, reloaded.getBoolean(AUDIO_ENABLED));
        assertFalse(Files.readString(directory.resolve("config.yaml")).contains("shaderLibraryRoot"));
        draft.reset(AUDIO_ENABLED);
        assertFalse(draft.nonDefault(AUDIO_ENABLED));
        assertTrue(draft.dirty());
    }

    @Test
    void sessionAndDerivedOverridesAreNotWrittenBackAsPreferences() throws IOException {
        SonicConfigurationService config = service();
        config.setConfigValue(DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(DISPLAY_ASPECT, "WIDE_16_9");
        config.resolveDisplayAspect();
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        assertEquals("NATIVE_4_3", draft.text(DISPLAY_ASPECT));
        draft.step(AUDIO_ENABLED, 1);
        draft.apply();
        assertEquals("NATIVE_4_3", service().getString(DISPLAY_ASPECT));
        assertEquals("WIDE_16_9", config.getString(DISPLAY_ASPECT));
        assertFalse(Files.readString(directory.resolve("config.yaml")).contains("widthPixels"));
    }

    @Test
    void saveFailureKeepsLivePreferencesUntouchedAndDraftAvailableForRetry() throws IOException {
        SonicConfigurationService config = service();
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        boolean before = config.getBoolean(AUDIO_ENABLED);
        draft.set(AUDIO_ENABLED, Boolean.toString(!before));
        Path target = directory.resolve("config.yaml");
        Files.deleteIfExists(target);
        Files.createDirectory(target);
        assertThrows(IOException.class, draft::apply);
        assertEquals(before, config.getBoolean(AUDIO_ENABLED));
        assertTrue(draft.dirty());
        Files.delete(target);
        draft.apply();
        assertFalse(draft.dirty());
        assertEquals(!before, service().getBoolean(AUDIO_ENABLED));
    }

    @Test
    void invalidTypedValuesNeverEnterTheDraftOrLiveConfiguration() {
        SonicConfigurationService config = service();
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        assertThrows(IllegalArgumentException.class, () -> draft.set(FPS, "0"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(FPS, "1000000001"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(FPS, "2.5"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(CONTROLLER_DEADZONE, "NaN"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(CONTROLLER_DEADZONE, "1"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(DISPLAY_ASPECT, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(P1_A, "not a key"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(SCREEN_WIDTH_PIXELS, "500"));
        assertFalse(draft.dirty());
    }

    @Test
    void crossFieldValidationRunsBeforeSaving() {
        SonicConfigurationService config = service();
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        draft.set(LIVE_REWIND_TAPE_COAST_MIN_STEPS, "1000");
        draft.set(LIVE_REWIND_TAPE_COAST_MAX_STEPS, "2");
        assertThrows(IllegalArgumentException.class, draft::apply);
        assertNotEquals(1000, config.getDouble(LIVE_REWIND_TAPE_COAST_MIN_STEPS));
        assertTrue(draft.dirty());
    }

    @Test
    void keyNamesAndCodesHaveEquivalentDefaultStatusAndChordsRoundTrip() throws IOException {
        SonicConfigurationService config = service();
        config.setConfigValue(P1_A, config.getInt(P1_A));
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        assertFalse(draft.nonDefault(P1_A));
        draft.set(P1_A, "CTRL+SHIFT+K");
        draft.apply();
        assertEquals(KeyChord.parse("CTRL+SHIFT+K"), service().getKeyChord(P1_A));
        draft.set(P1_A, "");
        draft.apply();
        assertFalse(service().getKeyChord(P1_A).isBound());
    }

    @Test
    void tabResetPreservesOtherTabsAndHiddenHubHistory() {
        SonicConfigurationService config = service();
        config.setConfigValue(AUDIO_HUB_MUSIC_VOLUME, 35);
        config.setConfigValue(HUB_CONTINUE_S1_ZONE, 3);
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        draft.set(P1_A, "K");
        draft.reset(EngineSettingsDraft.Category.AUDIO);
        assertEquals("20", draft.text(AUDIO_HUB_MUSIC_VOLUME));
        assertEquals("K", draft.text(P1_A));
        draft.reset(EngineSettingsDraft.Category.ADVANCED);
        assertEquals("3", draft.text(HUB_CONTINUE_S1_ZONE));
        assertEquals("35", draft.changes().stream().filter(c -> c.key() == AUDIO_HUB_MUSIC_VOLUME).findFirst().orElseThrow().before());
        assertThrows(IllegalArgumentException.class, () -> draft.set(AUDIO_HUB_MUSIC_VOLUME, "101"));
    }

    @Test
    void duplicateChordsWarnAcrossPlayersAndShortcutsButRemainEditable() {
        EngineSettingsDraft draft = new EngineSettingsDraft(service());
        draft.set(P1_A, "CTRL+K");
        draft.set(P2_A, "CTRL+K");
        assertTrue(draft.bindingConflicts(P1_A).contains(P2_A));
        draft.set(P2_A, "");
        assertFalse(draft.bindingConflicts(P1_A).contains(P2_A));
        assertTrue(draft.bindingConflicts(P2_A).isEmpty());
    }

    @Test
    void displayModesAreEditableImmediatePreferencesAndSurviveReload() throws IOException {
        SonicConfigurationService config = service();
        EngineSettingsDraft draft = new EngineSettingsDraft(config);
        assertEquals("WINDOWED", draft.text(DISPLAY_WINDOW_MODE));
        assertEquals("INTEGER", draft.text(DISPLAY_SCALING));
        assertTrue(draft.keys(EngineSettingsDraft.Category.DISPLAY).contains(DISPLAY_WINDOW_MODE));
        assertTrue(draft.keys(EngineSettingsDraft.Category.DISPLAY).contains(DISPLAY_SCALING));
        assertEquals(SettingTiming.NOW, SettingTiming.of(DISPLAY_WINDOW_MODE));
        assertEquals(SettingTiming.NOW, SettingTiming.of(DISPLAY_SCALING));
        assertThrows(IllegalArgumentException.class, () -> draft.set(DISPLAY_WINDOW_MODE, "invalid"));
        assertThrows(IllegalArgumentException.class, () -> draft.set(DISPLAY_SCALING, "STRETCH"));

        for (String mode : new String[] { "BORDERLESS", "FULLSCREEN", "WINDOWED" }) {
            draft.set(DISPLAY_WINDOW_MODE, mode);
            draft.set(DISPLAY_SCALING, "FIT");
            draft.apply();
            SonicConfigurationService reloaded = service();
            assertEquals(mode, reloaded.getString(DISPLAY_WINDOW_MODE));
            assertEquals("FIT", reloaded.getString(DISPLAY_SCALING));
        }
        draft.reset(EngineSettingsDraft.Category.DISPLAY);
        draft.apply();
        assertEquals("INTEGER", service().getString(DISPLAY_SCALING));
    }

    private SonicConfigurationService service() {
        return SonicConfigurationService.createStandalone(directory);
    }
}

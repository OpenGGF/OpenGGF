package com.openggf.game;

import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.ConfigType;
import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SettingTiming;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.InputBindingFactory;
import com.openggf.control.InputHandler;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.openggf.configuration.SonicConfiguration.UI_LANGUAGE;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestUiLanguageSettings {
    @TempDir Path directory;

    @AfterEach
    void restoreEnglish() { UiMessages.selectLanguage("en"); }

    @Test
    void ffmpegHelpDisplaysLiteralTemplateTokensWithoutRuntimeArgumentsInEveryLanguage() {
        var examples = java.util.Map.of(
                SonicConfiguration.CAPTURE_FFMPEG_PASS1_ARGS,
                java.util.List.of("width", "height", "fps", "scale", "scaledWidth", "scaledHeight", "videoCodecArgs", "videoOut"),
                SonicConfiguration.CAPTURE_FFMPEG_PASS2_ARGS,
                java.util.List.of("videoIn", "audioIn", "sampleRate", "audioCodecArgs", "output"));
        for (var language : UiMessages.languages()) {
            UiMessages.selectLanguage(language.tag());
            examples.forEach((key, tokens) -> {
                String help = assertDoesNotThrow(() -> EngineSettingLabels.description(key),
                        () -> language.tag() + " " + key);
                for (String token : tokens)
                    assertTrue(help.contains("{" + token + "}"),
                            () -> language.tag() + " " + key + " lost literal token " + token);
            });
        }
    }

    @Test
    void languageIsAnImmediateStringPreferenceWhoseDefaultFollowsTheHost() {
        var config = SonicConfigurationService.createStandalone(directory);
        assertEquals("auto", config.getString(UI_LANGUAGE));
        assertEquals("ui.language", ConfigCatalog.meta(UI_LANGUAGE).path());
        assertEquals(ConfigType.STRING, ConfigCatalog.meta(UI_LANGUAGE).type());
        assertEquals(SettingTiming.NOW, SettingTiming.of(UI_LANGUAGE));
        assertTrue(new EngineSettingsDraft(config).keys(EngineSettingsDraft.Category.DISPLAY).contains(UI_LANGUAGE));
    }

    @Test
    void editingOrCancellingDoesNotActivateAndSuccessfulSaveSwitchesWithoutAJvmLocaleChange() throws Exception {
        UiMessages.selectLanguage("en");
        Locale processLocale = Locale.getDefault();
        Locale displayLocale = Locale.getDefault(Locale.Category.DISPLAY);
        var config = SonicConfigurationService.createStandalone(directory);
        var draft = new EngineSettingsDraft(config);
        long revision = UiMessages.revision();
        draft.set(UI_LANGUAGE, "pt-br");
        assertEquals("pt-BR", draft.text(UI_LANGUAGE));
        assertEquals("auto", config.getString(UI_LANGUAGE));
        assertEquals(Locale.ENGLISH, UiMessages.locale());
        assertEquals(revision, UiMessages.revision());
        assertFalse(new EngineSettingsDraft(config).dirty(), "discarding a draft creates no saved language");

        UiLanguageSettings.apply(draft);
        assertEquals("pt-BR", SonicConfigurationService.createStandalone(directory).getString(UI_LANGUAGE));
        assertEquals("pt-BR", UiMessages.locale().toLanguageTag());
        assertEquals(UiMessages.locale(), HostTextContext.current().locale());
        assertTrue(UiMessages.revision() > revision);
        assertEquals(processLocale, Locale.getDefault());
        assertEquals(displayLocale, Locale.getDefault(Locale.Category.DISPLAY));
        assertTrue(Files.readString(directory.resolve("config.yaml")).contains("language: \"pt-BR\""));
    }

    @Test
    void failedSaveLeavesLanguageAndReadersUntouchedAndTheSameDraftCanRetry() throws Exception {
        UiMessages.selectLanguage("en");
        var config = SonicConfigurationService.createStandalone(directory);
        var draft = new EngineSettingsDraft(config);
        draft.set(UI_LANGUAGE, "ja");
        Path target = directory.resolve("config.yaml");
        Files.deleteIfExists(target);
        Files.createDirectory(target);
        long revision = UiMessages.revision();
        assertThrows(IOException.class, () -> UiLanguageSettings.apply(draft));
        assertEquals("auto", config.getString(UI_LANGUAGE));
        assertEquals(Locale.ENGLISH, UiMessages.locale());
        assertEquals(revision, UiMessages.revision());
        assertTrue(draft.dirty());
        Files.delete(target);
        UiLanguageSettings.apply(draft);
        assertEquals("ja", UiMessages.locale().toLanguageTag());
        assertFalse(draft.dirty());
    }

    @Test
    void startupUsesTheSavedPreferenceWithoutARomAndInvalidTagsNeverEnterTheDraft() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        UiLanguageSettings.save(config, "zh-Hant");
        UiMessages.selectLanguage("en");
        UiLanguageSettings.activateSaved(SonicConfigurationService.createStandalone(directory));
        assertEquals("zh-Hant", UiMessages.locale().toLanguageTag());
        var draft = new EngineSettingsDraft(config);
        assertThrows(IllegalArgumentException.class, () -> draft.set(UI_LANGUAGE, "zh_Hant"));
        assertEquals("zh-Hant", draft.text(UI_LANGUAGE));
        draft.set(UI_LANGUAGE, "AUTO");
        assertEquals("auto", draft.text(UI_LANGUAGE));
        draft.set(UI_LANGUAGE, "fr-CA");
        assertEquals("fr-CA", draft.text(UI_LANGUAGE), "valid tags are not a closed catalog enum");
    }

    @Test
    void theSettingsPickerEditsTheDraftAndTheVisibleSaveSwitchesLanguage() {
        UiMessages.selectLanguage("en");
        var config = SonicConfigurationService.createStandalone(directory);
        var screen = new EngineSettingsScreen(config, null);
        var input = new InputHandler(InputBindingFactory.supplier(config));
        int row = new EngineSettingsDraft(config).keys(EngineSettingsDraft.Category.DISPLAY).indexOf(UI_LANGUAGE);
        assertTrue(row >= 0);
        for (int i = 0; i < row; i++) press(screen, input, GLFW_KEY_DOWN);
        press(screen, input, GLFW_KEY_ENTER);
        int japanese = 1 + java.util.stream.IntStream.range(0, UiMessages.languages().size())
                .filter(i -> UiMessages.languages().get(i).tag().equals("ja")).findFirst().orElseThrow();
        for (int i = 0; i < japanese; i++) press(screen, input, GLFW_KEY_DOWN);
        press(screen, input, GLFW_KEY_ENTER);
        assertEquals("auto", config.getString(UI_LANGUAGE));
        assertEquals("en", UiMessages.locale().toLanguageTag());
        press(screen, input, GLFW_KEY_ESCAPE);
        press(screen, input, GLFW_KEY_ENTER);
        assertEquals("ja", config.getString(UI_LANGUAGE));
        assertEquals("ja", UiMessages.locale().toLanguageTag());
        assertTrue(screen.consumeApplied());
    }

    @Test
    void activationUsesTheSavedDraftRatherThanARunningGameOverride() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setSessionOverride(UI_LANGUAGE, "de");
        var draft = new EngineSettingsDraft(config);
        assertEquals("auto", draft.text(UI_LANGUAGE));
        draft.set(UI_LANGUAGE, "ja");
        UiLanguageSettings.apply(draft);
        assertEquals("de", config.getString(UI_LANGUAGE), "the running game's overlay has its own owner");
        assertEquals("ja", UiMessages.locale().toLanguageTag());
        assertEquals("ja", SonicConfigurationService.createStandalone(directory).getString(UI_LANGUAGE));
    }

    @Test
    void malformedSavedTagsFallBackAtBootWithoutRewritingThePreference() {
        UiMessages.selectLanguage("auto");
        Locale systemLanguage = UiMessages.locale();
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(UI_LANGUAGE, "not_a_tag");
        UiMessages.selectLanguage("ja");
        UiLanguageSettings.activateSaved(config);
        assertEquals(systemLanguage, UiMessages.locale());
        assertEquals("not_a_tag", config.getString(UI_LANGUAGE));
    }

    private static void press(EngineSettingsScreen screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        screen.update(input);
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.refreshLogicalSnapshot();
        screen.update(input);
        input.update();
    }
}

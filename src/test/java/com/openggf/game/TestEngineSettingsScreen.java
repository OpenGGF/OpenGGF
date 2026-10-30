package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.configuration.SonicConfiguration.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestEngineSettingsScreen {
    @TempDir Path directory;

    private static final int DISPLAY = 0, SOUND = 1, CONTROLS = 2, GAMEPLAY = 3, FILES = 4, CAPTURE = 5;

    @Test
    void spriteScanlineLimitIsEditableAndPersistsOnTheDisplayPage() {
        var key = ConfigCatalog.byPath("display.spriteScanlineLimit");
        assertNotNull(key, "sprite scanline limit must be a persisted setting");
        Fixture f = new Fixture();
        assertFalse(f.config.getBoolean(key), "normal play defaults to unrestricted scanlines");
        f.tab(DISPLAY);
        int row = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.DISPLAY).indexOf(key);
        assertTrue(row >= 0);
        f.focus(key);
        assertTrue(f.texts().contains("Sprite scanline limit"));
        assertEquals("PICTURE", EngineSettingLabels.group(key));
        f.press(GLFW_KEY_RIGHT);
        assertFalse(f.config.getBoolean(key), "the draft must not change the live preference");
        f.assertTextBounds(320);
        f.assertTextBounds(400);
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.screen.consumeApplied());
        assertTrue(f.config.getBoolean(key));
        assertTrue(SonicConfigurationService.createStandalone(directory).getBoolean(key));
        assertEquals(com.openggf.configuration.SettingTiming.NOW,
                com.openggf.configuration.SettingTiming.of(key));
    }

    @Test
    void personalMultiplayerChatChoicesAreVisibleOnGameplayAndApplyWhenSaved() {
        Fixture f = new Fixture();
        f.tab(GAMEPLAY);
        var keys = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.GAMEPLAY);
        int censorRow = keys.indexOf(MULTIPLAYER_CENSOR_PROFANITY);
        int disableRow = keys.indexOf(MULTIPLAYER_CHAT_DISABLED);
        assertTrue(censorRow >= 0 && disableRow > censorRow);
        f.press(GLFW_KEY_DOWN, censorRow);
        assertTrue(f.texts().contains("Censor profanity"));
        f.press(GLFW_KEY_RIGHT);
        assertTrue(f.config.getBoolean(MULTIPLAYER_CENSOR_PROFANITY), "edits stay in the draft");
        f.press(GLFW_KEY_DOWN, disableRow - censorRow);
        assertTrue(f.texts().contains("Disable multiplayer chat"));
        f.press(GLFW_KEY_RIGHT);
        assertFalse(f.config.getBoolean(MULTIPLAYER_CHAT_DISABLED), "edits stay in the draft");
        assertFalse(f.texts().contains("RESTART"));
        f.assertTextBounds(320);
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.screen.consumeApplied());
        assertFalse(f.config.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertTrue(f.config.getBoolean(MULTIPLAYER_CHAT_DISABLED));
        var reloaded = SonicConfigurationService.createStandalone(directory);
        assertFalse(reloaded.getBoolean(MULTIPLAYER_CENSOR_PROFANITY));
        assertTrue(reloaded.getBoolean(MULTIPLAYER_CHAT_DISABLED));
    }

    @Test
    void widescreenHudAnchorHasReadableChoiceLabels() {
        assertEquals("HUD position", EngineSettingLabels.label(DISPLAY_HUD_ANCHOR));
        assertEquals("Native (centred)", EngineSettingLabels.value(DISPLAY_HUD_ANCHOR, "DEFAULT"));
        assertEquals("Screen left edge", EngineSettingLabels.value(DISPLAY_HUD_ANCHOR, "LEFT_EDGE"));
    }

    @Test
    void tappingAModifierCanCaptureItAsAPlayerButton() {
        Fixture f = new Fixture();
        f.tab(CONTROLS);
        int buttonRow = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.INPUT)
                .stream().filter(key -> ConfigCatalog.meta(key).section().equals("input.player1"))
                .toList().indexOf(P1_A);
        f.press(GLFW_KEY_DOWN, buttonRow);
        f.press(GLFW_KEY_ENTER); // Binding menu.
        f.press(GLFW_KEY_ENTER); // Capture.
        f.press(GLFW_KEY_RIGHT_SHIFT); // A supported player-button key (P2 A's stock binding).
        assertFalse(f.texts().contains("Press a key or chord"),
                "releasing a tapped modifier must finish capture rather than wait forever");
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER); // Save.
        assertEquals("RIGHT_SHIFT", f.config.getString(P1_A), "the modifier must not qualify itself");
        assertEquals(GLFW_KEY_RIGHT_SHIFT, f.config.getInt(P1_A));
        assertEquals(GLFW_KEY_RIGHT_SHIFT,
                SonicConfigurationService.createStandalone(directory).getInt(P1_A));
    }

    @Test
    void heldModifiersStillCaptureChordsAndCancelledModifierTapsAreForgotten() {
        Fixture f = new Fixture();
        f.tab(CONTROLS);
        int buttonRow = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.INPUT)
                .stream().filter(key -> ConfigCatalog.meta(key).section().equals("input.player1"))
                .toList().indexOf(P1_A);
        f.press(GLFW_KEY_DOWN, buttonRow);
        f.press(GLFW_KEY_ENTER);
        f.press(GLFW_KEY_ENTER);
        f.input.handleKeyEvent(GLFW_KEY_RIGHT_SHIFT, GLFW_PRESS);
        f.frame();
        assertTrue(f.texts().contains("Press a key or chord"), "a held modifier may begin a chord");
        f.press(GLFW_KEY_ESCAPE); // Cancel while the modifier is still held.
        f.input.handleKeyEvent(GLFW_KEY_RIGHT_SHIFT, GLFW_RELEASE);
        f.frame();
        f.press(GLFW_KEY_ENTER); // Restart capture, without the cancelled modifier.
        assertTrue(f.texts().contains("Press a key or chord"));
        f.input.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        f.frame();
        f.press(GLFW_KEY_K);
        f.input.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_RELEASE);
        f.frame();
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER);
        assertEquals("CTRL+K", f.config.getString(P1_A));
        assertEquals("CTRL+K", SonicConfigurationService.createStandalone(directory).getString(P1_A));
    }

    @Test
    void colourExpansionOffersFullAndLimitedAndSavesFromTheDisplayPage() {
        Fixture f = new Fixture();
        String profile = f.config.getString(DISPLAY_COLOR_PROFILE);
        assertEquals(EngineSettingsDraft.Category.DISPLAY, EngineSettingsDraft.category(DISPLAY_COLOR_EXPANSION));
        f.focus(DISPLAY_COLOR_EXPANSION);
        assertTrue(f.texts().contains("Colour expansion"));
        assertTrue(f.texts().contains("Full"));
        f.press(GLFW_KEY_RIGHT); // Full -> Limited in the draft only
        assertTrue(f.texts().contains("Limited"));
        assertTrue(f.texts().contains("1 UNSAVED"));
        assertEquals("FULL", f.config.getString(DISPLAY_COLOR_EXPANSION), "edits stay in the draft");
        f.press(GLFW_KEY_ESCAPE);
        assertTrue(f.texts().contains("SAVE SETTINGS?"));
        f.press(GLFW_KEY_ENTER); // Save is the default choice.
        assertTrue(f.screen.consumeApplied());
        assertEquals("LIMITED", SonicConfigurationService.createStandalone(directory).getString(DISPLAY_COLOR_EXPANSION));
        assertEquals(profile, f.config.getString(DISPLAY_COLOR_PROFILE), "the colour profile is independent");
    }

    @Test
    void backWithACleanDraftClosesOnce() {
        Fixture f = new Fixture();
        f.press(GLFW_KEY_ESCAPE);
        assertTrue(f.screen.consumeCloseRequested());
        assertFalse(f.screen.consumeCloseRequested(), "Close is consumed once");
    }

    @Test
    void upFromTheFirstRowReachesTheTabsAndLeftRightSwitchTabs() {
        Fixture f = new Fixture();
        assertTrue(f.texts().contains("Aspect ratio"), "Settings opens on Display rows");
        f.press(GLFW_KEY_UP);
        f.press(GLFW_KEY_RIGHT);
        assertTrue(f.texts().contains("Music and sound effects"), "Right on the tabs shows Sound");
        assertTrue(f.texts().contains("TAB"), "the footer says the arrows switch tabs");
        f.press(GLFW_KEY_DOWN);
        f.press(GLFW_KEY_RIGHT);
        assertTrue(f.texts().contains("1 UNSAVED"), "Right on a row changes its value in the draft");
    }

    @Test
    void dirtyBackAsksSaveDiscardOrKeepEditing() {
        Fixture f = new Fixture();
        boolean original = f.config.getBoolean(AUDIO_ENABLED);
        f.tab(SOUND);
        f.press(GLFW_KEY_RIGHT);
        f.press(GLFW_KEY_ESCAPE);
        assertTrue(f.texts().contains("SAVE SETTINGS?"));
        f.press(GLFW_KEY_ESCAPE); // Back from the prompt keeps editing.
        assertFalse(f.screen.consumeCloseRequested());
        assertFalse(f.texts().contains("SAVE SETTINGS?"));
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_RIGHT); // Discard
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.screen.consumeCloseRequested());
        assertFalse(f.screen.consumeApplied());
        assertEquals(original, f.config.getBoolean(AUDIO_ENABLED));
        assertEquals(original, SonicConfigurationService.createStandalone(directory).getBoolean(AUDIO_ENABLED));
    }

    @Test
    void saveFromThePromptPersistsTheDraftAndSignalsOnce() {
        Fixture f = new Fixture();
        boolean original = f.config.getBoolean(AUDIO_ENABLED);
        f.tab(SOUND);
        f.press(GLFW_KEY_RIGHT);
        assertEquals(original, f.config.getBoolean(AUDIO_ENABLED), "edits stay in the draft");
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER); // Save is the default choice.
        assertTrue(f.screen.consumeApplied());
        assertFalse(f.screen.consumeApplied());
        assertTrue(f.screen.consumeCloseRequested());
        assertEquals(!original, SonicConfigurationService.createStandalone(directory).getBoolean(AUDIO_ENABLED));
    }

    @Test
    void visibleSaveActionPersistsWithoutTheBackDialog() {
        Fixture f = new Fixture();
        f.tab(SOUND);
        boolean original = f.config.getBoolean(AUDIO_ENABLED);
        f.press(GLFW_KEY_RIGHT);
        assertTrue(f.texts().contains("SAVE CHANGES"));
        f.press(GLFW_KEY_UP); // tabs
        f.press(GLFW_KEY_UP); // save action
        assertTrue(f.descriptionLines().contains("Save your changes and return to the menu."));
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.screen.consumeApplied());
        assertTrue(f.screen.consumeCloseRequested());
        assertEquals(!original, SonicConfigurationService.createStandalone(directory).getBoolean(AUDIO_ENABLED));
    }

    @Test
    void hoveringSaveAfterResetMakesKeyboardConfirmSave() {
        Fixture f = new Fixture();
        f.tab(SOUND);
        boolean original = f.config.getBoolean(AUDIO_ENABLED);
        f.press(GLFW_KEY_RIGHT);
        f.screen.updatePointer(new MenuInput.Pointer(310, 160, true, false, false));
        f.screen.updatePointer(new MenuInput.Pointer(250, 12, true, false, false));
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.screen.consumeApplied());
        assertTrue(f.screen.consumeCloseRequested());
        assertEquals(!original, SonicConfigurationService.createStandalone(directory).getBoolean(AUDIO_ENABLED));
    }

    @Test
    void controllerCanReachSaveAndReturnToEditing() {
        Fixture f = new Fixture();
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_UP);
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_UP);
        assertTrue(f.descriptionLines().contains("Save your changes and return to the menu."));
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        assertEquals(List.of(EngineSettingLabels.description(DISPLAY_ASPECT)), f.descriptionLines());
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_UP);
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_UP);
        f.padPress(GLFW_GAMEPAD_BUTTON_A);
        assertTrue(f.screen.consumeApplied());
        assertTrue(f.screen.consumeCloseRequested());
    }

    @Test
    void physicalTextEditorCanReplaceAPathAndCancelAnotherEdit() {
        Fixture f = new Fixture();
        f.tab(FILES);
        assertTrue(f.texts().contains("Sonic 1 ROM file"));
        assertFalse(f.texts().contains("roms.sonic1"), "Human labels are primary");
        f.press(GLFW_KEY_ENTER);
        f.press(GLFW_KEY_HOME);
        f.press(GLFW_KEY_DELETE, f.config.getString(SONIC_1_ROM).length());
        f.type("A");
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().contains("A"));
        assertTrue(f.texts().contains("1 UNSAVED"));
        f.press(GLFW_KEY_ENTER);
        f.press(GLFW_KEY_BACKSPACE);
        f.press(GLFW_KEY_ESCAPE);
        assertTrue(f.texts().contains("A"), "Cancel editing preserves the previous draft value");
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER);
        assertEquals("A", SonicConfigurationService.createStandalone(directory).getString(SONIC_1_ROM));
    }

    @Test
    void choicesShowFriendlyValuesAndCommitOnlyToTheDraft() {
        Fixture f = new Fixture();
        f.tab(SOUND);
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().contains("On"));
        assertTrue(f.texts().contains("Off"));
        assertTrue(f.texts().contains("Restore default"));
        f.press(GLFW_KEY_UP);
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().contains("Off"));
        assertTrue(f.texts().contains("1 UNSAVED"));
        assertTrue(f.config.getBoolean(AUDIO_ENABLED));
    }

    @Test
    void controllerBCancelsBindingCaptureThenEditorWithoutChangingBinding() {
        Fixture f = new Fixture();
        String original = f.config.getString(PAUSE_KEY);
        f.tab(CONTROLS);
        f.press(GLFW_KEY_ENTER); // PAUSE binding menu; capture is selected
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().contains("Press a key or chord"));
        f.padPress(GLFW_GAMEPAD_BUTTON_B);
        assertFalse(f.texts().contains("Press a key or chord"));
        assertTrue(f.texts().contains("Capture a new key"), "B returns to the binding menu");
        f.padPress(GLFW_GAMEPAD_BUTTON_B);
        assertFalse(f.texts().contains("Capture a new key"), "B then returns to the rows");
        assertEquals(original, f.config.getString(PAUSE_KEY));
        assertFalse(f.screen.consumeApplied());
    }

    @Test
    void everyTabRowAndEditorKeepsTextInsideNativeAndWideViewports() {
        for (EngineSettingsScreen.Scope scope : EngineSettingsScreen.Scope.values()) {
            Fixture f = new Fixture(scope);
            EngineSettingsDraft catalog = new EngineSettingsDraft(f.config);
            for (int tab = 0; tab < scope.tabs.length; tab++) {
                if (tab > 0) {
                    for (int guard = 0; guard < 200 && !f.texts().contains("TAB"); guard++) f.press(GLFW_KEY_UP);
                    f.press(GLFW_KEY_RIGHT);
                    f.press(GLFW_KEY_DOWN);
                }
                for (int row = 0; row < catalog.keys(scope.tabs[tab]).size(); row++) {
                    f.assertTextBounds(320);
                    f.assertTextBounds(426);
                    f.press(GLFW_KEY_ENTER);
                    f.assertTextBounds(320);
                    f.assertTextBounds(426);
                    f.press(GLFW_KEY_ESCAPE);
                    f.press(GLFW_KEY_DOWN);
                }
            }
        }
    }

    @Test
    void headerTabsAndEachPageUseConsistentNativeLettering() {
        Fixture f = new Fixture();
        f.texts();
        assertEquals(1f, f.line("SETTINGS").scale, "the title keeps full-size lettering");
        assertEquals(MenuStyle.COMPACT, f.line("Display").scale, "tabs use compact, catalog-authored lettering");
        assertEquals(MenuStyle.COMPACT, f.line("ALL GAMES").scale);
        for (int tab = 0; tab < 6; tab++) {
            if (tab > 0) { f.press(GLFW_KEY_UP); f.press(GLFW_KEY_RIGHT); f.press(GLFW_KEY_DOWN); }
            f.texts();
            var rows = f.font.lines.stream().filter(line -> line.y >= 52 && line.y < 164 && line.x == 18).toList();
            assertFalse(rows.isEmpty());
            assertTrue(rows.stream().allMatch(line -> line.scale == rows.getFirst().scale),
                    "one type size per page: " + rows);
        }
    }

    @Test
    void shortDescriptionDisplaysBothLinesImmediately() {
        Fixture f = new Fixture(); // Display / aspect ratio
        for (int width : new int[] {320, 426}) {
            f.assertTextBounds(width);
            List<Line> help = f.font.lines.stream().filter(line -> line.y == 173 || line.y == 173 + MenuChrome.DESCRIPTION_PITCH).toList();
            assertTrue(help.size() >= 1 && help.size() <= 2, "Help appears immediately");
            assertEquals(EngineSettingLabels.description(DISPLAY_ASPECT),
                    String.join(" ", help.stream().map(Line::text).toList()));
            for (Line line : help) assertEquals(MenuStyle.COMPACT, line.scale);
        }
    }

    @Test
    void longDescriptionsStayStillAndDetailsExplainWhenChangesApply() {
        Fixture f = new Fixture();
        f.tab(CAPTURE);
        f.focus(CAPTURE_CODEC);
        List<String> first = f.descriptionLines();
        for (int i = 0; i < 1200; i++) f.frame();
        assertEquals(first, f.descriptionLines(), "Help never moves while being read");
        f.press(GLFW_KEY_F1);
        List<String> all = new ArrayList<>(f.texts());
        for (int i = 0; i < 30; i++) { f.press(GLFW_KEY_DOWN); all.addAll(f.texts()); }
        assertTrue(all.stream().anyMatch(line -> line.contains("next recording")));
        f.press(GLFW_KEY_ESCAPE);
        assertEquals(first, f.descriptionLines());
        assertTrue(EngineSettingsScreen.effect(CONTROLLER_DEADZONE).contains("immediately"));
        assertTrue(EngineSettingsScreen.effect(SCREEN_WIDTH).contains("close and reopen OpenGGF"));
        assertTrue(EngineSettingsScreen.effect(LIVE_REWIND_ENABLED).contains("Game Setup uses the global value"),
                "all-games switches explain that Game Setup can override them");
    }

    @Test
    void windowRenderLimitAppliesImmediatelyAndGameStartSettingsKeepTheirHelp() {
        Fixture f = new Fixture(); // Display: aspect ratio first, frame rate further down
        List<String> aspect = f.descriptionLines();
        assertTrue(aspect.stream().noneMatch(line -> line.startsWith("Needs a restart")),
                "a game-start setting keeps both description lines");
        assertFalse(f.texts().contains("RESTART"), "unchanged settings need no restart badge");

        f = new Fixture(EngineSettingsScreen.Scope.ADVANCED);
        f.focus(FPS);
        assertFalse(f.descriptionLines().contains("Needs a restart: close and reopen OpenGGF."));
        f.press(GLFW_KEY_RIGHT);
        assertFalse(f.texts().contains("RESTART"), "the live window cap does not need a restart");
        assertFalse(f.descriptionLines().contains("Needs a restart: close and reopen OpenGGF."));
        assertTrue(EngineSettingsScreen.effect(FPS).contains("as soon as you save"));
        assertEquals(com.openggf.configuration.SettingTiming.NOW, com.openggf.configuration.SettingTiming.of(FPS));
    }

    @Test
    void playStationPromptsAppearAsKeyCaps() {
        Fixture f = new Fixture();
        f.padName = "DualSense Wireless Controller";
        f.padPress(GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        List<String> texts = f.texts();
        assertTrue(texts.contains("CROSS"), "confirm key cap in the description box");
        assertTrue(texts.contains("TRIANGLE"), "details key cap");
        assertTrue(texts.contains("CIRCLE"), "back key cap in the footer");
        f.assertTextBounds(320);
    }

    @Test
    void advancedScopeShowsOnlyTheAdvancedSettings() {
        Fixture f = new Fixture(EngineSettingsScreen.Scope.ADVANCED);
        List<String> texts = f.texts();
        assertTrue(texts.contains("ADVANCED SETTINGS"));
        assertFalse(texts.contains("DISPLAY"), "no tab strip for a single page");
        assertTrue(texts.stream().anyMatch(text -> text.startsWith("Rendering limit")), "technical display controls live here");
    }

    @Test
    void sidekicksAreEditedAsAnOrderedTeamRatherThanCommaText() {
        Fixture f = new Fixture(EngineSettingsScreen.Scope.ADVANCED, config -> {
            config.setConfigValue(MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SIDEKICK_CHARACTER_CODE, "tails");
        });
        f.focus(SIDEKICK_CHARACTER_CODE);
        f.press(GLFW_KEY_ENTER);
        List<String> page = f.texts();
        assertTrue(page.contains("Direct-launch sidekicks"), page.toString());
        assertTrue(page.contains("Player: Sonic"));
        assertTrue(page.contains("Tails") && page.contains("Follows Sonic"));
        assertFalse(page.contains("tails"), "the page never shows the stored comma value");
        f.press(GLFW_KEY_DOWN);
        f.press(GLFW_KEY_ENTER); // Add sidekick: copies the last one and focuses it.
        f.press(GLFW_KEY_RIGHT); // Tails -> Knuckles
        page = f.texts();
        assertTrue(page.contains("Knuckles") && page.contains("Follows Tails"), page.toString());
        f.press(GLFW_KEY_ENTER); // Actions for sidekick 2.
        assertTrue(f.texts().contains("Move earlier"));
        assertFalse(f.texts().contains("Move later"), "the last sidekick cannot move later");
        f.press(GLFW_KEY_ENTER); // Move earlier.
        assertTrue(f.texts().contains("Follows Knuckles"), "Tails now follows Knuckles");
        f.assertTextBounds(320);
        f.assertTextBounds(400);
        f.press(GLFW_KEY_DOWN, 4); // Focus is on Knuckles: Tails, Add sidekick, Restore default, Done.
        f.press(GLFW_KEY_ENTER);
        assertEquals("tails", f.config.getString(SIDEKICK_CHARACTER_CODE), "Done fills the draft only");
        assertTrue(f.texts().contains("Knuckles, Tails"), "the row reads as names");
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER); // Save.
        assertEquals("knuckles,tails", f.config.getString(SIDEKICK_CHARACTER_CODE));
    }

    @Test
    void extraSaveScreenTeamsAreAddedEditedAndOrderedAsTeams() {
        Fixture f = new Fixture(EngineSettingsScreen.Scope.ADVANCED,
                config -> config.setConfigValue(DATA_SELECT_EXTRA_PLAYER_COMBOS, "sonic,knuckles"));
        f.focus(DATA_SELECT_EXTRA_PLAYER_COMBOS);
        assertTrue(f.texts().contains("Sonic + Knuckles"), () -> "the Settings row reads as names: " + f.texts());
        f.press(GLFW_KEY_ENTER);
        List<String> page = f.texts();
        assertTrue(page.contains("Extra character teams") && page.contains("Sonic + Knuckles"), page.toString());
        f.press(GLFW_KEY_DOWN);
        f.press(GLFW_KEY_ENTER); // Add team.
        page = f.texts();
        assertTrue(page.contains("New team") && page.contains("Player") && page.contains("Follows Sonic"), page.toString());
        f.assertTextBounds(320);
        f.assertTextBounds(400);
        f.press(GLFW_KEY_RIGHT); // Player: Sonic -> Tails.
        f.press(GLFW_KEY_DOWN);
        f.press(GLFW_KEY_RIGHT); // Sidekick: Tails -> Knuckles.
        assertTrue(f.texts().contains("Follows Tails"));
        f.press(GLFW_KEY_DOWN, 2); // Add sidekick, Done.
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().contains("Tails + Knuckles"), "Done returns to the list with the new team");
        f.press(GLFW_KEY_ENTER); // Actions for team 2.
        assertEquals(List.of("Edit team", "Move earlier", "Remove"),
                f.texts().stream().filter(List.of("Edit team", "Move earlier", "Move later", "Remove")::contains).toList());
        f.press(GLFW_KEY_DOWN);
        f.press(GLFW_KEY_ENTER); // Move earlier.
        f.assertTextBounds(320);
        f.press(GLFW_KEY_DOWN, 4); // Team 2, Add team, Restore default, Done.
        f.press(GLFW_KEY_ENTER);
        assertEquals("sonic,knuckles", f.config.getString(DATA_SELECT_EXTRA_PLAYER_COMBOS), "Done fills the draft only");
        f.press(GLFW_KEY_ESCAPE);
        f.press(GLFW_KEY_ENTER); // Save.
        assertEquals("tails,knuckles;sonic,knuckles", f.config.getString(DATA_SELECT_EXTRA_PLAYER_COMBOS));
    }

    @Test
    void backingOutOfTheSidekickPageKeepsTheDraft() {
        Fixture f = new Fixture(EngineSettingsScreen.Scope.ADVANCED,
                config -> config.setConfigValue(SIDEKICK_CHARACTER_CODE, "tails"));
        f.focus(SIDEKICK_CHARACTER_CODE);
        f.press(GLFW_KEY_ENTER);
        f.press(GLFW_KEY_ENTER); // Actions for sidekick 1.
        f.press(GLFW_KEY_ENTER); // Remove (its only action).
        assertTrue(f.texts().contains("No sidekicks: the player plays alone"));
        f.press(GLFW_KEY_ESCAPE);
        assertEquals(SIDEKICK_CHARACTER_CODE, f.screen.selectedForTest());
        assertFalse(f.texts().contains("SAVE CHANGES"), "Back discards the page's edits");
    }

    @Test
    void settingsAreRegroupedByWhatPlayersLookFor() {
        assertEquals(EngineSettingsDraft.Category.GAMEPLAY, EngineSettingsDraft.category(DEBUG_VIEW_ENABLED));
        assertEquals(EngineSettingsDraft.Category.GAMEPLAY, EngineSettingsDraft.category(LIVE_REWIND_ENABLED));
        assertEquals(EngineSettingsDraft.Category.INPUT, EngineSettingsDraft.category(DISPLAY_SHADER_NEXT_KEY),
                "every non-debug hotkey lives under Controls");
        assertEquals(EngineSettingsDraft.Category.INPUT, EngineSettingsDraft.category(LIVE_REWIND_KEY));
        assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(MAIN_CHARACTER_CODE));
        for (var key : List.of(FPS, SCALE, DISPLAY_SHADER_LIBRARY_ROOT, DISPLAY_SHADER_DEFAULT_PHASE)) {
            assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(key));
        }
        assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(CROSS_GAME_SOURCE));
        assertEquals(EngineSettingsDraft.Category.LAUNCH, EngineSettingsDraft.category(LAUNCH_S2_REWIND));
        assertFalse(java.util.Arrays.asList(EngineSettingsScreen.Scope.MAIN.tabs).contains(EngineSettingsDraft.Category.LAUNCH),
                "Game Setup owns launch profiles");
    }

    @Test
    void controlsHaveDistinctPlayerGroupsWithEightCompactRows() {
        Fixture f = new Fixture();
        f.tab(CONTROLS);
        List<String> text = f.texts();
        assertTrue(text.contains("PLAYER 1"));
        assertTrue(text.contains("RESET BINDINGS"));
        assertTrue(text.contains("PLAYER 2"));
        var rows = f.font.lines.stream().filter(line -> line.x == 18 && line.y >= 66 && line.y < 155).toList();
        assertEquals(8, rows.size(), rows.toString());
        // An 11 px pitch leaves two pixels between 9 px key labels; the last focus band (one pixel
        // above the text row, a pitch tall) ends before the reset button at y 155.
        for (int i = 1; i < rows.size(); i++) assertEquals(11, rows.get(i).y - rows.get(i - 1).y);
        assertTrue(rows.getFirst().y - 2 >= 65, "first focus band clears the group tabs");
        assertTrue(rows.getLast().y - 2 + 11 <= 155, "last focus band clears the reset button");
        assertTrue(rows.stream().allMatch(line -> line.scale == MenuStyle.COMPACT));
        // The PLAYER 1 group already names the player, so rows name only the button.
        assertEquals(List.of("Up", "Down", "Left", "Right", "A", "B", "C", "Start"),
                rows.stream().map(Line::text).toList());
        List<String> playerOne = f.texts();
        f.press(GLFW_KEY_UP); // subgroup strip
        f.press(GLFW_KEY_RIGHT);
        f.press(GLFW_KEY_DOWN);
        assertFalse(f.texts().contains("Player 2 Up"), "Player 2 rows also drop the repeated prefix");
        assertNotEquals(playerOne, f.texts(), "Player 2 shows its own bindings");
        f.assertTextBounds(320);
    }

    @Test
    void resetTabAsksBeforeTouchingDraftAndPreservesOtherTabs() {
        Fixture f = new Fixture();
        f.tab(SOUND);
        f.press(GLFW_KEY_RIGHT); // sound off
        int count = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.AUDIO).size();
        f.press(GLFW_KEY_DOWN, count); // reset button after final row
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().contains("Reset Sound defaults?"));
        f.press(GLFW_KEY_ENTER); // Cancel is the safe initial choice.
        assertTrue(f.texts().contains("1 UNSAVED"));
        f.press(GLFW_KEY_ENTER);
        f.press(GLFW_KEY_LEFT);
        f.press(GLFW_KEY_ENTER);
        assertFalse(f.texts().contains("1 UNSAVED"));
        assertTrue(f.config.getBoolean(AUDIO_ENABLED), "Defaults stay in the draft until saved");
    }

    @Test
    void promptListsConcreteChangesAndGameplayHasOneRewindRow() {
        Fixture f = new Fixture();
        f.tab(SOUND);
        f.press(GLFW_KEY_RIGHT);
        f.press(GLFW_KEY_ESCAPE);
        // Each change draws its old value, an arrow glyph, then the new value.
        assertTrue(java.util.Collections.indexOfSubList(f.texts(), List.of("On", "Off")) >= 0, f.texts().toString());
        EngineSettingsDraft catalog = new EngineSettingsDraft(f.config);
        assertEquals(List.of(LIVE_REWIND_ENABLED), catalog.keys(EngineSettingsDraft.Category.GAMEPLAY).stream()
                .filter(key -> ConfigCatalog.meta(key).section().equals("rewind")).toList());
        assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(LOAD_TIME_SIMULATION));
    }

    /**
     * Display and Advanced group their rows under captions. Down walks every setting in caption
     * order without ever landing on a caption, each focused row is on screen with its caption
     * reachable above it, and a click on a drawn row focuses exactly that setting; captions ignore
     * clicks. Every tab setting stays reachable.
     */
    @Test
    void captionedTabsReachEverySettingByArrowsAndPointerAndSkipCaptions() {
        record Case(EngineSettingsScreen.Scope scope, EngineSettingsDraft.Category tab, List<String> groups) { }
        for (var c : List.of(
                new Case(EngineSettingsScreen.Scope.MAIN, EngineSettingsDraft.Category.DISPLAY,
                        List.of("WINDOW", "PICTURE", "WIDESCREEN", "HUD")),
                new Case(EngineSettingsScreen.Scope.ADVANCED, EngineSettingsDraft.Category.ADVANCED,
                        List.of("SHADERS", "RENDERING AND LOADING", "DIRECT LAUNCH", "REWIND", "MODS", "DISCORD",
                                "PLAYBACK AND RECORDING", "TRACE REPLAY", "DEBUG", "SETTINGS FILE")))) {
            for (int width : new int[] {320, 400}) {
                Fixture f = new Fixture(c.scope());
                if (c.scope() == EngineSettingsScreen.Scope.MAIN) f.tab(DISPLAY);
                var expected = new EngineSettingsDraft(f.config).keys(c.tab());
                var lines = f.screen.lines();
                assertEquals(c.groups(), lines.stream().filter(l -> l.caption() != null).map(EngineSettingsScreen.Line::caption).toList());
                assertEquals(new java.util.HashSet<>(expected), lines.stream().filter(l -> l.key() != null)
                        .map(EngineSettingsScreen.Line::key).collect(java.util.stream.Collectors.toSet()), "every setting is a row");
                assertEquals(expected.size(), lines.stream().filter(l -> l.key() != null).count(), "each setting once");
                assertNotNull(lines.getFirst().caption(), "the list opens with its first caption");
                var visited = new ArrayList<com.openggf.configuration.SonicConfiguration>();
                java.util.Set<String> captionsSeen = new java.util.LinkedHashSet<>();
                for (int i = 0; i < expected.size(); i++) {
                    var key = f.screen.selectedForTest();
                    visited.add(key);
                    f.font.lines.clear();
                    f.screen.render(width);
                    int y = f.screen.rowYForTest(key);
                    assertTrue(y >= 54 && y + 12 <= 155, key + " is on screen at " + width + ": " + y);
                    for (var line : f.font.lines) if (c.groups().contains(line.text)) captionsSeen.add(line.text);
                    f.assertTextBounds(width);
                    // The pointer reaches the same row where it is drawn.
                    f.screen.updatePointer(new MenuInput.Pointer(60, y + 5, true, false, false));
                    assertEquals(key, f.screen.selectedForTest(), "hover over the drawn row keeps " + key);
                    f.press(GLFW_KEY_DOWN);
                }
                assertEquals(lines.stream().filter(l -> l.key() != null).map(EngineSettingsScreen.Line::key).toList(), visited,
                        "Down visits every setting in drawn order, never a caption");
                assertEquals(c.groups(), List.copyOf(captionsSeen), "every caption scrolls into view above its rows");
                // Back at the top: a hover over a caption focuses nothing new; a row hover does.
                Fixture g = new Fixture(c.scope());
                if (c.scope() == EngineSettingsScreen.Scope.MAIN) g.tab(DISPLAY);
                var first = g.screen.selectedForTest();
                g.font.lines.clear(); g.screen.render(width);
                int firstY = g.screen.rowYForTest(first);
                g.screen.updatePointer(new MenuInput.Pointer(60, firstY - 12 + 5, true, true, false));
                assertEquals(first, g.screen.selectedForTest(), "the caption above the first row is not a row");
                var second = g.screen.lines().get(2).key();
                if (second != null) {
                    g.screen.updatePointer(new MenuInput.Pointer(60, g.screen.rowYForTest(second) + 5, true, false, false));
                    assertEquals(second, g.screen.selectedForTest());
                }
            }
        }
    }

    @Test
    void tapeCoastSettingsIndentUnderTheCoastAndDimWhileItIsOffButStayEditable() {
        Fixture f = new Fixture(EngineSettingsScreen.Scope.ADVANCED);
        assertFalse(f.config.getBoolean(LIVE_REWIND_TAPE_COAST_ENABLED), "the coast is off by default");
        f.focus(LIVE_REWIND_VHS_EFFECT); // the row after the coast's settings, so they all draw unfocused
        f.font.lines.clear();
        f.screen.render(320);
        var parent = f.line("Tape coast");
        for (String child : List.of("Tape coast acceleration", "Tape coast deceleration", "Tape coast top speed", "Tape coast lowest speed")) {
            var row = f.line(child);
            assertEquals(parent.x + 8, row.x, child + " sits 8 px under Tape coast");
        }
        // Still editable while off; the help says why it has no effect.
        while (f.screen.selectedForTest() != LIVE_REWIND_TAPE_COAST_ACCELERATION) f.press(GLFW_KEY_UP);
        assertTrue(f.descriptionLines().contains("Used only while Tape coast is on."), f.descriptionLines().toString());
        String before = new EngineSettingsDraft(f.config).text(LIVE_REWIND_TAPE_COAST_ACCELERATION);
        f.press(GLFW_KEY_ENTER);
        assertTrue(f.texts().stream().anyMatch(t -> t.equals("TAPE COAST ACCELERATION")), "the editor opens: " + f.texts());
        f.press(GLFW_KEY_ESCAPE);
        assertEquals(before, new EngineSettingsDraft(f.config).text(LIVE_REWIND_TAPE_COAST_ACCELERATION));
        // Switching the coast on removes the note.
        while (f.screen.selectedForTest() != LIVE_REWIND_TAPE_COAST_ENABLED) f.press(GLFW_KEY_UP);
        f.press(GLFW_KEY_RIGHT);
        f.press(GLFW_KEY_DOWN);
        assertFalse(f.descriptionLines().contains("Used only while Tape coast is on."));
    }

    /**
     * Player-facing labels keep acronyms in capitals and help never shows the catalog's raw
     * identifiers: no setting's camelCase leaf, no section.leaf path, no enum constant and no
     * assignment. Details keeps the technical text.
     */
    @Test
    void labelsKeepAcronymsAndHelpNeverLeaksRawConfigurationKeys() {
        var raw = new java.util.HashSet<String>();
        for (var key : com.openggf.configuration.SonicConfiguration.values()) {
            var meta = ConfigCatalog.meta(key);
            if (meta == null || !meta.persisted()) continue;
            if (!meta.leaf().equals(meta.leaf().toLowerCase(java.util.Locale.ROOT))) raw.add(meta.leaf());
            raw.add(meta.section() + "." + meta.leaf());
            raw.add(key.name());
            for (String value : meta.allowedValues()) if (value.contains("_")) raw.add(value);
        }
        var shown = new ArrayList<com.openggf.configuration.SonicConfiguration>();
        var draft = new EngineSettingsDraft(SonicConfigurationService.createStandalone(directory));
        for (var tabs : List.of(EngineSettingsScreen.Scope.MAIN.tabs, EngineSettingsScreen.Scope.ADVANCED.tabs))
            for (var tab : tabs) shown.addAll(draft.keys(tab));
        var acronym = java.util.regex.Pattern.compile("\\b(Vhs|Pcm|Mb|Fps|Tls|Rom|Hud|Dac|S3k|Bk2|Rgb|Ntsc)\\b");
        for (var key : shown) {
            for (String label : List.of(EngineSettingLabels.label(key), EngineSettingLabels.rowLabel(key))) {
                assertFalse(acronym.matcher(label).find(), key + " label keeps acronym capitals: " + label);
                assertFalse(label.isBlank(), key + " has a label");
            }
            String help = EngineSettingLabels.rowDescription(key);
            assertFalse(help.contains("="), key + " help shows no assignment: " + help);
            for (String token : raw) assertFalse(help.contains(token), key + " help leaks " + token + ": " + help);
            // Details' Applies row: one sentence for the timing class, no hedged parenthetical.
            String applies = EngineSettingsScreen.effect(key);
            assertFalse(applies.contains("("), key + " Applies: " + applies);
            assertTrue(applies.endsWith(".") && applies.indexOf(". ") < 0, key + " Applies is one sentence: " + applies);
            if (com.openggf.game.launch.LiveSettings.appliesOnResume(key, false)
                    && com.openggf.configuration.SettingTiming.of(key) == com.openggf.configuration.SettingTiming.NEXT_GAME)
                assertTrue(applies.contains("resume"), key + " also reaches a paused game: " + applies);
        }
    }

    private final class Fixture {
        final SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        final RecordingFont font = new RecordingFont();
        final EngineSettingsScreen screen;
        List<GamepadStateSource.DeviceState> pads = List.of();
        String padName = "Test pad";
        final InputHandler input = new InputHandler(InputBindingFactory.supplier(config), () -> pads);

        Fixture() { this(EngineSettingsScreen.Scope.MAIN); }

        Fixture(EngineSettingsScreen.Scope scope) { this(scope, config -> { }); }

        /** {@code setup} writes saved preferences before the screen takes its draft. */
        Fixture(EngineSettingsScreen.Scope scope, java.util.function.Consumer<SonicConfigurationService> setup) {
            setup.accept(config);
            screen = new EngineSettingsScreen(config, font, scope);
            config.setConfigValue(CONTROLLER_ENABLED, true);
            config.setConfigValue(CONTROLLER_PLAYER1, "auto");
            config.setConfigValue(CONTROLLER_PLAYER2, "none");
            pad();
            frame();
        }
        void press(int key) { press(key, 1); }
        /** Down through the drawn rows until {@code key} has the focus. */
        void focus(com.openggf.configuration.SonicConfiguration key) {
            for (int i = 0; i < 100 && screen.selectedForTest() != key; i++) press(GLFW_KEY_DOWN);
            assertEquals(key, screen.selectedForTest());
        }
        /** Up to the tab strip, across to {@code index}, and back down to its first row. */
        void tab(int index) {
            press(GLFW_KEY_UP);
            press(GLFW_KEY_RIGHT, index);
            press(GLFW_KEY_DOWN);
        }
        Line line(String text) {
            return font.lines.stream().filter(l -> l.text.equals(text)).findFirst().orElseThrow(() -> new AssertionError(text));
        }
        void press(int key, int times) {
            for (int i = 0; i < times; i++) {
                input.handleKeyEvent(key, GLFW_PRESS);
                frame();
                input.handleKeyEvent(key, GLFW_RELEASE);
                frame();
            }
        }
        void type(String text) { text.codePoints().forEach(c -> MenuInput.handleCharEvent(input, c)); frame(); }
        void padPress(int key) { pad(key); frame(); pad(); frame(); }
        void pad(int... buttons) {
            boolean[] state = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            for (int button : buttons) state[button] = true;
            pads = List.of(GamepadStateSource.DeviceState.connected(0, padName, state, 0, 0));
        }
        void frame() { input.refreshLogicalSnapshot(); screen.update(input); input.update(); }
        List<String> descriptionLines() {
            texts();
            return font.lines.stream().filter(line -> line.y == 173 || line.y == 173 + MenuChrome.DESCRIPTION_PITCH).map(Line::text).toList();
        }
        List<String> texts() {
            font.lines.clear();
            screen.render(320);
            return font.lines.stream().map(Line::text).toList();
        }
        void assertTextBounds(int width) {
            font.lines.clear();
            screen.render(width);
            for (Line line : font.lines) {
                assertTrue(line.x >= 0 && line.y >= 0, line.text);
                assertTrue(line.x + font.measureWidth(line.text, line.scale) <= width,
                        () -> "Text overflows " + width + ": " + line);
                assertTrue(line.y + (line.scale < 1 ? 8 : 10) <= 224, line.text);
                assertTrue(line.scale == MenuStyle.COMPACT || line.scale == 1, "Only native font sizes: " + line);
            }
        }
    }

    private record Line(String text, int x, int y, float scale) { }
    private static final class RecordingFont extends PixelFont {
        final List<Line> lines = new ArrayList<>();
        // Measure with the production menu face, as MenuChrome does.
        @Override public int measureWidth(String text, float scale) {
            return com.openggf.graphics.MenuPixelFont.textWidth(text, scale);
        }
        @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
            lines.add(new Line(text, x, y, scale));
        }
    }

}

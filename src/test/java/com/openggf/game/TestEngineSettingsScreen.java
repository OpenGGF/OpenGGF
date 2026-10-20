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
    void widescreenHudAnchorHasReadableChoiceLabels() {
        assertEquals("Widescreen HUD position", EngineSettingLabels.label(DISPLAY_HUD_ANCHOR));
        assertEquals("Centered native position", EngineSettingLabels.value(DISPLAY_HUD_ANCHOR, "DEFAULT"));
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
        int row = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.DISPLAY)
                .indexOf(DISPLAY_COLOR_EXPANSION);
        assertTrue(row >= 0);
        f.press(GLFW_KEY_DOWN, row);
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
        assertEquals(MenuStyle.COMPACT, f.line("DISPLAY").scale, "tabs use compact capitals");
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
            List<Line> help = f.font.lines.stream().filter(line -> line.y == 173 || line.y == 181).toList();
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
        int codecRow = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.RECORDING).indexOf(CAPTURE_CODEC);
        f.press(GLFW_KEY_DOWN, codecRow);
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
        assertTrue(EngineSettingsScreen.effect(SCREEN_WIDTH).contains("Restart"));
        assertTrue(EngineSettingsScreen.effect(LIVE_REWIND_ENABLED).contains("Use global"),
                "all-games switches explain that Game Setup can override them");
    }

    @Test
    void onlyChangedRestartSettingsSayItOnTheRowAndInTheHelp() {
        Fixture f = new Fixture(); // Display: aspect ratio first, frame rate further down
        List<String> aspect = f.descriptionLines();
        assertTrue(aspect.stream().noneMatch(line -> line.startsWith("Needs a restart")),
                "a game-start setting keeps both description lines");
        assertFalse(f.texts().contains("RESTART"), "unchanged settings need no restart badge");

        f = new Fixture(EngineSettingsScreen.Scope.ADVANCED);
        int fpsRow = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.ADVANCED).indexOf(FPS);
        f.press(GLFW_KEY_DOWN, fpsRow);
        assertFalse(f.descriptionLines().contains("Needs a restart: close and reopen OpenGGF."));
        f.press(GLFW_KEY_RIGHT);
        assertTrue(f.texts().contains("RESTART"));
        assertTrue(f.descriptionLines().contains("Needs a restart: close and reopen OpenGGF."));
        f.press(GLFW_KEY_LEFT);
        assertFalse(f.texts().contains("RESTART"), "returning to the running value clears the badge");
        assertTrue(EngineSettingsScreen.effect(FPS).contains("Restart"));
        assertEquals(com.openggf.configuration.SettingTiming.RESTART, com.openggf.configuration.SettingTiming.of(FPS));
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
        assertTrue(texts.stream().anyMatch(text -> text.startsWith("Game speed")), "technical display controls live here");
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
        var rows = f.font.lines.stream().filter(line -> line.x == 18 && line.y >= 69 && line.y < 150).toList();
        assertEquals(8, rows.size(), rows.toString());
        assertTrue(rows.stream().allMatch(line -> line.scale == MenuStyle.COMPACT));
        assertTrue(rows.stream().allMatch(line -> line.text.startsWith("Player 1")));
        f.press(GLFW_KEY_UP); // subgroup strip
        f.press(GLFW_KEY_RIGHT);
        f.press(GLFW_KEY_DOWN);
        assertTrue(f.texts().contains("Player 2 Up"));
        assertFalse(f.texts().contains("Player 1 Up"));
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
        assertTrue(f.texts().contains("RESET SOUND DEFAULTS?"));
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
        assertTrue(f.texts().contains("On -> Off"));
        EngineSettingsDraft catalog = new EngineSettingsDraft(f.config);
        assertEquals(List.of(LIVE_REWIND_ENABLED), catalog.keys(EngineSettingsDraft.Category.GAMEPLAY).stream()
                .filter(key -> ConfigCatalog.meta(key).section().equals("rewind")).toList());
        assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(LOAD_TIME_SIMULATION));
    }

    private final class Fixture {
        final SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
        final RecordingFont font = new RecordingFont();
        final EngineSettingsScreen screen;
        List<GamepadStateSource.DeviceState> pads = List.of();
        String padName = "Test pad";
        final InputHandler input = new InputHandler(InputBindingFactory.supplier(config), () -> pads);

        Fixture() { this(EngineSettingsScreen.Scope.MAIN); }

        Fixture(EngineSettingsScreen.Scope scope) {
            screen = new EngineSettingsScreen(config, font, scope);
            config.setConfigValue(CONTROLLER_ENABLED, true);
            config.setConfigValue(CONTROLLER_PLAYER1, "auto");
            config.setConfigValue(CONTROLLER_PLAYER2, "none");
            pad();
            frame();
        }
        void press(int key) { press(key, 1); }
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
            return font.lines.stream().filter(line -> line.y == 173 || line.y == 181).map(Line::text).toList();
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
        @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
            lines.add(new Line(text, x, y, scale));
        }
    }

}

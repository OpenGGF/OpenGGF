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
            if (width == 320) assertEquals(2, help.size(), "Both native-width help lines appear without waiting");
            else assertTrue(help.size() >= 1 && help.size() <= 2, "Wider screens may fit the description on one line");
            assertEquals(MenuDetailsScreen.readable(ConfigCatalog.meta(DISPLAY_ASPECT).description()),
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
        assertTrue(EngineSettingsScreen.effect(LIVE_REWIND_ENABLED).contains("Default"),
                "all-games switches explain that Game Setup can override them");
    }

    @Test
    void onlyChangedRestartSettingsSayItOnTheRowAndInTheHelp() {
        Fixture f = new Fixture(); // Display: aspect ratio first, frame rate further down
        List<String> aspect = f.descriptionLines();
        assertTrue(aspect.stream().noneMatch(line -> line.startsWith("Needs a restart")),
                "a game-start setting keeps both description lines");
        assertFalse(f.texts().contains("RESTART"), "unchanged settings need no restart badge");

        int fpsRow = new EngineSettingsDraft(f.config).keys(EngineSettingsDraft.Category.DISPLAY).indexOf(FPS);
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
        assertTrue(texts.contains("Direct-launch character"), "global characters only matter for direct launches");
    }

    @Test
    void settingsAreRegroupedByWhatPlayersLookFor() {
        assertEquals(EngineSettingsDraft.Category.GAMEPLAY, EngineSettingsDraft.category(DEBUG_VIEW_ENABLED));
        assertEquals(EngineSettingsDraft.Category.GAMEPLAY, EngineSettingsDraft.category(LIVE_REWIND_ENABLED));
        assertEquals(EngineSettingsDraft.Category.INPUT, EngineSettingsDraft.category(DISPLAY_SHADER_NEXT_KEY),
                "every non-debug hotkey lives under Controls");
        assertEquals(EngineSettingsDraft.Category.INPUT, EngineSettingsDraft.category(LIVE_REWIND_KEY));
        assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(MAIN_CHARACTER_CODE));
        assertEquals(EngineSettingsDraft.Category.ADVANCED, EngineSettingsDraft.category(CROSS_GAME_SOURCE));
        assertEquals(EngineSettingsDraft.Category.LAUNCH, EngineSettingsDraft.category(LAUNCH_S2_REWIND));
        assertFalse(java.util.Arrays.asList(EngineSettingsScreen.Scope.MAIN.tabs).contains(EngineSettingsDraft.Category.LAUNCH),
                "Game Setup owns launch profiles");
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

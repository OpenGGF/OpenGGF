package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.openggf.game.TestMasterTitleHub.press;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;

class TestMasterTitleHubMessages {
    @TempDir Path directory;
    private final RecordingFont font = new RecordingFont();

    @Test void unavailableStandaloneActionCannotLeakIntoRomLocation() throws Exception {
        var screen = screen(true);
        screen.setSelectedIndexForTest(3);
        press(screen, GLFW_KEY_TAB); // Game Setup shortcut on a standalone game.
        for (int i = 0; i < 600; i++) screen.update(new InputHandler());
        draw(screen);
        assertTrue(font.has("ACTION UNAVAILABLE"), "error stays open until acknowledged");
        assertTrue(font.wordText().contains("Game setup is not available for standalone games"));
        assertFalse(font.has("Check Files in Engine Settings"));

        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_LEFT);
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);
        draw(screen);
        assertTrue(screen.isRomLocatorOpenForTest());
        assertTrue(font.has("LOCATE SONIC 3K"));
        assertFalse(font.has("ROM NOT FOUND"));
        assertFalse(font.wordText().contains("is not available for standalone games"));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void missingRomActionsOpenTheStartLocatorAndBackRestoresTheCardAndAction(int action) throws Exception {
        var screen = screen(false);
        press(screen, GLFW_KEY_DOWN); // No chips without a ROM: straight to the actions.
        for (int i = 0; i < action; i++) press(screen, GLFW_KEY_RIGHT);
        var focused = screen.focusedActionForTest();
        draw(screen);
        var footer = font.texts.stream().filter(t -> t.y() >= MenuChrome.FOOTER_Y).map(Text::text).toList();
        assertTrue(footer.contains("LOCATE ROM"), "the footer says what Enter does here: " + footer);
        assertFalse(footer.contains("OPEN SETUP") || footer.contains("OPEN PLAYBACK"), footer.toString());
        press(screen, GLFW_KEY_ENTER);
        draw(screen);
        assertTrue(screen.isRomLocatorOpenForTest(), "Game setup and Playback reuse Start's locator");
        assertTrue(font.has("LOCATE SONIC 2"));
        assertFalse(font.has("ROM NOT FOUND"));
        assertFalse(font.allText().contains("Engine Settings"));
        press(screen, GLFW_KEY_ESCAPE);
        assertFalse(screen.isRomLocatorOpenForTest());
        assertFalse(screen.errorPageOpenForTest());
        assertEquals("s2", screen.getSelectedGameId());
        assertEquals(focused, screen.focusedActionForTest(), "focus returns to the action that found the missing ROM");
    }

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void romNotFoundPageOffersLocateFirstByKeyboardPadAndPointer(int width) throws Exception {
        // Keyboard: Enter is Locate ROM; Esc returns to the same card.
        var screen = screen(false);
        screen.setViewportWidth(width);
        screen.showRomLoadError("s2");
        draw(screen);
        assertTrue(font.has("UNABLE TO START") && font.has("ROM NOT FOUND") && font.has("missing-s2.gen"));
        assertTrue(font.has("Locate ROM"), font.allText());
        assertTrue(font.has("Or set its path in Settings > Files"));
        assertFalse(font.allText().contains("Engine Settings"));
        for (var text : font.texts) assertTrue(text.x() >= 0 && text.x() + MenuChrome.compactWidth(text.text()) <= width + 1, text.toString());
        press(screen, GLFW_KEY_ESCAPE);
        assertFalse(screen.errorPageOpenForTest());
        assertFalse(screen.isRomLocatorOpenForTest());
        assertEquals("s2", screen.getSelectedGameId());
        screen.showRomLoadError("s2");
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isRomLocatorOpenForTest());
        assertFalse(screen.errorPageOpenForTest());

        // Pad: A is Locate ROM.
        var padScreen = screen(false);
        padScreen.setViewportWidth(width);
        padScreen.showRomLoadError("s2");
        boolean[] none = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        boolean[] a = none.clone(); a[GLFW_GAMEPAD_BUTTON_A] = true;
        var pad = new java.util.concurrent.atomic.AtomicReference<List<com.openggf.control.GamepadStateSource.DeviceState>>(
                List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox pad", none, 0, 0)));
        var input = new InputHandler(com.openggf.InputBindingFactory.supplier(configOf(padScreen)), pad::get);
        input.refreshLogicalSnapshot(); input.update();
        pad.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox pad", a, 0, 0)));
        input.refreshLogicalSnapshot(); padScreen.update(input); input.update();
        assertTrue(padScreen.isRomLocatorOpenForTest(), "pad A locates the ROM");

        // Pointer: the drawn button opens the locator; a click elsewhere does not dismiss it; the footer goes back.
        var mouse = screen(false);
        mouse.setViewportWidth(width);
        mouse.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 1});
        mouse.showRomLoadError("s2");
        var page = mouse.errorPageLayout();
        click(mouse, width / 2, page.detailY() + 3);
        assertTrue(mouse.errorPageOpenForTest(), "a click on the path does nothing");
        click(mouse, width / 2, 206);
        assertFalse(mouse.errorPageOpenForTest(), "the footer returns to the games");
        assertFalse(mouse.isRomLocatorOpenForTest());
        mouse.showRomLoadError("s2");
        click(mouse, width / 2, page.locate().y() + page.locate().h() / 2);
        assertTrue(mouse.isRomLocatorOpenForTest(), "the Locate ROM button opens the locator");
        assertEquals("s2", mouse.getSelectedGameId());
    }

    @Test void standaloneMissingRomKeepsTheUnavailablePageWithoutALocator() throws Exception {
        var screen = screen(true);
        screen.setSelectedIndexForTest(3);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, false);
        press(screen, GLFW_KEY_TAB); // the Game setup shortcut on the standalone card
        draw(screen);
        assertTrue(font.has("UNABLE TO OPEN") && font.has("ACTION UNAVAILABLE"), font.allText());
        assertFalse(font.has("Locate ROM"));
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.isRomLocatorOpenForTest());
        assertFalse(screen.errorPageOpenForTest());
    }

    private static void click(MasterTitleScreen screen, int x, int y) {
        var input = new InputHandler();
        input.handleMouseMove(x, y); screen.update(input); input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS); screen.update(input); input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE); screen.update(input); input.update();
    }

    @Test void shelfShowsTheSelectedGameNeighboursAndMissingRoms() throws Exception {
        var screen = screen(false);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        for (int width : new int[] {320, 400, 528}) {
            screen.setViewportWidth(width);
            draw(screen);
            String name = "SONIC THE HEDGEHOG 2";
            assertTrue(font.has(name));
            assertEquals((width - MenuChrome.labelWidth(name)) / 2, font.x(name), "the name is centred under the card");
            assertTrue(font.has("ROM MISSING"), "neighbours without a ROM say so on their card");
            assertTrue(font.has("SETTINGS"));
            assertTrue(font.has("ADVANCED"));
            assertTrue(font.has("QUIT"));
            assertTrue(font.has("START"));
            assertTrue(font.has("GAME SETUP"));
            assertTrue(font.has("PLAYBACK"));
        }
    }

    @Test void setupChipsOnlyAppearForAnAvailableGame() throws Exception {
        var screen = screen(false);
        draw(screen);
        assertTrue(font.has("Choose your Sonic 2 ROM file to play"));
        assertTrue(font.has("LOCATE ROM"));
        assertFalse(font.has("SONIC & TAILS"));
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        draw(screen);
        assertTrue(font.has("SONIC & TAILS"));
        assertTrue(font.has("4:3"));
        assertTrue(font.has("Game Setup changes players, screen and extras"));
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, false);
        draw(screen);
        assertFalse(font.has("SONIC & TAILS"));
    }

    @Test void enabledModsFollowTheSelectedGameAsAChip() throws Exception {
        var screen = screen(false);
        screen.setEnabledModsByGame(Map.of(
                MasterTitleScreen.GameEntry.SONIC_1, List.of("Green Hill Remix"),
                MasterTitleScreen.GameEntry.SONIC_2, List.of("Tide Circuit", "Music Pack")));
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_1, true);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_3K, true);

        draw(screen);
        assertTrue(font.has("2 MODS"));
        assertTrue(font.has("MODS 3"), "the Mods tab counts every enabled mod once");
        screen.setSelectedIndexForTest(0);
        draw(screen);
        assertTrue(font.has("1 MOD"));
        screen.setSelectedIndexForTest(2);
        draw(screen);
        assertFalse(font.allText().contains(" MOD"), "no mods chip when no mod changes the game");
    }

    @Test void optionalExtrasRetainBrandSpaceAtNativeWidth() throws Exception {
        var screen = screen(false);
        screen.setTitleEntries(List.of("Extra mode"), (games, index) -> { });
        screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2, List.of("One", "Two")));
        draw(screen);
        assertTrue(screen.hubLayout().tabs().getFirst().x() >= 80);
        assertTrue(font.has("MORE"));
        assertTrue(font.has("EXTRAS"));
    }

    @Test void keyboardHelpListsEveryHubKeyOnceInReadingOrderAboveTheFooter() throws Exception {
        var screen = screen(false);
        press(screen, GLFW_KEY_F1);
        var groups = screen.helpGroups();
        assertEquals(List.of("MOVE", "SELECT", "SHORTCUTS"), groups.stream().map(MasterTitleScreen.HelpGroup::title).toList());
        var keys = groups.stream().flatMap(g -> g.rows().stream()).map(row -> row[0]).toList();
        assertEquals(keys.size(), keys.stream().distinct().count(), "each key once: " + keys);
        assertTrue(keys.containsAll(List.of("Enter", "Esc", "Tab", "M", "F1")), keys.toString());
        assertTrue(groups.getFirst().rows().stream().noneMatch(row -> row[1].startsWith("Choose")),
                "Move rows move the focus; selecting is its own group");
        drawHelpAndCheckGeometry(screen);
    }

    @Test void controllerHelpNamesItsHelpButtonAndWhereGameSetupAndModsSit() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        var entries = new ArrayList<MasterTitleEntry>();
        for (var game : MasterTitleScreen.GameEntry.values()) entries.add(new MasterTitleEntry.Stock(game));
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config), entries, cue -> {});
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        installFont(screen);
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        var pad = new java.util.concurrent.atomic.AtomicReference<List<com.openggf.control.GamepadStateSource.DeviceState>>(
                List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox pad", buttons, 0, 0)));
        var input = new InputHandler(com.openggf.InputBindingFactory.supplier(config), pad::get);
        input.refreshLogicalSnapshot();
        input.update();
        // The pad's own help button opens the page (as the hub footer's HELP cap says).
        boolean[] held = buttons.clone();
        held[GLFW_GAMEPAD_BUTTON_Y] = true;
        pad.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox pad", held, 0, 0)));
        input.refreshLogicalSnapshot();
        screen.update(input);
        input.update();
        pad.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox pad", buttons, 0, 0)));
        input.refreshLogicalSnapshot();
        screen.update(input);
        input.update();
        assertTrue(MenuChrome.controller(input), "the hub reads the pad's presentation");
        var groups = screen.helpGroups();
        var shortcuts = groups.getLast();
        assertEquals("SHORTCUTS", shortcuts.title());
        assertEquals(List.of(MenuChrome.detailsKey(input)), shortcuts.rows().stream().map(row -> row[0]).toList(),
                "the help button is the pad's one shortcut");
        var keys = groups.stream().flatMap(g -> g.rows().stream()).map(row -> row[0]).toList();
        assertEquals(keys.size(), keys.stream().distinct().count(), "no repeated pad cap: " + keys);
        var vertical = groups.getFirst().rows().get(1);
        assertEquals(List.of("Mods: in the top tabs", "Game Setup: below the game"), List.of(vertical).subList(2, 4));
        assertTrue(screen.helpOpenForTest(), "the help button opened help");
        drawHelpAndCheckGeometry(screen);
    }

    /** Every caption, key row and the legend sit where drawn, inside 320 and above the footer. */
    private void drawHelpAndCheckGeometry(MasterTitleScreen screen) {
        for (int width : new int[] {320, 400}) {
            screen.setViewportWidth(width);
            draw(screen);
            var layout = screen.helpLayout();
            var groups = screen.helpGroups();
            for (int i = 0; i < groups.size(); i++) {
                String title = groups.get(i).title();
                assertTrue(font.texts.stream().anyMatch(t -> t.text().equals(title)), title + " drawn");
            }
            var rows = groups.stream().flatMap(g -> g.rows().stream()).toList();
            assertEquals(rows.size(), layout.rowY().size());
            for (int i = 0; i < rows.size(); i++) {
                String description = rows.get(i)[1];
                var drawn = font.texts.stream().filter(t -> t.text().equals(description)).findFirst().orElseThrow(
                        () -> new AssertionError(description + " drawn whole at " + width));
                assertEquals(layout.rowY().get(i) + 2, (int) drawn.y(), description);
                assertEquals(layout.descriptionX(), (int) drawn.x(), description);
                assertTrue(layout.descriptionX() + MenuChrome.compactWidth(description) <= width - 12, description);
            }
            var legend = font.texts.stream().filter(t -> t.text().equals("Amber values are set for one game only")).findFirst().orElseThrow();
            assertEquals(layout.legendY() + 1, (int) legend.y());
            assertTrue(legend.y() + 9 <= 224 - 18, "legend clears the footer at " + width + ": " + legend.y());
        }
    }

    private void installFont(MasterTitleScreen screen) throws Exception {
        var rendererField = MasterTitleScreen.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        rendererField.set(screen, mock(TexturedQuadRenderer.class));
        var fontField = MasterTitleScreen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);
    }

    @Test void crowdedCardAndStartBothNameEveryHiddenChipInAmberWhereSetForThisGame() throws Exception {
        var screen = screen(false);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        var store = new LaunchProfileStore(configOf(screen));
        store.save(MasterTitleScreen.GameEntry.SONIC_2, new com.openggf.game.launch.LaunchProfile(
                com.openggf.game.launch.LaunchToggle.OFF, "s1", com.openggf.game.launch.LaunchToggle.ON,
                "WIDE_16_10", "sonic", "tails"));
        screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2,
                java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "Mod " + i).toList()));
        for (boolean onStart : new boolean[] {false, true}) {
            if (onStart) press(screen, GLFW_KEY_DOWN);
            assertEquals(onStart ? TitleHubNavigation.Zone.ACTIONS : TitleHubNavigation.Zone.CARD, screen.hubZoneForTest());
            draw(screen);
            var chips = screen.visibleHubChips();
            assertTrue(chips.getLast().label().endsWith(" MORE"), chips.toString());
            var hidden = HubSetupChips.hidden(screen.hubChips(), chips);
            for (var chip : hidden) {
                String phrase = HubSetupChips.phrase(chip);
                var drawn = font.texts.stream().filter(t -> t.text().equals(phrase)).findFirst()
                        .orElseThrow(() -> new AssertionError(phrase + " shown, start=" + onStart + " " + font.allText()));
                assertEquals(chip.thisGameOnly(), drawn.red() > .9f, phrase + " amber only when set for this game");
            }
            assertFalse(font.has("Game Setup changes players, screen and extras"), "no generic line hides the status");
        }
    }

    private static SonicConfigurationService configOf(MasterTitleScreen screen) throws Exception {
        var field = MasterTitleScreen.class.getDeclaredField("configService");
        field.setAccessible(true);
        return (SonicConfigurationService) field.get(screen);
    }

    private MasterTitleScreen screen(boolean standalone) throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setConfigValue(SonicConfiguration.SONIC_2_ROM, "missing-s2.gen");
        config.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "missing-s3k.gen");
        var entries = new ArrayList<MasterTitleEntry>();
        for (var game : MasterTitleScreen.GameEntry.values()) entries.add(new MasterTitleEntry.Stock(game));
        if (standalone) entries.add(new MasterTitleEntry.Standalone("sample", "Sample game", false));
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config), entries, cue -> {});
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var game : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(game, false);
        installFont(screen);
        return screen;
    }

    private void draw(MasterTitleScreen screen) {
        font.texts.clear();
        screen.drawForRecording();
    }

    private record Text(String text, float x, float y, float red) { }
    private static final class RecordingFont extends PixelFont {
        final List<Text> texts = new ArrayList<>();
        @Override public void beginMegaBatch() { }
        @Override public void endMegaBatch() { }
        @Override public void drawText(String text, float x, float y, float scale,
                float r, float g, float b, float a) { texts.add(new Text(text, x, y, r)); }
        boolean has(String text) { return texts.stream().anyMatch(t -> t.text.equals(text)); }
        String allText() { return texts.stream().map(Text::text).collect(java.util.stream.Collectors.joining()); }
        String wordText() { return texts.stream().map(Text::text).collect(java.util.stream.Collectors.joining(" ")); }
        float x(String text) { return texts.stream().filter(t -> t.text.equals(text)).findFirst().orElseThrow().x; }
    }
}

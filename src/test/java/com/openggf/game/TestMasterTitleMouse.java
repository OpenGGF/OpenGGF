package com.openggf.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The master title answers the mouse as well as the keyboard: hovering focuses, the left
 * button chooses, the right button goes back and the wheel changes game. Window points map
 * one to one onto menu pixels here, standing in for the letterboxed viewport mapping.
 */
class TestMasterTitleMouse {
    @TempDir
    Path directory;

    private final InputHandler input = new InputHandler();

    private MasterTitleScreen screen() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var game : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(game, true);
        screen.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 1});
        return screen;
    }

    private void moveTo(MasterTitleScreen screen, int x, int y) {
        input.handleMouseMove(x, y);
        screen.update(input);
        input.update();
    }

    private void click(MasterTitleScreen screen, int x, int y, int button) {
        moveTo(screen, x, y);
        input.handleMouseButton(button, GLFW_PRESS);
        screen.update(input);
        input.update();
        input.handleMouseButton(button, GLFW_RELEASE);
        screen.update(input);
        input.update();
    }

    private static int[] centre(MasterTitleScreen.Rect rect) {
        return new int[] {rect.x() + rect.w() / 2, rect.y() + rect.h() / 2};
    }

    /** Centre of hub action {@code index}, from the same layout drawHub uses. */
    private static int[] action(MasterTitleScreen screen, int index) {
        return centre(screen.hubLayout().actions().get(index));
    }

    private static int[] card(MasterTitleScreen screen, int offset) {
        return screen.hubLayout().cards().stream().filter(c -> c.offset() == offset)
                .map(c -> centre(c.rect())).findFirst().orElseThrow();
    }

    private static void settle(MasterTitleScreen screen) {
        for (int i = 0; i < 40; i++) screen.update(new InputHandler());
    }

    @Test
    void clickingAnActionRunsItAndHoveringFocusesIt() {
        var screen = screen();
        int[] setup = action(screen, 1);
        moveTo(screen, setup[0], setup[1]);
        assertEquals(TitleHubNavigation.Zone.ACTIONS, screen.hubZoneForTest(), "hovering focuses the button");
        assertFalse(screen.isLaunchConfigPanelOpenForTest(), "hovering alone does not open anything");
        click(screen, setup[0], setup[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(screen.isLaunchConfigPanelOpenForTest(), "Game Setup is the second action");
        assertFalse(screen.isGameSelected());
    }

    @Test
    void clickingStartLaunchesTheSelectedGame() {
        var screen = screen();
        int[] start = action(screen, 0);
        click(screen, start[0], start[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(screen.isGameSelected());
        assertEquals("s2", screen.getSelectedGameId());
    }

    @Test
    void sideCardsAndWheelChangeGameAndThePageDotsOpenTheBrowser() {
        var screen = screen();
        int[] left = card(screen, -1);
        click(screen, left[0], left[1], GLFW_MOUSE_BUTTON_LEFT);
        assertEquals("s1", screen.getSelectedGameId());
        assertFalse(screen.isGameSelected(), "a side card selects; it does not launch");
        settle(screen);
        int[] right = card(screen, 1);
        click(screen, right[0], right[1], GLFW_MOUSE_BUTTON_LEFT);
        assertEquals("s2", screen.getSelectedGameId());
        com.openggf.control.MouseWheel.of(input).scroll(-1); // towards the user: next game
        moveTo(screen, 300, 200);
        assertEquals("s3k", screen.getSelectedGameId());
        int[] dots = centre(screen.hubLayout().dots());
        click(screen, dots[0], dots[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(screen.isGameBrowserOpenForTest());
        click(screen, 100, 51 + 12, GLFW_MOUSE_BUTTON_LEFT); // first row: Sonic 1
        assertFalse(screen.isGameBrowserOpenForTest());
        assertEquals("s1", screen.getSelectedGameId());
        assertFalse(screen.isGameSelected(), "the browser picks a game; it does not launch it");
    }

    @Test
    void clickingTheCentreCardStartsAndAChipOpensItsSetupRow() {
        var screen = screen();
        int[] chip = centre(screen.hubLayout().chips().get(1));
        click(screen, chip[0], chip[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(screen.isLaunchConfigPanelOpenForTest());
        assertEquals(com.openggf.game.launch.LaunchProfile.Row.WIDESCREEN, screen.launchPanelRowForTest());

        var other = screen();
        int[] centre = card(other, 0);
        click(other, centre[0], centre[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(other.isGameSelected());
    }

    @Test
    void rightClickOpensTheQuitPromptAndItsRowsAnswerClicks() {
        var screen = screen();
        var exits = new AtomicInteger();
        click(screen, 10, 190, GLFW_MOUSE_BUTTON_RIGHT); // empty sky, not a control
        assertTrue(screen.isQuitPromptOpenForTest());
        click(screen, 160, 92 + 10, GLFW_MOUSE_BUTTON_LEFT); // "Return to menu"
        assertFalse(screen.isQuitPromptOpenForTest());
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(0, exits.get());
        click(screen, 10, 190, GLFW_MOUSE_BUTTON_RIGHT);
        click(screen, 160, 118 + 10, GLFW_MOUSE_BUTTON_LEFT); // "Quit"
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(1, exits.get());
    }

    @Test
    void rightClickAwayFromTheCardReturnsToTheCardFirst() {
        var screen = screen();
        int[] settingsTab = centre(screen.hubLayout().tabs().get(1));
        moveTo(screen, settingsTab[0], settingsTab[1]);
        assertEquals(TitleHubNavigation.Zone.TABS, screen.hubZoneForTest());
        click(screen, settingsTab[0], settingsTab[1], GLFW_MOUSE_BUTTON_RIGHT);
        assertFalse(screen.isQuitPromptOpenForTest(), "back to the card, not out of the menu");
        assertEquals(TitleHubNavigation.Zone.CARD, screen.hubZoneForTest());
        click(screen, 10, 190, GLFW_MOUSE_BUTTON_RIGHT);
        assertTrue(screen.isQuitPromptOpenForTest());
    }

    @Test
    void theLetterboxIgnoresTheMouse() {
        var screen = screen();
        screen.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 0});
        int[] start = action(screen, 0);
        click(screen, start[0], start[1], GLFW_MOUSE_BUTTON_LEFT);
        assertFalse(screen.isGameSelected());
    }
}

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
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
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

    /** Centre of hub action {@code index} (drawHub: x from the screen's middle, rows 20 apart from 39). */
    private static int[] action(int index) {
        return new int[] {160 + 60, 39 + index * 20 + 9};
    }

    @Test
    void clickingAnActionRunsItAndHoveringFocusesIt() {
        var screen = screen();
        int[] launch = action(1);
        moveTo(screen, launch[0], launch[1]);
        assertFalse(screen.isLaunchConfigPanelOpenForTest(), "hovering alone does not open anything");
        click(screen, launch[0], launch[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(screen.isLaunchConfigPanelOpenForTest(), "Launch Options is the second action");
        assertFalse(screen.isGameSelected());
    }

    @Test
    void clickingStartLaunchesTheSelectedGame() {
        var screen = screen();
        int[] start = action(0);
        click(screen, start[0], start[1], GLFW_MOUSE_BUTTON_LEFT);
        assertTrue(screen.isGameSelected());
        assertEquals("s2", screen.getSelectedGameId());
    }

    @Test
    void carouselArrowsAndWheelChangeGameAndItsNameOpensTheBrowser() {
        var screen = screen();
        click(screen, 10, 166, GLFW_MOUSE_BUTTON_LEFT); // the "<" arrow
        assertEquals("s1", screen.getSelectedGameId());
        click(screen, 8 + 144 - 4, 166, GLFW_MOUSE_BUTTON_LEFT); // the ">" arrow
        assertEquals("s2", screen.getSelectedGameId());
        com.openggf.control.MouseWheel.of(input).scroll(-1); // towards the user: next game
        moveTo(screen, 200, 120);
        assertEquals("s3k", screen.getSelectedGameId());
        click(screen, 80, 166, GLFW_MOUSE_BUTTON_LEFT); // the game's name
        assertTrue(screen.isGameBrowserOpenForTest());
        click(screen, 100, 51 + 12, GLFW_MOUSE_BUTTON_LEFT); // first row: Sonic 1
        assertFalse(screen.isGameBrowserOpenForTest());
        assertEquals("s1", screen.getSelectedGameId());
        assertFalse(screen.isGameSelected(), "the browser picks a game; it does not launch it");
    }

    @Test
    void rightClickOpensTheQuitPromptAndItsRowsAnswerClicks() {
        var screen = screen();
        var exits = new AtomicInteger();
        click(screen, 60, 60, GLFW_MOUSE_BUTTON_RIGHT); // over the game preview, not an action
        assertTrue(screen.isQuitPromptOpenForTest());
        click(screen, 100, 82 + 11, GLFW_MOUSE_BUTTON_LEFT); // "Return to menu"
        assertFalse(screen.isQuitPromptOpenForTest());
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(0, exits.get());
        click(screen, 60, 60, GLFW_MOUSE_BUTTON_RIGHT); // over the game preview, not an action
        click(screen, 100, 112 + 11, GLFW_MOUSE_BUTTON_LEFT); // "Quit"
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(1, exits.get());
    }

    @Test
    void rightClickInTheActionListReturnsToTheGameCarousel() {
        var screen = screen();
        int[] settings = action(5);
        moveTo(screen, settings[0], settings[1]);
        click(screen, settings[0], settings[1], GLFW_MOUSE_BUTTON_RIGHT);
        assertFalse(screen.isQuitPromptOpenForTest(), "back from the actions, not out of the menu");
        click(screen, 60, 60, GLFW_MOUSE_BUTTON_RIGHT);
        assertTrue(screen.isQuitPromptOpenForTest());
    }

    @Test
    void theLetterboxIgnoresTheMouse() {
        var screen = screen();
        screen.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 0});
        int[] start = action(0);
        click(screen, start[0], start[1], GLFW_MOUSE_BUTTON_LEFT);
        assertFalse(screen.isGameSelected());
    }
}

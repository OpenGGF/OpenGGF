package com.openggf.game;

import com.openggf.game.preview.TitlePreviewProvider.Image;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.game.TestMasterTitleHub.press;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** The card a held Escape leaves on the shelf: resume it, end it, or be asked before losing it. */
class TestMasterTitleSuspendedGame {
    @TempDir Path directory;

    private static final MasterTitleEntry.Suspended SONIC_2_EHZ = new MasterTitleEntry.Suspended(
            new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2), "Emerald Hill, Act 1", true);

    private final AtomicInteger ended = new AtomicInteger();

    private MasterTitleScreen screenWithSuspendedGame() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (MasterTitleScreen.GameEntry game : MasterTitleScreen.GameEntry.values()) {
            screen.setRomAvailableForTest(game, true);
        }
        screen.setSuspendedGame(SONIC_2_EHZ, 2, 2, new byte[16]);
        screen.setSuspendedGameEndHandler(ended::incrementAndGet);
        return screen;
    }

    @Test void zoomStartsAtTheGameFrameAndBlocksInputUntilItReachesTheCard() {
        for (int width : new int[] {320, 426}) {
            var screen = screenWithSuspendedGame();
            screen.setViewportWidth(width);
            screen.setSuspendedGame(SONIC_2_EHZ, width, 224, new byte[width * 224 * 4]);
            screen.animateSuspendedGame();
            var start = screen.suspendZoomLayout();
            assertEquals(width, start.width());
            assertEquals(224, start.height());
            assertEquals(0, start.x());
            assertEquals(0, start.y());
            var input = new com.openggf.control.InputHandler();
            input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
            for (int n = 0; n < MasterTitleScreen.SUSPEND_ZOOM_TICKS - 1; n++) screen.update(input);
            assertFalse(screen.consumeResumeRequest(), "Confirm cannot accidentally resume during the zoom");
            var almost = screen.suspendZoomLayout();
            assertTrue(almost.width() < start.width());
            var thumbnail = MasterTitleScreen.fitThumbnail(
                    new Image(width, 224, new byte[width * 224 * 4]), 126, 78);
            assertEquals(thumbnail.width(), almost.width(), 1);
            assertEquals(thumbnail.height(), almost.height(), 1);
            assertTrue(almost.height() < start.height());
            screen.update(input);
            input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE);
            screen.update(input);
            press(screen, GLFW_KEY_ENTER);
            finishResumeZoom(screen);
            assertTrue(screen.consumeResumeRequest());
        }
    }

    private static void finishResumeZoom(MasterTitleScreen screen) {
        var input = new com.openggf.control.InputHandler();
        for (int i = 0; i <= MasterTitleScreen.SUSPEND_ZOOM_TICKS; i++) screen.update(input);
    }

    @Test void resumeReversesTheZoomAndHandsOffOnlyAfterTheFullSizeFrame() {
        for (int width : new int[] {320, 426}) {
            var screen = screenWithSuspendedGame();
            screen.setViewportWidth(width);
            screen.setSuspendedGame(SONIC_2_EHZ, width, 224, new byte[width * 224 * 4]);
            screen.animateSuspendedGame();
            var input = new com.openggf.control.InputHandler();
            for (int i = 0; i < MasterTitleScreen.SUSPEND_ZOOM_TICKS; i++) screen.update(input);
            screen.update(input); // The menu becomes active after entry.
            var card = screen.suspendZoomLayout();
            input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
            screen.update(input);
            assertEquals(card, screen.suspendZoomLayout());
            input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE);
            input.update();
            for (int i = 0; i < MasterTitleScreen.SUSPEND_ZOOM_TICKS; i++) {
                assertFalse(screen.consumeResumeRequest());
                screen.update(input);
            }
            var full = screen.suspendZoomLayout();
            assertEquals(width, full.width());
            assertEquals(224, full.height());
            assertEquals(0, full.x());
            assertEquals(0, full.y());
            assertFalse(screen.consumeResumeRequest(), "the full-size frame must be rendered first");
            screen.update(input);
            assertTrue(screen.consumeResumeRequest());
            assertFalse(screen.consumeResumeRequest());
            screen.update(input);
            assertFalse(screen.consumeResumeRequest(), "the handoff is emitted only once");
        }
    }

    @Test void resumeWithoutAPictureStillWorks() {
        var screen = screenWithSuspendedGame();
        screen.setSuspendedGame(SONIC_2_EHZ, 0, 0, null);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.consumeResumeRequest());
    }

    @Test void theSuspendedGameSitsFirstAndSelectedAlongsideTheStockGames() {
        var screen = screenWithSuspendedGame();

        List<MasterTitleEntry> entries = screen.entriesForTest();
        assertEquals(SONIC_2_EHZ, entries.getFirst());
        assertEquals(4, entries.size(), "S1, S2 and S3K stay on the shelf");
        assertEquals("s2", screen.getSelectedGameId());
        assertEquals(List.of(TitleHubNavigation.Action.RESUME, TitleHubNavigation.Action.SETUP,
                TitleHubNavigation.Action.END), screen.hubActionsForTest());
    }

    @Test void enterOnThePausedCardResumesInsteadOfLaunching() {
        var screen = screenWithSuspendedGame();

        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.consumeResumeRequest(), "gameplay stays parked during the zoom");
        finishResumeZoom(screen);
        assertTrue(screen.consumeResumeRequest());
        assertFalse(screen.consumeResumeRequest(), "the host sees the request once");
        assertFalse(screen.isGameSelected(), "resuming is not a launch");
        assertEquals(0, ended.get());
    }

    @Test void startingAnotherGameAsksFirstAndKeepsTheGameByDefault() {
        var screen = screenWithSuspendedGame();
        press(screen, GLFW_KEY_LEFT); // the shelf wraps from the paused card to Sonic 3K

        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isEndPromptOpenForTest());
        press(screen, GLFW_KEY_ENTER); // "Keep it paused" is selected by default

        assertFalse(screen.isEndPromptOpenForTest());
        assertFalse(screen.isGameSelected());
        assertTrue(screen.hasSuspendedGame());
        assertEquals(0, ended.get());
    }

    @Test void endingTheGameThenStartsTheChosenOne() {
        var screen = screenWithSuspendedGame();
        press(screen, GLFW_KEY_LEFT);
        String chosen = screen.getSelectedGameId();

        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);

        assertEquals(1, ended.get(), "the host ends the suspended session before the launch");
        assertFalse(screen.hasSuspendedGame());
        assertEquals(3, screen.entriesForTest().size());
        assertTrue(screen.isGameSelected());
        assertEquals(chosen, screen.getSelectedGameId());
    }

    @Test void endGameButtonRemovesTheCardAndSelectsTheSameStockGame() {
        var screen = screenWithSuspendedGame();
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_RIGHT); // Resume, Game Setup, then End game
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isEndPromptOpenForTest());
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);

        assertEquals(1, ended.get());
        assertFalse(screen.hasSuspendedGame());
        assertEquals(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2),
                screen.entriesForTest().get(indexOfSelected(screen)));
        assertFalse(screen.isGameSelected(), "ending a game does not launch one");
    }

    @Test void modsAreAskedAboutBecauseTheyReplaceTheRunningContent() {
        var screen = screenWithSuspendedGame();
        var opened = new AtomicInteger();
        screen.setModManagerOpenHandler(opened::incrementAndGet);

        press(screen, GLFW_KEY_UP); // the Mods tab is first
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isEndPromptOpenForTest());
        assertEquals(0, opened.get());
        press(screen, GLFW_KEY_ESCAPE);
        assertFalse(screen.isEndPromptOpenForTest(), "Back keeps the game");
        assertEquals(0, ended.get());
    }

    @Test void settingsOpenWithoutEndingTheGame() {
        var screen = screenWithSuspendedGame();
        press(screen, GLFW_KEY_UP);
        press(screen, GLFW_KEY_RIGHT); // Settings
        press(screen, GLFW_KEY_ENTER);

        assertFalse(screen.isEndPromptOpenForTest(), "Settings apply to the next game, so nothing is lost");
        assertEquals(0, ended.get());
    }

    @Test void catalogRefreshKeepsThePausedCardFirstAndSelected() {
        var screen = screenWithSuspendedGame();
        screen.replaceEntries(List.of(
                new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_1),
                new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2)));

        assertEquals(SONIC_2_EHZ, screen.entriesForTest().getFirst());
        assertEquals(SONIC_2_EHZ, screen.entriesForTest().get(indexOfSelected(screen)));
    }

    @Test void thumbnailIsAveragedDownToTheCard() {
        byte[] rgba = new byte[320 * 224 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            int pixel = i / 4;
            byte value = (byte) ((pixel % 320 + pixel / 320) % 2 == 0 ? 0xFF : 0x00);
            rgba[i] = value; rgba[i + 1] = value; rgba[i + 2] = value; rgba[i + 3] = (byte) 0xFF;
        }
        var fitted = MasterTitleScreen.fitThumbnail(new Image(320, 224, rgba), 126, 78);

        assertTrue(fitted.width() <= 126 && fitted.height() <= 78);
        assertEquals(320 / (double) 224, fitted.width() / (double) fitted.height(), 0.03, "keeps the picture's shape");
        int grey = fitted.rgba()[(fitted.height() / 2 * fitted.width() + fitted.width() / 2) * 4] & 0xFF;
        assertTrue(grey > 60 && grey < 200, "a checkerboard averages to grey rather than aliasing, got " + grey);
    }

    private static int indexOfSelected(MasterTitleScreen screen) {
        return screen.selectedIndexForTest();
    }
}

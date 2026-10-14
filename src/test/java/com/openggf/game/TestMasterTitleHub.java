package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.LaunchToggle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestMasterTitleHub {
    @TempDir Path directory;

    private MasterTitleScreen screen() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (MasterTitleScreen.GameEntry game : MasterTitleScreen.GameEntry.values()) {
            screen.setRomAvailableForTest(game, true);
        }
        return screen;
    }

    @Test void extrasTabOpensTheChooserBeforeLaunchingAMod() {
        var screen = screen();
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        screen.setTitleEntries(List.of("Time Attack"), (games, index) -> {
            assertEquals(0, index);
            assertTrue(games.contains("s2"));
            opened.incrementAndGet();
        });
        press(screen, GLFW_KEY_UP);
        assertEquals(TitleHubNavigation.Zone.TABS, screen.hubZoneForTest());
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        assertEquals(0, opened.get(), "Extras opens the chooser instead of launching a specific mod");
        press(screen, GLFW_KEY_ENTER);
        assertEquals(1, opened.get());
    }

    @Test void leftRightChangesGameOnTheCardAndUpDownMovesBetweenRows() {
        var screen = screen();
        assertEquals(TitleHubNavigation.Zone.CARD, screen.hubZoneForTest(), "the hub opens on the game card");
        press(screen, GLFW_KEY_LEFT);
        assertEquals("s1", screen.getSelectedGameId());
        press(screen, GLFW_KEY_RIGHT);
        assertEquals("s2", screen.getSelectedGameId());

        press(screen, GLFW_KEY_DOWN);
        assertEquals(TitleHubNavigation.Zone.ACTIONS, screen.hubZoneForTest(),
                "the setup chips only report the launch; Down passes over them");
        press(screen, GLFW_KEY_ESCAPE);
        assertEquals(TitleHubNavigation.Zone.CARD, screen.hubZoneForTest(), "Back returns to the card");
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_UP);
        assertEquals(TitleHubNavigation.Zone.CARD, screen.hubZoneForTest(), "Up from the buttons skips the chips too");

        press(screen, GLFW_KEY_DOWN);
        assertEquals(TitleHubNavigation.Zone.ACTIONS, screen.hubZoneForTest());
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isLaunchConfigPanelOpenForTest(), "the second action is Game Setup");
        press(screen, GLFW_KEY_ESCAPE);
        assertFalse(screen.isLaunchConfigPanelOpenForTest(), "an unchanged setup closes without a prompt");
        press(screen, GLFW_KEY_LEFT);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected(), "the first action is Start");
    }

    @Test void enterOnTheCardStartsTheSelectedGame() {
        var screen = screen();
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
        assertEquals("s2", screen.getSelectedGameId());
    }

    @Test void standaloneGamesOnlyOfferStart() {
        var config = SonicConfigurationService.createStandalone(directory);
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config),
                List.of(new MasterTitleEntry.Standalone("example-game", "Example Game", false)));
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        press(screen, GLFW_KEY_DOWN);
        assertEquals(TitleHubNavigation.Zone.ACTIONS, screen.hubZoneForTest(), "no chips: down goes to the actions");
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected(), "Start is the only action; the chooser offers New Game");
    }

    @Test void quitIsExplicitCancelableAndDispatchedToTheHostOnce() {
        var screen = screen();
        var exits = new java.util.concurrent.atomic.AtomicInteger();
        press(screen, GLFW_KEY_ESCAPE);
        assertTrue(screen.isQuitPromptOpenForTest(), "Back on the card asks to quit");
        press(screen, GLFW_KEY_ENTER); // Return is selected by default.
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(0, exits.get());

        press(screen, GLFW_KEY_UP);
        for (int i = 0; i < 3; i++) press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isQuitPromptOpenForTest(), "the Quit tab opens the same prompt");
        press(screen, GLFW_KEY_ESCAPE);
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(0, exits.get());
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        TitleInputOwnership.routeQuit(screen, exits::incrementAndGet);
        assertEquals(1, exits.get());
        assertFalse(screen.isGameSelected());
    }

    @Test void gameSetupIsReachableWithoutShortcut() {
        var screen = screen();
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isLaunchConfigPanelOpenForTest());
        assertFalse(screen.isGameSelected());
    }

    @Test void theKeyboardPassesOverTheSetupChips() {
        var screen = screen();
        assertFalse(screen.hubChips().isEmpty(), "the selected game shows setup chips");
        press(screen, GLFW_KEY_DOWN);
        assertEquals(TitleHubNavigation.Zone.ACTIONS, screen.hubZoneForTest());
        press(screen, GLFW_KEY_UP);
        press(screen, GLFW_KEY_UP);
        assertEquals(TitleHubNavigation.Zone.TABS, screen.hubZoneForTest());
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_DOWN);
        assertEquals(TitleHubNavigation.Zone.ACTIONS, screen.hubZoneForTest(),
                "chips are reached by pointer only; clicking one is covered by TestMasterTitleMouse");
    }

    @Test void cancelledLaunchDraftDoesNotWriteAndFailedSaveCanBeRetried() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config) {
            boolean fail = true;
            @Override public void save(MasterTitleScreen.GameEntry game, LaunchProfile profile) {
                if (fail) {
                    fail = false;
                    throw new java.io.UncheckedIOException(new java.io.IOException("Read-only destination"));
                }
                super.save(game, profile);
            }
        };
        var screen = new MasterTitleScreen(config, store);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        press(screen, GLFW_KEY_TAB);
        for (int i = 0; i < 3; i++) press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.isLaunchConfigPanelOpenForTest(), "Discard closes Game Setup");
        assertEquals(LaunchToggle.DEFAULT, store.load(MasterTitleScreen.GameEntry.SONIC_2).rewind());

        press(screen, GLFW_KEY_TAB);
        for (int i = 0; i < 3; i++) press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isLaunchConfigPanelOpenForTest(), "failed save retains editable draft");
        assertEquals(LaunchToggle.ON, screen.currentLaunchProfileForTest().rewind());
        assertEquals(LaunchToggle.DEFAULT, store.load(MasterTitleScreen.GameEntry.SONIC_2).rewind());
        press(screen, GLFW_KEY_ESCAPE);
        press(screen, GLFW_KEY_ENTER);
        assertFalse(screen.isLaunchConfigPanelOpenForTest());
        assertEquals(LaunchToggle.ON, store.load(MasterTitleScreen.GameEntry.SONIC_2).rewind());
    }

    @Test void romLogosKeepTheirAspectRatioInsideTheCard() {
        for (int[] source : new int[][] {{320, 224}, {256, 224}, {320, 160}}) {
            var logo = MasterTitleScreen.cardLogoLayout(source[0], source[1], 100, 34, 124, 76);
            assertTrue(logo.width() <= 124 && logo.height() <= 76);
            assertEquals((double) source[0] / source[1], (double) logo.width() / logo.height(), .03);
            assertTrue(logo.x() >= 100 && logo.x() + logo.width() <= 224, "centred inside the card");
        }
    }

    static void press(MasterTitleScreen screen, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        screen.update(input);
    }
}

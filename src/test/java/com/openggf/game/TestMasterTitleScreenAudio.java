package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfileStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/** Interaction-level coverage for host-owned master-title menu feedback. */
class TestMasterTitleScreenAudio {

    private final AudioManager audio = AudioManager.getInstance();

    @BeforeEach
    void clearPriorAudio() {
        audio.resetState();
    }

    @AfterEach
    void resetAudio() {
        audio.resetState();
    }

    @Test
    void navigationAndRomLocationEmitConfirmationWithoutDuplicates() {
        MasterTitleScreen screen = activeScreen(true);
        InputHandler input = new InputHandler();

        pressFrame(screen, input, GLFW_KEY_DOWN); // past the chips to the actions, on Start
        pressFrame(screen, input, GLFW_KEY_ENTER);
        // CONFIRMING consumes another press without replaying the cue.
        pressFrame(screen, input, GLFW_KEY_ENTER);

        MasterTitleScreen missingRomScreen = activeScreen(false);
        pressFrame(missingRomScreen, input, GLFW_KEY_DOWN); // no chips without a ROM
        pressFrame(missingRomScreen, input, GLFW_KEY_ENTER);

        assertEquals(List.of("UI_NAVIGATE", "UI_CONFIRM", "UI_NAVIGATE", "UI_CONFIRM"),
                emittedSfxNames());
    }

    @Test
    void carouselWrapEmitsOneNavigationCue() {
        MasterTitleScreen screen = activeScreen(true);
        screen.setSelectedIndexForTest(MasterTitleScreen.GameEntry.SONIC_3K.ordinal());
        InputHandler input = new InputHandler();

        pressFrame(screen, input, GLFW_KEY_RIGHT);

        assertEquals("s1", screen.getSelectedGameId());
        assertEquals(List.of("UI_NAVIGATE"), emittedSfxNames());
    }

    @Test
    void repeatedRomLoadErrorDoesNotReplayTheErrorCue() {
        MasterTitleScreen screen = activeScreen(true);

        screen.showRomLoadError("s2");
        screen.showRomLoadError("s2");

        assertEquals(List.of("UI_ERROR"), emittedSfxNames());
    }

    @Test
    void openingAndCancellingLaunchOptionsUseConfirmAndCancelOnce() {
        MasterTitleScreen screen = activeScreen(true);
        InputHandler input = new InputHandler();
        pressFrame(screen, input, GLFW_KEY_DOWN);
        pressFrame(screen, input, GLFW_KEY_RIGHT);
        pressFrame(screen, input, GLFW_KEY_ENTER);
        pressFrame(screen, input, GLFW_KEY_ESCAPE); // leave an unchanged Game Setup
        pressFrame(screen, input, GLFW_KEY_ESCAPE); // back from the actions to the card
        assertEquals(List.of("UI_NAVIGATE", "UI_NAVIGATE", "UI_CONFIRM", "UI_CANCEL", "UI_CANCEL"),
                emittedSfxNames());
    }

    @Test
    void settingsAndToolsEnterWithConfirmationAndBackWithCancel() {
        MasterTitleScreen screen = activeScreen(true);
        InputHandler input = new InputHandler();
        pressFrame(screen, input, GLFW_KEY_UP);    // tabs, on Mods
        pressFrame(screen, input, GLFW_KEY_RIGHT); // Settings
        audio.resetState();
        pressFrame(screen, input, GLFW_KEY_ENTER);
        pressFrame(screen, input, GLFW_KEY_ESCAPE);
        pressFrame(screen, input, GLFW_KEY_RIGHT); // Advanced
        pressFrame(screen, input, GLFW_KEY_ENTER);
        pressFrame(screen, input, GLFW_KEY_DOWN);
        pressFrame(screen, input, GLFW_KEY_DOWN);  // Controls / help
        pressFrame(screen, input, GLFW_KEY_ENTER);
        pressFrame(screen, input, GLFW_KEY_ESCAPE);
        assertEquals(List.of("UI_CONFIRM", "UI_CANCEL", "UI_NAVIGATE", "UI_CONFIRM",
                "UI_NAVIGATE", "UI_NAVIGATE", "UI_CONFIRM", "UI_CANCEL"), emittedSfxNames());
    }

    @Test
    void cancellingRomLocationEmitsCancel() {
        MasterTitleScreen screen = activeScreen(false);
        InputHandler input = new InputHandler();
        pressFrame(screen, input, GLFW_KEY_DOWN);
        pressFrame(screen, input, GLFW_KEY_ENTER);
        pressFrame(screen, input, GLFW_KEY_ESCAPE);
        assertEquals(List.of("UI_NAVIGATE", "UI_CONFIRM", "UI_CANCEL"), emittedSfxNames());
    }

    @Test
    void programmaticLaunchDoesNotReplayThePickersConfirmation() {
        MasterTitleScreen screen = activeScreen(true);
        screen.selectEntry(MasterTitleScreen.GameEntry.SONIC_2);
        assertEquals(List.of(), emittedSfxNames());
    }

    @Test
    void unavailableLaunchOptionsShortcutOpensRomLocator() {
        MasterTitleScreen screen = activeScreen(false);
        screen.setSelectedIndexForTest(MasterTitleScreen.GameEntry.SONIC_2.ordinal());
        pressFrame(screen, new InputHandler(), GLFW_KEY_TAB);
        assertTrue(screen.isRomLocatorOpenForTest());
        assertEquals(List.of("UI_CONFIRM"), emittedSfxNames());
    }

    private MasterTitleScreen activeScreen(boolean selectedRomAvailable) {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        MasterTitleScreen screen = new MasterTitleScreen(config, new LaunchProfileStore(config),
                cue -> audio.playSfx(cue.sfxName()));
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, selectedRomAvailable);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_3K, true);
        return screen;
    }

    private static void pressFrame(MasterTitleScreen screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        screen.update(input); // A held key must not replay the transition cue.
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        screen.update(input);
    }

    private List<String> emittedSfxNames() {
        return audio.commandTimeline().entries().stream()
                .map(entry -> entry.command())
                .filter(AudioCommand.PlaySfx.class::isInstance)
                .map(AudioCommand.PlaySfx.class::cast)
                .map(AudioCommand.PlaySfx::sfxName)
                .toList();
    }
}

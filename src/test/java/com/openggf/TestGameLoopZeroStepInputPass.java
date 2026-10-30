package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.*;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.lwjgl.glfw.GLFW.*;

class TestGameLoopZeroStepInputPass {
    @AfterEach void clearSession() { SessionManager.clear(); }

    @Test
    void repeatedZeroStepServiceCannotPullALaterPadPressAheadOfAnEarlierKeyboardRelease() {
        SessionManager.clear();
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        boolean[] held = {false};
        var input = new InputHandler(InputBindingFactory.supplier(config), () -> {
            var buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_A] = held[0];
            return List.of(GamepadStateSource.DeviceState.connected(0, "pad", buttons, 0, 0));
        });
        var pacing = new GameplayMutatorPacing(() -> GameplayMutatorPolicy.STOCK);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        held[0] = true; input.pollPhysicalGamepads();
        for (int i = 0; i < 3; i++) {
            GameLoopZeroStepPresentation.run(GameMode.LEVEL, input, config, mock(PlaybackDebugManager.class),
                    false, new EscapeToMasterTitleController(() -> false, () -> {}), overlay -> false, () -> false,
                    paused -> fail("no pause requested"), () -> {}, () -> true, pacing);
            assertEquals(InputActionMasks.ACTION_A, pacing.pendingPlayer1().actionPressedMask(),
                    "zero-step retention previews the first prefix, rather than the later physical pad state");
        }
        InputHandlerInternalAccess.retainGameplayInput(input, pacing.pendingPlayer1(), pacing.pendingPlayer2());
        pacing.clearPendingInput();
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
            input.update();
        }
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_B, input.logical().player1().actionPressedMask());
            assertEquals(InputActionMasks.ACTION_B, input.logical().player1().actionHeldMask());
            input.update();
        }
    }

    @Test
    void zeroStepHostStartUnpausesOnceWithoutPublishingTheSameEdgeAgain() {
        SessionManager.clear();
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        var input = new InputHandler(InputBindingFactory.supplier(config), () -> {
            var buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_START] = true;
            return List.of(GamepadStateSource.DeviceState.connected(0, "pad", buttons, 0, 0));
        });
        var pacing = new GameplayMutatorPacing(() -> GameplayMutatorPolicy.STOCK);
        var playback = mock(PlaybackDebugManager.class);
        var paused = new java.util.concurrent.atomic.AtomicBoolean(true);
        GameLoopZeroStepPresentation.run(GameMode.LEVEL, input, config, playback, true,
                new EscapeToMasterTitleController(() -> false, () -> {}), overlay -> false, () -> false,
                paused::set, () -> {}, () -> true, pacing);
        assertFalse(paused.get());
        assertFalse(pacing.pendingPlayer1().startPressed());
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertFalse(input.logical().player1().startPressed());
            assertFalse(GameLoopPauseInput.nextUserPaused(GameMode.LEVEL, input, config, false,
                    false, false, playback, () -> false));
            input.update();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void zeroStepHostRereadRetainsPadAndKeyboardTapOnlyInTheExistingPacingOwner(boolean engineHandoff) {
        SessionManager.clear();
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        boolean[] held = {true};
        var input = new InputHandler(InputBindingFactory.supplier(config), () -> {
            var buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_A] = held[0];
            return List.of(GamepadStateSource.DeviceState.connected(0, "pad", buttons, 0, 0));
        });
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        var pacing = new GameplayMutatorPacing(() -> GameplayMutatorPolicy.STOCK);
        Runnable zeroStep = () -> GameLoopZeroStepPresentation.run(GameMode.LEVEL, input, config, mock(PlaybackDebugManager.class),
                false, new EscapeToMasterTitleController(() -> false, () -> { }), overlay -> false, () -> false,
                paused -> fail("no pause requested"), input::refreshLogicalSnapshot, () -> true, pacing);
        if (engineHandoff) {
            try (var host = InputHandlerInternalAccess.openHostInputPass(input)) {
                input.refreshLogicalSnapshot();
                host.handoff(zeroStep);
            }
        } else zeroStep.run();
        int both = InputActionMasks.ACTION_A | InputActionMasks.ACTION_B;
        assertEquals(both, pacing.pendingPlayer1().actionPressedMask());
        assertFalse(input.isPhysicalKeyPressed(GLFW_KEY_SPACE));
        held[0] = false;
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE);
        var saved = pacing.capture();
        InputHandlerInternalAccess.retainGameplayInput(input, pacing.pendingPlayer1(), pacing.pendingPlayer2());
        pacing.clearPendingInput();
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot(); input.refreshLogicalSnapshot();
            assertEquals(both, input.logical().player1().actionPressedMask());
            assertEquals(both, input.logical().player1().actionHeldMask(),
                    "the first ordered press precedes its observed release");
            input.update();
        }
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertEquals(0, input.logical().player1().actionPressedMask());
            input.update();
        }
        pacing.restore(saved);
        assertEquals(both, pacing.pendingPlayer1().actionPressedMask(), "rewind still restores the existing owner");
    }
}

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.lwjgl.glfw.GLFW.*;

class TestGameLoopZeroStepInputPass {
    @AfterEach void clearSession() { SessionManager.clear(); }

    @Test void zeroStepHostRereadRetainsPadAndKeyboardTapOnlyInTheExistingPacingOwner() {
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
        GameLoopZeroStepPresentation.run(GameMode.LEVEL, input, config, mock(PlaybackDebugManager.class),
                false, new EscapeToMasterTitleController(() -> false, () -> { }), overlay -> false, () -> false,
                paused -> fail("no pause requested"), input::refreshLogicalSnapshot, () -> true, pacing);
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
            assertEquals(0, input.logical().player1().actionHeldMask());
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

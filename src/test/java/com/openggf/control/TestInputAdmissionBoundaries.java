package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Characterizes the current boundary, including its limitations, before render and tick
 * admission separate. These are not acceptance tests for an independent render scheduler.
 */
class TestInputAdmissionBoundaries {
    @TempDir Path configDir;

    @Test
    void physicalObservationDoesNotAcknowledgeLogicalEdgesOrAdvanceTextClaims() {
        var config = configuration();
        var pads = new MutablePads();
        var input = new InputHandler(InputBindingFactory.supplier(config), pads);
        input.refreshLogicalSnapshot();
        input.update();
        input.captureKeyboardText();
        int actionKey = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(actionKey, GLFW_PRESS);
        MenuInput.handleCharEvent(input, 'x');
        pads.actionB = true;

        for (int observation = 0; observation < 3; observation++) {
            input.pollPhysicalGamepads();
            input.capturePhysicalInput();
        }

        assertTrue(input.isKeyboardTextCaptured(), "observation must not expire the text claim");
        assertFalse(input.isPhysicalKeyPressed(actionKey), "the text owner still hides the binding");
        assertTrue(input.isCapturedKeyPressed(actionKey), "physical capture did not acknowledge the key edge");
        assertEquals("x", input.consumeTypedText(), "observation did not discard committed text");
        input.refreshLogicalSnapshot();
        assertEquals(InputActionMasks.ACTION_B, input.logical().player1().actionPressedMask(),
                "physical polls did not acknowledge the pad edge, and the keyboard claim still applies");
        assertTrue(input.capturePhysicalInput().events().isEmpty(), "the earlier physical capture drained events");
    }

    @Test
    void logicalRefreshAcknowledgesPadEdgesWhileKeyboardWaitsForUpdate() {
        var config = configuration();
        var pads = new MutablePads();
        var input = new InputHandler(InputBindingFactory.supplier(config), pads);
        input.refreshLogicalSnapshot();
        input.update();
        int actionKey = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(actionKey, GLFW_PRESS);
        pads.actionB = true;

        input.refreshLogicalSnapshot();
        var first = input.logical();
        input.refreshLogicalSnapshot();
        var second = input.logical();

        int bothActions = InputActionMasks.ACTION_A | InputActionMasks.ACTION_B;
        assertEquals(bothActions, first.player1().actionPressedMask());
        assertEquals(InputActionMasks.ACTION_A, second.player1().actionPressedMask(),
                "a second refresh consumes the pad edge but repeats the keyboard edge");
        assertEquals(bothActions, second.player1().actionHeldMask());
        assertTrue(input.isPhysicalKeyPressed(actionKey));
        input.update();
        assertSame(second, input.logical(), "update acknowledges raw keys, not the cached logical sample");
        assertFalse(input.isPhysicalKeyPressed(actionKey));
        input.refreshLogicalSnapshot();
        assertEquals(0, input.logical().player1().actionPressedMask());
        assertEquals(bothActions, input.logical().player1().actionHeldMask());
    }

    @Test
    void releasedTapIsOrderedPhysicalEvidenceButIsAbsentFromFreshLogicalSample() {
        var config = configuration();
        var pads = new MutablePads();
        var clock = new AtomicLong(100);
        var input = new InputHandler(InputBindingFactory.supplier(config), pads, clock::get);
        input.pollPhysicalGamepads();
        input.refreshLogicalSnapshot();
        input.update();
        int actionKey = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(actionKey, GLFW_PRESS);
        pads.actionB = true;
        input.pollPhysicalGamepads();
        clock.set(200);
        input.handleKeyEvent(actionKey, GLFW_RELEASE);
        pads.actionB = false;
        input.pollPhysicalGamepads();
        input.refreshLogicalSnapshot();

        assertEquals(PlayerInputState.neutral(), input.logical().player1(),
                "held-state mapping cannot recover a tap released before logical admission");
        var physical = input.capturePhysicalInput();
        assertEquals(List.of(PhysicalInputEvent.Kind.KEY, PhysicalInputEvent.Kind.BUTTON,
                        PhysicalInputEvent.Kind.KEY, PhysicalInputEvent.Kind.BUTTON),
                physical.events().stream().map(PhysicalInputEvent::kind).toList());
        assertEquals(List.of(true, true, false, false),
                physical.events().stream().map(PhysicalInputEvent::pressed).toList());
        assertEquals(List.of(100L, 100L, 200L, 200L),
                physical.events().stream().map(PhysicalInputEvent::timestampNanos).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L),
                physical.events().stream().map(PhysicalInputEvent::sequence).toList(),
                "sequence preserves callback/poll order even when observation times tie");
        assertTrue(input.capturePhysicalInput().events().isEmpty(), "scene capture has its own acknowledgement");
    }

    @Test
    void sceneCaptureAndLogicalRefreshLeaveEachOthersInputAvailable() {
        var config = configuration();
        var input = new InputHandler(InputBindingFactory.supplier(config));
        int actionKey = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(actionKey, GLFW_PRESS);

        var physical = input.capturePhysicalInput();
        input.refreshLogicalSnapshot();

        assertEquals(1, physical.events().size());
        assertTrue(input.capturePhysicalInput().events().isEmpty());
        assertTrue(input.isPhysicalKeyPressed(actionKey));
        assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
        input.handleKeyEvent(actionKey, GLFW_RELEASE);
        input.refreshLogicalSnapshot();
        assertTrue(input.capturePhysicalInput().events().getFirst().released(),
                "refreshing logical state does not drain scene transitions");
    }

    @Test
    void retainedPlayerHandoffIsNotASampleForArbitraryMenuConsumers() {
        var config = configuration();
        var input = new InputHandler(InputBindingFactory.supplier(config));
        var tap = PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_A, false, false);
        InputHandlerInternalAccess.retainGameplayInput(input, tap, PlayerInputState.neutral());
        input.refreshLogicalSnapshot();

        assertFalse(input.logical().menuAccept(), "the player handoff preserves fresh snapshot menu flags");
        assertTrue(MenuInput.accept(input),
                "MenuInput derives mapped accept from player edges, so routing still owns menu exclusion");
        input.update();
        input.refreshLogicalSnapshot();
        assertFalse(MenuInput.accept(input), "the handoff is consumed on the first live refresh");
    }

    @Test
    void updateWithoutSceneConsumptionDiscardsTransitionsAndTextEvenUnderReplayOverride() {
        var config = configuration();
        var input = new InputHandler(InputBindingFactory.supplier(config));
        var recorded = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral());
        input.setLogicalOverride(recorded);
        int actionKey = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(actionKey, GLFW_PRESS);
        MenuInput.handleCharEvent(input, 'x');
        input.update();
        input.refreshLogicalSnapshot();

        assertSame(recorded, input.logical(), "only the replay owner replaces its logical sample");
        assertTrue(input.capturePhysicalInput().events().isEmpty(), "tick completion retires unconsumed scene edges");
        assertEquals("", input.consumeTypedText());
        assertTrue(input.isPhysicalKeyDown(actionKey), "physical held state stays independent of replay");
        assertFalse(input.isPhysicalKeyPressed(actionKey));
    }

    private SonicConfigurationService configuration() {
        var config = SonicConfigurationService.createStandalone(configDir);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        return config;
    }

    private static final class MutablePads implements GamepadStateSource {
        boolean actionB;

        @Override public List<DeviceState> pollDevices() {
            boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_A] = actionB;
            return List.of(DeviceState.connected(0, "pad", buttons, 0, 0));
        }
    }
}

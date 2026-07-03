package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

class TestInputHandlerLogicalSnapshot {

    @Test
    void refreshLogicalSnapshotReadsCurrentKeyboardState() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));

        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS);
        input.refreshLogicalSnapshot();

        assertEquals(
                AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                input.logical().player1().heldMask());
    }

    @Test
    void logicalOverrideSuppressesLiveInputForPlaybackOwnedFrame() {
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_LEFT, 0, 0, 0, false, false),
                PlayerInputState.neutral()));

        input.refreshLogicalSnapshot();

        assertEquals(AbstractPlayableSprite.INPUT_LEFT, input.logical().player1().heldMask());
        input.clearLogicalOverride();
        assertFalse(input.hasLogicalOverride());
    }

    @Test
    void logicalOverrideCarriesRecordedDebugModifiers() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        int debugModeKey = config.getInt(SonicConfiguration.DEBUG_MODE_KEY);
        input.setLogicalOverride(LogicalInputSnapshot.neutral().withDebugInput(true, true, true));

        assertTrue(input.isShiftDown());
        assertTrue(input.isControlDown());
        assertTrue(input.isKeyPressed(debugModeKey));

        input.clearLogicalOverride();

        assertFalse(input.isShiftDown());
        assertFalse(input.isControlDown());
    }

    @Test
    void logicalOverrideLeavesUnrelatedRawKeysLive() {
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
        input.setLogicalOverride(LogicalInputSnapshot.neutral());

        assertTrue(input.isKeyPressed(GLFW_KEY_F1));
    }

    @Test
    void logicalOverrideDiscardsGamepadPressedEdgesBeforeReturningToLiveInput() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), new GamepadInputManager(source));
        source.setDevices(connectedPad(0, buttons(GLFW_GAMEPAD_BUTTON_X)));
        input.setLogicalOverride(LogicalInputSnapshot.neutral());

        input.refreshLogicalSnapshot();
        input.clearLogicalOverride();
        input.refreshLogicalSnapshot();

        assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionHeldMask());
        assertEquals(0, input.logical().player1().actionPressedMask());
    }

    @Test
    void menuAcceptExcludingBackActionAcceptsOnlyABOrStart() {
        InputHandler input = new InputHandler();

        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_C, false, false),
                PlayerInputState.neutral()));
        assertFalse(input.menuAcceptExcludingBackAction());

        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_A, false, false),
                PlayerInputState.neutral()));
        assertTrue(input.menuAcceptExcludingBackAction());

        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_B, false, false),
                PlayerInputState.neutral()));
        assertTrue(input.menuAcceptExcludingBackAction());

        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, false, true),
                PlayerInputState.neutral()));
        assertTrue(input.menuAcceptExcludingBackAction());
    }

    @Test
    void gamepadNorthButtonTriggersDebugModeKeyPressedEdge() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), new GamepadInputManager(source));
        int debugModeKey = config.getInt(SonicConfiguration.DEBUG_MODE_KEY);

        source.setDevices(connectedPad(0, buttons(GLFW_GAMEPAD_BUTTON_Y)));
        input.refreshLogicalSnapshot();

        assertTrue(input.isKeyPressed(debugModeKey));
    }

    @Test
    void gamepadLeftBumperHoldsRewindKeyDown() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), new GamepadInputManager(source));
        int rewindKey = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);

        source.setDevices(connectedPad(0, buttons(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)));
        input.refreshLogicalSnapshot();

        assertTrue(input.isKeyDown(rewindKey));

        source.setDevices(connectedPad(0, buttons()));
        input.refreshLogicalSnapshot();

        assertFalse(input.isKeyDown(rewindKey));
    }

    @Test
    void gamepadRightBumperTriggersFrameStepKeyPressedEdge() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), new GamepadInputManager(source));
        int frameStepKey = config.getInt(SonicConfiguration.FRAME_STEP_KEY);

        source.setDevices(connectedPad(0, buttons(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)));
        input.refreshLogicalSnapshot();

        assertTrue(input.isKeyPressed(frameStepKey));
    }

    @Test
    void gamepadBackButtonPressedEdgeIsExposedAndSuppressedUnderOverride() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        FakeGamepadStateSource source = new FakeGamepadStateSource();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config), new GamepadInputManager(source));

        source.setDevices(connectedPad(0, buttons(GLFW_GAMEPAD_BUTTON_BACK)));
        input.refreshLogicalSnapshot();

        assertTrue(input.isGamepadBackButtonPressed());

        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        assertFalse(input.isGamepadBackButtonPressed());
    }

    private static GamepadStateSource.DeviceState connectedPad(int joystickId, boolean[] buttons) {
        return GamepadStateSource.DeviceState.connected(joystickId, "pad-" + joystickId, buttons, 0.0f, 0.0f);
    }

    private static boolean[] buttons(int... pressedButtons) {
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        for (int button : pressedButtons) {
            buttons[button] = true;
        }
        return buttons;
    }

    private static final class FakeGamepadStateSource implements GamepadStateSource {
        private final List<DeviceState> devices = new ArrayList<>();

        void setDevices(DeviceState... devices) {
            this.devices.clear();
            this.devices.addAll(List.of(devices));
        }

        @Override
        public List<DeviceState> pollDevices() {
            return List.copyOf(devices);
        }
    }
}

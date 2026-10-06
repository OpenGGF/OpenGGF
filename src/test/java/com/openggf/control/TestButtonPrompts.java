package com.openggf.control;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.*;

/** Per-player prompt labels follow the live bindings and each player's last intentional device. */
class TestButtonPrompts {
    private final AtomicReference<List<GamepadStateSource.DeviceState>> pads = new AtomicReference<>(List.of());
    private final AtomicReference<InputBindings> bindings = new AtomicReference<>(bindings(GLFW_KEY_SPACE, GLFW_KEY_RIGHT_SHIFT, GLFW_KEY_R));
    private final InputHandler input = new InputHandler(bindings::get, pads::get);

    private static InputBindings bindings(int p1A, int p2A, int rewind) {
        return new InputBindings(GLFW_KEY_UP, GLFW_KEY_DOWN, GLFW_KEY_LEFT, GLFW_KEY_RIGHT, p1A, GLFW_KEY_A, GLFW_KEY_ENTER,
                GLFW_KEY_ENTER, GLFW_KEY_I, GLFW_KEY_K, GLFW_KEY_J, GLFW_KEY_L, p2A, GLFW_KEY_RIGHT_CONTROL,
                GLFW_KEY_RIGHT_ALT, GLFW_KEY_KP_ENTER, true, 0.35, "auto", "auto", GLFW_KEY_F2, rewind, GLFW_KEY_F3);
    }
    private static GamepadStateSource.DeviceState pad(int id, String name, int... down) {
        boolean[] buttons = new boolean[15];
        for (int button : down) buttons[button] = true;
        return GamepadStateSource.DeviceState.connected(id, name, buttons, 0, 0);
    }
    private void frame() { input.refreshLogicalSnapshot(); input.update(); }
    private String label(int player, ButtonPrompts.Button button) { return ButtonPrompts.label(input, player, button).orElse("<unbound>"); }

    @Test void keyboardPlayersSeeTheirOwnBoundActionKeys() {
        frame();
        assertEquals("Space", label(0, ButtonPrompts.Button.A));
        assertEquals("Right Shift", label(1, ButtonPrompts.Button.A), "P2 uses P2's binding, not menu Enter");
        assertEquals("Keypad Enter", label(1, ButtonPrompts.Button.START));
        assertEquals("R", label(0, ButtonPrompts.Button.REWIND));
    }

    @Test void remappedAndUnboundKeysAreDerivedFromLiveBindings() {
        bindings.set(bindings(GLFW_KEY_F5, GLFW_KEY_LEFT_BRACKET, -1)); frame();
        assertEquals("F5", label(0, ButtonPrompts.Button.A));
        assertEquals("Left Bracket", label(1, ButtonPrompts.Button.A));
        assertEquals("<unbound>", label(0, ButtonPrompts.Button.REWIND), "no key and no pad means no prompt");
        bindings.set(bindings(-1, GLFW_KEY_RIGHT_SHIFT, -1)); frame();
        assertEquals(Optional.empty(), ButtonPrompts.label(input, 0, ButtonPrompts.Button.A));
        assertEquals(Optional.empty(), ButtonPrompts.keyName(0));
        assertEquals("Key 200", ButtonPrompts.keyName(200).orElseThrow(), "unknown codes stay identifiable");
        assertThrows(IllegalArgumentException.class, () -> ButtonPrompts.label(input, 2, ButtonPrompts.Button.A));
    }

    @Test void eachPlayersPadFamilyNamesThePhysicalButtonThatProducesTheAction() {
        pads.set(List.of(pad(0, "Xbox Wireless Controller"), pad(1, "Sony DualSense Wireless Controller"))); frame();
        assertEquals("Space", label(0, ButtonPrompts.Button.A), "connection alone is not an intentional device choice");
        pads.set(List.of(pad(0, "Xbox Wireless Controller", GLFW_GAMEPAD_BUTTON_X),
                pad(1, "Sony DualSense Wireless Controller", GLFW_GAMEPAD_BUTTON_X))); frame();
        assertEquals("X", label(0, ButtonPrompts.Button.A), "Genesis A is the west button");
        assertEquals("Square", label(1, ButtonPrompts.Button.A));
        assertEquals("Cross", label(1, ButtonPrompts.Button.B));
        assertEquals("Options", label(1, ButtonPrompts.Button.START));
        assertEquals("LB", label(0, ButtonPrompts.Button.REWIND), "primary pad owns the rewind bumper");
        assertEquals("R", label(1, ButtonPrompts.Button.REWIND), "a secondary pad has no rewind bumper");
    }

    @Test void deviceChoiceIsTrackedPerPlayerAndSwitchesOnTheNextIntentionalPress() {
        pads.set(List.of(pad(0, "Switch Pro Controller"))); frame();
        pads.set(List.of(pad(0, "Switch Pro Controller", GLFW_GAMEPAD_BUTTON_DPAD_DOWN))); frame();
        assertEquals("West", label(0, ButtonPrompts.Button.A), "unknown families use the standardized position");
        assertEquals("Right Shift", label(1, ButtonPrompts.Button.A), "P2 has no pad assigned");
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_PRESS); frame();
        assertEquals("Space", label(0, ButtonPrompts.Button.A), "a keyboard press switches P1 back");
        input.handleKeyEvent(GLFW_KEY_SPACE, GLFW_RELEASE); frame();
        pads.set(List.of(pad(0, "Switch Pro Controller"))); frame();
        pads.set(List.of(pad(0, "Switch Pro Controller", GLFW_GAMEPAD_BUTTON_START))); frame();
        assertEquals("West", label(0, ButtonPrompts.Button.A));
        pads.set(List.of()); frame();
        assertEquals("Space", label(0, ButtonPrompts.Button.A), "a disconnected pad falls back to the bound key");
    }

    @Test void replayOverridesDoNotChangeTheRememberedDevice() {
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false),
                PlayerInputState.neutral()));
        frame();
        assertEquals("Space", label(0, ButtonPrompts.Button.A));
    }
}

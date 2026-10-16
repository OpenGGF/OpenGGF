package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

/** A window-level editor front end owns the pads: mappings go neutral, physical state remains. */
class TestInputHandlerGamepadSuppression {
    private final boolean[] buttons = new boolean[15];

    private InputHandler input() {
        GamepadStateSource source = () -> List.of(GamepadStateSource.DeviceState.connected(
                0, "Xbox Wireless Controller", buttons, new float[] {0, 0, 0, 0, -1, -1}));
        return new InputHandler(InputBindingFactory.standaloneSupplier(), source);
    }

    @Test
    void suppressedPadIsNeutralForMappingsButStillPhysicallyVisible() {
        InputHandler input = input();
        input.refreshLogicalSnapshot();
        input.update();
        InputHandlerInternalAccess.suppressGamepad(input, true);
        buttons[GLFW_GAMEPAD_BUTTON_DPAD_RIGHT] = true;
        buttons[GLFW_GAMEPAD_BUTTON_A] = true;
        buttons[GLFW_GAMEPAD_BUTTON_Y] = true;
        buttons[GLFW_GAMEPAD_BUTTON_BACK] = true;
        buttons[GLFW_GAMEPAD_BUTTON_LEFT_BUMPER] = true;
        input.refreshLogicalSnapshot();

        assertEquals(0, input.logical().player1().heldMask(), "Genesis directions ignore the owned pad");
        assertEquals(0, input.logical().player1().actionHeldMask(), "Genesis buttons ignore the owned pad");
        assertFalse(MenuInput.details(input), "The north button no longer opens menus' details");
        assertFalse(input.isGamepadBackButtonPressed());
        assertFalse(input.isRewindHeld(), "The rewind bumper is the front end's while it owns the pads");
        PhysicalGamepad pad = input.capturePhysicalInput().gamepads().getFirst();
        assertTrue(pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_RIGHT), "Physical state stays available to the owner");
        assertTrue(MenuInput.controller(input), "Prompt-family tracking still follows the pad");
    }

    @Test
    void keyboardStillMapsWhileThePadIsSuppressed() {
        InputHandler input = input();
        InputHandlerInternalAccess.suppressGamepad(input, true);
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT,
                input.logical().player1().heldMask() & AbstractPlayableSprite.INPUT_RIGHT);
    }

    @Test
    void releasingSuppressionRestoresThePadMapping() {
        InputHandler input = input();
        InputHandlerInternalAccess.suppressGamepad(input, true);
        buttons[GLFW_GAMEPAD_BUTTON_DPAD_RIGHT] = true;
        input.refreshLogicalSnapshot();
        InputHandlerInternalAccess.suppressGamepad(input, false);
        input.refreshLogicalSnapshot();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT,
                input.logical().player1().heldMask() & AbstractPlayableSprite.INPUT_RIGHT);
    }
}

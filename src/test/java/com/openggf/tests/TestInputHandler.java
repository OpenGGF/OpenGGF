package com.openggf.tests;

import org.junit.jupiter.api.Test;
import com.openggf.control.InputHandler;

import static org.lwjgl.glfw.GLFW.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestInputHandler {
    @Test
    public void testKeyPressRelease() {
        InputHandler handler = new InputHandler();
        // Simulate key press using GLFW key codes
        handler.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS);
        assertTrue(handler.isKeyDown(GLFW_KEY_A));
        // Simulate key release
        handler.handleKeyEvent(GLFW_KEY_A, GLFW_RELEASE);
        assertFalse(handler.isKeyDown(GLFW_KEY_A));
        assertFalse(handler.isKeyDown(999));
    }

    @Test
    public void testModifierHelpersRecognizeHeldModifiers() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_RIGHT_CONTROL, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);

        assertTrue(handler.isShiftDown());
        assertTrue(handler.isControlDown());
        assertTrue(handler.isAltDown());
        assertTrue(handler.isAnyModifierDown());
    }

    @Test
    public void testKeyPressedWithoutModifiersIsSuppressedByModifier() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertTrue(handler.isKeyPressed(GLFW_KEY_B));
        assertFalse(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
    }

    @Test
    public void testKeyPressedWithoutModifiersAllowsPlainKey() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertTrue(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
    }

    @Test
    public void testSuperIsAModifierLikeShiftControlAndAlt() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);

        assertTrue(handler.isSuperDown());
        assertTrue(handler.isAnyModifierDown());
        assertFalse(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
    }

    @Test
    public void testRightSuperCountsAsSuper() {
        InputHandler handler = new InputHandler();

        handler.handleKeyEvent(GLFW_KEY_RIGHT_SUPER, GLFW_PRESS);

        assertTrue(handler.isSuperDown());
    }

    /**
     * Super is the window-switch modifier on Linux and Windows, so its GLFW_RELEASE
     * is routinely delivered to whichever window took focus. Without a focus-loss
     * clear the key latches and every isKeyPressedWithoutModifiers call site stays
     * dead for the rest of the process.
     */
    @Test
    public void testFocusLossClearsALatchedModifierAndItsHeldKeys() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);
        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        handler.clearKeyState();

        assertFalse(handler.isSuperDown());
        assertFalse(handler.isAnyModifierDown());
        assertFalse(handler.isKeyDown(GLFW_KEY_B));
        assertFalse(handler.isKeyPressed(GLFW_KEY_B), "no stale rising edge survives the clear");
    }

    @Test
    public void testAKeyPressedAfterFocusLossStillRegisters() {
        InputHandler handler = new InputHandler();
        handler.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);
        handler.clearKeyState();

        handler.handleKeyEvent(GLFW_KEY_B, GLFW_PRESS);

        assertTrue(handler.isKeyPressedWithoutModifiers(GLFW_KEY_B));
    }
}



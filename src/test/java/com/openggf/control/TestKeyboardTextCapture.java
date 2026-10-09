package com.openggf.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

/** Keyboard text capture: claimed keys hide from host shortcuts, not from the claim's owner. */
class TestKeyboardTextCapture {

    @Test
    void claimedLettersAndEditingKeysReadAsUpForHostShortcutsButNotForTheOwner() {
        InputHandler input = new InputHandler();
        input.captureKeyboardText();
        for (int key : new int[] {GLFW_KEY_R, GLFW_KEY_SPACE, GLFW_KEY_ENTER, GLFW_KEY_BACKSPACE,
                GLFW_KEY_LEFT_BRACKET, GLFW_KEY_KP_5, GLFW_KEY_RIGHT}) {
            input.handleKeyEvent(key, GLFW_PRESS);
            assertFalse(input.isKeyDown(key), "held " + key);
            assertFalse(input.isKeyPressed(key), "pressed " + key);
            assertFalse(input.isPhysicalKeyDown(key), "physical " + key);
            assertTrue(input.isCapturedKeyDown(key), "owner held " + key);
            assertTrue(input.isCapturedKeyPressed(key), "owner pressed " + key);
        }
    }

    @Test
    void escapeFunctionKeysAndModifiersStayWithTheHost() {
        InputHandler input = new InputHandler();
        input.captureKeyboardText();
        for (int key : new int[] {GLFW_KEY_ESCAPE, GLFW_KEY_F9, GLFW_KEY_F12, GLFW_KEY_LEFT_SHIFT}) {
            input.handleKeyEvent(key, GLFW_PRESS);
            assertTrue(input.isKeyDown(key), "held " + key);
            assertTrue(input.isKeyPressed(key), "pressed " + key);
        }
    }

    @Test
    void aClaimLastsThroughTheNextFrameAndThenLapsesUnlessRenewed() {
        InputHandler input = new InputHandler();
        input.handleKeyEvent(GLFW_KEY_R, GLFW_PRESS);
        input.captureKeyboardText();
        assertFalse(input.isKeyDown(GLFW_KEY_R));
        input.update();
        assertTrue(input.isKeyboardTextCaptured(), "a claim covers the following frame's shortcuts");
        assertFalse(input.isKeyDown(GLFW_KEY_R));
        input.update();
        assertFalse(input.isKeyboardTextCaptured());
        assertTrue(input.isKeyDown(GLFW_KEY_R), "an unrenewed claim lapses on its own");
    }

    @Test
    void typedTextIsConsumedOnceAndClearedEachFrame() {
        InputHandler input = new InputHandler();
        MenuInput.handleCharEvent(input, 'h');
        MenuInput.handleCharEvent(input, 'é');
        MenuInput.handleCharEvent(input, '\b');
        assertEquals("hé", input.consumeTypedText());
        assertEquals("", input.consumeTypedText());
        MenuInput.handleCharEvent(input, 'x');
        input.update();
        assertEquals("", input.consumeTypedText(), "text not read in its frame is dropped");
    }
}

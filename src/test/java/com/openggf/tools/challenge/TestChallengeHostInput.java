package com.openggf.tools.challenge;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

import com.openggf.control.GamepadStateSource;
import org.junit.jupiter.api.Test;

class TestChallengeHostInput {
    @Test
    void nativeStartAndDistinctActionsAreNotHostPauseOrRestart() {
        boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
        keys[GLFW_KEY_Z] = true;
        keys[GLFW_KEY_X] = true;
        keys[GLFW_KEY_C] = true;
        keys[GLFW_KEY_ENTER] = true;
        assertEquals(0xF0, ChallengeHostInput.held(null, keys));
        keys = new boolean[GLFW_KEY_LAST + 1];
        keys[GLFW_KEY_P] = true;
        keys[GLFW_KEY_R] = true;
        keys[GLFW_KEY_TAB] = true;
        assertEquals(0, ChallengeHostInput.held(null, keys));
    }
    @Test
    void onePadOffersSameLevelAndNeutralizesOpposingDirections() {
        boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        buttons[GLFW_GAMEPAD_BUTTON_A] = true;
        buttons[GLFW_GAMEPAD_BUTTON_START] = true;
        var pad = GamepadStateSource.DeviceState.connected(1, "pad", buttons, .8f, 0);
        assertEquals(0xA8, ChallengeHostInput.held(pad, keys));
        assertEquals(0xA8, ChallengeHostInput.held(pad, keys));
        keys[GLFW_KEY_LEFT] = true;
        assertEquals(0xA0, ChallengeHostInput.held(pad, keys));
    }
}

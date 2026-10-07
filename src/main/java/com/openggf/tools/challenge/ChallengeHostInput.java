package com.openggf.tools.challenge;

import static org.lwjgl.glfw.GLFW.*;

import com.openggf.control.GamepadStateSource;

/** The window samples one held pad; workers derive their own native press edges. */
final class ChallengeHostInput {
    private ChallengeHostInput() {}
    static int held(GamepadStateSource.DeviceState pad, boolean[] keys) {
        int value = 0;
        if (keys[GLFW_KEY_UP])
            value |= 1;
        if (keys[GLFW_KEY_DOWN])
            value |= 2;
        if (keys[GLFW_KEY_LEFT])
            value |= 4;
        if (keys[GLFW_KEY_RIGHT])
            value |= 8;
        if (keys[GLFW_KEY_Z])
            value |= 64;
        if (keys[GLFW_KEY_X])
            value |= 16;
        if (keys[GLFW_KEY_C] || keys[GLFW_KEY_SPACE])
            value |= 32;
        if (keys[GLFW_KEY_ENTER])
            value |= 128;
        if (pad != null) {
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_UP) || pad.leftY() < -.35)
                value |= 1;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_DOWN) || pad.leftY() > .35)
                value |= 2;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_LEFT) || pad.leftX() < -.35)
                value |= 4;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) || pad.leftX() > .35)
                value |= 8;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_X))
                value |= 64;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_B))
                value |= 16;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_A))
                value |= 32;
            if (pad.buttonDown(GLFW_GAMEPAD_BUTTON_START))
                value |= 128;
        }
        if ((value & 3) == 3)
            value &= ~3;
        if ((value & 12) == 12)
            value &= ~12;
        return value;
    }
}

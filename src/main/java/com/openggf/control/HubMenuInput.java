package com.openggf.control;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.lwjgl.glfw.GLFW.*;

/** Input used by the hub and game library. */
public final class HubMenuInput {
    private HubMenuInput() { }

    public static boolean library(InputHandler input) {
        return input.isKeyPressedWithoutModifiers(GLFW_KEY_L) || input.menuLibraryPressed();
    }

    public static String libraryLabel(InputHandler input) {
        if (input == null || !MenuInput.controller(input)) return "L";
        return "L1".equals(input.menuControllerStyle().leftBumper()) ? "R1" : "RB";
    }

    public static int horizontalHeld(InputHandler input) {
        int mask = MenuInput.mapped(input).player1().heldMask();
        boolean left = input.menuKeyDown(GLFW_KEY_LEFT) || (mask & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean right = input.menuKeyDown(GLFW_KEY_RIGHT) || (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        return (right ? 1 : 0) - (left ? 1 : 0);
    }
}

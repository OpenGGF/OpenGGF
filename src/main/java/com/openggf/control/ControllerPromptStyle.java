package com.openggf.control;

import java.util.Locale;

import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_START;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y;

/**
 * Names describe GLFW's standardized physical button positions for unknown pads.
 * confirm/back/details are the South/East/North buttons; west is the button that
 * {@link GamepadInputManager} maps to Genesis A.
 */
public record ControllerPromptStyle(String confirm, String back, String details,
                                    String west, String start, String leftBumper) {
    private static final ControllerPromptStyle PLAYSTATION =
            new ControllerPromptStyle("Cross", "Circle", "Triangle", "Square", "Options", "L1");
    private static final ControllerPromptStyle XBOX =
            new ControllerPromptStyle("A", "B", "Y", "X", "Start", "LB");
    private static final ControllerPromptStyle GENERIC =
            new ControllerPromptStyle("South", "East", "North", "West", "Start", "LB");

    public static ControllerPromptStyle forName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("playstation") || normalized.contains("dualshock")
                || normalized.contains("dualsense") || normalized.contains("ps4") || normalized.contains("ps5")) {
            return PLAYSTATION;
        }
        if (normalized.contains("xbox") || normalized.contains("xinput")) {
            return XBOX;
        }
        return GENERIC;
    }

    /** Label of a standardized GLFW gamepad button in this pad family. */
    public String button(int glfwGamepadButton) {
        return switch (glfwGamepadButton) {
            case GLFW_GAMEPAD_BUTTON_A -> confirm;
            case GLFW_GAMEPAD_BUTTON_B -> back;
            case GLFW_GAMEPAD_BUTTON_X -> west;
            case GLFW_GAMEPAD_BUTTON_Y -> details;
            case GLFW_GAMEPAD_BUTTON_START -> start;
            case GLFW_GAMEPAD_BUTTON_LEFT_BUMPER -> leftBumper;
            default -> throw new IllegalArgumentException("Unnamed gamepad button: " + glfwGamepadButton);
        };
    }
}

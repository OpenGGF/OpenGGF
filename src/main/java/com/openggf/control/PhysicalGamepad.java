package com.openggf.control;

/**
 * One physical gamepad using the standard GLFW gamepad layout, independent of Genesis
 * controller assignments. Button names describe the Xbox layout; equivalent positions
 * on other ordinary pads have the same codes. Both sticks and analog triggers are exposed.
 * Arrays are copied on construction and access.
 */
@com.openggf.game.ModApi
public record PhysicalGamepad(int deviceId, String name, boolean[] buttons, float[] axes) {
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_X = 2;
    public static final int BUTTON_Y = 3;
    public static final int BUTTON_LEFT_BUMPER = 4;
    public static final int BUTTON_RIGHT_BUMPER = 5;
    public static final int BUTTON_BACK = 6;
    public static final int BUTTON_START = 7;
    public static final int BUTTON_GUIDE = 8;
    public static final int BUTTON_LEFT_THUMB = 9;
    public static final int BUTTON_RIGHT_THUMB = 10;
    public static final int BUTTON_DPAD_UP = 11;
    public static final int BUTTON_DPAD_RIGHT = 12;
    public static final int BUTTON_DPAD_DOWN = 13;
    public static final int BUTTON_DPAD_LEFT = 14;

    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;

    public PhysicalGamepad {
        name = name != null ? name : "";
        buttons = buttons != null ? buttons.clone() : new boolean[0];
        axes = axes != null ? axes.clone() : new float[0];
    }

    @Override
    public boolean[] buttons() {
        return buttons.clone();
    }

    @Override
    public float[] axes() {
        return axes.clone();
    }

    public boolean buttonDown(int button) {
        return button >= 0 && button < buttons.length && buttons[button];
    }

    /** Axis position in [-1, 1]; triggers rest at -1, sticks at zero. */
    public float axis(int axis) {
        return axis >= 0 && axis < axes.length ? axes[axis] : neutralAxis(axis);
    }

    /** The rest position of a standard axis, also used for a disconnected device. */
    public static float neutralAxis(int axis) {
        return axis == AXIS_LEFT_TRIGGER || axis == AXIS_RIGHT_TRIGGER ? -1 : 0;
    }
}

package com.openggf.editor.hud;

import com.openggf.control.MenuRepeat;
import com.openggf.control.PhysicalGamepad;

import java.util.Arrays;
import java.util.List;

/**
 * Physical gamepad state for the editor HUD: every standard button, both sticks and analog
 * triggers of all connected pads, with press/release edges and the engine's menu repeat timing.
 * Positions follow the standard layout (South = Xbox A / PlayStation Cross). Independent of the
 * Genesis controller mapping, which the HUD suppresses while it owns the editor.
 */
final class EditorHudPad {
    enum Button {
        SOUTH, EAST, WEST, NORTH, LB, RB, VIEW, MENU, L3, R3,
        DPAD_UP, DPAD_RIGHT, DPAD_DOWN, DPAD_LEFT, LT, RT,
        /** Left stick pushed past the navigation threshold, with hysteresis. */
        STICK_UP, STICK_RIGHT, STICK_DOWN, STICK_LEFT
    }

    private static final float STICK_DEADZONE = .2f;
    private static final float NAV_ON = .55f;
    private static final float NAV_OFF = .35f;
    private static final float TRIGGER_ON = .6f;
    private static final float TRIGGER_OFF = .35f;

    private final boolean[] held = new boolean[Button.values().length];
    private final boolean[] previous = new boolean[held.length];
    private final MenuRepeat repeat = new MenuRepeat();
    private long frame;
    private float leftX;
    private float leftY;
    private float rightX;
    private float rightY;
    private boolean connected;
    private boolean activity;
    private String deviceName = "";

    void sample(List<PhysicalGamepad> pads) {
        frame++;
        System.arraycopy(held, 0, previous, 0, held.length);
        Arrays.fill(held, false);
        leftX = leftY = rightX = rightY = 0;
        connected = pads != null && !pads.isEmpty();
        activity = false;
        if (!connected) return;
        for (PhysicalGamepad pad : pads) {
            boolean any = false;
            any |= press(Button.SOUTH, pad.buttonDown(PhysicalGamepad.BUTTON_A));
            any |= press(Button.EAST, pad.buttonDown(PhysicalGamepad.BUTTON_B));
            any |= press(Button.WEST, pad.buttonDown(PhysicalGamepad.BUTTON_X));
            any |= press(Button.NORTH, pad.buttonDown(PhysicalGamepad.BUTTON_Y));
            any |= press(Button.LB, pad.buttonDown(PhysicalGamepad.BUTTON_LEFT_BUMPER));
            any |= press(Button.RB, pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_BUMPER));
            any |= press(Button.VIEW, pad.buttonDown(PhysicalGamepad.BUTTON_BACK));
            any |= press(Button.MENU, pad.buttonDown(PhysicalGamepad.BUTTON_START));
            any |= press(Button.L3, pad.buttonDown(PhysicalGamepad.BUTTON_LEFT_THUMB));
            any |= press(Button.R3, pad.buttonDown(PhysicalGamepad.BUTTON_RIGHT_THUMB));
            any |= press(Button.DPAD_UP, pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_UP));
            any |= press(Button.DPAD_RIGHT, pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_RIGHT));
            any |= press(Button.DPAD_DOWN, pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_DOWN));
            any |= press(Button.DPAD_LEFT, pad.buttonDown(PhysicalGamepad.BUTTON_DPAD_LEFT));
            any |= press(Button.LT, trigger(pad.axis(PhysicalGamepad.AXIS_LEFT_TRIGGER), Button.LT));
            any |= press(Button.RT, trigger(pad.axis(PhysicalGamepad.AXIS_RIGHT_TRIGGER), Button.RT));
            float lx = pad.axis(PhysicalGamepad.AXIS_LEFT_X), ly = pad.axis(PhysicalGamepad.AXIS_LEFT_Y);
            float rx = pad.axis(PhysicalGamepad.AXIS_RIGHT_X), ry = pad.axis(PhysicalGamepad.AXIS_RIGHT_Y);
            if (lx * lx + ly * ly > leftX * leftX + leftY * leftY) { leftX = lx; leftY = ly; }
            if (rx * rx + ry * ry > rightX * rightX + rightY * rightY) { rightX = rx; rightY = ry; }
            any |= Math.hypot(lx, ly) > NAV_ON || Math.hypot(rx, ry) > NAV_ON;
            // The pad that was last used names the button prompts.
            if (any || deviceName.isEmpty()) deviceName = pad.name();
            activity |= any;
        }
        held[Button.STICK_UP.ordinal()] = leftY < -(previous[Button.STICK_UP.ordinal()] ? NAV_OFF : NAV_ON);
        held[Button.STICK_DOWN.ordinal()] = leftY > (previous[Button.STICK_DOWN.ordinal()] ? NAV_OFF : NAV_ON);
        held[Button.STICK_LEFT.ordinal()] = leftX < -(previous[Button.STICK_LEFT.ordinal()] ? NAV_OFF : NAV_ON);
        held[Button.STICK_RIGHT.ordinal()] = leftX > (previous[Button.STICK_RIGHT.ordinal()] ? NAV_OFF : NAV_ON);
    }

    private boolean press(Button button, boolean down) {
        if (down) held[button.ordinal()] = true;
        return down;
    }

    private boolean trigger(float axis, Button button) {
        float pull = (axis + 1f) / 2f;
        return pull > (previous[button.ordinal()] ? TRIGGER_OFF : TRIGGER_ON);
    }

    boolean connected() { return connected; }

    /** True when any pad button, trigger or stick was intentionally used this frame. */
    boolean activity() { return activity; }

    String deviceName() { return deviceName; }

    boolean held(Button button) { return held[button.ordinal()]; }

    boolean pressed(Button button) { return held[button.ordinal()] && !previous[button.ordinal()]; }

    boolean released(Button button) { return !held[button.ordinal()] && previous[button.ordinal()]; }

    /** Press edge, then the engine's menu repeat while held. */
    boolean repeated(Button button) {
        return repeat.pulse(button.ordinal(), frame, held(button), pressed(button));
    }

    /** -1, 0 or 1 per axis from the D-pad or left stick, with menu repeat. */
    int navX() {
        int x = 0;
        if (repeated(Button.DPAD_LEFT) | repeated(Button.STICK_LEFT)) x--;
        if (repeated(Button.DPAD_RIGHT) | repeated(Button.STICK_RIGHT)) x++;
        return x;
    }

    int navY() {
        int y = 0;
        if (repeated(Button.DPAD_UP) | repeated(Button.STICK_UP)) y--;
        if (repeated(Button.DPAD_DOWN) | repeated(Button.STICK_DOWN)) y++;
        return y;
    }

    /** Left stick with a radial deadzone, rescaled to 0..1 magnitude. */
    float[] leftStick() { return deadzone(leftX, leftY); }

    float[] rightStick() { return deadzone(rightX, rightY); }

    private static float[] deadzone(float x, float y) {
        float magnitude = (float) Math.hypot(x, y);
        if (magnitude < STICK_DEADZONE) return new float[] {0, 0};
        float scaled = Math.min(1f, (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE));
        return new float[] {x / magnitude * scaled, y / magnitude * scaled};
    }
}

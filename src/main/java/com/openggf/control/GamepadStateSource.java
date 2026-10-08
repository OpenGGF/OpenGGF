package com.openggf.control;

import java.util.List;

@com.openggf.game.ModApi
public interface GamepadStateSource {
    List<DeviceState> pollDevices();

    static GamepadStateSource noop() {
        return List::of;
    }

    @com.openggf.game.ModApi
    record DeviceState(
            int joystickId,
            String name,
            boolean connected,
            boolean[] buttons,
            float leftX,
            float leftY,
            float rightX,
            float rightY,
            float leftTrigger,
            float rightTrigger) {

        public DeviceState {
            name = name != null ? name : "";
            buttons = buttons != null ? buttons.clone() : new boolean[0];
        }

        /** Compatibility constructor for a pad exposing only the original left stick. */
        public DeviceState(int joystickId, String name, boolean connected, boolean[] buttons,
                           float leftX, float leftY) {
            this(joystickId, name, connected, buttons, leftX, leftY, 0, 0, -1, -1);
        }

        public static DeviceState connected(
                int joystickId,
                String name,
                boolean[] buttons,
                float leftX,
                float leftY) {
            return new DeviceState(joystickId, name, true, buttons, leftX, leftY);
        }

        /** A complete standard gamepad sample: left/right X/Y and left/right triggers. */
        public static DeviceState connected(int joystickId, String name, boolean[] buttons, float[] axes) {
            return new DeviceState(joystickId, name, true, buttons,
                    axis(axes, 0), axis(axes, 1), axis(axes, 2), axis(axes, 3), axis(axes, 4), axis(axes, 5));
        }

        /** All six standard axes, copied in GLFW gamepad order. Triggers rest at -1. */
        public float[] axes() {
            return new float[]{leftX, leftY, rightX, rightY, leftTrigger, rightTrigger};
        }

        private static float axis(float[] axes, int axis) {
            if (axes == null || axis >= axes.length || !Float.isFinite(axes[axis])) {
                return axis >= 4 ? -1 : 0;
            }
            return Math.max(-1, Math.min(1, axes[axis]));
        }

        @Override
        public boolean[] buttons() {
            return buttons.clone();
        }

        public boolean buttonDown(int button) {
            return button >= 0 && button < buttons.length && buttons[button];
        }
    }
}

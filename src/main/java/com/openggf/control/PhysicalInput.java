package com.openggf.control;

import java.util.List;

/**
 * Immutable physical keyboard/gamepad state and transitions for a scene tick. These
 * controls are independent of Genesis mappings and logical replay input. Scenes can
 * bind arbitrary actions without losing short taps or simultaneous button presses.
 *
 * <p>The engine captures once before a scene update. Event times and {@code timestampNanos}
 * share the monotonic clock used by scene song playback. Gamepad times describe polling
 * observation, not a hardware timestamp. A bounded queue holds 4096 events; if
 * {@code droppedEvents} is nonzero, a timing-sensitive scene should pause or invalidate
 * the performance rather than infer missing transitions from the final held state.
 *
 * @param timestampNanos monotonic capture time
 * @param keysDown physical GLFW key codes currently held
 * @param gamepads connected physical devices, with all standard buttons and axes
 * @param events ordered transitions since the preceding capture in this tick
 * @param droppedEvents transitions omitted because the bounded pending queue filled
 */
@com.openggf.game.ModApi
public record PhysicalInput(long timestampNanos, List<Integer> keysDown, List<PhysicalGamepad> gamepads,
                         List<PhysicalInputEvent> events, int droppedEvents) {
    public PhysicalInput {
        keysDown = List.copyOf(keysDown);
        gamepads = List.copyOf(gamepads);
        events = List.copyOf(events);
        if (droppedEvents < 0) throw new IllegalArgumentException("droppedEvents must be nonnegative");
    }

    /** No physical devices or events, for scenes with no live input provider. */
    public static PhysicalInput neutral() {
        return new PhysicalInput(0, List.of(), List.of(), List.of(), 0);
    }

    public boolean keyDown(int code) {
        return keysDown.contains(code);
    }

    /** Returns the connected device with this physical ID, or null when absent. */
    public PhysicalGamepad gamepad(int deviceId) {
        for (PhysicalGamepad gamepad : gamepads) {
            if (gamepad.deviceId() == deviceId) return gamepad;
        }
        return null;
    }

    public boolean buttonDown(int deviceId, int button) {
        PhysicalGamepad gamepad = gamepad(deviceId);
        return gamepad != null && gamepad.buttonDown(button);
    }

    /** Returns a device axis, or its neutral position when the device is absent. */
    public float axis(int deviceId, int axis) {
        PhysicalGamepad gamepad = gamepad(deviceId);
        return gamepad != null ? gamepad.axis(axis) : PhysicalGamepad.neutralAxis(axis);
    }
}

package com.openggf.control;

/**
 * A physical transition observed since the previous scene input capture. Times use the
 * same monotonic nanosecond clock as {@link PhysicalInput#timestampNanos()}; they are neither
 * simulation ticks nor wall-clock dates. Keyboard events are timestamped on callback
 * arrival, gamepad events when the engine observes the changed state.
 *
 * @param sequence strictly increasing engine-local order, including equal-time events
 * @param timestampNanos monotonic observation time
 * @param kind physical key, standardized gamepad button, or standardized gamepad axis
 * @param deviceId {@link #KEYBOARD_DEVICE} for keys, otherwise the physical gamepad ID
 * @param code physical GLFW key code or {@link PhysicalGamepad} button/axis code
 * @param value 1 for a press, 0 for a release, or the new axis value in [-1, 1]
 */
@com.openggf.game.ModApi
public record PhysicalInputEvent(long sequence, long timestampNanos, Kind kind, int deviceId, int code, float value) {
    public static final int KEYBOARD_DEVICE = -1;

    /** The control that changed; action names and bindings belong to the scene. */
    @com.openggf.game.ModApi
    public enum Kind { KEY, BUTTON, AXIS }

    public PhysicalInputEvent {
        java.util.Objects.requireNonNull(kind, "kind");
    }

    /** True for a key/button rising edge; axis changes are not button presses. */
    public boolean pressed() {
        return kind != Kind.AXIS && value > 0;
    }

    /** True for a key/button falling edge; axis changes are not button releases. */
    public boolean released() {
        return kind != Kind.AXIS && value == 0;
    }
}

package com.openggf.mods.testing;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputBindings;
import com.openggf.control.InputHandler;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

/** Physical input delivered through the real input handler, with an explicitly controlled clock. */
public final class DeterministicInput {
    private long nanos;
    private long baseNanos;
    private long ticks;
    private List<GamepadStateSource.DeviceState> pads = List.of();
    private final InputHandler handler;

    public DeterministicInput() { this(InputBindingFactory.standaloneSupplier()); }

    public DeterministicInput(SonicConfigurationService configuration) {
        this(InputBindingFactory.supplier(configuration));
    }

    /** Reads the same live bindings source on each normal engine input sample. */
    public DeterministicInput(Supplier<InputBindings> bindings) {
        handler = new InputHandler(Objects.requireNonNull(bindings, "input bindings"),
                () -> pads, () -> nanos);
    }

    public InputHandler handler() { return handler; }
    public long nanos() { return nanos; }

    public void atNanos(long value) {
        if (value < nanos) throw new IllegalArgumentException("Input time must advance monotonically");
        nanos = value;
        baseNanos = value;
        ticks = 0;
    }

    public void key(int glfwKey, boolean down) {
        handler.handleKeyEvent(glfwKey, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
    }

    public void gamepads(List<GamepadStateSource.DeviceState> devices) {
        pads = List.copyOf(devices);
    }

    public void beginTick() {
        nanos = Math.addExact(baseNanos, Math.multiplyExact(++ticks, 1_000_000_000L) / 60);
        handler.refreshLogicalSnapshot();
    }

    public void endTick() { handler.update(); }
}

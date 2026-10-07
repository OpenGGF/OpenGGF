package sitarhero.controls;

import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reduces timestamped physical transitions without losing taps between scene ticks. */
public final class MappedControls {
    public record Frame(long timestamp, int frets, int directPressed, boolean strum,
                        boolean power, boolean pause, float whammy) { }
    private final ControlSettings settings;
    private final Set<Integer> keys = new HashSet<>();
    private final Map<String, Float> pads = new HashMap<>();
    private final boolean[][] bindingDown = new boolean[2][10];
    private boolean drums;

    public MappedControls(ControlSettings settings) { this.settings = settings; }
    public void reset(PhysicalInput input, boolean drums) {
        this.drums = drums;
        readState(input);
        for (int action = 0; action < 10; action++) {
            bindingDown[0][action] = bindingAmount(false, action) > .5f;
            bindingDown[1][action] = bindingAmount(true, action) > .5f;
        }
    }
    public List<Frame> accept(PhysicalInput input) {
        var frames = new ArrayList<Frame>();
        for (PhysicalInputEvent event : input.events()) {
            switch (event.kind()) {
                case KEY -> { if (event.value() > .5f) keys.add(event.code()); else keys.remove(event.code()); }
                case BUTTON -> pads.put(event.deviceId() + ":B:" + event.code(), event.value());
                case AXIS -> pads.put(event.deviceId() + ":A:" + event.code(), event.value());
            }
            frames.add(frame(event.timestampNanos(), true));
        }
        // New device baselines and final held state carry no synthetic strike edges.
        readState(input);
        frames.add(frame(input.timestampNanos(), false));
        return frames;
    }
    private Frame frame(long timestamp, boolean edges) {
        int frets = 0, presses = 0;
        boolean strum = false, power = false, pause = false;
        for (int action = 0; action < 10; action++) {
            boolean keyboardDown = bindingAmount(false, action) > .5f;
            boolean gamepadDown = bindingAmount(true, action) > .5f;
            boolean active = keyboardDown || gamepadDown;
            // Strikes belong to physical rising edges. Holding one alternative must
            // not suppress a tap on the other; frets still merge their held states.
            // OR also counts an identical binding in both slots only once.
            boolean pressed = edges && (keyboardDown && !bindingDown[0][action]
                    || gamepadDown && !bindingDown[1][action]);
            if (action < 5) { if (active) frets |= 1 << action; if (pressed) presses |= 1 << action; }
            if (action == ControlSettings.STRUM_UP || action == ControlSettings.STRUM_DOWN) strum |= pressed;
            if (action == ControlSettings.POWER) power = pressed;
            if (action == ControlSettings.PAUSE) pause = pressed;
            bindingDown[0][action] = keyboardDown;
            bindingDown[1][action] = gamepadDown;
        }
        return new Frame(timestamp, frets, presses, strum, power, pause, amount(ControlSettings.WHAMMY));
    }
    private float amount(int action) {
        return Math.max(bindingAmount(false, action), bindingAmount(true, action));
    }
    private float bindingAmount(boolean pad, int action) {
        return settings.binding(drums, pad, action).amount(keys, pads);
    }
    private void readState(PhysicalInput input) {
        keys.clear(); keys.addAll(input.keysDown()); pads.clear();
        for (var pad : input.gamepads()) {
            boolean[] buttons = pad.buttons(); float[] axes = pad.axes();
            for (int i = 0; i < buttons.length; i++) pads.put(pad.deviceId() + ":B:" + i, buttons[i] ? 1f : 0f);
            for (int i = 0; i < axes.length; i++) pads.put(pad.deviceId() + ":A:" + i, axes[i]);
        }
    }
}

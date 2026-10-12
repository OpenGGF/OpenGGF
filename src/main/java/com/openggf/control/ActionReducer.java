package com.openggf.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reduces timestamped physical transitions without dropping taps between simulation ticks. */
@com.openggf.game.ModApi
public final class ActionReducer {
    private final ActionMap actions;
    private final Set<Integer> keys = new HashSet<>();
    private final Map<String, Float> pads = new HashMap<>();
    private final Map<String, boolean[]> previous = new HashMap<>();
    private Map<String, List<PhysicalBinding>> bindings = Map.of();
    private long revision = -1;
    public ActionReducer(ActionMap actions) { this.actions = Objects.requireNonNull(actions, "actions"); }
    /** Consume transition edges on scene entry, pause/resume or input-map changes. */
    public void reset(PhysicalInput input) {
        Objects.requireNonNull(input, "input");
        revision = actions.revision();
        bindings = actions.bindings();
        previous.clear();
        readState(input);
        frame(input.timestampNanos(), false, input.droppedEvents());
    }
    public List<ActionFrame> accept(PhysicalInput input) {
        Objects.requireNonNull(input, "input");
        if (revision != actions.revision()) {
            reset(input);
            return List.of(frame(input.timestampNanos(), false, input.droppedEvents()));
        }
        List<ActionFrame> frames = new ArrayList<>();
        for (PhysicalInputEvent event : input.events()) {
            switch (event.kind()) {
                case KEY -> { if (event.value() > .5f) keys.add(event.code()); else keys.remove(event.code()); }
                case BUTTON -> pads.put(event.deviceId() + ":B:" + event.code(), event.value());
                case AXIS -> pads.put(event.deviceId() + ":A:" + event.code(), event.value());
            }
            frames.add(frame(event.timestampNanos(), true, input.droppedEvents()));
        }
        // New/disconnected devices and final baselines update held state without synthetic presses.
        readState(input);
        frames.add(frame(input.timestampNanos(), false, input.droppedEvents()));
        return List.copyOf(frames);
    }
    private ActionFrame frame(long timestamp, boolean edges, int droppedEvents) {
        Map<String, Float> amounts = new LinkedHashMap<>();
        Set<String> pressed = new HashSet<>();
        bindings.forEach((name, alternatives) -> {
            boolean[] prior = previous.computeIfAbsent(name, ignored -> new boolean[alternatives.size()]);
            float amount = 0;
            for (int i = 0; i < alternatives.size(); i++) {
                float value = alternatives.get(i).amount(keys, pads);
                boolean down = value > .5f;
                if (edges && down && !prior[i]) pressed.add(name);
                prior[i] = down;
                amount = Math.max(amount, value);
            }
            amounts.put(name, amount);
        });
        return new ActionFrame(timestamp, amounts, pressed, droppedEvents);
    }
    private void readState(PhysicalInput input) {
        keys.clear(); keys.addAll(input.keysDown()); pads.clear();
        for (PhysicalGamepad pad : input.gamepads()) {
            boolean[] buttons = pad.buttons(); float[] axes = pad.axes();
            for (int i = 0; i < buttons.length; i++) pads.put(pad.deviceId() + ":B:" + i, buttons[i] ? 1f : 0f);
            for (int i = 0; i < axes.length; i++) pads.put(pad.deviceId() + ":A:" + i, axes[i]);
        }
    }
}

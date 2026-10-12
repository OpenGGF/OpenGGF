package com.openggf.control;

import java.util.Map;
import java.util.Set;

/** One ordered action state at a physical observation time, plus independent alternative press edges. */
@com.openggf.game.ModApi
public record ActionFrame(long timestampNanos, Map<String, Float> amounts, Set<String> pressedActions, int droppedEvents) {
    public ActionFrame {
        amounts = Map.copyOf(amounts);
        pressedActions = Set.copyOf(pressedActions);
        if (droppedEvents < 0) throw new IllegalArgumentException("Negative dropped event count");
    }
    public float amount(String action) { return amounts.getOrDefault(action, 0f); }
    public boolean held(String action) { return amount(action) > .5f; }
    public boolean pressed(String action) { return pressedActions.contains(action); }
}

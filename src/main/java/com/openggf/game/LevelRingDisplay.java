package com.openggf.game;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;

/** Internal access to the ring digits retained between native HUD redraw requests. */
public final class LevelRingDisplay {
    private LevelRingDisplay() { }

    record State(int value, boolean dirty, boolean latched) { }

    public static int value(LevelState state) {
        return state instanceof LevelGamestate nativeState
                ? nativeState.ringsForDisplay() : state.getRings();
    }

    /** A Ring_count write without Update_HUD_ring_count; preserves a pending redraw. */
    public static void writeWithoutRefresh(LevelState state, int rings) {
        if (state instanceof LevelGamestate nativeState) nativeState.writeRingsWithoutRefresh(rings);
        else state.setRings(rings);
    }

    /** Called by the existing semantic VBlank counter-publication owner, also headlessly. */
    public static void publish(LevelState state) {
        if (state instanceof LevelGamestate nativeState) nativeState.publishRingDisplay();
    }

    public static void unregister(RewindRegistry registry) {
        registry.deregister("level-ring-display");
    }

    public static void register(LevelManager manager, RewindRegistry registry) {
        unregister(registry);
        registry.register(new RewindSnapshottable<State>() {
            @Override public String key() { return "level-ring-display"; }
            @Override public State capture() {
                return manager.getLevelGamestate() instanceof LevelGamestate state
                        ? state.captureRingDisplay() : new State(0, true, false);
            }
            @Override public void restore(State saved) {
                if (manager.getLevelGamestate() instanceof LevelGamestate state) {
                    state.restoreRingDisplay(saved);
                }
            }
        });
    }
}

package com.openggf.game.internal;

import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;

/** Native Game_paused belongs to the current world, independently of host pause. */
public final class NativePauseRewindAccess {
    public static final String KEY = "native-pause";

    private NativePauseRewindAccess() { }

    public static void register(RewindRegistry registry, GameStateManager state) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");
        registry.register(new RewindSnapshottable<Boolean>() {
            @Override public String key() { return KEY; }
            @Override public Boolean capture() { return state.isGamePaused(); }
            @Override public void restore(Boolean snapshot) {
                state.setGamePaused(Objects.requireNonNull(snapshot, "snapshot"));
            }
            @Override public void resetForMissingSnapshot() {
                // Older keyframes did not retain Game_paused. Their ordinary
                // gameplay inlet must not inherit the current world's pause.
                state.setGamePaused(false);
            }
        });
    }
}

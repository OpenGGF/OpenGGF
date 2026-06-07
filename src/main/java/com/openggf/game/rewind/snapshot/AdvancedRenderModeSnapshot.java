package com.openggf.game.rewind.snapshot;

import com.openggf.game.render.AdvancedRenderMode;

import java.util.List;

/**
 * Snapshot of {@link com.openggf.game.render.AdvancedRenderModeController}
 * registered-contributor list.
 *
 * <p>Contributor object references are captured by identity. Contributors are
 * stateless unless they implement {@link com.openggf.game.rewind.RewindSnapshottable};
 * stateful contributor snapshots are captured alongside the identity list and
 * restored into the same registered contributor instance.
 */
public record AdvancedRenderModeSnapshot(List<AdvancedRenderMode> modes, List<ModeState> modeStates) {
    public record ModeState(int index, String key, Object snapshot) {
    }

    public AdvancedRenderModeSnapshot(List<AdvancedRenderMode> modes) {
        this(modes, List.of());
    }

    public AdvancedRenderModeSnapshot {
        modes = List.copyOf(modes);
        modeStates = List.copyOf(modeStates);
    }
}

package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;

/** Rewind owner for transition state that crosses HPZ and special-stage modes. */
final class LevelTransitionRewindAdapter
        implements RewindSnapshottable<LevelTransitionCoordinator.SanctuaryRewindState> {
    static final String KEY = "level-transition";

    private final LevelTransitionCoordinator transitions;

    LevelTransitionRewindAdapter(LevelTransitionCoordinator transitions) {
        this.transitions = transitions;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public LevelTransitionCoordinator.SanctuaryRewindState capture() {
        return transitions.captureSanctuaryRewindState();
    }

    @Override
    public void restore(LevelTransitionCoordinator.SanctuaryRewindState snapshot) {
        transitions.restoreSanctuaryRewindState(snapshot);
    }
}

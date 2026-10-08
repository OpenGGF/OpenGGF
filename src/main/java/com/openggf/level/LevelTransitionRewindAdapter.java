package com.openggf.level;

import com.openggf.game.rewind.RewindSnapshottable;

/** Rewind owner for sanctuary state and deferred fresh-level publication. */
final class LevelTransitionRewindAdapter
        implements RewindSnapshottable<LevelTransitionRewindAdapter.Snapshot> {
    static final String KEY = "level-transition";

    private final LevelTransitionCoordinator transitions;
    private final FreshLevelTransitionBoundaryController freshBoundary;

    LevelTransitionRewindAdapter(LevelTransitionCoordinator transitions,
                                 FreshLevelTransitionBoundaryController freshBoundary) {
        this.transitions = transitions;
        this.freshBoundary = freshBoundary;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(transitions.captureSanctuaryRewindState(),
                freshBoundary.captureForRewind());
    }

    @Override
    public void restore(Snapshot snapshot) {
        transitions.restoreSanctuaryRewindState(snapshot.sanctuary());
        freshBoundary.restoreForRewind(snapshot.freshBoundary());
    }

    record Snapshot(LevelTransitionCoordinator.SanctuaryRewindState sanctuary,
                    FreshLevelTransitionBoundaryController.RewindState freshBoundary) {}
}

package com.openggf.game.sonic1.scroll;

import com.openggf.game.CheckpointBackgroundScrollAccess;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.game.rewind.RewindSnapshottable;

/** Captures the lamppost bank separately from the public, cross-game checkpoint record. */
public final class Sonic1CheckpointScrollRewindAdapter
        implements RewindSnapshottable<Sonic1CheckpointScrollRewindAdapter.Snapshot> {
    public record Snapshot(CheckpointBackgroundScroll background) { }

    @Override public String key() { return "s1-checkpoint-background"; }

    @Override public Snapshot capture() {
        CheckpointState state = current();
        return new Snapshot(state == null ? null : CheckpointBackgroundScrollAccess.saved(state));
    }

    @Override public void restore(Snapshot snapshot) {
        CheckpointState state = current();
        if (state != null) {
            CheckpointBackgroundScrollAccess.save(state, snapshot.background());
        }
    }

    @Override public void resetForMissingSnapshot() { restore(new Snapshot(null)); }

    private CheckpointState current() {
        var level = GameServices.levelOrNull();
        return level != null && level.getCheckpointState() instanceof CheckpointState state ? state : null;
    }
}

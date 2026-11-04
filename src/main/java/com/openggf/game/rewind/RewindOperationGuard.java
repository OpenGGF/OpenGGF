package com.openggf.game.rewind;

/** Internal preflight for an owner whose transient production state cannot be snapshotted or restored. */
public interface RewindOperationGuard {
    void requireSnapshotBoundary();
}

package com.openggf.game.rewind.snapshot;

/** Immutable rewind capture of the native shared collision angle outputs. */
@com.openggf.game.ModApi
public record CollisionSystemSnapshot(
        int primaryAngleOutput,
        int secondaryAngleOutput) {
}

package com.openggf.game.internal;

/** Display order and tile priority for a lost ring's captured spill layer. */
@FunctionalInterface
public interface LostRingDisplayProfile {
    record Priority(int bucket, boolean highPriority) {}

    Priority priorityForLayer(int spillLayer);
}

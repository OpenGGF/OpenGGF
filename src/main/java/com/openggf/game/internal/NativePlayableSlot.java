package com.openggf.game.internal;

import com.openggf.level.LevelManager;

/**
 * Engine-internal opt-in to ownership of a complete ordinary playable slot.
 *
 * <p>The owner consumes its world's already-serviced controller latches; it must not sample
 * host input here. It owns routine selection, input/control policy, history, animation,
 * interactions and all return/bookkeeping work, including an Init-only return. No legacy
 * playable setup, movement, tail or {@code endOfTick()} accompanies this dispatch.
 *
 * <p>Only the ordinary input-bearing SpriteManager path admits this slot. Legacy neutral,
 * scripted, initial-assembly, warm-up and movement-only entries reject it before mutation.
 * Admission of the world's serviced latches remains the native owner's responsibility.
 * This interface is not part of the public Mod API.
 */
public interface NativePlayableSlot {
    /**
     * Runs once inside the manager's active-playable/deferred-mutation scope.
     *
     * @param levelManager the current world level manager
     * @param levelFrames the low word of the already-incremented level frame counter,
     *                    independent of the sprite dispatch ordinal and V-int clock
     */
    void runPlayableSlot(LevelManager levelManager, int levelFrames);
}

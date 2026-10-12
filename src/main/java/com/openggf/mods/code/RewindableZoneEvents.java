package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.ModApi;
import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Stateful contributed events with an immutable rewind payload.
 * The engine owns the registered identity; {@link #key()} is local metadata.
 * Factories create a fresh handler on every load or respawn. Restore must only
 * hydrate state; reconcile references to restored objects in the final callback.
 */
@ModApi
public interface RewindableZoneEvents<S> extends LevelEventProvider, RewindSnapshottable<S> {
    @Override default String key() { return "events"; }

    /** Reset all owned state when an older composite has no event snapshot. */
    @Override void resetForMissingSnapshot();

    /** Called after the engine has restored every subsystem, including dynamic objects. */
    default void reconcileAfterRewindRestore() { }
}

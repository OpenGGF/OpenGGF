package com.openggf.mods.mutators;

/** Earliest host boundary at which one activation action or option edit is safe. */
@com.openggf.game.ModApi
public enum MutatorScope {
    /** Explicit configuration Resume, before the next forward gameplay tick. */
    LIVE,
    /** A qualifying full assembly/restart; never a checkpoint restore or preview. */
    LOAD,
    /** A new gameplay session, without requiring an executable restart. */
    LAUNCH;

    public boolean admits(MutatorScope required) {
        return ordinal() >= java.util.Objects.requireNonNull(required, "required").ordinal();
    }
}

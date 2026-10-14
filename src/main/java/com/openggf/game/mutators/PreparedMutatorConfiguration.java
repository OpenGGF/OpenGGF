package com.openggf.game.mutators;

/** Internal semantic projection of prepared definitions; no creator/fault registry crosses this boundary. */
@FunctionalInterface
public interface PreparedMutatorConfiguration {
    boolean hasDefinitions();
}

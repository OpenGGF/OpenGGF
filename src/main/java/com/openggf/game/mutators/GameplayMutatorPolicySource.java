package com.openggf.game.mutators;

/** World-owned provider for immutable physics/pacing decisions, never a creator callback. */
@FunctionalInterface
public interface GameplayMutatorPolicySource {
    GameplayMutatorPolicy policy();
}

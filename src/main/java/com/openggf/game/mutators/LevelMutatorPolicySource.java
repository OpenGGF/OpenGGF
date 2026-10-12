package com.openggf.game.mutators;

/** World-owned provider, resolved through WorldSessionPolicyAccess by native owners. */
@FunctionalInterface
public interface LevelMutatorPolicySource {
    LevelMutatorPolicy policy();
}

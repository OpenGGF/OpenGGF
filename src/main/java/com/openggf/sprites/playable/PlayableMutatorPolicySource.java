package com.openggf.sprites.playable;

/** Structural session binding; implementations read only the published effective revision. */
@FunctionalInterface
public interface PlayableMutatorPolicySource {
    PlayableMutatorPolicy policyFor(AbstractPlayableSprite sprite);

    /** Called once before an ordinary forward player dispatch, never initial assembly or rendering. */
    default void beforeForwardTick() { }
    /** Internal structural binding for native motion consumers, never creator callback authority. */
    default com.openggf.game.mutators.TransientMutatorPolicySource transientPolicies() { return null; }
}

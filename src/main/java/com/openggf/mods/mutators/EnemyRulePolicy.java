package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record EnemyRulePolicy(Kind kind) implements MutatorPolicy {
    public EnemyRulePolicy { java.util.Objects.requireNonNull(kind); }
    @Override public MutatorCapability capability() { return MutatorCapability.ENEMY_RULE; }
    @com.openggf.game.ModApi public enum Kind { FIRE_CADENCE, STASIS, BUDDING, LOOK_GATE, BLIND_SPOT, CHAIN_REACTION, TRUCE }
}

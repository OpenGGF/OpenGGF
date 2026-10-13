package com.openggf.mods.mutators;

/** Immutable bounded semantic request; all world authority remains in the native host consumer. */
@com.openggf.game.ModApi
public record ProjectileRulePolicy(Kind kind) implements MutatorPolicy {
    public ProjectileRulePolicy { java.util.Objects.requireNonNull(kind); }
    @Override public MutatorCapability capability() { return MutatorCapability.PROJECTILE_RULE; }
    @com.openggf.game.ModApi public enum Kind { SHORT_RANGE, PLAYER_FIRE, IMPACT_BLAST }
}

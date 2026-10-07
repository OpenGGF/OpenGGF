package com.openggf.mods.mutators;

import java.util.Objects;

/** Immutable host-interpreted policy values. They cannot read or mutate a gameplay world. */
@com.openggf.game.ModApi
public sealed interface MutatorPolicy permits MutatorPolicy.DrySonicGravity,
        MutatorPolicy.PlayerStealth {
    MutatorCapability capability();

    /** Dimensionless integer percent; only the host's documented dry Sonic owner consumes it. */
    @com.openggf.game.ModApi
    record DrySonicGravity(int percent) implements MutatorPolicy {
        public DrySonicGravity {
            if (percent < 25 || percent > 200) throw new IllegalArgumentException("Gravity must be 25..200 percent");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.DRY_SONIC_GRAVITY; }
    }

    /** Presentation only, after native sprite admission. Collision and targeting remain stock. */
    @com.openggf.game.ModApi
    record PlayerStealth(boolean hideBody, boolean hideAppendage, Target target,
                         boolean hideAttachedEffects) implements MutatorPolicy {
        public PlayerStealth { Objects.requireNonNull(target, "target"); }
        @Override public MutatorCapability capability() { return MutatorCapability.PLAYER_STEALTH; }
    }

    @com.openggf.game.ModApi
    enum Target { LEADER, ALL_TEAM }
}

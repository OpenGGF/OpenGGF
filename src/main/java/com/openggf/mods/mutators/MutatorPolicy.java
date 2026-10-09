package com.openggf.mods.mutators;

import java.util.Objects;

/** Immutable host-interpreted policy values. They cannot read or mutate a gameplay world. */
@com.openggf.game.ModApi
public sealed interface MutatorPolicy permits MutatorPolicy.DrySonicGravity,
        MutatorPolicy.PlayerStealth, MutatorPolicy.Ringfall, MutatorPolicy.BigHead,
        MutatorPolicy.MonitorFilter, MutatorPolicy.NoCheckpoints, MutatorPolicy.NoRings,
        MutatorPolicy.GameplaySpeed, MutatorPolicy.NoSpecialStages, MutatorPolicy.NoBonusStages,
        MutatorPolicy.DefeatKnockback {
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

    /** Percentage of the native recoverable scatter, with zero meaning no optional cap. */
    @com.openggf.game.ModApi
    record Ringfall(int percent, int hardCap) implements MutatorPolicy {
        public Ringfall {
            if (percent < 10 || percent > 100 || hardCap < 0 || hardCap > 32)
                throw new IllegalArgumentException("Ringfall requires 10..100 percent and cap 0..32");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.RINGFALL; }
    }

    /**
     * Scale only reviewed anatomical head pixels; unsupported and ball art remains native.
     * With {@code scaleWithRings}, {@code percent} is the size reached at 100 native rings:
     * the host grows linearly from 100% at zero rings and clamps above 100 rings.
     */
    @com.openggf.game.ModApi
    record BigHead(int percent, Target target, boolean scaleWithRings) implements MutatorPolicy {
        public BigHead {
            if (percent < 100 || percent > 200) throw new IllegalArgumentException("Head scale must be 100..200 percent");
            Objects.requireNonNull(target, "target");
        }
        /** A fixed head size, independent of rings. */
        public BigHead(int percent, Target target) { this(percent, target, false); }
        @Override public MutatorCapability capability() { return MutatorCapability.BIG_HEAD; }
    }

    /** Placement decisions use semantic contents, never a shared numeric object/subtype ID. */
    @com.openggf.game.ModApi
    record MonitorFilter(java.util.Set<com.openggf.game.mutators.MonitorContent> removedContents)
            implements MutatorPolicy {
        public MonitorFilter { removedContents = java.util.Set.copyOf(removedContents); }
        @Override public MutatorCapability capability() { return MutatorCapability.MONITOR_FILTER; }
    }

    /** Disables death respawn banking independently of checkpoint presentation and stage return. */
    @com.openggf.game.ModApi
    record NoCheckpoints() implements MutatorPolicy {
        @Override public MutatorCapability capability() { return MutatorCapability.NO_CHECKPOINTS; }
    }

    /** Main-level placement and awards only; special and bonus stage puzzles retain native rules. */
    @com.openggf.game.ModApi
    record NoRings() implements MutatorPolicy {
        @Override public MutatorCapability capability() { return MutatorCapability.NO_RINGS; }
    }

    /** Whole native step budgets; never scales physics velocities or the desktop frame limiter. */
    @com.openggf.game.ModApi
    record GameplaySpeed(int percent, boolean audioFollowsSpeed) implements MutatorPolicy {
        public GameplaySpeed {
            if (percent < 25 || percent > 400) throw new IllegalArgumentException("Speed must be 25..400 percent");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.GAMEPLAY_SPEED; }
    }

    /** Rejects a new semantic entry before player capture, preserving previously admitted entry. */
    @com.openggf.game.ModApi
    record NoSpecialStages() implements MutatorPolicy {
        @Override public MutatorCapability capability() { return MutatorCapability.NO_SPECIAL_STAGES; }
    }

    @com.openggf.game.ModApi
    record NoBonusStages() implements MutatorPolicy {
        @Override public MutatorCapability capability() { return MutatorCapability.NO_BONUS_STAGES; }
    }

    /** Amplifies resolved native badnik-defeat vertical rebound once; X motion remains native. */
    @com.openggf.game.ModApi
    record DefeatKnockback(int percent, int verticalSpeedCap) implements MutatorPolicy {
        public DefeatKnockback {
            if (percent < 150 || percent > 300 || verticalSpeedCap < 0x100 || verticalSpeedCap > 0x2000)
                throw new IllegalArgumentException("Defeat knockback exceeds host bounds");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.DEFEAT_KNOCKBACK; }
    }

    @com.openggf.game.ModApi
    enum Target { LEADER, ALL_TEAM }
}

package com.openggf.game.sonic2.kis2;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;

/**
 * Physics for the Knuckles in Sonic 2 lock-on patch
 * ({@code docs/kis2/BRANCH_DIFFS.md} §Physics constants).
 *
 * <p>Knuckles receives {@link Kis2Physics#KNUCKLES}; every other character
 * code falls through to the stock Sonic 2 profiles. Stateless per call: it
 * does not copy {@code Sonic3kPhysicsProvider}'s last-character caching.
 */
public final class Kis2PhysicsProvider implements PhysicsProvider, com.openggf.game.internal.PlayerStageRoutines {
    private final java.util.function.BooleanSupplier demoMode;

    public Kis2PhysicsProvider() {
        this(() -> false);
    }

    Kis2PhysicsProvider(java.util.function.BooleanSupplier demoMode) {
        this.demoMode = demoMode;
    }

    @Override
    public int jumpImpulse(com.openggf.sprites.playable.AbstractPlayableSprite player, int impulse) {
        // KiS2 c336fede Sonic_Jump :39968-39980 adds $80 even underwater.
        return demoMode.getAsBoolean() ? impulse + 0x80 : impulse;
    }

    @Override
    public boolean allowsMidairAbility(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // KiS2 c336fede Sonic_CheckGoSuper :40086-40095 skips both Glide and Super.
        return !demoMode.getAsBoolean();
    }

    @Override
    public boolean alwaysCapsAirSpeed(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // KiS2 c336fede Sonic_ChgJumpDir :39763-39796 retains the stock cap in demos.
        return demoMode.getAsBoolean();
    }

    @Override
    public PhysicsProfile getProfile(String characterType) {
        if ("knuckles".equalsIgnoreCase(characterType)) {
            return Kis2Physics.KNUCKLES;
        }
        if ("tails".equalsIgnoreCase(characterType)) {
            return PhysicsProfile.SONIC_2_TAILS;
        }
        return PhysicsProfile.SONIC_2_SONIC;
    }

    /**
     * KiS2 {@code Sonic_Jump}: {@code move.w #$300,d2} when underwater. The
     * lock-on program has exactly one player object, so the modifiers are
     * module-wide; a configured sidekick (an engine-only divergence) shares
     * them.
     */
    @Override
    public PhysicsModifiers getModifiers() {
        return PhysicsModifiers.KNUCKLES;
    }
}

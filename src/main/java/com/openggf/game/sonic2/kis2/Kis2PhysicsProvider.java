package com.openggf.game.sonic2.kis2;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rules.GameRules;

/**
 * Physics for the Knuckles in Sonic 2 lock-on patch
 * ({@code docs/kis2/BRANCH_DIFFS.md} §Physics constants).
 *
 * <p>Knuckles receives {@link Kis2Physics#KNUCKLES}; every other character
 * code falls through to the stock Sonic 2 profiles. Stateless per call: it
 * does not copy {@code Sonic3kPhysicsProvider}'s last-character caching.
 */
public final class Kis2PhysicsProvider implements PhysicsProvider {

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

    @Override
    public GameRules getRules() {
        return Kis2Rules.RULES;
    }
}

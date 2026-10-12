package com.openggf.game;

import com.openggf.game.rules.GameRules;

/**
 * Per-game physics provider interface.
 * Returns character-specific physics profiles, modifier rules, and typed per-game rules.
 *
 * <p>Implementations are game-specific (S1, S2, S3K) and accessed via
 * {@link GameModule#getPhysicsProvider()}.
 */
@com.openggf.game.ModApi
public interface PhysicsProvider {

    /** Constant tuning with explicit game-wide modifiers and rules. */
    static PhysicsProvider fixed(PhysicsProfile profile, PhysicsModifiers modifiers, GameRules rules) {
        java.util.Objects.requireNonNull(profile, "profile");
        java.util.Objects.requireNonNull(modifiers, "modifiers");
        java.util.Objects.requireNonNull(rules, "rules");
        return new PhysicsProvider() {
            @Override public PhysicsProfile getProfile(String character) { return profile; }
            @Override public PhysicsModifiers getModifiers() { return modifiers; }
            @Override public GameRules getRules() { return rules; }
        };
    }

    /**
     * Returns the physics profile for the given character type.
     *
     * @param characterType identifier such as "sonic" or "tails"
     * @return the physics profile for the character
     */
    PhysicsProfile getProfile(String characterType);

    /**
     * Returns the init-time physics profile for the given character type.
     *
     * <p>S3K loads per-character values from the {@code Character_Speeds} table
     * (sonic3k.asm:202288) at level init and respawn. These differ from the
     * canonical profile and persist until the first water or speed shoes event.
     *
     * <p>Returns {@code null} for S1/S2 where init values equal the canonical profile.
     *
     * @param characterType identifier such as "sonic" or "tails"
     * @return the init-time profile, or null if init uses the canonical profile
     */
    default PhysicsProfile getInitProfile(String characterType) {
        return null;
    }

    /**
     * Returns the physics modifiers (water/speed shoes rules) for this game.
     *
     * @return the physics modifiers
     */
    PhysicsModifiers getModifiers();

    GameRules getRules();
    /** Applies an immutable edit only to matching characters, including their optional init profile. */
    default PhysicsProvider transform(java.util.function.Predicate<String> characters,
                                      java.util.function.Function<PhysicsProfile, PhysicsProfile> edit) {
        java.util.Objects.requireNonNull(characters, "characters");
        java.util.Objects.requireNonNull(edit, "edit");
        PhysicsProvider source = this;
        return new PhysicsProvider() {
            private PhysicsProfile map(String character, PhysicsProfile profile) {
                return profile == null || !characters.test(character) ? profile
                        : java.util.Objects.requireNonNull(edit.apply(profile), "Profile transform returned null");
            }
            @Override public PhysicsProfile getProfile(String character) { return map(character, source.getProfile(character)); }
            @Override public PhysicsProfile getInitProfile(String character) { return map(character, source.getInitProfile(character)); }
            @Override public PhysicsModifiers getModifiers() { return source.getModifiers(); }
            @Override public GameRules getRules() { return source.getRules(); }
        };
    }

}

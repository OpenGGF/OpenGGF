package com.openggf.game;

/**
 * Per-game physics provider interface.
 * Returns character-specific physics profiles and water/speed-shoes modifiers.
 *
 * <p>Implementations are game-specific and accessed via
 * {@link GameModule#getPhysicsProvider()}. Game-wide behavioural rules are not
 * physics: they belong to {@link GameModule#getPolicies()}.
 */
@com.openggf.game.ModApi
public interface PhysicsProvider {

    /** Constant tuning with explicit game-wide modifiers. */
    static PhysicsProvider fixed(PhysicsProfile profile, PhysicsModifiers modifiers) {
        java.util.Objects.requireNonNull(profile, "profile");
        java.util.Objects.requireNonNull(modifiers, "modifiers");
        return new PhysicsProvider() {
            @Override public PhysicsProfile getProfile(String character) { return profile; }
            @Override public PhysicsModifiers getModifiers() { return modifiers; }
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
     * (sonic3k.asm:202403) at level init and respawn. These differ from the
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

    /** Applies an immutable edit only to matching characters, including their optional init profile. */
    default PhysicsProvider transform(java.util.function.Predicate<String> characters,
                                      java.util.function.Function<PhysicsProfile, PhysicsProfile> edit) {
        return new com.openggf.game.internal.PhysicsProfileOverrides(this, characters, edit);
    }

}

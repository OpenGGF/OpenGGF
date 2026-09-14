package com.openggf.game.sonic2;

/** Shipped object variations selected by the host module, including lock-on patches. */
public record Sonic2ObjectBehaviorProfile(
        boolean heldVinePinsPlayer,
        boolean propellerClearsAirAbility,
        boolean fallingPillarAlwaysSquashesGroundedContact) {
    public static final Sonic2ObjectBehaviorProfile STOCK =
            new Sonic2ObjectBehaviorProfile(false, false, false);
}

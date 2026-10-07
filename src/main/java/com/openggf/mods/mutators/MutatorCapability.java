package com.openggf.mods.mutators;

/** Bounded semantic policies implemented by the host, not arbitrary world callbacks. */
@com.openggf.game.ModApi
public enum MutatorCapability {
    DRY_SONIC_GRAVITY(MutatorScope.LIVE),
    PLAYER_STEALTH(MutatorScope.LIVE);

    private final MutatorScope minimumScope;
    MutatorCapability(MutatorScope minimumScope) { this.minimumScope = minimumScope; }
    public MutatorScope minimumScope() { return minimumScope; }
}

package com.openggf.mods.mutators;

/** Bounded semantic policies implemented by the host, not arbitrary world callbacks. */
@com.openggf.game.ModApi
public enum MutatorCapability {
    DRY_SONIC_GRAVITY(MutatorScope.LIVE),
    PLAYER_STEALTH(MutatorScope.LIVE),
    RINGFALL(MutatorScope.LIVE),
    BIG_HEAD(MutatorScope.LIVE),
    MONITOR_FILTER(MutatorScope.LOAD),
    NO_CHECKPOINTS(MutatorScope.LOAD),
    NO_RINGS(MutatorScope.LOAD),
    GAMEPLAY_SPEED(MutatorScope.LIVE),
    NO_SPECIAL_STAGES(MutatorScope.LIVE),
    NO_BONUS_STAGES(MutatorScope.LIVE),
    DEFEAT_KNOCKBACK(MutatorScope.LIVE);

    private final MutatorScope minimumScope;
    MutatorCapability(MutatorScope minimumScope) { this.minimumScope = minimumScope; }
    public MutatorScope minimumScope() { return minimumScope; }
}

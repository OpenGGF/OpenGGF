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
    DEFEAT_KNOCKBACK(MutatorScope.LIVE),
    HOMING_ATTACK(MutatorScope.LIVE),
    SCENE_PRESENTATION(MutatorScope.LIVE), SPRITE_PRESENTATION(MutatorScope.LIVE),
    HUD_PRESENTATION(MutatorScope.LIVE), AUDIO_EFFECT(MutatorScope.LIVE),
    OVERLAY_CONTENT(MutatorScope.LIVE), GAMEPLAY_HOLD(MutatorScope.LIVE),
    ENCOUNTER(MutatorScope.LIVE), ENEMY_RULE(MutatorScope.LIVE), PROJECTILE_RULE(MutatorScope.LIVE),
    OBJECT_FIELD(MutatorScope.LIVE), RING_ECONOMY(MutatorScope.LIVE), NATIVE_POWERUP(MutatorScope.LIVE),
    RELOCATION(MutatorScope.LIVE), TRANSPORT(MutatorScope.LIVE), CHALLENGE(MutatorScope.LIVE),
    PLAYER_MOTION(MutatorScope.LIVE), INPUT_CONTROL(MutatorScope.LIVE), SIGNED_GRAVITY(MutatorScope.LIVE),
    SPRING_RESPONSE(MutatorScope.LIVE), FLUID_CONTEXT(MutatorScope.LIVE);

    private final MutatorScope minimumScope;
    MutatorCapability(MutatorScope minimumScope) { this.minimumScope = minimumScope; }
    public MutatorScope minimumScope() { return minimumScope; }
}

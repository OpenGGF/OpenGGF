package com.openggf.sprites.managers;

import com.openggf.sprites.playable.PlayableMutatorPolicySource;

/** Engine-only binding, deliberately outside the creator-facing Mod API. */
public final class SpriteManagerInternalAccess {
    private SpriteManagerInternalAccess() { }

    /**
     * Bind after gameplay-context creation. New/recreated roster members inherit
     * this source; a replacement gameplay context must be bound independently.
     */
    public static void bindMutatorPolicies(SpriteManager manager, PlayableMutatorPolicySource source) {
        manager.bindMutatorPolicies(source);
    }

    public static void bindMotionMutators(SpriteManager manager, com.openggf.game.mutators.MotionMutatorRuntime runtime) {
        requireEngineCaller();
        manager.bindMotionMutators(runtime);
    }

    public static com.openggf.game.mutators.MotionMutatorRuntime motionMutators(
            com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        requireEngineCaller();
        return sprite.getMovementManager() instanceof PlayableSpriteMovement movement ? movement.motionMutators : null;
    }

    private static void requireEngineCaller() {
        if (com.openggf.util.EngineCallerAccess.callerOutside(SpriteManagerInternalAccess.class).getClassLoader()
                != SpriteManagerInternalAccess.class.getClassLoader()) throw new SecurityException("Engine motion access only");
    }
}

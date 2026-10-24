package com.openggf.sprites.managers;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Read-only native readiness for engine-owned input planners; never activates an ability. */
public final class NativeAirAbilityReadiness {
    private NativeAirAbilityReadiness() { }

    /**
     * Knux_JumpHeight / Knux_Test_For_Glide (sonic3k.asm:32548-32586) require
     * an actual jump and its velocity gate before the next controller edge can
     * start gliding. Reuse the production movement gate, including underwater
     * readiness; no director-specific native constants or physics writes.
     */
    public static boolean canAttempt(AbstractPlayableSprite sprite) {
        return sprite.getAir() && sprite.isJumping()
                && sprite.getMovementManager() instanceof PlayableSpriteMovement movement
                && movement.isAirAbilityWindowOpen();
    }
}

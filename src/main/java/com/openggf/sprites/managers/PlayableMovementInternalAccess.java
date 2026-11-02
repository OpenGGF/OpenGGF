package com.openggf.sprites.managers;

import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-only access to the native terrain dispatch used by movement-owning objects. */
public final class PlayableMovementInternalAccess {
    private PlayableMovementInternalAccess() { }

    public static void resolveLevelCollision(AbstractPlayableSprite player, CollisionSystem collision) {
        if (player.getMovementManager() instanceof PlayableSpriteMovement movement) {
            movement.resolveObjectControlledLevelCollision(collision, false);
        }
    }
}

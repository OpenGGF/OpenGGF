package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.game.PlayableEntity;

import java.util.List;

/** Runtime boundary for powered full-screen attacks and their frozen target view. */
@ModApi
public final class PoweredAttackSurface {
    private final ObjectTouchResponseController touchResponses;
    private final ObjectCollisionResponseList collisionResponses;

    PoweredAttackSurface(
            ObjectTouchResponseController touchResponses,
            ObjectCollisionResponseList collisionResponses) {
        this.touchResponses = touchResponses;
        this.collisionResponses = collisionResponses;
    }

    public void apply(PlayableEntity player) {
        touchResponses.applyPoweredScreenAttack(player);
    }

    public List<ObjectInstance> targetReadView() {
        return collisionResponses.playerReadView();
    }
}

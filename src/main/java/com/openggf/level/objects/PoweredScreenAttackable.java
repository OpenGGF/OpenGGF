package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

/** Target-owned destruction path for ROM powered full-screen attacks. */
public interface PoweredScreenAttackable {
    void onPoweredScreenAttack(PlayableEntity player);
}

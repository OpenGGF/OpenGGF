package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

/** Target-owned destruction path for ROM powered full-screen attacks. */
@com.openggf.game.ModApi
public interface PoweredScreenAttackable {
    void onPoweredScreenAttack(PlayableEntity player);
}

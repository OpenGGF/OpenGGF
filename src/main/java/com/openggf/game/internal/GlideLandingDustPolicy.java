package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Host admission for arming the fixed dust emitter on a glide landing. */
@FunctionalInterface
public interface GlideLandingDustPolicy {
    boolean canArm(AbstractPlayableSprite player);
}

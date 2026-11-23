package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Host rules for the fixed dust emitter on a glide landing. */
public record GlideLandingDustPolicy(boolean emitsUnderwater) {
    public static boolean hasAirForDust(AbstractPlayableSprite player) {
        var drowning = player.getDrowningController();
        return drowning == null || drowning.getRemainingAir() >= 12;
    }

    public boolean canEmit(AbstractPlayableSprite player) {
        return emitsUnderwater || !player.isInWater();
    }
}

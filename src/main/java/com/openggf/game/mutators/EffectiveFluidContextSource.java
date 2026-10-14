package com.openggf.game.mutators;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Internal target query; a local fluid region is never projected into the native water plane. */
@FunctionalInterface
public interface EffectiveFluidContextSource {
    boolean underwaterAt(AbstractPlayableSprite player, int centreX, int centreY);
}

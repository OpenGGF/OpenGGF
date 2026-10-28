package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Internal admission for the mean-to-target step; water presentation still runs. */
@FunctionalInterface
public interface DynamicWaterMotionPolicy {
    boolean shouldAdvanceDynamicWater(AbstractPlayableSprite player);
}

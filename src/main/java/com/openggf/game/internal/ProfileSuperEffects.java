package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Native host owns optional effects; authored profiles grant only the requested presentation. */
public interface ProfileSuperEffects {
    void updateStars(AbstractPlayableSprite player);
    void renderTrail(AbstractPlayableSprite player);
}

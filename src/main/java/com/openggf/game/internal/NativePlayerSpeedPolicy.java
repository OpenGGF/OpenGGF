package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

/** Engine-only authority for literal player speed-word writers, supplied by a physics provider. */
public interface NativePlayerSpeedPolicy {
    boolean supports(AbstractPlayableSprite player, com.openggf.game.session.WorldSession ownerWorld);
    void initialize(AbstractPlayableSprite player);
    void waterEdge(AbstractPlayableSprite player, boolean underwater);
    void giveSpeedShoes(AbstractPlayableSprite player);
    void expireSpeedShoes(AbstractPlayableSprite player);
    short neutralGroundFriction(AbstractPlayableSprite player, short acceleration);
    boolean ownsProfileInstallation(SuperStateController controller);
}

package com.openggf.game;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

/** Internal runtime access to donor-owned shield playback, outside the creator API. */
public final class CrossGameShieldArt {
    private CrossGameShieldArt() { }

    public static PlayerSpriteRenderer renderer(CrossGameFeatureProvider donor, AbstractPlayableSprite owner) {
        return donor.getOwnedInstaShieldRenderer(owner);
    }
}

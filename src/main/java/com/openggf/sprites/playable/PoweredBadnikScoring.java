package com.openggf.sprites.playable;

import com.openggf.game.PlayableEntity;

/**
 * Internal scoring bridge for ROM powered-screen attacks.
 */
public final class PoweredBadnikScoring {

    private PoweredBadnikScoring() {
    }

    public static int incrementChain(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite nativePlayer) {
            return nativePlayer.incrementPoweredBadnikChain();
        }
        return player.incrementBadnikChain();
    }
}

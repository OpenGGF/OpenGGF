package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;

/** The player's 3D-ramp state as the ramp objects read it, from the shared player owner. */
final class PpzRampPlayer {
    private PpzRampPlayer() { }

    /** {@code btst #1,oPlayerCtrl(a6)}. */
    static boolean onRamp(PlayableEntity player) {
        return player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite
                && com.openggf.game.soniccd.player.CdPlayerStage.onPseudo3dRamp(sprite);
    }
}

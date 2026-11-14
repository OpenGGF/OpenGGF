package com.openggf.game.sonic3k;

import com.openggf.game.internal.ProfileSuperEffects;
import com.openggf.game.sonic3k.objects.HyperSonicStarsObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Reuses native ROM-backed effects with the authored character as their owner. */
public final class Sonic3kProfileSuperEffects implements ProfileSuperEffects {
    @Override public void renderTrail(AbstractPlayableSprite player) { HyperFormTrailRenderer.drawProfile(player); }
    @Override public void updateStars(AbstractPlayableSprite player) {
        var level = player.currentLevelManagerIfAvailable();
        var objects = level == null ? null : level.getObjectManager();
        if (objects != null && objects.getActiveObjects().stream().noneMatch(object ->
                object instanceof HyperSonicStarsObjectInstance stars && stars.isBoundTo(player) && !stars.isDestroyed()))
            objects.addDynamicObject(new HyperSonicStarsObjectInstance(player));
    }
}

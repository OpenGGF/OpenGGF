package com.openggf.sprites.playable;

import com.openggf.level.objects.ObjectInstance;

/** Engine-only cross-package access to playable controller internals. */
public final class PlayableSpriteInternalAccess {
    private PlayableSpriteInternalAccess() {
    }

    public static Short projectedObjectControlledSolidContactXSpeed(
            AbstractPlayableSprite sprite, ObjectInstance candidate) {
        return sprite.getObjectControlledSolidContactProjectedXSpeed(candidate);
    }
}

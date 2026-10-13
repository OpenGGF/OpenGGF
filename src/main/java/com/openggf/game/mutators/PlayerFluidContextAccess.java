package com.openggf.game.mutators;

import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Semantic effective fluid queries. Native status/history and native surface owners stay separate. */
public final class PlayerFluidContextAccess {
    private PlayerFluidContextAccess() { }
    public static boolean movementUnderwater(AbstractPlayableSprite sprite) {
        return sprite.isInWater() || localFluid(sprite);
    }
    public static boolean speedConstantsUnderwater(AbstractPlayableSprite sprite, boolean nativeWaterPhysics) {
        return nativeWaterPhysics || localFluid(sprite);
    }
    public static boolean breathProtected(AbstractPlayableSprite sprite) { return localFluid(sprite); }
    private static boolean localFluid(AbstractPlayableSprite sprite) {
        var motion = SpriteManagerInternalAccess.motionMutators(sprite);
        return motion != null && motion.movementFluidActive(sprite);
    }
}

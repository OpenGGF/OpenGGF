package com.openggf.sprites.playable;

import com.openggf.game.internal.BadnikChainPolicy;

/** Semantic ResetOnFloor tail, separate from direct native airborne-bit writes. */
public final class PlayableNativeFloorReset {
    private PlayableNativeFloorReset() { }

    public static BadnikChainPolicy chain(AbstractPlayableSprite sprite) {
        // Chain RAM belongs to the world host; a bound actor may use a donor's movement module.
        var level = sprite.currentLevelManagerIfAvailable();
        var world = level != null ? level.getWorldSession() : null;
        var host = world != null ? world.getGameModule() : sprite.currentGameModule();
        return host != null ? host.getGameService(BadnikChainPolicy.class) : null;
    }

    public static void resetChain(AbstractPlayableSprite sprite) {
        var policy = chain(sprite);
        if (policy != null) policy.reset();
    }
}

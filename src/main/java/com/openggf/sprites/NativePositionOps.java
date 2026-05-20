package com.openggf.sprites;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Central helpers for ROM native x_pos/y_pos writes on playable sprites.
 */
public final class NativePositionOps {
    private NativePositionOps() {
    }

    public static void writeXPosPreserveSubpixel(AbstractPlayableSprite player, int x) {
        player.setCentreXPreserveSubpixel((short) x);
    }

    public static void writeYPosPreserveSubpixel(AbstractPlayableSprite player, int y) {
        player.setCentreYPreserveSubpixel((short) y);
    }

    public static void addXPosPreserveSubpixel(AbstractPlayableSprite player, int dx) {
        player.shiftX(dx);
    }

    public static void addYPosPreserveSubpixel(AbstractPlayableSprite player, int dy) {
        player.shiftY(dy);
    }

    public static void writeXPosResetSubpixel(AbstractPlayableSprite player, int x) {
        player.setCentreX((short) x);
    }

    public static void writeYPosResetSubpixel(AbstractPlayableSprite player, int y) {
        player.setCentreY((short) y);
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Shared shipped-ROM sub_8E388 pressure consumer (sonic3k.asm:194275–194305). */
public final class CluckoidWindPressure {
    private CluckoidWindPressure() { }

    public static void apply(int sourceX, int sourceY, boolean facingLeft, PlayableEntity actor) {
        if (!(actor instanceof AbstractPlayableSprite player)
                || player.getAnimationId() == 8 || player.getSpindash() || player.isObjectControlled()) return;
        // Find_OtherObject subtracts native position words before its signed direction/absolute-range tests.
        int dx = (short) (player.getCentreX() - sourceX);
        int dy = Math.abs((short) (player.getCentreY() - sourceY));
        if (Math.abs(dx) >= 0x100 || dy >= 0x40 || (facingLeft ? dx > 0 : dx <= 0)) return;
        int push = 0x10 - Math.min(0x0F, Math.abs(dx) >> 4);
        NativePositionOps.addXPosPreserveSubpixel(player, facingLeft ? -push : push);
    }
}

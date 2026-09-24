package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code loc_460A6} (sonic3k.asm:92009-92022): the pose table both {@code Obj_SSZRotating-
 * Platform} and its carrier child use to draw a player who is being swung around a post.
 *
 * <p>The index arithmetic is {@code d0 = ((((angle + $A) & $FF) * 3) >> 5) & $FFFE} — twelve
 * two-byte rows of {@code byte_468C4}, each a render-flag pair and a mapping frame. Multiplying by
 * three and shifting by five is {@code 256/12} rounded the way the 68000 does it, so the twelve
 * poses are not evenly spaced: rows at the ends of the cycle hold for one step longer.
 *
 * <p>The ROM writes the row's low two bits into {@code render_flags(a1)} directly. This engine
 * derives a player's horizontal flip from {@link Direction}, so the X-flip bit is applied that way
 * instead; that additionally moves the player's status facing bit, which the ROM leaves alone. The
 * player is under {@code object_control 3} throughout, so nothing reads the facing bit until the
 * release — see the SSZ entry in {@code docs/status/s3k-known-bugs.md}.
 */
public final class SszCarriedPlayerPose {
    private static final Logger LOGGER = Logger.getLogger(SszCarriedPlayerPose.class.getName());

    /** {@code byte_468C4}: twelve (render flags, mapping frame) pairs. */
    public static final int TABLE_ADDR = Sonic3kConstants.SSZ_CARRIED_PLAYER_FRAME_TABLE_ADDR;
    public static final int ROW_COUNT = 12;
    /** {@code addi.w #$A,d0}. */
    static final int ANGLE_BIAS = 0x0A;

    private SszCarriedPlayerPose() {
    }

    /** {@code addi.w #$A,d0} … {@code andi.w #$FFFE,d0}: the byte offset into {@code byte_468C4}. */
    public static int rowOffset(int angleByte) {
        int biased = (angleByte + ANGLE_BIAS) & 0xFF;
        return ((biased * 3) >> 5) & 0xFFFE;
    }

    /** Applies one {@code byte_468C4} row, then {@code Perform_Player_DPLC}. */
    static void apply(Rom rom, AbstractPlayableSprite sprite, int angleByte) {
        if (rom == null) {
            return;
        }
        int offset = rowOffset(angleByte);
        try {
            byte[] row = rom.readBytes(TABLE_ADDR + offset, 2);
            int flags = row[0] & 0xFF;
            int frame = row[1] & 0xFF;
            sprite.setDirection((flags & 1) != 0 ? Direction.LEFT : Direction.RIGHT);
            sprite.setObjectMappingFrameControl(true);
            sprite.setMappingFrame(frame);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "byte_468C4 row at offset " + offset + " unavailable", e);
        }
    }
}

package com.openggf.game.soniccd.objects.ssz;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The player-overlap test the launch objects share ({@code loc_20BFA4}, objects/r7/launch.asm:655-693):
 * the object's centre against the player's, using the player's radii ({@code obj.width} = {@code oXRadius},
 * {@code obj.height} = {@code oYRadius}) plus the caller's {@code d2}/{@code d3}.
 */
final class SszPlayerOverlap {
    /** {@code cmpi.b #$2B,obj.anim_id(a2)} (:662): the player's anim $2B skips the test. */
    static final int ANIM_SKIP = 0x2B;
    /** {@code move.w #$78,obj.var_30(a2)} (:659): the post-hurt blink timer. */
    static final int BLINK_FRAMES = 0x78;

    private SszPlayerOverlap() { }

    /**
     * Whether the object at ({@code objX}, {@code objY}) touches the player; {@code halfWidth}/{@code halfHeight}
     * are {@code d2}/{@code d3}. First, as the routine does before any compare, a player in the hurt routine (4)
     * returns to the normal one (2) with the blink timer set (:656-659).
     */
    static boolean touches(AbstractPlayableSprite player, int objX, int objY, int halfWidth, int halfHeight) {
        if (player.isHurt()) {                               // cmpi.b #4,obj.routine(a2); subq.b #2,obj.routine(a2)
            player.setHurt(false);
            player.setInvulnerableFrames(BLINK_FRAMES);
        }
        if (player.getAnimationId() == ANIM_SKIP || player.getDead()) return false;   // :662-665 (routine >= 6)
        int d1 = (byte) player.getXRadius() + halfWidth;
        int d0 = ((objX - player.getCentreX()) + d1) & 0xFFFF;
        if ((short) d0 < 0) return false;                    // bmi.s loc_20BFFE
        if (d0 >= ((d1 * 2) & 0xFFFF)) return false;        // cmp.w d1,d0; bcc.s loc_20BFFE
        d1 = (byte) player.getYRadius() + halfHeight;
        d0 = ((objY - player.getCentreY()) + d1) & 0xFFFF;
        if ((short) d0 < 0) return false;
        return d0 < ((d1 * 2) & 0xFFFF);
    }
}

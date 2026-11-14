package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.soniccd.player.CdPlayerWater;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The player's side of Tidal Tempest's boss {@code $C0} touches while {@code boss_started} is 4
 * ({@code Player_TouchSpecial}, loc_207422-loc_2074B6, r4/player_object_collide.asm:296-392). Only the boss sets
 * {@code boss_started} on this stage, and only to 4, so its bubbles ($FF) and missiles ($FE) are the only
 * {@code $C0} types it meets; both decode as special touches whose listener is the part.
 */
final class TtzBossTouch {
    /** loc_2074B6: move.w #$AD,d0; jsr PlayFmSound (r4/player_object_collide.asm:372-373). */
    static final int FM_BUBBLE = 0xAD;
    /** loc_2074B6: move.b #$15,obj.anim_id(a0) (r4/player_object_collide.asm:371), the breath pose. */
    static final int ANIM_BREATH = 0x15;

    private TtzBossTouch() { }

    /**
     * sub_20730C and HurtPlayer (r4/player_object_collide.asm:208-270): no effect while invincible or while the
     * hurt timer ({@code oPlayerHurt}, var_30) runs; otherwise the shared hurt (lost rings, or death without rings
     * or shield) away from {@code source}. The time-warp gate ({@code warping}) is not hosted.
     */
    static void hurt(AbstractPlayableSprite player, TtzBossPart source, int frameCounter) {
        if (player.getInvincibleFrames() > 0 || player.getInvulnerableFrames() > 0 || player.getDead()) return;
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) source.objectServices().spawnLostRings(player, frameCounter);
        player.applyHurtOrDeath(source.getX(), false, hadRings);
    }

    /**
     * loc_2074B6 (r4/player_object_collide.asm:362-382): a rolling or roll-jumping player ({@code oFlags & $14}) pops
     * the bubble: both bits clear, the breath pose, FM $AD, {@code PlayerResetDrown}, and a $400 drop with $200
     * sideways away from the bubble. Returns whether it popped (the caller clears the bubble's type).
     * {@code PlayerResetDrown} (objects/r4/player_bubbles.asm:153-177: drown timer $1E; the drown music back to
     * the zone's is the audio owner's) is the player water owner's.
     */
    static boolean popBubble(AbstractPlayableSprite player, TtzBossPart bubble) {
        if (!player.getRolling() && !player.getRollingJump()) return false;   // andi.b #$14,d0; beq.s
        player.setRollingFlagPreserveRadii(false);                      // bclr #2,obj.flags(a0)
        player.setRollingJump(false);                                   // bclr #4,obj.flags(a0)
        player.setAnimationId(ANIM_BREATH);
        bubble.objectServices().playSfx(FM_BUBBLE);
        CdPlayerWater.resetDrownFor(player);                            // jsr PlayerResetDrown (:374)
        player.setYSpeed((short) 0x400);
        int xSpeed = -0x200;
        if ((player.getCentreX() & 0xFFFF) >= (bubble.getX() & 0xFFFF)) xSpeed = 0x200;   // cmp.w obj.x(a1),d0; bcs.s; neg.w
        player.setXSpeed((short) xSpeed);
        return true;
    }
}

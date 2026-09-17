package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** {@code sub_82C28} (sonic3k.asm:175310-175336) for the phase-2 bombs and rockets. */
final class DdzEndBossHitSupport {
    /** {@code word_82C62}. */
    private static final int[] BOX = {-0x10, 0x20, -0x10, 0x20};

    private DdzEndBossHitSupport() {
    }

    /**
     * While Player 1 is powered, a projectile whose box contains Player 1 sets
     * {@code invulnerability_timer = 89} and slows the autoscroll by {@code $30000}.
     *
     * <p>FixBugs = 0: {@code tst.b invulnerability_timer(a1)} is followed directly by the
     * {@code lea}/{@code jsr Check_InMyRange}, so its result is discarded and an already invulnerable
     * Player 1 is hit again. A fixed branch would skip the hit while the timer is running.
     */
    static boolean hitPlayer(ObjectServices services, int x, int y) {
        if (!DdzObjectSupport.playerPowered(services)) {
            return false;
        }
        AbstractPlayableSprite player = DdzObjectSupport.player(services);
        if (player == null || !DdzObjectSupport.inMyRange(x, y, player.getCentreX(), player.getCentreY(), BOX)) {
            return false;
        }
        player.setInvulnerableFrames(90 - 1);
        DdzObjectSupport.penaliseScrollSpeed(DdzObjectSupport.ddz(services));
        return true;
    }
}

package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * A bomb's touch, shared by the large and the small bomb (type $FD, status 2). Quartz Quadrant's
 * {@code PlayerObjectCollide} sends a {@code $C0}-class type of size $3D, while {@code boss_started} is 5
 * (loc_207186, r5/player_object_collide.asm:296-322; loc_20721E, :349-350), through loc2_20722C (:364-366) to
 * sub_207070 (:208-224: hurt the player unless it is warping, invincible or still blinking) and then loc_207226
 * (:358-360) adds 1 to the collision status (see {@link #onTouchResponse} for where that 1 lands); a rolling player
 * gets no rebound. With {@code boss_started} clear the touch does nothing. A status above 2 makes the bomb explode on
 * its next routine (loc_20BC7C).
 */
abstract class QqzBomb extends QqzBossPiece implements TouchResponseListener {
    /** move.b #$FD,obj.collide_type; move.b #2,obj.collide_status (objects/r5/boss.asm:785-786, 885-886). */
    static final int TOUCH_TYPE = 0xFD;

    /** var_2e: the dropper. */
    QqzBombDropper dropper;

    QqzBomb(int id, int x, int y, QqzBombDropper dropper, String name) {
        super(id, x, y, name);
        this.dropper = dropper;
    }

    QqzBomb(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override String sheet() { return QqzBossArt.BOMB; }

    /** {@code cmpi.b #2,obj.collide_status(a0); bgt.w loc_20BC7C}. */
    final boolean touchedPlayer() {
        return colStatus > 2;
    }

    /**
     * {@code btst #2,obj.var_2c(a1)} on the dropper (objects/r5/boss.asm:827-829, 919-921). The dropper never sets bit
     * 2; once it is deleted its cleared slot reads 0 too (a slot reused by another object is not modelled).
     */
    final boolean dropperBeaten() {
        return dropper != null && !dropper.isDestroyed() && dropper.has(BEATEN);
    }

    /**
     * The touch. {@code FixBugs = 0}: HurtPlayer (r5/player_object_collide.asm:226-235) allocates the lost rings with
     * {@code SpawnObject}, which leaves a1 on the new slot, so when the hurt scatters rings loc_207226 adds 1 to the
     * lost-ring slot's collision status and the bomb's stays 2: the bomb flies on. Without rings (death), with a
     * shield, or while the player is blinking or invincible, a1 is still the bomb and the bomb explodes. Fixed, every
     * touch would raise the bomb's status. Native R53C-death shows it: the large bomb hurts the player and still
     * bursts into four small bombs.
     */
    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (colType == 0) return;
        QqzBossTop top = top();
        if (top == null || top.bossFight() != QqzBossTop.BOSS_ACTIVE) return;   // tst.b boss_started; cmpi.b #5
        if (!hurt(player, frameCounter)) colStatus = (colStatus + 1) & 0xFF;   // loc_207226
    }

    /** sub_207070 (r5/player_object_collide.asm:208-224) into HurtPlayer; true when it allocated the lost rings. */
    private boolean hurt(PlayableEntity player, int frameCounter) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return false;
        // tst.b warping; tst.b invincible; tst.w obj.var_30: no hurt.
        var timeTravel = CdTimeTravel.of(services().gameModule());
        boolean warping = timeTravel != null && timeTravel.starsActive();
        if (warping || sprite.getInvincibleFrames() > 0 || sprite.getInvulnerableFrames() > 0 || sprite.isHurt()
                || sprite.getDead()) {
            return false;
        }
        boolean hadRings = sprite.getRingCount() > 0;
        boolean scatter = hadRings && !sprite.hasShield();
        if (scatter) services().spawnLostRings(sprite, frameCounter);
        sprite.applyHurtOrDeath(x, false, hadRings);
        return scatter;
    }

    /** A $C0-class type is the zone's special touch, never the shared boss bounce. */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (colType & 0xC0) == 0xC0; }

    /** Native's touch loop runs every frame while the player overlaps. */
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }

}

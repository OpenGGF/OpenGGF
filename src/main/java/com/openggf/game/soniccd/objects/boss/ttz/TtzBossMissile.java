package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Boss missile, object $4E ({@code BossMissileObject}, objects/r4/boss.asm:1200-1283), four at a time from Eggman
 * ({@code sub_20CEFA}, :1138-1176, offsets and speeds from byte_20CF5A, mirrored when he faces right). It rides at its
 * offset from Eggman for $2D frames, then becomes a $FE hazard and flies at its own speed until it leaves the arena
 * (X $9A0-$B00, Y up to $5D0); it is deleted at once when Eggman is beaten ({@code var_2c} bit 7).
 *
 * <p>Touch: $FE is a {@code $C0} type of size $3E. While {@code boss_started} is 4 the zone's
 * {@code Player_TouchSpecial} hurts the player and adds 1 to {@code collide_status}
 * (loc_207494/loc_2074B0, r4/player_object_collide.asm:334-358); the missile keeps flying.
 */
public final class TtzBossMissile extends TtzBossPart implements TouchResponseListener {
    /** Object $4E: BossMissileObject is index entry $4D (r4/object_index_3.asm:3-79). */
    public static final int ID = 0x4E;

    /** var_2e: Eggman. */
    TtzBossEggman eggman;
    /** var_38 / var_3a: the offset from Eggman while riding. */
    private int offsetX;
    private int offsetY;
    /** var_3c and x_speed: the X and Y speeds (16.16). */
    private long speedX;
    private long speedY;
    /** var_2a: frames ridden. */
    private int timer;

    TtzBossMissile(int x, int y, TtzBossEggman eggman, int offsetX, int offsetY, long speedX, long speedY) {
        super(ID, x, y, "TtzBossMissile");
        this.eggman = eggman;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.speedX = speedX;
        this.speedY = speedY;
    }

    private TtzBossMissile(ObjectSpawn spawn) {
        super(spawn, "TtzBossMissile");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                             // BossMissileObject_0_Routine0 (:1218-1225)
            routine = 2;
            priority = 6;
            width = 4;
            yRadius = 4;
            tileWord = TtzBossArt.BOSS_TILE;
        }
        if (eggman != null && eggman.defeated()) {                      // btst #7,$2C(a1); bne.s loc_20D03C
            hidden = true;
            deleteObject();
            return;
        }
        if (routine == 2) ride();
        else if (routine == 4 && fly()) return;
        animateObject("missileAnimations");
    }

    /** BossMissileObject_0_Routine2 (objects/r4/boss.asm:1227-1245). */
    private void ride() {
        if (eggman != null) {
            setXWord(eggman.getX());                                    // move.w 8(a1),obj.x
            setYWord(eggman.getY());
        }
        addX(offsetX);
        addY(offsetY);
        timer = (timer + 1) & 0xFF;
        if (timer != 0x2D) return;
        setCollision(0xFE, 2);
        routine = 4;
    }

    /** BossMissileObject_0_Routine4 and sub_20D044 (objects/r4/boss.asm:1249-1283); true when deleted. */
    private boolean fly() {
        addXLong(speedX);
        addYLong(speedY);
        if (xWord() >= 0x9A0 && xWord() <= 0xB00 && yWord() <= 0x5D0) return false;
        hidden = true;                                                  // addq.l #4,sp; jmp DeleteObject
        deleteObject();
        return true;
    }

    /** loc_2074B0 (r4/player_object_collide.asm:355-358): sub_20730C hurts the player; the status counts the touch. */
    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (player instanceof AbstractPlayableSprite sprite) TtzBossTouch.hurt(sprite, this, frameCounter);
        colStatus = (colStatus + 1) & 0xFF;
    }

    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }

    @Override String sheet() { return TtzBossArt.MISSILE; }

    @Override
    protected TtzBossMissile shell(ObjectSpawn spawn) {
        return new TtzBossMissile(spawn);
    }
}

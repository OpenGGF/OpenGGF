package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.zones.wwz.WwzStageState;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Bouncing platform, object $23 ({@code ObjBouncePlatform}, Level/Wacky Workbench/Objects/Platform/Main
 * (Bounce).asm:13-216): a $20-wide solid that jumps at $-600 and bounces off the terrain at its feet until
 * the electric floor's flash steps to the value that switches the bounce off (past $3C, present $5A; the
 * future always bounces), then rises once at -$180 and settles. Gravity is $10 a frame, the speed
 * is capped at $600.
 *
 * <p>The solid call wraps {@code SolidObject}: a player rising faster than $600 is not tested; the
 * platform's Y radius grows by 4 while it moves; a standing player's vertical speed is cleared for the call and
 * restored when there is no contact; on contact the player's position is the platform's 16.16 Y minus
 * (radius + $10), and a rising platform hands its speed to the rider (:152-214).
 */
public final class ObjBouncePlatform extends WwzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private static final int INIT = 0, MAIN = 2, BOUNCED = 4, LANDED = 6;
    /** {@code move.w #-$600,oYVel(a0)} (:51). */
    private static final int BOUNCE_SPEED = -0x600;
    /** {@code move.w #-$180,oYVel(a0)} (:82). */
    private static final int SETTLE_SPEED = -0x180;
    /** {@code move.w #$10,oBPtfmGrav(a0)} (:52). */
    private static final int GRAVITY = 0x10;
    /** {@code cmpi.w #$600,d0} (:111). */
    private static final int MAX_SPEED = 0x600;
    /** {@code move.b #$3C,d0} / {@code addi.b #$1E,d0} (:127-130). */
    private static final int PAST_OFF_STEP = 0x3C;
    private static final int PRESENT_OFF_STEP = 0x3C + 0x1E;

    private int yVel;
    private int gravity;
    private int radiusGrowth;

    public ObjBouncePlatform(ObjectSpawn spawn) {
        super(spawn, "BouncePlatform");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case INIT -> {
                init();
                main(sprite);
            }
            case MAIN -> main(sprite);
            case BOUNCED -> bounced(sprite);
            case LANDED -> landed(sprite);
            default -> { }
        }
    }

    /** ObjBouncePlatform_Init (:37-44). */
    private void init() {
        routine = MAIN;
        priority = 3;
        tileWord = WwzObjectArt.BOUNCE_PLATFORM_TILE;
        yRadius = 16;
        width = 32;
    }

    /** ObjBouncePlatform_Main (:48-56). */
    private void main(AbstractPlayableSprite player) {
        if (bounceAllowed()) {
            yVel = BOUNCE_SPEED;
            gravity = GRAVITY;
            routine = BOUNCED;
        }
        solid(player);
    }

    /** ObjBouncePlatform_Bounced (:60-87). */
    private void bounced(AbstractPlayableSprite player) {
        move();
        if (ObjectTerrainUtils.checkCeilingDist(x, y, yRadius).distance() < 0) {   // jsr ObjGetCeilDist
            yVel = 0;
        }
        if (ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance() < 0) {      // jsr ObjGetFloorDist
            if (bounceAllowed()) {
                yVel = BOUNCE_SPEED;
                gravity = GRAVITY;
                if (renderFlagOnScreen()) services().playSfx(WwzSounds.B4);          // FM_B4
            } else {
                yVel = SETTLE_SPEED;
                gravity = GRAVITY;
                routine = LANDED;
            }
        }
        solid(player);
    }

    /** ObjBouncePlatform_Landed (:91-103). */
    private void landed(AbstractPlayableSprite player) {
        move();
        if (yVel >= 0 && ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance() < 0) {
            yVel = 0;
            gravity = 0;
            routine -= 4;
        }
        solid(player);
    }

    /** ObjBouncePlatform_Move (:107-120): gravity, the $600 cap, then {@code add.l d0,oY}. */
    private void move() {
        int speed = (short) (yVel + gravity);
        if (speed >= 0 && speed >= MAX_SPEED) speed = MAX_SPEED;
        yVel = (short) speed;
        addToY(speed << 8);
    }

    /**
     * ObjBouncePlatform_CheckFloor (:124-141): true (d0 = 0, bounce) unless the zone is the past or
     * present and {@code palCycleSteps+3} equals the off step.
     */
    private boolean bounceAllowed() {
        int zone = timeZone();
        if (zone >= 2) return true;
        int off = zone == 0 ? PAST_OFF_STEP : PRESENT_OFF_STEP;
        var levels = services().levelManager();
        return WwzStageState.floorFlashStep(levels) != off;
    }

    /** ObjBouncePlatform_Solid (:152-214). */
    private void solid(AbstractPlayableSprite player) {
        if (player == null) return;
        int playerYVel = player.getYSpeed();
        if (playerYVel < 0 && -playerYVel > MAX_SPEED) return;        // neg.w / cmpi.w #$600 / bgt.w .End
        radiusGrowth = yVel != 0 ? 4 : 0;                              // oBPtfmYRad
        yRadius += radiusGrowth;
        try {
            int saved = player.getYSpeed();                            // move.w oYVel(a1),oBPftmYVel(a0)
            if (player.isOnObject() && !player.getAir()) player.setYSpeed((short) 0);
            if (!solidObject(Entry.SOLID)) {
                player.setYSpeed((short) saved);                       // move.w oBPftmYVel(a0),oYVel(a1)
                return;
            }
            if (player.getDead()) {                                    // cmpi.b #6,oRoutine(a1) / bcc.s .StopYVel
                player.setYSpeed((short) 0);
                return;
            }
            NativePositionOps.writeYPosPreserveSubpixel(player, y);    // move.l oY(a0),oY(a1)
            player.setSubpixelRaw(player.getXSubpixelRaw(), ySub);
            NativePositionOps.addYPosPreserveSubpixel(player, -(player.getYRadius() + 0x10));
            player.setYSpeed((short) (yVel < 0 ? yVel : 0));           // tst.w oYVel(a0) / bge.s .StopYVel
        } finally {
            yRadius -= radiusGrowth;
        }
    }

    @Override public int solidYVelocity() { return yVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    protected String sheetKey() {
        return WwzObjectArt.key(WwzObjectArt.BOUNCE_PLATFORM);
    }

    public int yVelocity() { return yVel; }
}

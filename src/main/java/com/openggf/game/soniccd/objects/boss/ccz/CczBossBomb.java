package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * A bomb, object $3E ({@code BombObject}, objects/r3/boss.asm:971-1175; index entry $3D, r3/object_index_3.asm:65).
 * The launcher drops it (routine 0): special touch type $D7, gravity $20 to a $600 cap, bouncing off floors,
 * ceilings and walls by reversing its speed, its two frames swapping every other frame. A touch adds to its
 * collision status (loc_207472 sends size $17 to loc_207502, r3/player_object_collide.asm:300-304, 358-360); on its next frame it throws
 * the player away at -$700 along the angle from the player ({@code Atan2}, {@code SineCosine}) and explodes with
 * {@code FM_EXPLODE}; after $F0 frames it explodes by itself. The arena's explosions are bombs started in routine
 * $A: the same explosion ({@code ExplosionAnims} at tile $680) with animation 0 and no sound of its own. An
 * explosion's script ends with $FC (routine 6), and the next frame deletes it.
 *
 * <p>The bomb's {@code oXRadius} is never written, so its wall probes run at its own X; its {@code oYRadius} is 8.
 */
final class CczBossBomb extends CczBossPiece implements TouchResponseListener {
    /** BombObject is index entry $3D (r3/object_index_3.asm:3-65). */
    static final int ID = BOMB_ID;
    /** cmpi.w #$F0,bomb.lifetime(a0) (objects/r3/boss.asm:1006). */
    static final int LIFETIME = 0xF0;
    /** muls.w #-$700 (objects/r3/boss.asm:1078, 1081). */
    static final int THROW = -0x700;

    int xSpeed;
    int ySpeed;
    /** {@code bomb.lifetime} (var_2a, word). */
    int lifetime;
    /** {@code bomb.x_acceleration}, {@code bomb.y_acceleration}, {@code bomb.terminal_y_speed} (var_38/3a/3c). */
    int xAccel;
    int yAccel;
    int terminalY;
    /** {@code obj.anim_timer}: the frame-swap counter. */
    int swapTimer;

    CczBossBomb(int x, int y) {
        super(ID, x, y, "CczBossBomb");
    }

    private CczBossBomb(ObjectSpawn spawn) {
        super(spawn, "CczBossBomb");
    }

    /** An arena explosion: {@code move.b #$3E,obj.id(a1); move.b #$A,obj.routine(a1)} (objects/r3/boss.asm:710-711). */
    static CczBossBomb silentExplosion(int x, int y) {
        CczBossBomb bomb = new CczBossBomb(x, y);
        bomb.routine = 0xA;
        return bomb;
    }

    /** The launcher's writes (BombLauncher_Launch .SetVelocity, objects/r3/boss.asm:922-925). */
    void launch(int dx, int xVelocity, int yVelocity) {
        x = (x + dx) & 0xFFFF;
        xSpeed = (short) xVelocity;
        ySpeed = (short) yVelocity;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                init();
                active(player);
            }
            case 2 -> active(player);
            case 4 -> explosion();
            case 6 -> deleteObject();                               // Bomb_Delete
            case 8 -> detonate();
            case 0xA -> {
                setAnimWord(1);                                     // Bomb_BeginSilentExplosion: anim 0, restart
                routine = 4;
                convertToExplosion();
            }
            default -> { }
        }
    }

    /** Bomb_Init (objects/r3/boss.asm:989-1000). */
    private void init() {
        routine = 2;
        priority = 5;
        width = 8;
        yRadius = 8;
        tileWord = CczBossArt.BOMB_TILE;
        colType = 0xD7;
        xAccel = 0;
        yAccel = 0x20;
        terminalY = 0x600;
    }

    /** Bomb_Active (objects/r3/boss.asm:1002-1065). */
    private void active(PlayableEntity player) {
        if (colStatus != 0) {
            hitPlayer(player);
            return;
        }
        lifetime = (lifetime + 1) & 0xFFFF;
        if ((short) lifetime >= LIFETIME) {
            detonate();
            return;
        }
        swapTimer = (swapTimer + 1) & 0xFF;
        if ((swapTimer & 2) != 0) mapFrame ^= 1;                    // btst #1,obj.anim_timer; eori.b #1,obj.sprite_frame
        move();
        if (ySpeed >= 0) {
            int d1 = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();   // CheckBlockDown
            if (d1 <= 0) {
                y = (y + d1) & 0xFFFF;
                ySpeed = (short) -ySpeed;
            }
        } else {
            // CheckBlockUp is ObjGetCeilDist's FindLevelFloor from (y - height) ^ $F upward (a3 = -$10, d6 = $1000,
            // object_block_collide.asm:301-327): a full block regresses to the one below. One collision index
            // (collisionPtr), so the path reads the same block on either.
            int d1 = ObjectTerrainUtils.checkNativeUpwardCeilingDist(services().levelManager(), false, x, y, yRadius)
                    .distance();
            if (d1 <= 0) {
                y = (y - d1) & 0xFFFF;
                ySpeed = (short) -ySpeed;
            }
        }
        if (xSpeed >= 0) {
            int d1 = ObjectTerrainUtils.checkRightWallDist(x, y).distance();       // CheckBlockRight, d3 = oXRadius (0)
            if (d1 <= 0) {
                x = (x + d1) & 0xFFFF;
                xSpeed = (short) -xSpeed;
            }
        } else {
            // CheckBlockLeft passes X unflipped (no eori.w #$F, FixBugs = 0): Sonic CD's object left-wall rule.
            int d1 = ObjectTerrainUtils.checkLeftWallDist(x, y).distance();        // CheckBlockLeft
            if (d1 <= 0) {
                x = (x - d1) & 0xFFFF;
                xSpeed = (short) -xSpeed;
            }
        }
    }

    /**
     * Bomb_ApplyAcceleratedMotion and Bomb_ApplyPosition (objects/r3/boss.asm:1134-1175): the accelerations, the
     * cap toward the acceleration's sign, then speed &lt;&lt; 8 added to the 16.16 position. The duplicate
     * acceleration block before Bomb_ApplyPosition is never reached.
     */
    private void move() {
        ySpeed = (short) (ySpeed + yAccel);
        xSpeed = (short) (xSpeed + xAccel);
        if (yAccel > 0) {
            if (terminalY <= ySpeed) ySpeed = terminalY;            // cmp.w obj.y_speed(a0),d0; bgt.s
        } else if (yAccel < 0) {
            if (terminalY >= ySpeed) ySpeed = terminalY;            // blt.s
        }
        addXLong((long) xSpeed << 8);
        addYLong((long) ySpeed << 8);
    }

    /** Bomb_HitPlayer (objects/r3/boss.asm:1069-1098), falling into Bomb_Detonate. */
    private void hitPlayer(PlayableEntity player) {
        colStatus = 0;
        if (player instanceof AbstractPlayableSprite sprite) {
            int d1 = (short) (x - sprite.getCentreX());
            int d2 = (short) (y - sprite.getCentreY());
            int angle = TrigLookupTable.calcAngle((short) d1, (short) d2) & 0xFF;
            sprite.setXSpeed((short) ((TrigLookupTable.cosHex(angle) * THROW) >> 8));
            sprite.setYSpeed((short) ((TrigLookupTable.sinHex(angle) * THROW) >> 8));
            sprite.setAir(true);                                    // bset #1,obj.flags(a1)
            sprite.setRollingJump(false);                           // bclr #4,obj.flags(a1)
            sprite.setPushing(false);                               // bclr #5,obj.flags(a1)
            sprite.setJumping(false);                               // clr.b obj.var_3c(a1)
            sprite.setOnObject(false);                              // .ClearPlayerFlag: bclr #3,obj.flags(a1)
        }
        // move.b #1,obj.anim_id(a0) is overwritten by Bomb_Detonate; the bomb has no object state entry.
        detonate();
    }

    /** Bomb_Detonate (objects/r3/boss.asm:1100-1105). */
    private void detonate() {
        fmSound(FM_EXPLODE);
        setAnimWord(0x100);
        routine = 4;
        convertToExplosion();
    }

    /** Bomb_ConvertToExplosion (objects/r3/boss.asm:1113-1120), falling into Bomb_Explosion. */
    private void convertToExplosion() {
        colType = 0;
        priority = 1;
        width = 0x10;
        yRadius = 0x10;
        tileWord = CczBossArt.EXPLOSION_TILE;
        explosion();
    }

    /** Bomb_Explosion (objects/r3/boss.asm:1122-1125). */
    private void explosion() {
        routine += animate(CczBossCatalogue.offset(payload(), "bombExplosionAnimations"));
    }

    private void setAnimWord(int word) {
        anim = (word >> 8) & 0xFF;
        prevAnim = word & 0xFF;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        colStatus = (colStatus + 1) & 0xFF;                         // loc_207502: addq.b #1,obj.collide_status(a1)
    }

    /** A {@code $C0}-class type only adds to the collision status (loc_207472, r3/player_object_collide.asm:300-304). */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override String sheet() {
        return tileWord == CczBossArt.EXPLOSION_TILE ? CczBossArt.EXPLOSION : CczBossArt.BOMB;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CczBossBomb(spawn);
    }
}

package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Dango, the armadillo badnik (object $2A in Stardust Speedway's indexes, {@code DangoObject}, objects/r7/dango.asm:3-281; Metallic
 * Madness's act 2 assembles the same body with one tile word, objects/r8/dango.asm). It drops to the floor (routine 2), walks along it at $6000 a frame ($1000
 * for subtype 1) turning at walls and ledges, and when the player is within $80 pixels on both axes and in front of it
 * rolls up (routines 8 to $10): collision type $ED, a bumper that throws the player away ($F900 times the cosine and sine of the angle
 * from the player) and goes into a fast roll of its own, then unrolls and walks on. Collision type $2C (walking) is an enemy.
 */
public final class ObjDango extends SharedBadnik implements TouchResponseListener {
    /** Routine0: {@code move.b #$E,obj.width / width_2; move.b #9,obj.height; move.b #$2C,obj.collide_type} (:33-41). */
    private static final int WIDTH = 0xE, HEIGHT = 9, WALK_COLLISION = 0x2C, BUMP_COLLISION = 0xED;
    /** {@code muls.w #$F900,d1; asr.l #8,d1} (:176-196). */
    private static final int BUMP_FACTOR = (short) 0xF900;

    private int speed;                    // obj.var_2a (a long: x velocity, 16.16)
    private int height = HEIGHT;
    private boolean touched;              // obj.collide_status

    public ObjDango(ObjectSpawn spawn) {
        super(spawn, "Dango");
    }

    /**
     * Dango's tiles arrive with the camera's section list in both zones (r7/section_gfx_1a.asm, r8/section_gfx_1.asm), so the
     * sheet is the zone art provider's window into that section's VRAM; the level-start sheet is the fallback.
     */
    @Override
    protected String sheetKey() {
        boolean first = (getSpawn().subtype() & 0xFF) == 0;
        var camera = services().camera();
        int cameraX = camera == null ? 0 : camera.getX();
        boolean mmz = SharedObjectCatalogue.offset(cdLevel().payload(), "mmzDangoId") >= 0;
        String zoneKey = mmz
                ? com.openggf.game.soniccd.objects.mmz.MmzObjectArt.key(artProvider(),
                        first ? com.openggf.game.soniccd.objects.mmz.MmzObjectArt.DANGO_1 : com.openggf.game.soniccd.objects.mmz.MmzObjectArt.DANGO_2, cameraX)
                : com.openggf.game.soniccd.objects.ssz.SszObjectArt.key(artProvider(),
                        first ? com.openggf.game.soniccd.objects.ssz.SszObjectArt.DANGO1 : com.openggf.game.soniccd.objects.ssz.SszObjectArt.DANGO2,
                        tileWord, cameraX);
        if (zoneKey != null) return zoneKey;
        return first ? SharedObjectArt.DANGO_1 : SharedObjectArt.DANGO_2;
    }

    @Override
    protected void run(PlayableEntity player) {
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite s ? s : null;
        switch (routine) {
            case 0 -> init();
            case 2 -> fall();
            case 4 -> { routine += 2; setSpeed(0x6000, 0x1000); }
            case 6 -> walk(sprite);
            case 8 -> { routine += 2; animator.anim++; }
            case 0xC -> { routine += 2; animator.anim++; y = (y - 6) & 0xFFFF; height = 0x10; }
            case 0x10 -> {
                routine += 2;
                animator.anim++;
                y = (y + 3) & 0xFFFF;
                height = 0xD;
                colType = BUMP_COLLISION;
                speed = 0;
            }
            case 0x14 -> { routine += 2; animator.anim++; setSpeed(0x40000, 0x10000); }
            case 0x12, 0x16 -> rolled(sprite);
            case 0x18 -> {
                routine += 2;
                animator.anim++;
                y = (y - 3) & 0xFFFF;
                height = 0x10;
                colType = WALK_COLLISION;
            }
            case 0x1C -> { routine = 4; animator.anim = 0; y = (y + 6) & 0xFFFF; height = 9; }
            default -> animate();                          // RoutineA (also $E and $1A)
        }
        yRadius = height;
    }

    /** DangoObject_0_Routine0 (:33-60). */
    private void init() {
        routine += 2;
        width = WIDTH;
        yRadius = HEIGHT;
        colType = WALK_COLLISION;
        priority = 3;
        var level = cdLevel();
        // Metallic Madness's Routine0 writes one tile word for every period (objects/r8/dango.asm:41); Stardust Speedway's
        // picks the past's or the other periods' (objects/r7/dango.asm:46-50).
        boolean mmz = SharedObjectCatalogue.offset(level.payload(), "mmzDangoId") >= 0;
        tileWord = SharedObjectCatalogue.offset(level.payload(),
                mmz ? "mmzDangoTile" : liveTimeZone() != 0 ? "dangoTile" : "dangoTilePast");
        boolean second = (getSpawn().subtype() & 0xFF) != 0;
        String anims = (mmz ? "mmzDango" : "dango") + (second ? "Anims2" : "Anims1");
        animator = new CdAnimator(artProvider().image(), SharedObjectCatalogue.offset(level.payload(), anims), 0);
        initialised = true;
    }

    /** DangoObject_0_Routine2 (:62-73): fall one pixel a frame until the floor is under it. */
    private void fall() {
        addToY(0x10000);
        int d1 = ObjectTerrainUtils.checkFloorDist(x, y, height).distance();   // CheckBlockDown
        if (d1 >= 0) return;
        y = (y + d1) & 0xFFFF;
        routine += 2;
    }

    /** loc_20F426 (:217-231): the walking speed, signed by the facing. */
    private void setSpeed(int fast, int slow) {
        int d0 = (getSpawn().subtype() & 0xFF) != 0 ? slow : fast;
        speed = (spriteFlip & 1) != 0 ? d0 : -d0;
    }

    /** DangoObject_0_Routine6 (:83-115). */
    private void walk(AbstractPlayableSprite player) {
        if (!step()) {                                 // loc_20F308: turn
            speed = -speed;
            flipHorizontally();
            return;
        }
        if (player != null) {
            int dx = ((player.getCentreX() & 0xFFFF) - (x & 0xFFFF)) & 0xFFFF;
            boolean right = (short) dx >= 0;             // spl d1
            boolean outside = ((dx + 0x80) & 0xFFFF) >= 0x100;
            int dy = ((player.getCentreY() & 0xFFFF) - (y & 0xFFFF)) & 0xFFFF;
            outside |= ((dy + 0x80) & 0xFFFF) >= 0x100;
            boolean facingRight = (spriteFlip & 1) != 0; // btst #0,obj.sprite_flags / sne
            if (!outside && facingRight == right) routine += 2;
        }
        animate();
    }

    /** DangoObject_0_Routine12 (:153-168) and the rolled-ball move after a bump. */
    private void rolled(AbstractPlayableSprite player) {
        if (touched && player != null) {
            bump(player);
            return;
        }
        if (step()) {
            animate();
        } else {
            routine += 2;
        }
    }

    /** loc_20F38E (:169-197): throw the player away from the Dango. */
    private void bump(AbstractPlayableSprite player) {
        int dx = (x - player.getCentreX()) & 0xFFFF;
        int dy = (y - player.getCentreY()) & 0xFFFF;
        int angle = TrigLookupTable.calcAngle((short) dx, (short) dy) & 0xFF;     // jsr Atan2; jsr SineCosine
        int velocityX = (TrigLookupTable.cosHex(angle) * BUMP_FACTOR) >> 8;
        int velocityY = (TrigLookupTable.sinHex(angle) * BUMP_FACTOR) >> 8;
        player.setXSpeed((short) velocityX);
        player.setYSpeed((short) velocityY);
        player.setAir(true);                                                      // bset #1,obj.flags(a1)
        player.setRollingJump(false);                                             // bclr #4,obj.flags(a1)
        player.setPushing(false);                                                 // bclr #5,obj.flags(a1)
        player.setJumping(false);                                                 // clr.b obj.var_3c(a1)
        touched = false;
        speed = (-(short) velocityX) << 8;                                         // neg.w d1; ext.l d1; asl.l #8,d1
        animator.anim = 4;
        routine = 0x16;
    }

    /**
     * sub_20F43E (:233-266): move by {@code speed}, then require no wall ahead and a floor within seven pixels either way; on
     * failure the move is undone. True when it moved.
     */
    private boolean step() {
        addToX(speed);
        int probe = (x + ((speed >> 16) < 0 ? -width : width)) & 0xFFFF;
        int wall = (speed >> 16) < 0
                ? ObjectTerrainUtils.checkLeftWallDist(probe, y).distance()         // CheckBlockLeft (no eori.w #$F)
                : ObjectTerrainUtils.checkRightWallDist(probe, y).distance();
        if (wall >= 0) {
            int floor = ObjectTerrainUtils.checkFloorDist(x, y, height).distance();
            if (((floor + 7) & 0xFFFF) < 0xE) {                                    // addq.w #7,d0; subi.w #$E,d0; bcc
                y = (y + floor) & 0xFFFF;
                return true;
            }
        }
        addToX(-speed);
        return false;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched = true;                                                           // addq.b #1,oColStatus(a0)
    }

    @Override
    public boolean usesS3kTouchSpecialPropertyResponse() { return (colType & 0xC0) == 0xC0; }

    @Override
    public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }
}

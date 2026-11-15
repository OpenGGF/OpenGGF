package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Quartz Quadrant's block ({@code BlockObject}, objects/r5/block.asm:12-197), a different routine
 * from the Tidal Tempest and Stardust Speedway block ({@link ObjBlock}) under the same name. A
 * solid 32x32 block; with a non-zero subtype it starts to fall once the player is under it
 * ({@code BlockCheckPlayer}), becomes a special touch ($F8) and breaks into four pieces on
 * touching the player or the floor; the block's slot becomes an explosion. The pieces are
 * subtype -1 of the same ID ({@link Piece}).
 *
 * <p>Touch: Quartz Quadrant's {@code PlayerObjectCollide} flags collision type $38
 * ({@code addq.b #1,obj.collide_status(a1)}) and then hurts the player through its hazard path
 * (r5/player_object_collide.asm:296-307, 400-404 into sub_207070 :208-216).
 */
public final class ObjFallingBlock extends CdObject implements ReevaluatingSolidObject, TouchResponseProvider,
        TouchResponseListener, ObjectVisualFacing.NonDirectional {
    /** BlockFall: addi.l #$4000,block.y_speed(a0) (objects/r5/block.asm:99). */
    static final int GRAVITY = 0x4000;
    /** BlockMain: move.b #$F8,obj.collide_type(a0) (objects/r5/block.asm:58). */
    private static final int FALLING_TOUCH = 0xF8;
    /** .Explode: move.w #$9E,d0; jmp PlayFmSound (objects/r5/block.asm:149-150). */
    private static final int SFX_BREAK = 0x9E;
    /**
     * .Properties: per piece X/Y offset and X/Y velocity (16.16), consumed in order while
     * {@code d6} counts 3..0, so piece k shows frame 4-k (objects/r5/block.asm:108-138, 157-165).
     */
    private static final int[][] PIECES = {
            {-8, -8, -0x10000, -0x40000},
            {8, -8, 0x10000, -0x40000},
            {-8, 8, -0x20000, -0x30000},
            {8, 8, 0x20000, -0x30000},
    };

    private int routine;
    private long yFixed;
    private int ySpeed;
    private int collisionFlags;
    private boolean touched;

    public ObjFallingBlock(ObjectSpawn spawn) {
        super(spawn, "Falling block");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if ((getSpawn().subtype() & 0x80) != 0) {
            // BlockObject: tst.b obj.subtype(a0); bmi.w to the piece routine (objects/r5/block.asm:12-14); a layout piece never occurs.
            return;
        }
        if (routine == 0) init();
        if (routine == 2) main(player);
        else if (routine == 4) fall(player);
    }

    /** BlockInit (objects/r5/block.asm:35-44); falls through into BlockMain. */
    private void init() {
        var level = cdLevel();
        if (level == null) return;
        routine = 2;
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        tileWord = SharedObjectCatalogue.offset(level.payload(), "fallingBlockTile");
        yFixed = (long) y << 16;
    }

    /** BlockMain (objects/r5/block.asm:50-62). */
    private void main(PlayableEntity player) {
        if (getSpawn().subtype() != 0 && player instanceof AbstractPlayableSprite sprite && playerNearby(sprite)) {
            routine = 4;
            collisionFlags = FALLING_TOUCH;
        }
        solidObject(ReevaluatingSolidObject.Entry.SOLID);
    }

    /**
     * BlockCheckPlayer (objects/r5/block.asm:74-87): carry set when the player is 8 to $5F pixels
     * below the block's centre and within 8 pixels horizontally.
     */
    private boolean playerNearby(AbstractPlayableSprite sprite) {
        int dy = (sprite.getCentreY() - y - 8) & 0xFFFF;
        if (dy >= 0x58) return false;
        int dx = (sprite.getCentreX() - x + 8) & 0xFFFF;
        return dx < 0x10;
    }

    /** BlockFall (objects/r5/block.asm:93-105). */
    private void fall(PlayableEntity player) {
        if (touched) {
            breakApart();
            return;
        }
        yFixed += ySpeed;
        ySpeed += GRAVITY;
        y = (int) (yFixed >> 16);
        // jsr CheckBlockDown (ObjGetFloorDist); tst.w d1; bmi.s .Break.
        var floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (floor.hasCollision() && floor.distance() < 0) {
            breakApart();
            return;
        }
        Piece.deleteIfFallenOffScreen(this, player, y);
    }

    /** .Break / .Explode (objects/r5/block.asm:107-153): four pieces by SpawnObject, then this slot explodes. */
    private void breakApart() {
        var objects = services().objectManager();
        if (objects != null) {
            for (int i = 0; i < PIECES.length; i++) {
                int[] p = PIECES[i];
                objects.addDynamicObject(new Piece(x + p[0], y + p[1], p[2], p[3], 4 - i, tileWord, getSpawn()));
            }
        }
        boolean onScreen = renderFlagOnScreen();
        collisionFlags = 0;                                  // clr.w obj.collide_type(a0)
        // move.b #$18,obj.id(a0); clr routine; move.b #1,obj.routine_2(a0): not from a badnik, keeps the subtype.
        becomeExplosion(0, 0, getSpawn().subtype(), false);
        if (onScreen) services().playSfx(SFX_BREAK);         // tst.b obj.sprite_flags(a0); bpl.s .End
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (collisionFlags == 0) return;
        touched = true;                                      // addq.b #1,obj.collide_status(a1)
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        // sub_207070: tst.b warping; tst.b invincible; tst.w obj.var_30 (r5/player_object_collide.asm:208-224).
        var timeTravel = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        boolean warping = timeTravel != null && timeTravel.starsActive();
        if (warping || sprite.getInvincibleFrames() > 0 || sprite.getInvulnerableFrames() > 0 || sprite.isHurt()
                || sprite.getDead()) {
            return;
        }
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, frameCounter);
        sprite.applyHurtOrDeath(x, false, hadRings);
    }

    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }
    /**
     * A $C0-class type is the native special touch, never a boss bounce or hurt in the shared owner: $38 adds to the collision status and runs the hazard routine itself (loc_20727C, r5/player_object_collide.asm:307-308, 400-404).
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    /** BlockObject ends in DrawObject and CheckObjectDespawn for both routines (objects/r5/block.asm:21-22). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(SharedObjectArt.FALLING_BLOCK, 0, hFlip(), vFlip());
    }

    int routine() { return routine; }

    /** A broken piece ({@code BlockPieceObject}, objects/r5/block.asm:171-197): subtype -1 of the block's ID. */
    static final class Piece extends CdObject implements ObjectVisualFacing.NonDirectional {
        private long xFixed;
        private long yFixed;
        private final int xSpeed;
        private int ySpeed;
        private final int mapFrame;

        Piece(int x, int y, int xSpeed, int ySpeed, int mapFrame, int tileWord, ObjectSpawn parent) {
            super(new ObjectSpawn(x, y, parent.objectId(), 0xFF, parent.renderFlags(), false, 0), "Falling block piece");
            this.xFixed = (long) x << 16;
            this.yFixed = (long) y << 16;
            this.xSpeed = xSpeed;
            this.ySpeed = ySpeed;
            this.mapFrame = mapFrame;
            this.tileWord = tileWord;
            this.priority = 3;                          // move.b #3,obj.sprite_layer(a1)
            this.width = 8;                             // oXRadius, oWidth 8
            this.yRadius = 8;                           // oYRadius 8
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            ySpeed += GRAVITY;                          // gravity before the move (objects/r5/block.asm:172)
            xFixed += xSpeed;
            yFixed += ySpeed;
            x = (int) (xFixed >> 16);
            y = (int) (yFixed >> 16);
            deleteIfFallenOffScreen(this, player, y);
        }

        /** BlockCheckOffScreen: player Y - Y <= -$E0 deletes (objects/r5/block.asm:188-197). */
        static void deleteIfFallenOffScreen(CdObject object, PlayableEntity player, int y) {
            if (player == null) return;
            if ((short) (player.getCentreY() - y) <= -0xE0) object.setDestroyed(true);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawFrame(SharedObjectArt.FALLING_BLOCK, mapFrame, hFlip(), vFlip());
        }
    }
}

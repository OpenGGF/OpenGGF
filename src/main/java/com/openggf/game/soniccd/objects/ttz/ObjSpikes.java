package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tidal Tempest's spikes, object $22 in the act 1 and 2 indexes ({@code SpikesObject}, objects/r4/spikes.asm:3-148). Subtype 0
 * is a solid block of spikes (frame 0) that hurts a player standing on it ({@code loc_20C0C0}, :49-77) and slides $20 pixels
 * down and up with a $3C frame pause at each end ({@code sub_20C182}, :115-145; a sound, {@code $B7}, when it
 * restarts on screen); any other subtype is frame 1, solid and hurting by touch ({@code oColType $83}), and subtype 1 does not
 * move. It despawns by its own X ({@code obj.var_36}). The parent-follow of {@code obj.var_34} (:12-31) is used by no
 * object hosted here.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSpikes extends TtzObject implements ReevaluatingSolidObject, TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x03A0;
    /** move.w #$B7,d0; jsr PlayFmSound (:120-123). */
    private static final int FM_B7 = 0xB7;

    private int routine;
    private int originX;           // obj.var_36
    private int homeY;             // obj.var_32
    private int pause;             // obj.var_3a
    private int offset;            // obj.var_3b
    private boolean retracting;    // obj.var_3c
    private int mapFrame;
    private int colType;

    public ObjSpikes(ObjectSpawn spawn) {
        super(spawn, "Spikes");
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                             // SpikesObject_0_Routine0 (:82-100)
            routine = 2;
            tileWord = TILE;
            priority = 4;
            originX = x;
            homeY = y;
            width = 0x12;
            yRadius = 0xC;
            if (spawn.subtype() != 0) {
                mapFrame = 1;
                colType = 0x83;
            }
        }
        if (spawn.subtype() != 1) {                                     // SpikesObject_0_Routine2 (:101-113)
            slide();
            y = (homeY + offset) & 0xFFFF;
        }
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite p ? p : null;
        if (mapFrame != 0) {                                            // loc_20C0B0: frame 1 is solid only
            solidObject(Entry.SOLID);
            return;
        }
        if (!solidObject(Entry.SOLID) || sprite == null || !stoodOn(sprite)) return;   // loc_20C0C0
        if (sprite.getInvincibleFrames() > 0) return;                  // tst.b invincible
        if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;   // cmpi.b #4,obj.routine; tst.w obj.var_30
        NativePositionOps.addYPos16_16(sprite, -(sprite.getYSpeed() << 8));   // oY.l -= oYVel << 8
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, vIntRunCount);
        sprite.applyHurtOrDeath(x, true, hadRings);                     // jsr HurtPlayer
    }

    /** sub_20C182 (objects/r4/spikes.asm:115-145). */
    private void slide() {
        if (pause != 0) {
            pause--;
            if (pause != 0) return;
            if (renderFlagOnScreen()) services().playSfx(FM_B7);
        }
        if (retracting) {                                               // loc_20C1A0
            offset -= 8;
            if (offset >= 0) return;
            offset = 0;
            retracting = false;
            pause = 0x3C;
        } else {                                                        // loc_20C1C0
            offset += 8;
            if (offset < 0x20) return;
            offset = 0x20;
            retracting = true;
            pause = 0x3C;
        }
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.SPIKES, mapFrame, false, false);
    }
}

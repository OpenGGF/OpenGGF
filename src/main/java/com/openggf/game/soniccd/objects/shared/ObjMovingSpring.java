package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.objects.common.CdSpring;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Moving spring of every zone but Metallic Madness ({@code MoveSpringObject}, objects/spring.asm:3-97, the
 * Level dialect's {@code ObjMovingSpring}, Level/_Objects/Spring/Main.asm:7-83): a small platform that
 * settles onto the floor, then crawls at 1.5 pixels a frame, turning when the floor under it has dropped
 * $C or more from where it settled. The spring on top (object $A, the same subtype, $10 pixels up)
 * is a child that copies this object's position; sideways and diagonal springs are plain springs placed
 * without the link. The routine ends in an own despawn on the starting X's chunk, with no on-screen test.
 * Metallic Madness's moving spring (r8/move_spring.asm) is another routine.
 *
 * <p>Not modelled: {@code time_stop} (the object stops moving while it is nonzero, :81-83), which no
 * other hosted object models either.
 */
public final class ObjMovingSpring extends CdObject implements ObjectVisualFacing.NonDirectional {
    private boolean initialised;
    private int routine;
    private int originX;                         // obj.var_36
    private int settledY;                        // obj.var_32
    private int xSpeed = 0x180;                  // obj.x_speed (8.8)
    private int xSub;
    private CdAnimator animator;
    private int mapFrame;

    public ObjMovingSpring(ObjectSpawn spawn) {
        super(spawn, "Moving spring");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && !init()) return;
        if (routine == 2) {
            fall();
        } else if (routine == 4) {
            move();
        }
    }

    /** sub_208254 (:27-61); it falls into sub_2082C2 on the same frame. */
    private boolean init() {
        var level = cdLevel();
        var provider = artProvider();
        if (level == null || provider == null) return false;
        routine = 2;
        priority = 4;
        width = 8;
        yRadius = 7;
        originX = x;
        xSpeed = 0x180;
        tileWord = provider.objectTileId(0xE, subtype2());          // moveq #$E,d0; jsr SetObjectSpriteTile (:44-45)
        initialised = true;
        var objects = services().objectManager();
        if (objects == null) return true;
        int subtype = getSpawn().subtype() & 0xFF;
        ObjectSpawn topSpawn = new ObjectSpawn(x, y - 0x10, 0x0A, subtype, 0, false, 0);
        if ((subtype & 0xC) == 0) {
            objects.addDynamicObject(new SharedAttachedSpring(topSpawn, this, 0, -0x10));   // jsr SpawnObject; id $A (:46-60)
        } else {
            objects.addDynamicObject(new CdSpring(topSpawn));
        }
        return true;
    }

    /** sub_2082C2 (:63-77): one pixel down until the floor, then onto it. */
    private void fall() {
        int d1 = floorDistance();
        if (d1 < 0) {
            y = (y + d1) & 0xFFFF;
            settledY = y;
            routine += 2;
        } else {
            y = (y + 1) & 0xFFFF;
        }
    }

    /** sub_2082E2 (:80-97). */
    private void move() {
        y = (y + floorDistance()) & 0xFFFF;
        if (((settledY - y) & 0xFFFF) >= 0xC) xSpeed = -xSpeed;      // cmpi.w #$C,d0; bcs.s (:85-89)
        int position = ((x & 0xFFFF) << 16 | xSub) + (xSpeed << 8);  // MoveObject: x_speed << 8 onto the 16.16 X
        x = position >>> 16;
        xSub = position & 0xFFFF;
        if (animator == null) {
            animator = new CdAnimator(artProvider().image(),
                    SharedObjectCatalogue.offset(cdLevel().payload(), "movingSpringAnims"), 0);
        }
        animator.step();
        mapFrame = animator.mapFrame();
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    /** andi.w #$FF80 on var_36 and the camera - $80; cmpi.w #$280; bhi DeleteObject (:8-17). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        int chunk = originX & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x280;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.MOVING_SPRING + tileWord, mapFrame, false, false);
    }

    public int routine() { return routine; }
}

package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdSpring;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Moving spring, object $0F in every Metallic Madness index ({@code MoveSpringObjectR8}, objects/r8/move_spring.asm:3-118): a
 * small platform that settles on the floor, then crawls at one pixel a frame along it, turning at walls, ledges and
 * after {@code oVar3A} pixels from where it started ($50, or $40 for a negative subtype). The spring that rides it
 * (object $0A, subtype = this subtype's low nibble) is a child that copies the parent's position 16 pixels up each
 * frame ({@link MmzAttachedSpring}). The routine ends in CheckObjectDespawn2 against its initial X.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzMoveSpring extends MmzObject implements ObjectVisualFacing.NonDirectional {
    private int initialX;                        // oVar36
    private int routine;
    private int xSpeed = 0x100;                  // oXVel
    private int range;                           // oVar3A
    private boolean initialised;
    private com.openggf.game.soniccd.objects.common.CdAnimator animator;
    private int mapFrame;

    public MmzMoveSpring(ObjectSpawn spawn) {
        super(spawn, "MoveSpring");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); fall(); }
            case 2 -> fall();
            case 4 -> move();
            default -> { }
        }
    }

    /** MoveSpringInit (objects/r8/move_spring.asm:20-51). */
    private void init() {
        routine += 2;
        initialised = true;
        priority = 4;
        width = 8;
        yRadius = 6;
        initialX = x;
        xSpeed = 0x100;
        var provider = artProvider();
        if (provider != null) tileWord = MmzObjectArt.moveSpringTile(provider.payload());   // moveq #$E,d0; SetObjectSpriteTile
        range = (spawn.subtype() & 0x80) != 0 ? 0x40 : 0x50;
        int topSubtype = spawn.subtype() & 0xF;
        ObjectSpawn topSpawn = new ObjectSpawn(x, y - 0x10, 0x0A, topSubtype, 0, false, 0);
        // jsr SpawnObjectAfter, else jmp DeleteObject (:31-33): the spring takes the first free slot after
        // this one, so it runs after its moving spring in every pass. Sideways and diagonal springs are unattached.
        var top = (topSubtype & 0xC) == 0 ? new MmzAttachedSpring(topSpawn, this, 0, -0x10) : new CdSpring(topSpawn);
        if (!findNextObjSlot(top)) setDestroyed(true);
    }

    /** MoveSpringFall (objects/r8/move_spring.asm:52-68): one pixel down until the floor, then onto it. */
    private void fall() {
        int d1 = floorDistance();
        if (d1 < 0) {
            y = (y + d1) & 0xFFFF;
            routine += 2;
        } else {
            y = (y + 1) & 0xFFFF;
        }
    }

    /** MoveSpringMain (objects/r8/move_spring.asm:69-109). */
    private void move() {
        boolean right = xSpeed >= 0;
        int probeX = x + (right ? 0x20 : -0x18);
        // CheckBlockLeft passes X unflipped (common/object_block_collide.asm:384-403, no eori.w #$F, FixBugs = 0):
        // Sonic CD's object left-wall rule (ObjectLeftWallProbeEntry.RAW).
        int wall = right
                ? ObjectTerrainUtils.checkRightWallDist(probeX, y).distance()
                : ObjectTerrainUtils.checkLeftWallDist(probeX, y).distance();
        boolean turn = true;
        if (wall >= 7) {                                            // cmpi.w #7,d1; bmi
            int travelled = Math.abs((short) (x - initialX));
            if (travelled < range) {
                int d1 = floorDistance();
                if (d1 >= -7 && d1 <= 7) {
                    y = (y + d1) & 0xFFFF;
                    turn = false;
                }
            }
        }
        if (turn) xSpeed = -xSpeed;                                 // loc_20E900
        addToX(xSpeed << 8);                                        // MoveObject
        if (animator == null) animator = mmzAnimator("moveSpringAnimations", 0);
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.moveSpringKey(tileWord), mapFrame, false, false);
    }
}

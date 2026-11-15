package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Move wall, object $45 in Tidal Tempest act 2 past ({@code MoveWallObject}, objects/r4/move_wall.asm:3-102):
 * a solid $20 wide, $80 tall wall (tile $446A, priority 3). On its first load it waits $3C frames, then
 * slides right half a pixel a frame for $180 frames ($C0 pixels), carrying a rider along at ground speed
 * $80. The saved-flag bit 0 of its state (per layout state ID and time zone, object_states) records that the
 * wall has been triggered: a later load starts $C0 pixels right and at rest, which the shared respawn state
 * models as "remembered" (the routine's bit 0 is the only bit read). It deletes itself when its chunk is more
 * than $280 from the camera's, whether or not it is on screen (loc_20F03E, objects/r4/move_wall.asm:91-102).
 *
 * <p>A layout entry without a state ID reads the shared scratch state 0 (MoveWallObject_0_Routine0, objects/r4/move_wall.asm:29-38), which a
 * respawn-tracked entry model cannot hold; such an entry always takes the first-load path.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjMoveWall extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** MoveWallObject_0_Routine0: move.w #$3C,obj.var_2a; addq.b #2,obj.routine (MoveWallObject_0_Routine0, objects/r4/move_wall.asm:43-44). */
    private static final int WAIT_FRAMES = 0x3C;
    /** MoveWallObject_0_Routine2: move.w #$180,obj.var_2a (MoveWallObject_0_Routine2, objects/r4/move_wall.asm:59). */
    private static final int MOVE_FRAMES = 0x180;
    /** MoveWallObject_0_Routine4: move.l #$8000,d0; asr.l #8,d0 -> player ground speed $80 (MoveWallObject_0_Routine4, objects/r4/move_wall.asm:70-72). */
    private static final int RIDER_SPEED = 0x8000 >> 8;

    private int routine;
    /** oX as a 16.16 value: addi.l #$8000,obj.x(a0) adds half a pixel (loc_20F01E, objects/r4/move_wall.asm:75). */
    private int xFixed;
    private int timer;

    public ObjMoveWall(ObjectSpawn spawn) {
        super(spawn, "MoveWall");
        xFixed = x << 16;
    }

    @Override public boolean shouldStayActiveWhenRemembered() { return true; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(x, cameraX);                // loc_20F03E (objects/r4/move_wall.asm:91-102)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> {
                timer = (timer - 1) & 0xFFFF;                // addi.w #-1,obj.var_2a(a0)
                if (timer == 0) {
                    timer = MOVE_FRAMES;
                    routine += 2;
                }
                solid();                                     // loc_20F00C: bra.s Routine6
            }
            case 4 -> carry(player);
            case 6 -> solid();
            default -> { }
        }
    }

    /** MoveWallObject_0_Routine0 (objects/r4/move_wall.asm:21-52). */
    private void init() {
        priority = 3;
        width = 0x20;
        yRadius = 0x40;
        tileWord = TtzObjectArt.MOVE_WALL_TILE;
        var objects = services().objectManager();
        if (objects != null && objects.isRemembered(spawn)) {
            x += 0xC0;                                       // loc_20EFEE (objects/r4/move_wall.asm:49-51): addi.w #$C0,obj.x(a0); addq.b #6
            xFixed = x << 16;
            routine = 6;
            return;
        }
        if (objects != null) ObjectLifetimeOps.markSpawnRemembered(objects, spawn);   // bset #0,(a1)
        timer = WAIT_FRAMES;
        routine = 2;
    }

    /** MoveWallObject_0_Routine4 (objects/r4/move_wall.asm:67-81). */
    private void carry(PlayableEntity player) {
        if (solid() && player != null) player.setGSpeed((short) RIDER_SPEED);
        xFixed += 0x8000;
        x = xFixed >>> 16;
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) routine += 2;
    }

    /** MoveWallObject_0_Routine6 (objects/r4/move_wall.asm:85-87): SolidObject for the player. */
    private boolean solid() {
        return solidObject(Entry.SOLID);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.MOVE_WALL, 0, false, false);
    }
}

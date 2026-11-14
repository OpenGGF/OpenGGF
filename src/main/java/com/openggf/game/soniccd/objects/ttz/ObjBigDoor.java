package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Big door, object $25 in R42A, R42C, R42D ({@code BigDoorObject}, objects/r4/big_door.asm:3-102): a solid
 * $80 wide, $80 tall closed door (tile $446A, priority 3; {@code move.l #$40400340,obj.height} sets
 * yRadius $40, xRadius $40, priority 3, width $40, :30). It is deleted on load when switch
 * {@code switch_flags[subtype]} is already set (BigDoorObject_0_Routine0, objects/r4/big_door.asm:19-24). Routine 2 stays solid and ends in
 * CheckObjectDespawn until the switch is set; then it splits into four $20 wide panels
 * ({@code x - $30}, {@code x - $10}, {@code x + $10}, {@code x + $30}; frames 1 or 2) that slide up
 * (the first and third) or down (the others) one pixel a frame for $81 frames and are deleted (loc_20CF48, objects/r4/big_door.asm:43-98).
 *
 * <p>{@code switch_flags} are the Switch objects' (owner shared), read through {@link TtzObject#switchFlag}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBigDoor extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private int routine;
    private int mapFrame;
    private int timer;
    private int speed;
    private int yFixed;

    public ObjBigDoor(ObjectSpawn spawn) {
        super(spawn, "BigDoor");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return routine == 2; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            if (switchFlag(spawn.subtype())) { setDestroyed(true); return; }       // tst.b (a1); bne.w loc_20CFE2
            routine = 2;
            tileWord = TtzObjectArt.BIG_DOOR_TILE;
            yRadius = 0x40;
            width = 0x40;
            priority = 3;
        }
        if (routine == 2) {
            if (!switchFlag(spawn.subtype())) {
                solidObject(Entry.SOLID);
                return;
            }
            open();                                                                  // loc_20CF48
            return;
        }
        // BigDoorObject_0_Routine4 (objects/r4/big_door.asm:89-98)
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) != 0) { setDestroyed(true); return; }                   // bmi.s loc_20CFE2
        yFixed += speed;
        y = yFixed >> 16;
        solidObject(Entry.SOLID);                                                    // loc_20CFD2
    }

    /** loc_20CF48: the door becomes the first of four panels and copies itself three times. */
    private void open() {
        routine = 4;
        width = 0x10;
        timer = 0x80;
        speed = -0x10000;
        yFixed = y << 16;
        mapFrame++;
        x -= 0x30;
        // loc_20CF78: three SpawnObjectAfter copies of the changed object at +$20 steps; even loop counts
        // flip the direction and take the next frame.
        int d5 = x;
        for (int d6 = 2; d6 >= 0; d6--) {
            d5 += 0x20;
            boolean down = (d6 & 1) == 0;
            ObjBigDoorPanel copy = new ObjBigDoorPanel(d5, y, tileWord, mapFrame + (down ? 1 : 0), down ? 0x10000 : -0x10000, priority);
            if (!spawnChildAfter(copy)) { setDestroyed(true); return; }              // bne.w loc_20CFE2
        }
        solidObject(Entry.SOLID);                                                    // loc_20CFD2
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.BIG_DOOR, mapFrame, false, false);
    }
}

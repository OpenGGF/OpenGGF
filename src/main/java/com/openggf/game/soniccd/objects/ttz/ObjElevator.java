package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Elevator, object $26 in R41A, R41C, R41D and R42A ({@code ElevatorObject}, objects/r4/elevator.asm:3-118): a
 * $20 wide, $20 tall platform (tile $435C, priority 3). Once it has been on screen and the player lands on it
 * (SolidObject returns top contact) it moves along the diagonal {@code (+x, -y)} at {@code word_20CB1C}'s
 * speed for its count: past and present $100 ($1 pixel a frame) for $A0 frames, future $200 for $50. The
 * speeds are the object's velocity, which a rider inherits. It deletes itself by its own chunk test, which
 * ignores the on-screen bit and clears the spawner's loaded bit (ElevatorObject, objects/r4/elevator.asm:3-46).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjElevator extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** word_20CB1C (word_20CB1C, objects/r4/elevator.asm:89-92): x speed, y speed and frame count for time zones 0-2. */
    private static final int[][] TABLE = {{0x100, -0x100, 0xA0}, {0x100, -0x100, 0xA0}, {0x200, -0x200, 0x50}};

    private int routine;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;
    private int count;

    public ObjElevator(ObjectSpawn spawn) {
        super(spawn, "Elevator");
        xFixed = x << 16;
        yFixed = y << 16;
    }

    @Override public int solidXVelocity() { return xVel; }
    @Override public int solidYVelocity() { return yVel; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(x, cameraX);                   // ElevatorObject (objects/r4/elevator.asm:3-24)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                routine = 2;
                priority = 3;
                tileWord = TtzObjectArt.ELEVATOR_TILE;
                width = 0x10;
                yRadius = 0x10;
            }
            case 2 -> {
                if (!renderFlagOnScreen()) return;             // btst #7,obj.sprite_flags(a0); beq
                if (!solidObject(Entry.SOLID)) return;         // bsr.w sub_20CAB2; beq
                routine = 4;
                int[] row = TABLE[timeZone() & 3];
                xVel = row[0];
                yVel = row[1];
                count = row[2];
            }
            case 4 -> {
                solidObject(Entry.SOLID);
                if (count == 0) return;
                xFixed += xVel << 8;
                yFixed += yVel << 8;
                x = xFixed >>> 16;
                y = yFixed >>> 16;
                count--;
                if (count == 0) { xVel = 0; yVel = 0; }
            }
            default -> { }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.ELEVATOR, 0, false, false);
    }
}

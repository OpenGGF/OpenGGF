package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Twin move block, object $48 in R42C and R42D ({@code TwinMoveBlockObject}, objects/r4/twin_move_block.asm:3-126): a
 * solid $20 wide, $40 tall block (tile $446A, priority 3) that moves up and down in a four-step cycle
 * ({@code byte_20D8E6}, :118-126: $80 frames each of accelerating -4/+4 from a start speed of +2 or 0 or -2 or 0 in 8.8). The
 * layout entry makes a twin $20 to its right (the same ID with {@code oVar3C = 1}) whose speeds and accelerations
 * are negated, so the pair moves in opposition; the twin deletes itself when the entry's slot stops being ID $48.
 * SolidObject runs before the move while the block is rising and after it otherwise (:66-96).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTwinMoveBlock extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** byte_20D8E6 (byte_20D8E6, objects/r4/twin_move_block.asm:118-126): count, acceleration, initial speed. */
    private static final int[][] CYCLE = {{0x80, -4, 0x200}, {0x80, -4, 0}, {0x80, 4, -0x200}, {0x80, 4, 0}};

    private final ObjTwinMoveBlock parent;
    private int routine;
    private int yFixed;
    private int ySpeed;       // oYVel
    private int accel;        // oVar3E
    private int counter;      // oVar3A
    private int step;         // oVar3B

    public ObjTwinMoveBlock(ObjectSpawn spawn) {
        super(spawn, "TwinMoveBlock");
        this.parent = null;
        this.yFixed = y << 16;
    }

    private ObjTwinMoveBlock(ObjTwinMoveBlock parent) {
        super(childSpawn(parent.getX() + 0x20, parent.getY(), 0x48, 0, 0), "TwinMoveBlock");
        this.parent = parent;
        this.yFixed = y << 16;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return parent == null; }
    @Override public int solidYVelocity() { return ySpeed; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            init();
            return;
        }
        if (parent != null && parent.isDestroyed()) { setDestroyed(true); return; }
        // TwinMoveBlockObject_0_Routine2 (objects/r4/twin_move_block.asm:66-116)
        if (ySpeed < 0) solidObject(Entry.SOLID);                        // tst.w obj.y_speed(a0); bpl; bsr.w sub_20D80C
        if (counter == 0) {
            int[] row = CYCLE[step];
            counter = row[0];
            accel = parent != null ? -row[1] : row[1];
            ySpeed = parent != null ? -row[2] : row[2];
        } else {
            yFixed += ySpeed << 8;
            y = yFixed >> 16;
            ySpeed += accel;
            counter--;
            if (counter == 0) {
                step = (step + 1) & 3;                                   // cmpi.b #4,obj.var_3b(a0); bne
            }
        }
        if (ySpeed >= 0) solidObject(Entry.SOLID);                       // loc_20D8AC
    }

    /** TwinMoveBlockObject_0_Routine0 (objects/r4/twin_move_block.asm:41-62). */
    private void init() {
        routine = 2;
        priority = 3;
        tileWord = TtzObjectArt.TWIN_MOVE_TILE;
        width = 0x10;
        yRadius = 0x20;
        if (parent != null) return;
        spawnChild(new ObjTwinMoveBlock(this));
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.TWIN_MOVE, 0, false, false);
    }
}

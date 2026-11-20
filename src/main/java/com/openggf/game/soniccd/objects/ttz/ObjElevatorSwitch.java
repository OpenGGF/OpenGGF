package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** The elevator's pressure switch, distinct from the stage-wide flag switches. */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjElevatorSwitch extends TtzObject
        implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
    static final int ID = 0x31; // ObjectIndex (r4/object_index_1a.asm:44-52)
    static final int TILE = 0x050A;

    private int columnSlot = -1;
    private int anchorX;
    private boolean pressed;

    public ObjElevatorSwitch(ObjectSpawn spawn) {
        super(spawn, "Elevator switch");
        anchorX = x;
        // SwitchObject2_0_Routine0 (objects/r4/switch_elevator.asm:42-54).
        tileWord = TILE;
        priority = 3;
        width = 0xE;
        yRadius = 5;
    }

    ObjElevatorSwitch(int columnSlot, int anchorX) {
        this(childSpawn(0, 0, ID, 0, 0));
        this.columnSlot = columnSlot;
        this.anchorX = anchorX;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // sub_20E69A (objects/r4/switch_elevator.asm:82-110): feet less than 16 px above
        // through the switch centre, and X inside the width plus 8 on each side.
        if (player != null) {
            int reach = width + 8;
            int dx = (short) (player.getCentreX() - x);
            int feet = (short) (player.getCentreY() + player.getYRadius() - y);
            pressed = dx >= -reach && dx < reach && feet > -0x10 && feet <= 0;
        } else {
            pressed = false;
        }
        // SwitchObject2 (objects/r4/switch_elevator.asm:3-24) checks pressure and solid
        // contact before following the column. It doesn't inherit the column's velocity.
        solidObject(Entry.SOLID);
        if (slotOccupant(columnSlot) instanceof ObjSwitchElevator column) {
            x = column.getX();
            y = column.getY() - 0x44;
        }
    }

    public boolean pressed() { return pressed; }

    @Override protected int despawnX() { return anchorX; }
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // DespawnObjTimeWarp (Level/Object Spawner.asm:442-446) also clears this switch on a warp.
        var time = services().gameService(CdTimeTravel.class);
        return (time != null && time.warping()) || outsideChunkRange(anchorX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.ELEVATOR_SWITCH, pressed ? 1 : 0, false, false);
    }
}

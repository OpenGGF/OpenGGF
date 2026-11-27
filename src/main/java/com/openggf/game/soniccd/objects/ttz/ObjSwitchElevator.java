package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/** Main elevator column and its solid side platform. Each occupies its own object slot. */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSwitchElevator extends TtzObject
        implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
    static final int ID = 0x30; // ObjectIndex (r4/object_index_1a.asm:44-52)
    static final int TILE = 0x446A;

    private int anchorX;
    private boolean sidePlatform;
    private int otherSlot = -1;
    private int switchSlot = -1;
    private int yFixed;
    private int ySpeed;
    private boolean initialized;

    public ObjSwitchElevator(ObjectSpawn spawn) {
        super(spawn, "Switch elevator");
        anchorX = x;
        yFixed = y << 16;
    }

    private ObjSwitchElevator(ObjSwitchElevator column) {
        super(childSpawn(column.x - 0x20, column.y + 0x20, ID, 0, 0), "Switch elevator");
        anchorX = column.anchorX;
        sidePlatform = true;
        otherSlot = column.getSlotIndex();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized && !initialize()) return;

        if (!sidePlatform) moveColumn();
        // SwitchElevatorObject (objects/r4/switch_elevator.asm:120-141): the side platform
        // resolves contact at its previous location, then syncs its position to the column.
        solidObject(Entry.SOLID);
        if (sidePlatform) {
            if (slotOccupant(otherSlot) instanceof ObjSwitchElevator column) {
                x = column.x - 0x20;
                y = column.y + 0x20;
            } else {
                setDestroyed(true);
            }
        }
    }

    private boolean initialize() {
        // SwitchElevatorObject_0_Routine0 (objects/r4/switch_elevator.asm:173-221).
        initialized = true;
        tileWord = TILE;
        priority = 3;
        width = 0x10;
        yRadius = sidePlatform ? 0x20 : 0x40;
        if (sidePlatform) return true;

        x += 0x10;
        ObjSwitchElevator platform = new ObjSwitchElevator(this);
        if (findNextObjSlot(platform)) {
            otherSlot = platform.getSlotIndex();
            var button = new ObjElevatorSwitch(getSlotIndex(), anchorX);
            if (findNextObjSlot(button)) {
                switchSlot = button.getSlotIndex();
                return true;
            }
        }
        setDestroyed(true);
        return false;
    }

    private void moveColumn() {
        // loc_20E84C (objects/r4/switch_elevator.asm:232-262): probe the leading edge,
        // integrate the old speed, then give that speed to the side platform.
        var level = services().levelManager();
        int distance = ySpeed < 0
                ? ObjectTerrainUtils.checkNativeUpwardCeilingDist(level, false, x, y - 0x40, yRadius).distance()
                : ObjectTerrainUtils.checkFloorDist(level, x, y + yRadius).distance();
        if (distance < 0) ySpeed = 0;
        yFixed += ySpeed << 8;
        y = yFixed >>> 16;
        if (slotOccupant(otherSlot) instanceof ObjSwitchElevator platform) {
            platform.ySpeed = ySpeed;
        }

        // loc_20E8A0 (objects/r4/switch_elevator.asm:264-289): accelerate toward +1 px
        // per frame when released, or -1 when pressed. The column's solid call sees the new speed.
        if (slotOccupant(switchSlot) instanceof ObjElevatorSwitch button) {
            ySpeed = button.pressed() ? Math.max(-0x100, ySpeed - 8) : Math.min(0x100, ySpeed + 8);
        }
    }

    @Override protected int despawnX() { return anchorX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // SwitchElevatorObject (objects/r4/switch_elevator.asm:140-155).
        return outsideChunkRange(anchorX, cameraX);
    }

    @Override public int solidYVelocity() { return ySpeed; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialized) drawSheet(TtzObjectArt.SWITCH_ELEVATOR, sidePlatform ? 2 : 1, false, false);
    }
}

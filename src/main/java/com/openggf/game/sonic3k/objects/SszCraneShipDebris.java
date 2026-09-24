package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** loc_7CE6C: ObjDat3_7D45C, Set_IndexedVelocity(8), then Obj_FlickerMove. */
public final class SszCraneShipDebris extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private boolean initialized;
    private boolean flicker;
    private boolean visible;
    private boolean pendingDelete;

    public SszCraneShipDebris(ObjectSpawn spawn) {
        super(spawn, "SszCraneShipDebris");
        xFixed = spawn.x() << 16; yFixed = spawn.y() << 16;
    }

    @Override public void update(int clock, PlayableEntity player) {
        visible = false;
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!initialized) {
            initialized = true;
            // Obj_VelocityIndex + 8 + subtype*2, subtypes 0/2/4/6.
            xVelocity = switch (spawn.subtype()) {
                case 0 -> -0x200; case 2 -> 0x200; case 4 -> -0x300; case 6 -> 0x300;
                default -> throw new IllegalStateException("SSZ crane debris subtype " + spawn.subtype());
            };
            yVelocity = -0x200;
            return; // initialization jumps to Set_IndexedVelocity, not the movement routine.
        }
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        yVelocity = (short) (yVelocity + 0x38);
        var camera = services().camera();
        if (isCoarseXOutOfRange(getX(), camera.getX(), coarseXCullRange())
                || ((getY() - camera.getY() + 0x80) & 0xFFFF) > 0x200) {
            pendingDelete = true; // Go_Delete_Sprite_3
            return;
        }
        // bchg bit6 / beq return: the first movement pass is hidden.
        visible = flicker;
        flicker = !flicker;
        updateDynamicSpawn(getX(), getY());
    }

    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public boolean isHighPriority() { return true; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public int getOnScreenHalfWidth() { return 0x1C; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_CRANE_SHIP_DEBRIS);
        if (visible && renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(spawn.subtype() >>> 1, getX(), getY(), false, false);
        }
    }
}

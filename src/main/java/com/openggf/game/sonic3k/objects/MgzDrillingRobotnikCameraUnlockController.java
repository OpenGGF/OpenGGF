package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * ROM {@code Obj_IncLevEndXGradual}/{@code Obj_DecLevStartXGradual} worker
 * allocated when a drilling-Robotnik appearance finishes fleeing.
 *
 * <p>This remains a real dynamic object because its slot and execution phase
 * matter: the boss allocates it after its own slot, so it receives its first
 * {@code $4000} accumulator step later in the same {@code ExecuteObjects}
 * pass, before the camera scroll.</p>
 */
final class MgzDrillingRobotnikCameraUnlockController extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int ACCELERATION = 0x4000;
    private static final int OPEN_MIN_X = 0;
    private static final int OPEN_MAX_X = 0x6000;

    // Non-final so generic rewind capture can restore the constructor variant.
    private boolean decrementMinX;
    private int accumulator;
    private boolean skipFirstUpdate;

    MgzDrillingRobotnikCameraUnlockController(boolean decrementMinX) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "MGZDrillCameraUnlock");
        this.decrementMinX = decrementMinX;
    }

    MgzDrillingRobotnikCameraUnlockController(ObjectSpawn spawn) {
        this(false);
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (skipFirstUpdate) {
            skipFirstUpdate = false;
            return;
        }
        accumulator += ACCELERATION;
        int step = accumulator >>> 16;
        if (decrementMinX) {
            updateMinX(step);
        } else {
            updateMaxX(step);
        }
    }

    void preserveWrappedAllocationSetupPass(int parentSlot) {
        // The split Java boss routine reaches cleanup one execution pass ahead
        // of the native slot replacement. When AllocateObject wraps to a slot
        // already visited this pass, retain that setup pass; a later slot will
        // execute in the current pass and needs no compensation.
        skipFirstUpdate = getSlotIndex() >= 0 && getSlotIndex() < parentSlot;
    }

    private void updateMinX(int step) {
        var camera = services().camera();
        int current = camera.getMinX() & 0xFFFF;
        if (current <= OPEN_MIN_X) {
            camera.setMinX((short) OPEN_MIN_X);
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        int next = current - step;
        if (next <= OPEN_MIN_X) {
            camera.setMinX((short) OPEN_MIN_X);
            ObjectLifetimeOps.expireDynamic(this);
        } else {
            camera.setMinX((short) next);
        }
    }

    private void updateMaxX(int step) {
        var camera = services().camera();
        int current = camera.getMaxX() & 0xFFFF;
        if (current >= OPEN_MAX_X) {
            camera.setMaxX((short) OPEN_MAX_X);
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        int next = current + step;
        if (next >= OPEN_MAX_X) {
            camera.setMaxX((short) OPEN_MAX_X);
            ObjectLifetimeOps.expireDynamic(this);
        } else {
            camera.setMaxX((short) next);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible ROM control object.
    }
}

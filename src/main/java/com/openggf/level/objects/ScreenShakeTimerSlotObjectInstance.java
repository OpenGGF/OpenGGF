package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;

import java.util.List;

/**
 * Slot-owning countdown used by dynamic-water handlers whose ROM routine
 * allocates an otherwise invisible timer object (S3K {@code Obj_6E6E}).
 */
public final class ScreenShakeTimerSlotObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {

    private int remainingFrames;

    public ScreenShakeTimerSlotObjectInstance(int remainingFrames) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "ScreenShakeTimer");
        this.remainingFrames = Math.max(0, remainingFrames);
    }

    ScreenShakeTimerSlotObjectInstance(ObjectSpawn spawn) {
        this(0);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (remainingFrames > 0) {
            remainingFrames--;
        }
        if (remainingFrames == 0) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Native object has no mappings and never enters the sprite table.
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new ScreenShakeTimerSlotObjectInstance(0);
    }
}

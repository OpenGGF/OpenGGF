package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/** Shared ROM {@code Child6_IncLevX} / {@code Obj_IncLevEndXGradual} worker. */
public final class S3kIncLevelEndXGradualInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int ACCELERATION = 0x4000;
    private int accumulator;

    public S3kIncLevelEndXGradualInstance(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, y), "S3kIncLevelEndXGradual");
    }

    public S3kIncLevelEndXGradualInstance(ObjectSpawn spawn) {
        super(spawn, "S3kIncLevelEndXGradual");
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new S3kIncLevelEndXGradualInstance(ctx.spawn());
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        var camera = services().camera();
        camera.claimCustomMaxXBoundaryEasing();
        accumulator += ACCELERATION;
        int step = accumulator >>> 16;
        int nextMaxX = (camera.getMaxX() & 0xFFFF) + step;
        int targetMaxX = camera.getMaxXTarget() & 0xFFFF;
        if (Integer.compareUnsigned(nextMaxX, targetMaxX) >= 0) {
            camera.setMaxXCurrent((short) targetMaxX);
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        camera.setMaxXCurrent((short) nextMaxX);
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}

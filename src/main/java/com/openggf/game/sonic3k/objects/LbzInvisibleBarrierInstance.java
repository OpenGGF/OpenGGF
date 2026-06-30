package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.List;

/**
 * Invisible solid wall guarding the LBZ1 boss approach during and after the
 * ending-building collapse.
 *
 * <p>ROM: {@code Obj_LBZ1InvisibleBarrier} at {@code sonic3k.asm:111145} —
 * pinned at ({@code $3BC0}, {@code $100}) with {@code width_pixels=$40} and
 * {@code SolidObjectFull2} d1={@code $4B}, d2=d3={@code $100}; it deletes
 * itself once {@code Camera_X_pos} reaches {@code $3D80}.
 */
public final class LbzInvisibleBarrierInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    private static final int BARRIER_X = 0x3BC0;
    private static final int BARRIER_Y = 0x0100;
    private static final int RELEASE_CAMERA_X = 0x3D80;
    private static final int SOLID_HALF_WIDTH = 0x4B;
    private static final int SOLID_HALF_HEIGHT = 0x100;

    public LbzInvisibleBarrierInstance(ObjectSpawn spawn) {
        super(spawn, "LBZ1InvisibleBarrier");
    }

    @Override
    public LbzInvisibleBarrierInstance recreateForRewind(RewindRecreateContext ctx) {
        return new LbzInvisibleBarrierInstance(ctx.spawn());
    }

    @Override
    public int getX() {
        return BARRIER_X;
    }

    @Override
    public int getY() {
        return BARRIER_Y;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if ((services().camera().getX() & 0xFFFF) >= RELEASE_CAMERA_X) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible solid.
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code Obj_CreateBossExplosion} subtype {@code $14} ({@code CreateBossExp14}: timer 8,
 * X range {@code $80}, Y range {@code $20}, routine set {@code $10} =
 * {@code Obj_WaitForParent} + {@code Obj_BossExpControl2}), created by {@code loc_64930}.
 *
 * <p>{@code loc_64930} writes {@code ($1880,$3D0)} after creation, but
 * {@code Obj_WaitForParent} copies the parent's position on every pass before the wait, so the
 * explosions follow the Knuckles object. Every third pass {@code Obj_BossExpControl2} creates
 * an {@code Obj_BossExplosion2} at a {@code Random_Number} offset until {@code $39} reaches 0.
 */
public final class HpzBossExplosionSpawnerObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int X_RANGE = 0x80;
    private static final int Y_RANGE = 0x20;

    @RewindTransient(reason = "object link restored by ObjectRefId in restoreRewindState")
    private AbstractObjectInstance parent;
    private int x;
    private int y;
    private int counter = 8;
    private int wait;
    private boolean deletePending;


    public HpzBossExplosionSpawnerObjectInstance(ObjectSpawn spawn, AbstractObjectInstance parent) {
        super(spawn, "HpzBossExplosionSpawner");
        this.parent = parent;
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzBossExplosionSpawnerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzBossExplosionSpawnerObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // Obj_WaitForParent: tst.l (a1) / beq.w -> Go_Delete_Sprite
        if (deletePending || parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        x = parent.getX() & 0xFFFF;
        y = parent.getY() & 0xFFFF;
        wait = (short) (wait - 1);
        if (wait >= 0) {
            return;
        }
        // Obj_BossExpControl2
        counter = (counter - 1) & 0xFF;
        if (counter == 0) {
            deletePending = true;
            return;
        }
        wait = 2;
        int random = services().rng().nextRaw();
        int dx = ((random & (X_RANGE * 2 - 1)) - X_RANGE);
        int dy = (((random >>> 16) & (Y_RANGE * 2 - 1)) - Y_RANGE);
        int ex = (x + dx) & 0xFFFF;
        int ey = (y + dy) & 0xFFFF;
        spawnChild(() -> S3kBossExplosionChild.createWithNativeInitSfx(ex, ey));
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    private record Links(ObjectRefId parentId) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable().map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new Links(parentId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof Links links) {
            parent = links.parentId() == null ? null
                    : (AbstractObjectInstance) context.requireIdentityTable().resolveObject(links.parentId(), true);
        }
    }
}

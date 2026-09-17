package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code Obj_CreateBossExplosion} subtype {@code $14} ({@code CreateBossExp14}: timer 8,
 * X range {@code $80}, Y range {@code $20}, routine set {@code $10} = {@code Obj_Wait} +
 * {@code Obj_NormalExpControl}, sonic3k.asm:176674-176688), created by {@code loc_64930} at
 * {@code ($1880,$3D0)}.
 *
 * <p>{@code Obj_Wait} never looks at the parent, so the spawner stays at {@code ($1880,$3D0)} and
 * outlives the Knuckles object. Every third pass {@code Obj_NormalExpControl}
 * (sonic3k.asm:176782-176795) creates a {@code Child6_MakeNormalExplosion} ({@code Obj_Explosion}
 * routine 2, {@code art_tile} bit 7) and only after a successful {@code CreateChild6_Simple}
 * draws {@code Random_Number} for its offset; the eighth decrement deletes the spawner.
 */
public final class HpzBossExplosionSpawnerObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int X_RANGE = 0x80;
    private static final int Y_RANGE = 0x20;

    private int counter = 8;
    /** {@code $2E}: zero from {@code CreateChild6_Simple}'s fresh slot. */
    private int wait;

    public HpzBossExplosionSpawnerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HpzBossExplosionSpawner");
    }

    @Override
    public HpzBossExplosionSpawnerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzBossExplosionSpawnerObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // Obj_Wait
        wait = (short) (wait - 1);
        if (wait >= 0) {
            return;
        }
        // Obj_NormalExpControl
        counter = (counter - 1) & 0xFF;
        if (counter == 0) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        wait = 2;
        int x = spawn.x();
        int y = spawn.y();
        var explosion = spawnAfterCurrentSibling(() -> HpzKnucklesCeilingExplosionObjectInstance.normalExplosion(x, y));
        if (explosion == null) {
            // bne.w locret_83EC0: no Random_Number without a child.
            return;
        }
        int random = services().rng().nextRaw();
        int dx = (random & (X_RANGE * 2 - 1)) - X_RANGE;
        int dy = ((random >>> 16) & (Y_RANGE * 2 - 1)) - Y_RANGE;
        explosion.moveTo((x + dx) & 0xFFFF, (y + dy) & 0xFFFF);
    }

    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * ROM {@code loc_83004} (sonic3k.asm:175722-175740) and {@code loc_8303E} (175742-175756): anchors
 * that follow the end boss at an offset ({@code $90,$60} while it falls in phase 1; {@code $40,$40} at
 * the exit) carrying {@code Obj_CreateBossExplosion} children ({@code $1C}, or two {@code $E}). The
 * phase-1 anchor deletes itself once the boss's {@code $38} bit 4 is set; the exit anchor lives as
 * long as the boss.
 */
final class DdzEndBossExplosionAnchorObjectInstance extends AbstractDdzObjectInstance
        implements DdzCreateBossExplosionObjectInstance.ExplosionStopFlag {
    static final int KIND_FALL = 0;
    static final int KIND_EXIT = 1;

    private int kind;
    private int x;
    private int y;
    private boolean initialized;


    DdzEndBossExplosionAnchorObjectInstance(DdzEndBossObjectInstance boss, int kind) {
        this(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, kind, 0, false, 0), boss, kind);
    }

    private DdzEndBossExplosionAnchorObjectInstance(ObjectSpawn spawn, DdzEndBossObjectInstance boss, int kind) {
        super(spawn, "DDZEndBossExplosionAnchor", boss);
        this.kind = kind;
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzEndBossExplosionAnchorObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, spawn.subtype());
    }

    @Override
    public DdzEndBossExplosionAnchorObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        // The parent link is restored later; retain the captured spawn instead of
        // deriving a new origin from the temporarily absent parent.
        return new DdzEndBossExplosionAnchorObjectInstance(ctx.spawn());
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public boolean stopsChildExplosions() {
        return false;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        if (!(parent instanceof DdzEndBossObjectInstance boss) || boss.isDestroyed()) {
            deleteNow();
            return;
        }
        int offset = kind == KIND_FALL ? 0x90 : 0x40;
        int offsetY = kind == KIND_FALL ? 0x60 : 0x40;
        if (!initialized) {
            initialized = true;
            // The ROM init leaves x/y at the allocation position for the children's first frame.
            int startX = x;
            int startY = y;
            if (kind == KIND_FALL) {
                spawnChild(() -> new DdzCreateBossExplosionObjectInstance(startX, startY, 0x1C, this));
            } else {
                spawnChild(() -> new DdzCreateBossExplosionObjectInstance(startX, startY, 0x0E, this));
                spawnChild(() -> new DdzCreateBossExplosionObjectInstance(startX, startY, 0x0E, this));
            }
        }
        if (kind == KIND_FALL && boss.flag(4)) {
            deleteNow();
            return;
        }
        x = (boss.getX() + offset) & 0xFFFF;
        y = (boss.getY() + offsetY) & 0xFFFF;
    }


    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }


}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * ROM {@code Obj_CreateBossExplosion} (sonic3k.asm:176661-176800) for the parameter sets that use
 * {@code Obj_BossExpControl1}: set {@code 0} ({@code Obj_Wait}) and set {@code 8}
 * ({@code Obj_WaitForParent}, which follows a live parent and deletes when the parent is gone or
 * has {@code $38} bit 5 set).
 *
 * <p>Each expiry of {@code $2E} decrements the timer {@code $39} (a negative timer never runs out),
 * reloads {@code $2E = 2}, creates an {@code Obj_BossExplosion1} with {@code CreateChild6_Simple}
 * and only then draws {@code Random_Number} to offset it within {@code +-range}.
 */
final class DdzCreateBossExplosionObjectInstance extends AbstractDdzObjectInstance {
    /** Subtype, timer, X range, Y range, follows parent. */
    private record Parameters(int subtype, int timer, int xRange, int yRange, boolean followParent) {}

    private static final Parameters[] PARAMETERS = new Parameters[0x22];
    static {
        // CreateBossExp00-20 rows with routine sets 0 and 8.
        put(0x00, 0x20, 0x20, 0x20, false);
        put(0x04, 0x80, 0x20, 0x20, true);
        put(0x06, 0x04, 0x10, 0x10, false);
        put(0x0A, 0x20, 0x20, 0x20, false);
        put(0x0C, 0x40, 0x80, 0x20, false);
        put(0x0E, 0x80, 0x40, 0x40, true);
        put(0x16, 0x80, 0x80, 0x80, true);
        put(0x1C, 0x80, 0x80, 0x40, true);
        put(0x1E, 0x80, 0x10, 0x10, true);
    }

    private static void put(int subtype, int timer, int xRange, int yRange, boolean followParent) {
        PARAMETERS[subtype] = new Parameters(subtype, timer, xRange, yRange, followParent);
    }

    private final int subtype;
    private int x;
    private int y;
    /** {@code $39}. */
    private int timer;
    /** {@code $2E}: zero in a fresh {@code CreateChild6_Simple} slot. */
    private int wait;


    DdzCreateBossExplosionObjectInstance(int x, int y, int subtype, AbstractObjectInstance parent) {
        super(new ObjectSpawn(x, y, 0, subtype, 0, false, 0), "DDZCreateBossExplosion", parent);
        Parameters parameters = subtype >= 0 && subtype < PARAMETERS.length ? PARAMETERS[subtype] : null;
        if (parameters == null) {
            throw new IllegalArgumentException("Obj_CreateBossExplosion subtype $" + Integer.toHexString(subtype));
        }
        this.subtype = subtype;
        this.x = x;
        this.y = y;
        this.timer = parameters.timer();
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzCreateBossExplosionObjectInstance(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), spawn.subtype(), null);
    }

    @Override
    public DdzCreateBossExplosionObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzCreateBossExplosionObjectInstance(ctx.spawn().x(), ctx.spawn().y(), ctx.spawn().subtype(), null);
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
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        Parameters parameters = PARAMETERS[subtype];
        if (parameters.followParent()) {
            // Obj_WaitForParent
            // tst.l (a1): a deleted parent; btst #5,$38(a1): explosions stopped.
            if (parent == null || parent.isDestroyed() || parentWantsExplosionsStopped(parent)) {
                goDelete();
                return;
            }
            x = parent.getX();
            y = parent.getY();
        }
        wait = (short) (wait - 1);
        if (wait >= 0) {
            return;
        }
        // Obj_BossExpControl1
        if ((byte) timer >= 0) {
            timer = (timer - 1) & 0xFF;
            if (timer == 0) {
                goDelete();
                return;
            }
        }
        wait = 2;
        int baseX = x;
        int baseY = y;
        // CreateChild6_Simple: AllocateObjectAfterCurrent.
        S3kBossExplosionChild child = spawnChild(() -> S3kBossExplosionChild.createWithNativeInitSfx(baseX, baseY));
        if (child == null) {
            return;
        }
        // loc_83E90: Random_Number, masked to 2*range-1 and centred.
        int random = services().rng().nextRaw();
        int dx = ((random & 0xFFFF) & (parameters.xRange() * 2 - 1)) - parameters.xRange();
        int dy = (((random >>> 16) & 0xFFFF) & (parameters.yRange() * 2 - 1)) - parameters.yRange();
        child.setSpawnPosition(baseX + dx, baseY + dy);
    }

    /** {@code btst #5,$38(a1)}: DDZ parents raise it through {@link ExplosionStopFlag}. */
    private static boolean parentWantsExplosionsStopped(AbstractObjectInstance parent) {
        return parent instanceof ExplosionStopFlag flag && flag.stopsChildExplosions();
    }

    /** A parent whose {@code $38} bit 5 ends {@code Obj_WaitForParent} explosion children. */
    public interface ExplosionStopFlag {
        boolean stopsChildExplosions();
    }




    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}

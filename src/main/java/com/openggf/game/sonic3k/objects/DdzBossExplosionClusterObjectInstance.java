package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * The Doomsday end boss's explosion spawners (sonic3k.asm:175574-175720).
 *
 * <p>{@link Kind#DEFEAT} is {@code loc_82E9A}: it follows the boss and every {@code $20} frames
 * ({@code $2E}) flashes the second {@code word_82D9E} row onto palette line 3 (the first row on the
 * other frames), plays {@code sfx_MissileExplode} and allocates a {@link Kind#CLUSTER} at the next
 * {@code word_82EF4} offset; the tenth deletes it. {@link Kind#EXIT} is {@code loc_82F1C}: the same
 * cadence without the flash, three {@code word_82F6C} clusters flagged to follow the scrolling
 * camera. {@link Kind#CLUSTER} is {@code loc_82F78}: it follows its spawner at its offset and on each
 * of 24 frames allocates a {@code loc_826CC} explosion at the next {@code byte_82FD4} offset.
 */
final class DdzBossExplosionClusterObjectInstance extends AbstractDdzObjectInstance {
    enum Kind { DEFEAT, EXIT, CLUSTER }

    /** {@code word_82EF4}. */
    private static final int[][] DEFEAT_OFFSETS = {
            {0xC0, 0x38}, {0x78, 0x80}, {0xB0, 0x78}, {0x58, 0x40}, {0x98, 0x60},
            {0xE0, 0x70}, {0xC8, 0x40}, {0x70, 0x80}, {0x98, 0x70}, {0x50, 0x40}};
    /** {@code word_82F6C}. */
    private static final int[][] EXIT_OFFSETS = {{0x50, 0x48}, {0x30, 0x30}, {0x30, 0x68}};
    /** {@code byte_82FD4}. */
    private static final int[][] BURST_OFFSETS = {
            {0, 0}, {-0x10, -8}, {0, 8}, {8, -8}, {-0x10, 8}, {0, -0x10}, {-0x10, -0x18}, {0x10, 8},
            {0x10, -0x10}, {-0x18, 0}, {-8, -0x20}, {0x18, 0}, {-8, 0x10}, {-0x20, -0x10}, {8, -0x20},
            {8, 0x18}, {-0x18, -0x20}, {-0x20, 0x10}, {0x20, -0x10}, {0x20, 0x10}, {0x18, -0x28},
            {-0x28, -8}, {-0x10, 0x20}, {0x28, 0}};

    private Kind kind;
    private int offsetX;
    private int offsetY;
    /** Set by {@code loc_82F1C}: {@code st subtype(a1)}. */
    private boolean followCamera;
    private int x;
    private int y;
    private int timer;
    private int index;


    private DdzBossExplosionClusterObjectInstance(Kind kind, AbstractObjectInstance parent,
                                                  int offsetX, int offsetY, boolean followCamera, int x, int y) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, kind.ordinal(), 0, false, 0), "DDZBossExplosion" + kind, parent);
        this.kind = kind;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.followCamera = followCamera;
        this.x = x & 0xFFFF;
        this.y = y & 0xFFFF;
    }

    static DdzBossExplosionClusterObjectInstance defeat(DdzEndBossObjectInstance boss, int x, int y) {
        return new DdzBossExplosionClusterObjectInstance(Kind.DEFEAT, boss, 0, 0, false, x, y);
    }

    static DdzBossExplosionClusterObjectInstance exit(DdzEndBossObjectInstance boss, int x, int y) {
        return new DdzBossExplosionClusterObjectInstance(Kind.EXIT, boss, 0, 0, false, x, y);
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzBossExplosionClusterObjectInstance(ObjectSpawn spawn) {
        this(Kind.values()[spawn.subtype()], null, 0, 0, false, spawn.x(), spawn.y());
    }

    @Override
    public DdzBossExplosionClusterObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzBossExplosionClusterObjectInstance(Kind.values()[ctx.spawn().subtype()], null,
                0, 0, false, ctx.spawn().x(), ctx.spawn().y());
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
        // sub_82C86: follow the parent at $42/$44.
        if (parent != null) {
            // The defeated boss is deleted before the last bursts: its cleared slot reads (0,0), which
            // puts those bursts off-screen near the level origin (native slot history, row 5477).
            x = (parentXPos() + offsetX) & 0xFFFF;
            y = (parentYPos() + offsetY) & 0xFFFF;
        }
        switch (kind) {
            case DEFEAT -> updateSpawner(DEFEAT_OFFSETS, true);
            case EXIT -> updateSpawner(EXIT_OFFSETS, false);
            case CLUSTER -> updateCluster();
        }
    }

    private void updateSpawner(int[][] offsets, boolean flash) {
        timer = (short) (timer - 1);
        if (timer >= 0) {
            if (flash) {
                DdzPalette.applyFlashRow(services(), 0);
            }
            return;
        }
        if (flash) {
            DdzPalette.applyFlashRow(services(), 1);
        }
        services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
        timer = 0x1F;
        int[] offset = offsets[index];
        boolean exit = kind == Kind.EXIT;
        spawnFreeChild(() -> new DdzBossExplosionClusterObjectInstance(Kind.CLUSTER, this,
                offset[0], offset[1], exit, x + offset[0], y + offset[1]));
        index++;
        if (index >= offsets.length) {
            if (flash) {
                goDelete();
                // loc_82EEA: Go_Delete_Sprite falls through into loc_82EEE's first-row write.
                DdzPalette.applyFlashRow(services(), 0);
            } else {
                // loc_83038: Delete_Current_Sprite.
                deleteNow();
            }
        }
    }

    /** {@code loc_82F78}. */
    private void updateCluster() {
        if (index >= BURST_OFFSETS.length) {
            // loc_83038: Delete_Current_Sprite.
            deleteNow();
            return;
        }
        int[] offset = BURST_OFFSETS[index++];
        int ex = x + offset[0];
        int ey = y + offset[1];
        spawnFreeChild(() -> new DdzBossExplosionObjectInstance(ex, ey, offset[1] >> 4, followCamera));
    }


    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }


}

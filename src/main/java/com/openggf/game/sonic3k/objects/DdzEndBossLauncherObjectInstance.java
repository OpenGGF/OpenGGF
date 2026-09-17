package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code loc_81F94} (sonic3k.asm:174094-174129): the phase-1 missile launcher at boss
 * {@code + ($D8, $80)}. Its count {@code $39} starts negative; while negative and Player 1 is at or
 * right of it, it sets the count to 2 and allocates three subtype-1 {@code Obj_DDZMissile}s with
 * indices 0-2 ({@code sfx_TubeLauncher}). Each spent missile decrements the count, so the next volley
 * waits until all three have hit or exploded. The launcher deletes once the boss's {@code status}
 * bit 7 is set.
 */
final class DdzEndBossLauncherObjectInstance extends AbstractDdzObjectInstance
        implements DdzMissileObjectInstance.Launcher {
    private static final int OFFSET_X = 0xD8;
    private static final int OFFSET_Y = 0x80;

    private int x;
    private int y;
    /** {@code $39}: {@code st} at allocation. */
    private int count = 0xFF;


    DdzEndBossLauncherObjectInstance(DdzEndBossObjectInstance boss) {
        super(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, 0, 0, false, 0),
                "DDZEndBossLauncher", boss);
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzEndBossLauncherObjectInstance(ObjectSpawn spawn) {
        this((DdzEndBossObjectInstance) null);
    }

    @Override
    public DdzEndBossLauncherObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossLauncherObjectInstance((DdzEndBossObjectInstance) null);
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
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        if (!(parent instanceof DdzEndBossObjectInstance boss) || boss.isDestroyed()) {
            goDelete();
            return;
        }
        x = (boss.getX() + OFFSET_X) & 0xFFFF;
        y = (boss.getY() + OFFSET_Y) & 0xFFFF;
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        // tst.b $39(a0) / bpl.s; cmp.w x_pos(a0),d0 / blo.s
        if ((byte) count < 0 && player != null && (player.getCentreX() & 0xFFFF) >= x) {
            count = 2;
            boolean allocated = true;
            for (int index = 0; index < 3 && allocated; index++) {
                int missileIndex = index;
                allocated = spawnFreeChild(() -> new DdzMissileObjectInstance(x, y, missileIndex, this)) != null;
            }
            // AllocateObject failure branches to loc_81FDA past the sound.
            if (allocated) {
                services().playSfx(Sonic3kSfx.TUBE_LAUNCHER.id);
            }
        }
        if (boss.destroyedStatus()) {
            goDelete();
        }
    }

    @Override
    public int launcherX() {
        return x;
    }

    @Override
    public int launcherY() {
        return y;
    }

    /** {@code subq.b #1,$39(a1)}. */
    @Override
    public void missileSpent() {
        count = (count - 1) & 0xFF;
    }


    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }


}

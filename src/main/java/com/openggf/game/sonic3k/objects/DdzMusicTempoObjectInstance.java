package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * ROM {@code loc_82722} (sonic3k.asm:174759-174770), allocated by the Doomsday flight controller's
 * init. Each frame it selects {@code zTempoSpeedup} 8 while {@code Ring_count <= 10} and 0 otherwise,
 * and calls {@code Change_Music_Tempo} only when the selection changes. The stored selection
 * ({@code $3A(a0)}) starts at 0, so nothing happens until the ring count first drops to ten.
 */
public final class DdzMusicTempoObjectInstance extends AbstractDdzObjectInstance {
    private static final int LOW_RING_THRESHOLD = 10;
    private static final int SPEEDUP = 8;

    /** {@code $3A(a0)}. */
    private int selection;


    public DdzMusicTempoObjectInstance() {
        this(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
    }

    public DdzMusicTempoObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DdzMusicTempo", null);
    }

    @Override
    public DdzMusicTempoObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzMusicTempoObjectInstance(ctx.spawn());
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        var main = services().spriteManager().getMainPlayable();
        if (main == null) {
            return;
        }
        // cmpi.w #10,(Ring_count).w / bhi.s: unsigned, so only 0..10 speeds up.
        int next = Integer.compareUnsigned(main.getRingCount() & 0xFFFF, LOW_RING_THRESHOLD) > 0 ? 0 : SPEEDUP;
        if (next == selection) {
            return;
        }
        selection = next;
        services().audioManager().setSpeedShoes(next != 0);
    }

    int selectionForTest() {
        return selection;
    }



    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.InlinePlaneSwitcher;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * S3K path-switch marker (object 0x02).
 *
 * <p>The actual player path/priority change is handled by the shared placement-backed
 * plane-switcher pass. The ROM still allocates an SST entry for Obj_PathSwap and keeps
 * it alive until its routine ends in Delete_Sprite_If_Not_In_Range, so it must consume
 * a normal object slot for downstream allocation/RNG parity (docs/skdisasm/sonic3k.asm:
 * 39699-39720, 39740-39776).
 */
public final class Sonic3kPathSwapObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, InlinePlaneSwitcher {

    public Sonic3kPathSwapObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PathSwap");
    }

    @Override
    public Sonic3kPathSwapObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic3kPathSwapObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // Obj_PathSwap executes in its SST slot. Earlier slots in the same pass
        // must still observe the old path bits (sonic3k.asm:39764-39887).
        services().objectManager().applyInlinePlaneSwitcher(getSpawn(), playerEntity);
        ObjectPlayerQuery query = services().playerQuery();
        PlayableEntity playerTwo = new ObjectPlayerQuery(() -> playerEntity, query::sidekicks)
                .nativeP2OrNull();
        if (playerTwo != null && playerTwo != playerEntity) {
            services().objectManager().applyInlinePlaneSwitcher(getSpawn(), playerTwo);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}

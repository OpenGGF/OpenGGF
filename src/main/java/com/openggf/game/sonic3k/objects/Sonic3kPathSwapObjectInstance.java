package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.InlinePlaneSwitcher;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;

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
        implements RewindRecreatable, InlinePlaneSwitcher, RomWorldPositionedObject {

    public Sonic3kPathSwapObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PathSwap");
    }

    @Override
    public Sonic3kPathSwapObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic3kPathSwapObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
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

    /**
     * {@code Offset_ObjectsDuringTransition} (sonic3k.asm:104166-104181) walks every SST slot
     * from {@code Dynamic_object_RAM+object_size} to {@code Breathing_bubbles} and subtracts
     * {@code d0}/{@code d1} from the {@code x_pos}/{@code y_pos} of each one whose
     * {@code render_flags} bit 2 is set. {@code Obj_PathSwap} holds a normal slot with that bit
     * set, so a seamless act change moves it like any other placed object; the switcher reads
     * this position, so it has to be the moved one and not the placement it loaded at.
     */
    @Override
    public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        updateDynamicSpawn((getCollisionX() + offsetX) & 0xFFFF,
                (getCollisionY() + offsetY) & 0xFFFF);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}

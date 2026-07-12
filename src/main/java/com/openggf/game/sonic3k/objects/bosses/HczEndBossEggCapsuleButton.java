package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.List;

/** Separate top-button SST slot created by {@code Obj_EggCapsule}. */
public final class HczEndBossEggCapsuleButton extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x1B, 4, 6);

    @RewindTransient(reason = "Structural parent link relinked to the live egg capsule during recreate.")
    private final HczEndBossEggCapsuleInstance parent;

    public HczEndBossEggCapsuleButton(HczEndBossEggCapsuleInstance parent, int x, int y) {
        super(new ObjectSpawn(x, y, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, y),
                "HCZEggCapsuleButton");
        this.parent = parent;
    }

    @Override
    public HczEndBossEggCapsuleButton recreateForRewind(RewindRecreateContext ctx) {
        HczEndBossEggCapsuleInstance restoredParent = RewindRecreateObjectLinks.nearestLiveObject(
                ctx, HczEndBossEggCapsuleInstance.class);
        return restoredParent == null ? null : new HczEndBossEggCapsuleButton(
                restoredParent, ctx.spawn().x(), ctx.spawn().y());
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (hasStandingContact(checkpointAll())) {
            parent.signalButtonPressed();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}

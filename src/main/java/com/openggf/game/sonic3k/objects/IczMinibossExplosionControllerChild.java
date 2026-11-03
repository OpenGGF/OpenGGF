package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/** Native ICZ miniboss {@code Child6_CreateBossExplosion} controller SST. */
final class IczMinibossExplosionControllerChild extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int RANGE = 0x20;
    private static final int DISPATCH_INTERVAL = 3;
    private static final int INITIAL_TIMER = 0x20;

    private final IczMinibossInstance parent;
    private int remaining = INITIAL_TIMER;
    // Obj_CreateBossExplosion tail-jumps through zeroed Obj_Wait, so the
    // controller's first SST dispatch emits immediately.
    private int interval;

    IczMinibossExplosionControllerChild(IczMinibossInstance parent, int centreX, int centreY) {
        super(new ObjectSpawn(centreX, centreY, 0, 0, 0, false, 0),
                "ICZMinibossExplosionController");
        this.parent = parent;
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        IczMinibossInstance restoredParent =
                RewindRecreateObjectLinks.nearestLiveObject(ctx, IczMinibossInstance.class);
        return restoredParent == null ? null
                : new IczMinibossExplosionControllerChild(
                        restoredParent, ctx.spawn().x(), ctx.spawn().y());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (--interval >= 0) {
            return;
        }
        if (--remaining <= 0) {
            parent.onDefeatExplosionControllerFinished();
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }

        interval = DISPATCH_INTERVAL - 1;
        var manager = services().objectManager();
        if (manager == null) return;
        // Obj_BossExpControl1 attempts CreateChild6_Simple before loc_83E90
        // consumes Random_Number; a full SST consumes the attempt but not RNG/audio.
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) return;
        try {
            int random = services().rng().nextRaw();
            int x = spawn.x() + S3kExplosionOffsets.xOffset(random, RANGE);
            int y = spawn.y() + S3kExplosionOffsets.yOffset(random, RANGE);
            var child = ObjectConstructionContext.with(services(), slot,
                    () -> new S3kBossExplosionChild(x, y));
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
        services().playSfx(Sonic3kSfx.EXPLODE.id);
    }

    @Override
    public int getPriorityBucket() {
        return 5;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Controller SST has no mapping of its own.
    }
}

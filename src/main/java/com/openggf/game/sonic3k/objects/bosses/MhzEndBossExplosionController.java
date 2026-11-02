package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import java.util.List;

/** MHZ's CreateBossExp00 stationary burst and CreateBossExp20 following burst. */
public final class MhzEndBossExplosionController extends AbstractObjectInstance implements RewindRecreatable {
    @RewindTransient(reason = "Structural link restored by object reference identity.")
    private final MhzEndBossInstance parent;
    private int remaining;
    private int waitCounter;
    private boolean pendingDelete;

    static MhzEndBossExplosionController stationary(int x, int y) {
        return new MhzEndBossExplosionController(x, y, null);
    }

    static MhzEndBossExplosionController following(MhzEndBossInstance parent) {
        return new MhzEndBossExplosionController(parent.getX(), parent.getY(), parent);
    }

    private MhzEndBossExplosionController(int x, int y, MhzEndBossInstance parent) {
        this(new ObjectSpawn(x, y, 0, parent == null ? 0 : 0x20, 0, false, y), parent);
    }

    private MhzEndBossExplosionController(ObjectSpawn spawn, MhzEndBossInstance parent) {
        super(spawn, "MHZEndBossExplosionControl");
        this.parent = parent;
        remaining = parent == null ? 0x20 : 0x80;
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        var restoredParent = ctx.spawn().subtype() == 0 ? null
                : RewindRecreateObjectLinks.nearestLiveObject(ctx, MhzEndBossInstance.class);
        if (ctx.spawn().subtype() != 0 && restoredParent == null) return null;
        return new MhzEndBossExplosionController(ctx.spawn(), restoredParent);
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            setDestroyed(true);
            return;
        }
        if (parent != null) {
            // Obj_WaitForParent runs even on the two intervening wait passes.
            if (parent.isDestroyed() || (parent.getCustomFlag(0x38) & 0x20) != 0) {
                pendingDelete = true;
                return;
            }
            updateDynamicSpawn(parent.getX(), parent.getY());
        }
        if (--waitCounter >= 0) return;
        if (--remaining == 0) {
            pendingDelete = true;
            return;
        }
        waitCounter = 2;
        var manager = services().objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) return;
        try {
            var child = ObjectConstructionContext.with(services(), slot,
                    () -> S3kBossExplosionChild.createWithNativeInitSfx(getX(), getY()));
            child.setNativeLevelRepeatOffset(parent != null);
            int random = services().rng().nextRaw();
            child.writeNativePositionWords(getX() + (random & 0x3F) - 0x20,
                    getY() + ((random >>> 16) & 0x3F) - 0x20);
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}

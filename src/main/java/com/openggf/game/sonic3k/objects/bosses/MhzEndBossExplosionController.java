package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import java.util.List;

/** MHZ's CreateBossExp00 stationary burst and CreateBossExp20 following burst. */
public final class MhzEndBossExplosionController extends AbstractObjectInstance implements RewindRecreatable {
    // Captured as an exact object-reference identity and rebound after graph recreation.
    // Mutable so phase two can hydrate the saved owner after all shells exist.
    private MhzEndBossInstance parent;
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

    /** Pure probe/recreation shell: stationary bursts have no parent to resolve. */
    private MhzEndBossExplosionController(ObjectSpawn spawn) {
        super(spawn, "MHZEndBossExplosionControl");
    }

    private MhzEndBossExplosionController(ObjectSpawn spawn, MhzEndBossInstance parent) {
        this(spawn);
        this.parent = parent;
        remaining = parent == null ? 0x20 : 0x80;
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        // Phase-one shell only: generic graph hydration restores parent and scalar state.
        // Spatial proximity cannot identify the owning boss when two graphs overlap.
        return new MhzEndBossExplosionController(ctx.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            ObjectLifetimeOps.expireDynamic(this);
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

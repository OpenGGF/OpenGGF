package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7C886..7C8F8: the first-defeat charge, followed by Mecha's standing glow. */
public final class SszMechaSonicChargeChild extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable {
    private int parentSlot = -1;
    private boolean initialized;
    private boolean following;
    private boolean flipped;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;

    public SszMechaSonicChargeChild(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaCharge");
    }

    static void spawnFor(ObjectServices services, int parentSlot, int x, int y) {
        var manager = services.objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return;
        try {
            var child = ObjectConstructionContext.with(services, slot,
                    () -> new SszMechaSonicChargeChild(x, y));
            child.parentSlot = parentSlot;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    private SszMechaSonicObjectInstance parent() {
        var manager = services().objectManager();
        if (manager != null) {
            for (var object : manager.getActiveObjects()) {
                if (object instanceof SszMechaSonicObjectInstance mecha
                        && mecha.getSlotIndex() == parentSlot && !mecha.isDestroyed()) return mecha;
            }
        }
        return null;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var parent = parent();
        if (!initialized) {
            initialized = true;
            animation.script = 0x7D668;
            animation.mappingFrame = 0x11;
            // Init refreshes once. loc_7C8BA animates at that stationary position;
            // continuous following starts only after its $F4 callback.
            refresh(parent, -4, 0xC);
        }
        if (following) {
            refresh(parent, -7, 3);
            // Native parent3 is an SST address. A missing Mecha cannot expose the
            // replacement slot's mapping byte; model its cleared frame (zero).
            if (parent == null || parent.mappingFrameForTest() != 0xE)
                ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (animator == null) {
            try {
                animator = S3kRawAnimation.load(services().romReader(), 0x7D668, 19);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        animator.animateMultiDelay(animation, () -> {
            following = true;
            if (parent != null) parent.finishChargeAnimation();
        });
    }

    private void refresh(SszMechaSonicObjectInstance parent, int dx, int dy) {
        flipped = parent != null && parent.renderFlippedForTest();
        int x = parent == null ? 0 : parent.getX();
        int y = parent == null ? 0 : parent.getY();
        updateDynamicSpawn((x + (flipped ? -dx : dx)) & 0xFFFF, (y + dy) & 0xFFFF);
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public int getOnScreenHalfWidth() { return 4; }
    @Override public int getOnScreenHalfHeight() { return 4; }
    int mappingFrameForTest() { return animation.mappingFrame; }
    boolean followingForTest() { return following; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_CHARGE);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), flipped, false, 1);
    }
}

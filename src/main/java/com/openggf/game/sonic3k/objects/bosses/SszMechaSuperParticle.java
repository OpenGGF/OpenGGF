package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7C7D4: a stationary, randomly offset sparkle with animation-driven deferred deletion. */
public final class SszMechaSuperParticle extends AbstractObjectInstance implements SpawnCoordinateRewindRecreatable {
    private boolean initialized, flipped, pendingDelete;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;
    public SszMechaSuperParticle(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaSuperParticle");
    }
    static void spawnFor(ObjectServices services, int parentSlot, int x, int y, boolean flipped) {
        var manager = services.objectManager();
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return;
        try {
            var child = ObjectConstructionContext.with(services, slot, () -> new SszMechaSuperParticle(x, y));
            child.flipped = flipped;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!initialized) {
            initialized = true;
            int random = services().rng().nextRaw();
            updateDynamicSpawn((getX() + (random & 31) - 15) & 0xFFFF,
                    (getY() + ((random >>> 16) & 31) - 15) & 0xFFFF);
            animation.mappingFrame = 8;
            animation.script = 0x7D683;
        }
        if (animator == null) {
            try { animator = S3kRawAnimation.load(services().romReader(), 0x7D683, 9); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        animator.animateMultiDelay(animation, () -> pendingDelete = true);
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 4; }
    @Override public int getOnScreenHalfHeight() { return 4; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_SUPER_EFFECTS);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), flipped, false, 1);
    }
}

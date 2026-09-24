package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7C78E: the Super dash glow; its parent $38 bit6 owns its lifetime. */
public final class SszMechaSuperGlow extends AbstractObjectInstance implements SpawnCoordinateRewindRecreatable {
    private int parentSlot = -1;
    private boolean initialized, flipped, visible;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;

    public SszMechaSuperGlow(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaSuperGlow");
    }
    static void spawnFor(ObjectServices services, int parentSlot, int x, int y) {
        var manager = services.objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return;
        try {
            var child = ObjectConstructionContext.with(services, slot, () -> new SszMechaSuperGlow(x, y));
            child.parentSlot = parentSlot;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (!initialized) { initialized = true; animation.mappingFrame = 8; }
        if (animator == null) {
            try { animator = S3kRawAnimation.load(services().romReader(), 0x7D67B, 8); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        animator.animateNoSst(animation, 0x7D67B, () -> { });
        SszMechaSonicObjectInstance parent = null;
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof SszMechaSonicObjectInstance mecha && mecha.getSlotIndex() == parentSlot
                    && !mecha.isDestroyed()) { parent = mecha; break; }
        }
        flipped = parent != null && parent.renderFlippedForTest();
        updateDynamicSpawn(((parent == null ? 0 : parent.getX()) + (flipped ? -0x14 : 0x14)) & 0xFFFF,
                ((parent == null ? 0 : parent.getY()) - 4) & 0xFFFF);
        if (parent == null || !parent.superGlowActive()) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (parent.mappingFrameForTest() != 0x12) return;
        // The ROM tests V_int bit0 here but never branches on the result. Preserve
        // one allocation attempt per visible pass, rather than inventing alternate-frame gating.
        SszMechaSuperParticle.spawnFor(services(), getSlotIndex(), getX(), getY(), flipped);
        visible = true;
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_SUPER_EFFECTS);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), flipped, false, 1);
    }
}

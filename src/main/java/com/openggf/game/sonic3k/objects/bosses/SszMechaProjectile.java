package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7C726 radial orbs and loc_7C744 parent-following charge/aimed laser. */
public final class SszMechaProjectile extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable, TouchResponseProvider {
    private int parentSlot = -1;
    private int subtype;
    private boolean radial, initialized, launched, flipped, pendingDelete, collisionEnabled;
    private int posX, posY, xVel, yVel;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;

    public SszMechaProjectile(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaProjectile");
        posX = x << 16; posY = y << 16;
    }

    /** CreateChild1_Normal searches forward; CreateChild6_Simple starts each search at the parent. */
    static SszMechaProjectile spawnFor(ObjectServices services, int parentSlot, int x, int y,
                                      boolean radial, int subtype) {
        var manager = services.objectManager();
        if (manager == null) return null;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return null;
        try {
            var child = ObjectConstructionContext.with(services, slot, () -> new SszMechaProjectile(radial ? x : (x - 7) & 0xFFFF, radial ? y : (y - 8) & 0xFFFF));
            child.parentSlot = parentSlot; child.radial = radial; child.subtype = subtype;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
            return child;
        } catch (RuntimeException | Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!initialized) {
            initialized = true;
            try {
                if (radial) {
                    animation.mappingFrame = 0x15;
                    xVel = (short) services().rom().read16BitAddr(0x7D172 + subtype * 2);
                    yVel = (short) services().rom().read16BitAddr(0x7D174 + subtype * 2);
                } else {
                    animation.mappingFrame = 0xA;
                    animation.script = services().rom().read32BitAddr(0x7D1A6 + subtype);
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        if (radial || launched) {
            if (!radial) animator().animateNoSst(animation, 0x7D6A3, () -> { });
            posX += xVel << 8; posY += yVel << 8;
            collisionEnabled = insideNativeCullWindow();
            if (!collisionEnabled) pendingDelete = true;
            return;
        }
        // loc_7C756 continues refresh/draw even when Animate_Raw switches its code pointer.
        animator().animateNoSst(animation, animation.script, this::finishCharge);
        SszMechaSonicObjectInstance parent = null;
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof SszMechaSonicObjectInstance mecha && mecha.getSlotIndex() == parentSlot
                    && !mecha.isDestroyed()) { parent = mecha; break; }
        }
        flipped = parent != null && parent.renderFlippedForTest();
        int x = ((parent == null ? 0 : parent.getX()) + (flipped ? 7 : -7)) & 0xFFFF;
        int y = ((parent == null ? 0 : parent.getY()) - 8) & 0xFFFF;
        posX = (posX & 0xFFFF) | x << 16; posY = (posY & 0xFFFF) | y << 16;
        collisionEnabled = parent != null && !parent.defeatedForTest();
        if (!collisionEnabled) pendingDelete = true;
    }

    private void finishCharge() {
        if (subtype == 8) { pendingDelete = true; return; }
        launched = true;
        services().playSfx(Sonic3kSfx.BOSS_LASER.id);
        var p1 = services().playerQuery().mainPlayerOrNull();
        var velocity = SszMechaAim.toward(getX(), getY(), p1 == null ? 0 : p1.getCentreX(),
                p1 == null ? 0 : p1.getCentreY(), 2);
        xVel = velocity.x(); yVel = velocity.y();
    }

    private S3kRawAnimation animator() {
        if (animator == null) {
            try { animator = S3kRawAnimation.load(services().romReader(), 0x7D68C, 0x4C); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        return animator;
    }

    private boolean insideNativeCullWindow() {
        var camera = services().camera();
        // Sprite_CheckDeleteTouchXY uses a $280 coarse-X window at 320 pixels.
        // Wider viewports extend that window to retain visible projectiles at their right edge;
        // velocity and collision stay native. Y uses the original unsigned $200 window.
        int coarseBack = (camera.getX() - 0x80) & 0xFF80;
        int xDistance = ((getX() & 0xFF80) - coarseBack) & 0xFFFF;
        return xDistance <= 0x280 + Math.max(0, camera.getWidth() - 320)
                && ((getY() - camera.getY() + 0x80) & 0xFFFF) < 0x200;
    }

    @Override public int getX() { return posX >>> 16; }
    @Override public int getY() { return posY >>> 16; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return radial ? 5 : 4; }
    @Override public int getOnScreenHalfWidth() { return radial ? 8 : 0x18; }
    @Override public int getOnScreenHalfHeight() { return getOnScreenHalfWidth(); }
    @Override public int getCollisionFlags() { return collisionEnabled ? radial ? 0x87 : 0x86 : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean usesCurrentTouchResponseState() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this, multiRegionSource);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed() || !collisionEnabled) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_SUPER_EFFECTS);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), flipped, false, 1);
    }
}

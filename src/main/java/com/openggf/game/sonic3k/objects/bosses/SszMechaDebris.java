package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7CE90 / Obj_FlickerMove: sixteen ROM-mapped pieces of the defeated body. */
public final class SszMechaDebris extends AbstractObjectInstance implements SpawnCoordinateRewindRecreatable {
    private int parentSlot = -1, subtype;
    private int posX, posY, xVel, yVel;
    private boolean initialized, flipped, flicker, visible, pendingDelete;

    public SszMechaDebris(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaDebris");
        posX = x << 16; posY = y << 16;
    }
    static boolean spawnFor(ObjectServices services, int parentSlot, int x, int y, int subtype) {
        var manager = services.objectManager();
        if (manager == null) return false;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return false;
        try {
            var child = ObjectConstructionContext.with(services, slot, () -> new SszMechaDebris(x, y));
            child.parentSlot = parentSlot; child.subtype = subtype;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
            return true;
        } catch (RuntimeException | Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!initialized) {
            initialized = true;
            for (var object : services().objectManager().getActiveObjects()) {
                if (object instanceof SszMechaSonicObjectInstance parent && parent.getSlotIndex() == parentSlot) {
                    flipped = parent.renderFlippedForTest(); break;
                }
            }
            try {
                int dx = (byte) services().romReader().readU8(0x7CEE2 + subtype);
                int dy = (byte) services().romReader().readU8(0x7CEE3 + subtype);
                // FixBugs=0 negates d2 instead of d1 when the parent is flipped.
                // d2 is then overwritten by dy, so neither dx nor its velocity is
                // mirrored. Only the piece's render flip changes; preserve that bug.
                posX += dx << 16; posY += dy << 16;
                xVel = (short) (dx << 5); yVel = (short) (dy << 4);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            return; // Init returns without moving or drawing.
        }
        if (((SszZoneRuntimeState) services().zoneRuntimeState()).act2EndingActive()) {
            ObjectLifetimeOps.deleteNoRespawn(this); return;
        }
        posX += xVel << 8; posY += yVel << 8; yVel = (short) (yVel + 0x38);
        var camera = services().camera();
        int coarseBack = (camera.getX() - 0x80) & 0xFF80;
        // Obj_FlickerMove's native $280 window grows only to retain pieces visible
        // in the extra widescreen area. Its unsigned Y limit remains >$200.
        if ((((getX() & 0xFF80) - coarseBack) & 0xFFFF) > 0x280 + Math.max(0, camera.getWidth() - 320)
                || ((getY() - camera.getY() + 0x80) & 0xFFFF) > 0x200) {
            pendingDelete = true; return;
        }
        visible = flicker; flicker = !flicker; // BCHG/BEQ: the old zero bit skips the first move's draw.
    }
    @Override public int getX() { return posX >>> 16; }
    @Override public int getY() { return posY >>> 16; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public int getOnScreenHalfWidth() { return 4; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    boolean visibleForTest() { return visible; }
    boolean flippedForTest() { return flipped; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_PIECES);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(subtype / 2, getX(), getY(), flipped, false, 1);
    }
}

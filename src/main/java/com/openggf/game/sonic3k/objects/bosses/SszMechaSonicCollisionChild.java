package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;

import java.io.IOException;
import java.util.List;

/** loc_7C9BA / sub_7D260: Mecha's invisible, frame-dependent secondary hurt box. */
public final class SszMechaSonicCollisionChild extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable, TouchResponseProvider {
    private int parentSlot = -1;
    private int collisionFlags;
    private int mappingFrame;
    private boolean pendingDelete;

    public SszMechaSonicCollisionChild(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaSonicCollision");
    }

    static void spawnFor(ObjectServices services, int parentSlot, int x, int y) {
        var manager = services.objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return;
        try {
            var child = ObjectConstructionContext.with(services, slot,
                    () -> new SszMechaSonicCollisionChild(x, y));
            child.parentSlot = parentSlot;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        SszMechaSonicObjectInstance parent = null;
        var manager = services().objectManager();
        if (manager != null) {
            for (var object : manager.getActiveObjects()) {
                if (object instanceof SszMechaSonicObjectInstance mecha
                        && mecha.getSlotIndex() == parentSlot && !mecha.isDestroyed()) {
                    parent = mecha;
                    break;
                }
            }
        }
        if (parent == null) {
            collisionFlags = 0;
            pendingDelete = true;
            return;
        }
        try {
            var reader = services().romReader();
            int row = Sonic3kConstants.SSZ_MECHA_SECONDARY_COLLISION_ADDR
                    + (parent.mappingFrameForTest() << 2);
            int dx = (byte) reader.readU8(row);
            int dy = (byte) reader.readU8(row + 1);
            collisionFlags = reader.readU8(row + 2);
            mappingFrame = reader.readU8(row + 3);
            updateDynamicSpawn((parent.getX() + (parent.renderFlippedForTest() ? -dx : dx)) & 0xFFFF,
                    (parent.getY() + dy) & 0xFFFF);
        } catch (IOException failure) {
            throw new IllegalStateException("Mecha secondary collision requires the S3K ROM", failure);
        }
        // loc_7C9C8 refreshes before checking status bit 7. A hit window's bit 6
        // does not suppress this independent hurt box; only the killing hit does.
        if (parent.defeatedForTest()) {
            collisionFlags = 0;
            pendingDelete = true; // Go_Delete_Sprite, next object pass.
        }
    }

    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresRenderFlagForTouch() { return false; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 1; }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    public int mappingFrameForTest() { return mappingFrame; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}

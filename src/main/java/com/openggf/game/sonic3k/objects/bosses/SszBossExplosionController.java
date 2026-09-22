package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;

import java.util.List;

/** Child6_CreateBossExplosion, subtype 4, for loc_7A5EC and loc_7AD3A. */
public final class SszBossExplosionController extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable {
    private int parentSlot = -1;
    private int waitCounter;
    private boolean pendingDelete;

    public SszBossExplosionController(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 4, 0, false, 0), "SSZBossExplosionControl");
    }

    SszBossExplosionController(int x, int y, int parentSlot) {
        this(x, y);
        this.parentSlot = parentSlot;
    }

    static void spawnFor(ObjectServices services, int parentSlot, int x, int y) {
        if (services.renderManager() != null
                && services.renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureBossExplosionArtLoaded();
        }
        var manager = services.objectManager();
        if (manager == null) {
            return;
        }
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) {
            return;
        }
        try {
            var child = ObjectConstructionContext.with(services, slot,
                    () -> new SszBossExplosionController(x, y, parentSlot));
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            setDestroyed(true);
            return;
        }
        // Obj_WaitForParent reads the SST address, not a lifetime-owned Java parent.
        AbstractObjectInstance parent = null;
        var manager = services().objectManager();
        if (manager != null) {
            for (var object : manager.getActiveObjects()) {
                if (object instanceof AbstractObjectInstance candidate
                        && candidate.getSlotIndex() == parentSlot && !candidate.isDestroyed()) {
                    parent = candidate;
                    break;
                }
            }
        }
        // These two ships keep $38 bit 5 clear throughout defeat. MTZ's $38 is armX;
        // loc_7AD3A clears it before allocating us. Other SST occupants do not expose
        // arbitrary native bytes; their $38 stop-bit behavior remains unsupported.
        if (parent == null || parent instanceof SszMtzBossObjectInstance mtz
                && mtz.stopsDefeatExplosions()) {
            pendingDelete = true; // Go_Delete_Sprite installs next-pass deletion.
            return;
        }
        updateDynamicSpawn(parent.getX(), parent.getY());
        waitCounter = (short) (waitCounter - 1);
        if (waitCounter >= 0) {
            return;
        }
        // Subtype 4 has negative $39 ($80), so the count never expires.
        waitCounter = 2;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) {
            return; // CreateChild6_Simple failure consumes no Random_Number call.
        }
        try {
            var child = ObjectConstructionContext.with(services(), slot,
                    () -> S3kBossExplosionChild.createWithNativeInitSfx(getX(), getY()));
            int random = services().rng().nextRaw();
            child.writeNativePositionWords(getX() + (random & 0x3F) - 0x20,
                    getY() + ((random >>> 16) & 0x3F) - 0x20);
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) { }
}

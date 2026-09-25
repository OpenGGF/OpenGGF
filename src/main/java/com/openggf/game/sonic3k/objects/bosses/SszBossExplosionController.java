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

/** Child6_CreateBossExplosion subtypes4/$C/$1E used by SSZ defeat graphs. */
public final class SszBossExplosionController extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable {
    /** Semantic view of the parent's native $38 bit 5, read by Obj_WaitForParent. */
    public interface StopFlag {
        boolean stopsDefeatExplosions();
    }

    private int parentSlot = -1;
    private int waitCounter;
    private boolean followParent = true;
    private int remaining = 0x80, rangeX = 0x20, rangeY = 0x20;
    private boolean pendingDelete;

    public SszBossExplosionController(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 4, 0, false, 0), "SSZBossExplosionControl");
    }

    SszBossExplosionController(int x, int y, int parentSlot) {
        this(x, y);
        this.parentSlot = parentSlot;
    }

    static void spawnFor(ObjectServices services, int parentSlot, int x, int y) {
        spawnFor(services, parentSlot, x, y, 4);
    }

    static boolean spawnFor(ObjectServices services, int parentSlot, int x, int y, int subtype) {
        if (services.renderManager() != null
                && services.renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureBossExplosionArtLoaded();
        }
        var manager = services.objectManager();
        if (manager == null) {
            return false;
        }
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) {
            return false;
        }
        try {
            var child = ObjectConstructionContext.with(services, slot,
                    () -> new SszBossExplosionController(x, y, parentSlot));
            // CreateBossExp0C is finite Obj_Wait; 04/1E use Obj_WaitForParent.
            if (subtype == 0xC) {
                child.followParent = false; child.remaining = 0x40;
                child.rangeX = 0x80; child.rangeY = 0x20;
            } else if (subtype == 0x1E) { child.rangeX = 0x10; child.rangeY = 0x10; }
            else if (subtype != 4) throw new IllegalArgumentException("Unsupported SSZ explosion subtype");
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
            return true;
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
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
        // MTZ aliases $38 to armX; Mecha raises bit 5 when his defeated body lands.
        // GHZ keeps the bit clear. Other SST occupants do not expose arbitrary native
        // bytes; their $38 stop-bit behavior remains unsupported.
        if (followParent && (parent == null || parent instanceof StopFlag stop && stop.stopsDefeatExplosions())) {
            pendingDelete = true; // Go_Delete_Sprite installs next-pass deletion.
            return;
        }
        if (followParent) updateDynamicSpawn(parent.getX(), parent.getY());
        waitCounter = (short) (waitCounter - 1);
        if (waitCounter >= 0) {
            return;
        }
        // Obj_BossExpControl1 decrements only nonnegative byte counts, before allocation.
        // A $40 count therefore emits63 explosions; a failed reservation still spends a count.
        if ((byte) remaining >= 0 && (remaining = (remaining - 1) & 255) == 0) {
            pendingDelete = true;
            return;
        }
        waitCounter = 2;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) {
            return; // CreateChild6_Simple failure consumes no Random_Number call.
        }
        try {
            var child = ObjectConstructionContext.with(services(), slot,
                    () -> S3kBossExplosionChild.createWithNativeInitSfx(getX(), getY()));
            int random = services().rng().nextRaw();
            child.writeNativePositionWords(getX() + (random & (rangeX * 2 - 1)) - rangeX,
                    getY() + ((random >>> 16) & (rangeY * 2 - 1)) - rangeY);
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

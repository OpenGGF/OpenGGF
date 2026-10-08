package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_7CF14: one stationary burst plus two independently moving explosion sources. */
public final class SszMechaDefeatRunner extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean initialized;
    private int speed;

    public SszMechaDefeatRunner(ObjectSpawn spawn) { super(spawn, "SSZMechaDefeatRunner"); }

    static boolean spawnFor(ObjectServices services, int parentSlot, int x, int y, int subtype) {
        var manager = services.objectManager();
        if (manager == null) return false;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return false;
        try {
            var child = ObjectConstructionContext.with(services, slot,
                    () -> new SszMechaDefeatRunner(new ObjectSpawn(x, y, 0, subtype, 0, false, 0)));
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
            return true;
        } catch (RuntimeException | Error failure) { manager.releaseDynamicSlot(slot); throw failure; }
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var camera = services().camera();
        int nativeCameraX = com.openggf.game.sonic3k.runtime.SszArenaCamera.nativeX(camera,
                (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) services().zoneRuntimeState());
        if (!initialized) {
            initialized = true;
            if (getSpawn().subtype() == 0) {
                updateDynamicSpawn((nativeCameraX + 0xA0) & 0xFFFF, (camera.getY() + 0xD0) & 0xFFFF);
                SszBossExplosionController.spawnFor(services(), getSlotIndex(), getX(), getY(), 0xC);
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
            updateDynamicSpawn(getX(), (getY() + 0x30) & 0xFFFF);
            speed = getSpawn().subtype() == 2 ? -2 : 2;
            SszBossExplosionController.spawnFor(services(), getSlotIndex(), getX(), getY(), 0x1E);
            return; // loc_7CF4E does not fall through into movement.
        }
        int next = (getX() + speed) & 0xFFFF;
        updateDynamicSpawn(next, getY());
        // loc_7CF7C uses world-space native camera offsets, not viewport dimensions.
        // It is the source's lifetime, separate from a projectile's visibility cull window.
        int left = (nativeCameraX - 0x10) & 0xFFFF;
        if (next < left || next > ((left + 0x160) & 0xFFFF)) ObjectLifetimeOps.deleteNoRespawn(this);
    }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}

package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.objects.DezFinalArenaFloor;
import com.openggf.game.sonic3k.objects.DezFinalBossController;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.ObjectManager;

/** DEZ3_ScreenInit object allocation and loc_5A442 camera setup. */
public final class DezFinalScreenEvents {
    private DezFinalScreenEvents() { }

    public static void initializeObjectsAndCamera(ObjectManager objects, DezFinalBossZoneRuntimeState state) {
        if (state.screenInitApplied()) return;
        // Native ScreenInit runs once during loading, before Process_Sprites. Engine loading
        // positions the camera after installing the zone state, so this runs at first
        // pre-physics instead. Capture the marker: restoring an arena must not spawn it again.
        state.markScreenInitApplied();
        state.bossPosition(0, 0);
        // AllocateObject followed by two CreateNewSprite4 scans. With no intervening
        // allocations, successive lowest-free allocations reproduce the forward-only prefix.
        var moving = objects.createDynamicObject(DezFinalArenaFloor::moving);
        if (moving != null) {
            var entry = objects.createDynamicObject(DezFinalArenaFloor::entry);
            if (entry != null) {
                var root = objects.createDynamicObject(DezFinalBossController::new);
                if (root != null) state.bossPosition(0x3C0, 0xF8);
            }
        }
        // loc_5A442 executes even when any allocation above fails.
        state.windowBase(0x6C0);
        state.breakFrontier(0x80);
        var camera = objects.getObjectServices().camera();
        camera.setScrollLocked(true);
        camera.setX((short) 0x80);
        camera.setXCopy((short) 0x80);
        state.publishPlanePosition(0x80, state.screenShake().offset());
        camera.setYCopy((short) state.screenY());
        DezFinalLaserArt.update(objects.getObjectServices(), state);
    }
}

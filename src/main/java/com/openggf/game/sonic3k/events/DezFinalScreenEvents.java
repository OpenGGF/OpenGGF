package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.DezFinalCamera;

import com.openggf.game.sonic3k.objects.DezFinalArenaFloor;
import com.openggf.game.sonic3k.objects.DezFinalArenaBreakup;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.level.LevelManager;
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
        camera.setX(DezFinalCamera.visibleX(camera, 0x80));
        camera.setXCopy(DezFinalCamera.visibleX(camera, 0x80));
        state.publishPlanePosition(0x80, state.screenShake().offset());
        camera.setYCopy((short) state.screenY());
        DezFinalLaserArt.update(objects.getObjectServices(), state);
        // DEZ3_BackgroundInit refreshes the boss Plane A after ScreenInit. Source row
        // aliases are resolved before writing the physical 64x32 retained name table.
        var level = objects.getObjectServices().levelManager();
        // Refresh_PlaneTileDeform starts at rounded Y $20. Its $E0 split uses
        // static X=0 above the floor and Camera_X & $1FF below it, sixteen rows.
        state.arenaPlane().resetDrawPosition(0x80, 0x20);
        for (int y = 0x20; y < 0x120; y += 16)
            state.arenaPlane().writeRow(y < 0xE0 ? 0 : 0x80, y, 32,
                    (x, sourceY) -> arenaDescriptor(level, x, sourceY));
        state.plane().resetDrawPosition(state.planeX(), state.planeY());
        state.plane().refresh(state.planeX(), state.planeY(), (x, y) -> bossDescriptor(level, x, y));
    }

    /** ScreenEvents runs after Process_Sprites; new collapse workers begin next object pass. */
    public static void update(ObjectManager objects, DezFinalBossZoneRuntimeState state, int levelFrameCounter) {
        if (!state.screenInitApplied()) return;
        var services = objects.getObjectServices();
        var level = services.levelManager();
        var camera = services.camera();
        int oldPlaneX = state.planeX(); // sub_5A76C saves _unkEE8E before publishing new words.
        state.publishPlanePosition(DezFinalCamera.nativeCopyX(camera), state.screenShake().offset());
        camera.setYCopy((short) state.screenY());
        if (state.foregroundRoutine() == 0) {
            // loc_5A49A restores the Act3 bank once; zero values leave counters alone.
            com.openggf.level.LevelContinuationCarry.restoreCounters(level);
            state.foregroundRoutine(4);
        }
        // DEZ3_ScreenEvent / Draw_BGNoVert retains the fixed rounded Y=$20:
        // of fifteen visible block rows, the last three use the moving floor band.
        state.arenaPlane().drawHorizontalBand(DezFinalCamera.nativeCopyX(camera) & 0x1FF, 0xE0, 3,
                (x, y) -> arenaDescriptor(level, x, y));
        int redraw = state.redrawRequest();
        if (redraw != 0) {
            state.redrawRequest(0);
            // Source $1E0/$1F0 lands at physical $E0/$F0; each row clears two blocks.
            for (int y = 0x1E0; y < 0x200; y += 16)
                state.arenaPlane().writeRow(redraw & 0x1E0, y, 2,
                        (x, sourceY) -> arenaDescriptor(level, x, sourceY));
        }
        var context = new LayoutMutationContext(LevelMutationSurface.forLevel(services.currentLevel()),
                level::applyMutationEffects);
        services.zoneLayoutMutationPipeline().applyImmediately(mutation -> {
            DezFinalBackgroundEvents.update(state, DezFinalCamera.nativeCopyX(camera), oldPlaneX,
                    new DezFinalBackgroundEvents.Surface() {
                        public int descriptor(int x, int y) { return bossDescriptor(level, x, y); }
                        public void writeChunk(int column, int row, int chunk) {
                            // Native writes change layout immediately, then draw selected rows.
                            // Publishing a full automatic redraw would destroy retained cells.
                            level.applyMutationEffects(mutation.surface()
                                    .setBlockInMapWithoutRedraw(0, column, row, chunk));
                        }
                        public boolean spawnOpeningCollapse() {
                            return objects.createDynamicObject(DezFinalArenaBreakup::opening) != null;
                        }
                        public boolean spawnChaseCollapse(int x) {
                            return objects.createDynamicObject(() -> DezFinalArenaBreakup.chase(x)) != null;
                        }
                        public void updateLaser() { DezFinalLaserArt.update(services, state); }
                    });
            return MutationEffects.NONE;
        }, context);
        state.publishDisplayedWindow();
        // Native KosM work belongs to the global queue, including after the root retires.
        state.art().service(services);
        var player = services.playerQuery().mainPlayerOrNull();
        state.screenShake().setup(levelFrameCounter, player != null && player.getDead());
    }

    private static int arenaDescriptor(LevelManager level, int x, int y) {
        // The same ScreenInit pointer writes alias BG rows 4 and 31 to FG row 0.
        int sourceY = y & 0xFFF;
        if ((sourceY >>> 7) == 4 || (sourceY >>> 7) == 31)
            return level.getForegroundTileDescriptorAtWorld(x & 0xFFFF, sourceY & 0x7F);
        return level.getBackgroundTileDescriptorAtWorld(x & 0xFFFF, sourceY);
    }

    private static int bossDescriptor(LevelManager level, int x, int y) {
        // DEZ3_ScreenInit copies FG row-0's pointer to FG rows 4 and 31. The engine
        // stores decoded chunk grids, so resolve these native aliases at the draw read.
        int sourceY = y & 0xFFF;
        if ((sourceY >>> 7) == 4 || (sourceY >>> 7) == 31) sourceY &= 0x7F;
        return level.getForegroundTileDescriptorAtWorld(x & 0xFFFF, sourceY);
    }
}

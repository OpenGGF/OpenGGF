package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code LRZ1_ScreenEvent}'s chunk edits ({@code loc_56B2C}/{@code loc_56B54}) rewrite a few
 * foreground layout cells and redraw the screen. They must redraw only those cells' tiles:
 * rebuilding the whole level image was a 10-60 ms frame on the LRZ1 route. The retained
 * image must still equal a from-scratch rebuild of the edited layout.
 *
 * <p>Lives in {@code com.openggf.level} to read the tilemap manager's package-private
 * build counters and drive the render-side ensure that headless stepping does not.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrz1ChunkEditForegroundRedraw {

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void negativeRequestRedrawsOnlyTheEditedCells() {
        assertRequestRedrawsCellsInPlace(-1);
    }

    @Test
    void positiveRequestRedrawsOnlyTheEditedCell() {
        assertRequestRedrawsCellsInPlace(0x00FF);
    }

    @Test
    void effectsBeyondAForegroundRedrawKeepTheirOrdinaryHandling() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LevelManager manager = GameServices.level();
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        manager.ensureForegroundTilemapData();
        int fullBuilds = tilemaps.foregroundFullBuildCount;
        var sink = LevelLayoutCellRedrawAccess.effectSinkForCell(manager, 0, 29, 14);
        sink.accept(MutationEffects.redrawAllTilemaps());
        manager.ensureForegroundTilemapData();
        assertEquals(fullBuilds + 1, tilemaps.foregroundFullBuildCount,
                "a whole-tilemap redraw request still rebuilds the foreground");
        // A pending full rebuild absorbs a later cell request instead of patching a stale image.
        manager.invalidateForegroundTilemap();
        sink.accept(MutationEffects.foregroundRedraw());
        manager.ensureForegroundTilemapData();
        assertEquals(fullBuilds + 2, tilemaps.foregroundFullBuildCount);
        assertEquals(0, tilemaps.foregroundCellRedrawCount);
    }

    @Test
    void cellWritesThroughTheMutationPipelineMatchAFullRebuild() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LevelManager manager = GameServices.level();
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        manager.ensureForegroundTilemapData();
        int fullBuilds = tilemaps.foregroundFullBuildCount;
        Level level = manager.getCurrentLevel();
        // Edge and interior cells, including the last column and row of the layout.
        int lastColumn = tilemaps.getForegroundTilemapWidthTiles() * 8 / level.getBlockPixelSize() - 1;
        int lastRow = tilemaps.getForegroundTilemapHeightTiles() * 8 / level.getBlockPixelSize() - 1;
        int[][] cells = {{0, 0}, {lastColumn, lastRow}, {12, 7}};
        for (int[] cell : cells) {
            LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                    LevelLayoutCellRedrawAccess.effectSinkForCell(manager, 0, cell[0], cell[1]));
            GameServices.zoneLayoutMutationPipeline().applyImmediately(
                    ctx -> ctx.surface().setBlockInMap(0, cell[0], cell[1], 0x44), context);
        }
        manager.ensureForegroundTilemapData();
        assertEquals(fullBuilds, tilemaps.foregroundFullBuildCount);
        assertEquals(1, tilemaps.foregroundCellRedrawCount);
        assertMatchesFullRebuild(manager, tilemaps);
    }

    private static void assertRequestRedrawsCellsInPlace(int request) {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LevelManager manager = GameServices.level();
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        manager.ensureForegroundTilemapData();
        byte[] image = tilemaps.getForegroundTilemapData();
        byte[] before = image.clone();
        int fullBuilds = tilemaps.foregroundFullBuildCount;

        S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow()
                .setChunkEditRequest(request);
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getLrzEventsForTest().update(0, 1);
        manager.ensureForegroundTilemapData();

        assertSame(image, tilemaps.getForegroundTilemapData(), "the level image is patched in place");
        assertEquals(fullBuilds, tilemaps.foregroundFullBuildCount, "no full foreground rebuild");
        assertEquals(1, tilemaps.foregroundCellRedrawCount);
        assertFalse(java.util.Arrays.equals(before, image), "the edit must be visible in the image");
        assertMatchesFullRebuild(manager, tilemaps);
    }

    private static void assertMatchesFullRebuild(LevelManager manager, LevelTilemapManager tilemaps) {
        byte[] retained = tilemaps.getForegroundTilemapData().clone();
        int width = tilemaps.getForegroundTilemapWidthTiles();
        manager.invalidateForegroundTilemap();
        manager.ensureForegroundTilemapData();
        assertEquals(width, tilemaps.getForegroundTilemapWidthTiles());
        assertArrayEquals(retained, tilemaps.getForegroundTilemapData(),
                "the patched image differs from a full rebuild of the edited layout");
    }
}

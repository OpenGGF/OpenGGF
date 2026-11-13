package com.openggf.level;

import com.openggf.game.GameServices;
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

import static com.openggf.level.LayoutRedrawEquivalence.Foreground.CHANGED;
import static com.openggf.level.LayoutRedrawEquivalence.Foreground.UNCHANGED;
import static com.openggf.level.LayoutRedrawEquivalence.assertWriterRedrawsCellsInPlace;
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
    void aWholeTilemapRequestWithoutALayoutChangeRewritesNoCell() {
        LevelManager manager = load();
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED,
                () -> manager.applyMutationEffects(MutationEffects.redrawAllTilemaps()));
    }

    @Test
    void aTileWrittenStraightIntoTheImageIsReplacedByTheNextRedraw() {
        LevelManager manager = load();
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        manager.ensureForegroundTilemapData();
        byte[] before = tilemaps.getForegroundTilemapData().clone();
        int fullBuilds = tilemaps.foregroundFullBuildCount;
        assertTrue(manager.setForegroundTileDescriptorAtWorld(0x120, 0x88, 0x07FF));
        manager.invalidateForegroundTilemap();
        manager.ensureForegroundTilemapData();
        assertEquals(fullBuilds, tilemaps.foregroundFullBuildCount);
        assertArrayEquals(before, tilemaps.getForegroundTilemapData(),
                "a redraw replaces a direct tile write, as a full rebuild would");
    }

    private static LevelManager load() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        return GameServices.level();
    }

    private static void assertRequestRedrawsCellsInPlace(int request) {
        LevelManager manager = load();
        long cells = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> {
            S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow()
                    .setChunkEditRequest(request);
            ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                    .getLrzEventsForTest().update(0, 1);
        });
        assertTrue(cells <= 6, "only the edited cells are rewritten: " + cells);
    }
}

package com.openggf.level;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;

import static com.openggf.level.LayoutRedrawEquivalence.Foreground.CHANGED;
import static com.openggf.level.LayoutRedrawEquivalence.Foreground.EITHER;
import static com.openggf.level.LayoutRedrawEquivalence.Foreground.UNCHANGED;
import static com.openggf.level.LayoutRedrawEquivalence.assertWriterRedrawsCellsInPlace;
import static com.openggf.level.LayoutRedrawEquivalence.invoke;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Each S3K layout writer the all-level sweep caught rebuilding the whole foreground
 * image (7-121 ms a frame) now rewrites only the cells its edit changed. The patched
 * image must equal a full rebuild of the edited layout. Writers are driven through
 * their production entry points; private ones by reflection.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLayoutEditCellRedraw {

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static LevelManager load(int zone, int act) {
        HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        return GameServices.level();
    }

    private static Sonic3kLevelEventManager events() {
        return (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
    }

    /** MGZ2 collapse: {@code applyChunkMutationPair} restores the two quake block definitions. */
    @Test
    void mgz2CollapseBlockRestoresRedrawOnlyCellsPlacingThoseBlocks() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_MGZ, 1);
        var mgz = events().getMgzEventsForTest();
        for (int replaceIndex = 0; replaceIndex <= 8; replaceIndex += 4) {
            int index = replaceIndex;
            assertWriterRedrawsCellsInPlace(manager, CHANGED,
                    () -> invoke(mgz, "applyChunkMutationPair", index));
        }
    }

    /** FBZ1 {@code applyRuntimeCopies}: foreground layout copies between regions. */
    @Test
    void fbz1RuntimeLayoutCopiesRedrawOnlyTheDestinationCells() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_FBZ, 0);
        var fbz = events().getFbzEventsForTest();
        long total = 0;
        for (boolean outdoor : new boolean[]{false, true}) {
            for (int region = 1; region <= 6; region++) {
                var copies = Sonic3kFBZEvents.act1LayoutCopies(region, outdoor);
                int destinationCells = copies.stream().mapToInt(c -> c.width() * c.height()).sum();
                long cells = assertWriterRedrawsCellsInPlace(manager, EITHER,
                        () -> invoke(fbz, "applyRuntimeCopies", copies));
                assertTrue(cells <= destinationCells, "region " + region + " rewrote " + cells + " cells");
                total += cells;
            }
        }
        assertTrue(total > 0, "the region swaps must change the foreground");
    }

    /** LBZ2 entry corridor ({@code LBZ2_LayoutMod}) copy. */
    @Test
    void lbz2EntryCorridorCopyRedrawsInPlace() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_LBZ, 1);
        assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> events().getLbzEvents().applyLbz2EntryCorridorMod());
    }

    /** LBZ2 transition layout: cell writes plus an in-place block-definition rotation. */
    @Test
    void lbz2TransitionLayoutWithBlockRotationRedrawsInPlace() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_LBZ, 1);
        assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> events().getLbzEvents().applyLbz2TransitionLayout());
    }

    /** SOZ2 {@code openAct2ForegroundPassage} (sub_5622C) and the background-only column clear. */
    @Test
    void soz2PassageRedrawsInPlaceAndBackgroundClearLeavesForegroundUntouched() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_SOZ, 1);
        var soz = events().getSozEvents();
        assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> invoke(soz, "openAct2ForegroundPassage"));
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED, () -> invoke(soz, "clearAct2BackgroundColumns"));
    }

    /** SOZ1 door-row replacement ({@code replaceDoorRow}) writes layer 1 only. */
    @Test
    void soz1DoorRowReplacementRewritesNoForegroundCell() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_SOZ, 0);
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED, () -> GameServices.zoneLayoutMutationPipeline()
                .queue(context -> {
                    context.surface().setBlockInMap(1, 13, 6, 0xFD);
                    return MutationEffects.redrawAllTilemaps();
                }));
    }

    /**
     * SOZ1 custom block load ({@code claimReadyResources}, queued by the act 1 background
     * exit fade): the prepared Kos payload restores chunk definitions $1D4.. through
     * {@code restoreChunkState}. The intent is reproduced here with the ROM payload
     * because its readiness is owned by the hardware-timing queue. The chunks belong to
     * the background, so the foreground keeps every cell while the chunk table changes.
     */
    @Test
    void soz1BackgroundChunkLoadRewritesNoForegroundCell() throws Exception {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_SOZ, 0);
        byte[] blocks = KosinskiReader.decompress(Channels.newChannel(new ByteArrayInputStream(
                GameServices.rom().getRom().readBytes(0x1AD68E, 0x2000))), false);
        Level level = manager.getCurrentLevel();
        int chunkCount = blocks.length / 8;
        int[][] before = new int[chunkCount][];
        for (int i = 0; i < chunkCount; i++) before[i] = level.getChunk(0x1D4 + i).saveState();
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED, () -> GameServices.zoneLayoutMutationPipeline()
                .queue(context -> {
                    for (int offset = 0; offset < blocks.length; offset += 8) {
                        int chunkIndex = 0x1D4 + offset / 8;
                        Chunk chunk = manager.getCurrentLevel().getChunk(chunkIndex);
                        int[] values = new int[6];
                        for (int word = 0; word < 4; word++) values[word] =
                                ((blocks[offset + word * 2] & 255) << 8) | (blocks[offset + word * 2 + 1] & 255);
                        values[4] = chunk.getSolidTileIndex();
                        values[5] = chunk.getSolidTileAltIndex();
                        context.surface().restoreChunkState(chunkIndex, values);
                    }
                    return MutationEffects.redrawAllTilemaps();
                }));
        boolean changed = false;
        for (int i = 0; i < chunkCount; i++) {
            changed |= !java.util.Arrays.equals(before[i], manager.getCurrentLevel().getChunk(0x1D4 + i).saveState());
        }
        assertTrue(changed, "the payload must change the chunk table");
    }

    /** AIZ2 {@code applyBattleshipTerrain}: a chunk-table overlay, then a direct whole-tilemap invalidation. */
    @Test
    void aiz2BattleshipChunkOverlayRedrawsInPlace() throws Exception {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_AIZ, 1);
        var rom = GameServices.rom().getRom();
        byte[] terrain = KosinskiReader.decompress(Channels.newChannel(new ByteArrayInputStream(
                rom.readBytes(Sonic3kConstants.AIZ2_16X16_BOMBERSHIP_ADDR, 0x4000))), false);
        byte[] tiles = KosinskiReader.decompressModuled(
                rom.readBytes(Sonic3kConstants.AIZ2_8X8_BOMBERSHIP_ADDR, 0x8000), 0);
        var aiz = events().getAizEventsForTest();
        assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> invoke(aiz, "applyBattleshipTerrain", terrain, tiles));
    }

    /** A rewind restore swaps the edited layout back; the redraw must match the restored layout. */
    @Test
    void rewindRestoreOfALayoutEditRedrawsTheRestoredCells() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_MGZ, 1);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var mgz = events().getMgzEventsForTest();
        manager.ensureForegroundTilemapData();
        var before = registry.capture();
        assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> invoke(mgz, "applyChunkMutationPair", 8));
        assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> registry.restore(before));
    }

    /**
     * Runtime art loads grow the chunk table (LBZ2's Death Egg appends chunks); an index
     * past the table draws nothing. Shrinking and regrowing the table redraws exactly the
     * cells placing the affected chunks.
     */
    @Test
    void chunkTableShrinkAndGrowthRedrawTheCellsUsingTheAffectedChunks() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_MGZ, 0);
        AbstractLevel level = (AbstractLevel) manager.getCurrentLevel();
        Chunk[] full = level.chunksReference();
        long shrunk = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> {
            level.replaceChunks(java.util.Arrays.copyOf(full, full.length - 64));
            manager.applyMutationEffects(MutationEffects.redrawAllTilemaps());
        });
        long regrown = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> {
            level.replaceChunks(full);
            manager.applyMutationEffects(MutationEffects.redrawAllTilemaps());
        });
        assertEquals(shrunk, regrown, "regrowing restores exactly the cells the shrink emptied");
    }

    /** A layout write the pipeline publishes to any layer is redrawn with layer accuracy. */
    @Test
    void pipelineCellWritesOnEitherLayerMatchAFullRebuild() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_LRZ, 0);
        Level level = manager.getCurrentLevel();
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        manager.ensureForegroundTilemapData();
        // Edge and interior cells, including the last column and row of the layout.
        int lastColumn = tilemaps.getForegroundTilemapWidthTiles() * 8 / level.getBlockPixelSize() - 1;
        int lastRow = tilemaps.getForegroundTilemapHeightTiles() * 8 / level.getBlockPixelSize() - 1;
        long cells = assertWriterRedrawsCellsInPlace(manager, CHANGED, () -> {
            for (int[] cell : new int[][]{{0, 0}, {lastColumn, lastRow}, {12, 7}}) {
                LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                        manager::applyMutationEffects);
                GameServices.zoneLayoutMutationPipeline().applyImmediately(
                        ctx -> ctx.surface().setBlockInMap(0, cell[0], cell[1], 0x44), context);
            }
        });
        assertTrue(cells <= 3);
        assertWriterRedrawsCellsInPlace(manager, UNCHANGED, () -> {
            LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                    manager::applyMutationEffects);
            GameServices.zoneLayoutMutationPipeline().applyImmediately(
                    ctx -> ctx.surface().setBlockInMap(1, 2, 1, 0x10), context);
        });
    }
}

package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A level load draws the foreground image column by column instead of in one frame.
 * Oracles: at every step the columns any draw can sample equal a whole-level build
 * of the same layout; once every column is drawn the image equals that build byte for
 * byte; layout edits, rewind restores and silent layout writes during the stream end
 * in exactly the image the whole-level build plus the same edits would hold; and a
 * reload of the same act keeps the drawn image and redraws only changed cells.
 *
 * <p>Headless runs have no GPU renderer, so these drive the stream through the
 * {@link LevelTilemapManager#streamWithoutRendererForTests} seam and call the same
 * coverage owner {@code TilemapGpuRenderer} consults before each foreground draw.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestStreamedForegroundBuild {

    @BeforeEach
    void streamHeadless() {
        LevelTilemapManager.streamWithoutRendererForTests = true;
    }

    @AfterEach
    void cleanup() {
        LevelTilemapManager.streamWithoutRendererForTests = false;
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static LevelManager load(int zone, int act) {
        HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        return GameServices.level();
    }

    /** Starts a fresh streamed build with the camera at world X {@code cameraX}. */
    private static LevelTilemapManager startStream(LevelManager manager, int cameraX) {
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        tilemaps.invalidateAllTilemaps();
        // A whole-level rebuild request on an existing image is a fresh build, not a redraw.
        tilemaps.resetState();
        tilemaps.setForegroundStreamFocus(cameraX, 320);
        tilemaps.ensureForegroundTilemapData(manager::getBlockAtPosition, manager.zoneFeatureProvider,
                manager.currentZone, manager.parallaxManager, manager.verticalWrapEnabled);
        assertTrue(tilemaps.isForegroundStreaming(), "a large level must stream");
        return tilemaps;
    }

    private static void ensure(LevelManager manager) {
        manager.getTilemapManager().ensureForegroundTilemapData(manager::getBlockAtPosition,
                manager.zoneFeatureProvider, manager.currentZone, manager.parallaxManager,
                manager.verticalWrapEnabled);
    }

    /** Runs stream slices until every column is drawn; returns the number of slices. */
    private static int drainStream(LevelManager manager) {
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        int slices = 0;
        while (tilemaps.isForegroundStreaming()) {
            ensure(manager);
            tilemaps.advanceForegroundStream();
            slices++;
            assertTrue(slices < 10_000, "the stream must finish");
        }
        return slices;
    }

    /** Asserts tile columns {@code firstPx..lastPx} (wrapping) of {@code image} equal {@code full}. */
    private static void assertColumnsEqual(byte[] full, byte[] image, int widthTiles, int heightTiles,
                                           int firstPx, int lastPx, String what) {
        int widthPx = widthTiles * 8;
        for (long x = firstPx; x <= lastPx; x += 8) {
            int tileX = Math.floorMod((int) x, widthPx) / 8;
            for (int tileY = 0; tileY < heightTiles; tileY++) {
                int offset = (tileY * widthTiles + tileX) * 4;
                if (full[offset] != image[offset] || full[offset + 1] != image[offset + 1]
                        || full[offset + 3] != image[offset + 3]) {
                    fail(what + ": tile (" + tileX + ", " + tileY + ") was sampled before it was drawn");
                }
            }
        }
    }

    /**
     * ICZ1 is the largest load the sweep measured (a 50 ms whole-level build). The load
     * frame draws only the camera's columns, and the image completes over later slices.
     */
    @Test
    void streamedImageEqualsAWholeLevelBuildOnceComplete() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        byte[] full = manager.buildForegroundImageForComparison();
        LevelTilemapManager tilemaps = startStream(manager, 0x1800);
        byte[] image = tilemaps.getForegroundTilemapData();
        int widthTiles = tilemaps.getForegroundTilemapWidthTiles();
        int heightTiles = tilemaps.getForegroundTilemapHeightTiles();
        int columns = tilemaps.foregroundStreamedColumnCount + tilemaps.foregroundCoveredColumnCount;
        assertTrue(columns > 0 && columns < widthTiles * 8 / manager.getCurrentLevel().getBlockPixelSize() / 4,
                "the load frame draws only the columns around the camera, drew " + columns);
        assertFalse(Arrays.equals(full, image), "the rest of the level is not drawn yet");
        assertColumnsEqual(full, image, widthTiles, heightTiles, 0x1800 - 256, 0x1800 + 320 + 256,
                "load frame window");

        int slices = drainStream(manager);
        assertTrue(slices > 3, "the remainder spreads over several frames, took " + slices);
        assertSame(image, tilemaps.getForegroundTilemapData(), "slices draw into the same image");
        assertArrayEquals(full, image, "the completed stream equals a whole-level build");
    }

    /**
     * Every draw asks for the columns it can sample first: camera steps, a teleport across
     * the level, a wide picture, a window straddling the right edge (the shader repeats
     * the image) and per-line scroll far outside the camera. After each request those
     * columns already equal the whole-level build, while the rest is still undrawn.
     */
    @Test
    void everyDrawSamplesOnlyDrawnColumns() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        byte[] full = manager.buildForegroundImageForComparison();
        LevelTilemapManager tilemaps = startStream(manager, 0x200);
        byte[] image = tilemaps.getForegroundTilemapData();
        int widthTiles = tilemaps.getForegroundTilemapWidthTiles();
        int heightTiles = tilemaps.getForegroundTilemapHeightTiles();
        int widthPx = widthTiles * 8;
        int[][] draws = {
                {0x210, 0x210 + 320},              // next frame, one step right
                {widthPx - 900, widthPx - 900 + 320}, // teleport (debug, editor jump, seamless offset)
                {0x3000, 0x3000 + 800},            // wide picture
                {widthPx - 100, widthPx + 300},    // window over the right edge
                {-260, 60},                        // window over the left edge
                {0x2400 - 16, 0x2400 + 320 + 16},  // per-line scroll range far from the camera
        };
        for (int[] draw : draws) {
            assertTrue(tilemaps.isForegroundStreaming(), "the image must still be streaming");
            tilemaps.foregroundCoverage.coverColumns(image, draw[0], draw[1]);
            assertColumnsEqual(full, image, widthTiles, heightTiles, draw[0], draw[1],
                    "draw " + draw[0] + ".." + draw[1]);
        }
        // A draw whose sampling is not known covers everything.
        tilemaps.foregroundCoverage.coverAll(image);
        assertFalse(tilemaps.isForegroundStreaming());
        assertArrayEquals(full, image);
    }

    /**
     * A directed (cinematic) view clones the native image and has no record or coverage
     * owner of its own, so the copy must be the complete image.
     */
    @Test
    void cinematicCopyOfAStreamingImageIsComplete() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        byte[] full = manager.buildForegroundImageForComparison();
        LevelTilemapManager tilemaps = startStream(manager, 0x200);
        LevelTilemapManager copy = tilemaps.cinematicBackgroundCopy();
        assertFalse(tilemaps.isForegroundStreaming());
        assertArrayEquals(full, copy.getForegroundTilemapData());
    }

    /** A draw for a different array (a stale registration) draws nothing. */
    @Test
    void coverageForAnotherArrayDrawsNothing() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        LevelTilemapManager tilemaps = startStream(manager, 0x200);
        int drawn = tilemaps.foregroundCoveredColumnCount;
        tilemaps.foregroundCoverage.coverColumns(new byte[16], 0x3000, 0x3200);
        tilemaps.foregroundCoverage.coverAll(new byte[16]);
        assertEquals(drawn, tilemaps.foregroundCoveredColumnCount);
        assertTrue(tilemaps.isForegroundStreaming());
    }

    /**
     * A layout write with a redraw request during the stream, both inside the drawn
     * window and in a column not drawn yet: the completed image equals a whole-level build
     * of the edited layout.
     */
    @Test
    void layoutEditsDuringTheStreamAppearInTheCompletedImage() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        LevelTilemapManager tilemaps = startStream(manager, 0);
        Level level = manager.getCurrentLevel();
        int lastColumn = tilemaps.getForegroundTilemapWidthTiles() * 8 / level.getBlockPixelSize() - 1;
        assertTrue(tilemaps.isForegroundStreaming());
        for (int[] cell : new int[][]{{1, 3}, {lastColumn, 2}, {lastColumn - 3, 5}}) {
            LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                    manager::applyMutationEffects);
            GameServices.zoneLayoutMutationPipeline().applyImmediately(
                    ctx -> ctx.surface().setBlockInMap(0, cell[0], cell[1], 0x44), context);
        }
        ensure(manager);
        assertTrue(tilemaps.isForegroundStreaming(), "the edit must not force the whole level in one frame");
        drainStream(manager);
        assertArrayEquals(manager.buildForegroundImageForComparison(), tilemaps.getForegroundTilemapData(),
                "the completed image differs from a whole-level build of the edited layout");
    }

    /**
     * A layout written without a redraw request (a ROM script editing layout RAM) leaves a
     * whole-level image untouched until a redraw; the stream must do the same, so columns
     * drawn after the write show the layout of the load.
     */
    @Test
    void silentLayoutWritesDuringTheStreamDoNotReachTheImage() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        byte[] atLoad = manager.buildForegroundImageForComparison();
        LevelTilemapManager tilemaps = startStream(manager, 0);
        Level level = manager.getCurrentLevel();
        int lastColumn = tilemaps.getForegroundTilemapWidthTiles() * 8 / level.getBlockPixelSize() - 1;
        int before = level.getMap().getValue(0, lastColumn, 2) & 0xFF;
        level.getMap().setValue(0, lastColumn, 2, (byte) (before == 0x44 ? 0x45 : 0x44));
        drainStream(manager);
        assertArrayEquals(atLoad, tilemaps.getForegroundTilemapData(),
                "an undrawn column must show the layout the image was started from");
        // The next redraw request then shows the write, as for a whole-level image.
        manager.invalidateForegroundTilemap();
        ensure(manager);
        assertArrayEquals(manager.buildForegroundImageForComparison(), tilemaps.getForegroundTilemapData());
    }

    /** A rewind restore during the stream swaps an edited layout back. */
    @Test
    void rewindRestoreDuringTheStreamCompletesToTheRestoredLayout() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        LevelTilemapManager tilemaps = startStream(manager, 0);
        byte[] unedited = manager.buildForegroundImageForComparison();
        var snapshot = registry.capture();
        Level level = manager.getCurrentLevel();
        int lastColumn = tilemaps.getForegroundTilemapWidthTiles() * 8 / level.getBlockPixelSize() - 1;
        LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                manager::applyMutationEffects);
        GameServices.zoneLayoutMutationPipeline().applyImmediately(
                ctx -> ctx.surface().setBlockInMap(0, lastColumn, 4, 0x44), context);
        ensure(manager);
        registry.restore(snapshot);
        manager = GameServices.level();
        ensure(manager);
        drainStream(manager);
        assertArrayEquals(manager.buildForegroundImageForComparison(),
                manager.getTilemapManager().getForegroundTilemapData());
        assertArrayEquals(unedited, manager.getTilemapManager().getForegroundTilemapData(),
                "the restore must bring back the layout before the edit");
    }

    /**
     * Gameplay readers of the image (S3K AIZ/LBZ tile copies) read and write single tiles:
     * the column under the tile is drawn first, and the stream carries on.
     */
    @Test
    void tileReadsAndWritesDrawTheirColumnFirst() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_ICZ, 0);
        byte[] full = manager.buildForegroundImageForComparison();
        LevelTilemapManager tilemaps = startStream(manager, 0);
        int widthTiles = tilemaps.getForegroundTilemapWidthTiles();
        int x = widthTiles * 8 - 40;
        int y = 0x300;
        int tile = ((y / 8) * widthTiles + x / 8) * 4;
        int expected = (full[tile] & 0xFF) | ((full[tile + 1] & 7) << 8) | (((full[tile + 1] >> 3) & 3) << 13)
                | ((full[tile + 1] & 0x20) != 0 ? 0x800 : 0) | ((full[tile + 1] & 0x40) != 0 ? 0x1000 : 0)
                | ((full[tile + 1] & 0x80) != 0 ? 0x8000 : 0);
        assertEquals(expected, manager.getForegroundTileDescriptorFromTilemapAtWorld(x, y));
        assertTrue(manager.setForegroundTileDescriptorAtWorld(x - 64, y, 0x2123));
        assertTrue(tilemaps.isForegroundStreaming(), "single-tile access must not finish the stream");
        drainStream(manager);
        assertEquals(0x2123, manager.getForegroundTileDescriptorFromTilemapAtWorld(x - 64, y));
        int written = ((y / 8) * widthTiles + (x - 64) / 8) * 4;
        byte[] expectedImage = full.clone();
        System.arraycopy(tilemaps.getForegroundTilemapData(), written, expectedImage, written, 4);
        assertArrayEquals(expectedImage, tilemaps.getForegroundTilemapData());
    }

    /**
     * A death reload of the same act installs a freshly decoded level with the same
     * geometry. The drawn image is kept and only cells whose layout differs are redrawn,
     * instead of a whole-level build; a layout edit made during the life is undone.
     */
    @Test
    void sameActReloadKeepsTheImageAndRedrawsOnlyChangedCells() {
        LevelManager manager = load(Sonic3kZoneIds.ZONE_HCZ, 0);
        manager.ensureForegroundTilemapData();
        drainStream(manager);
        LevelTilemapManager first = manager.getTilemapManager();
        byte[] image = first.getForegroundTilemapData();
        assertNotNull(image);
        byte[] beforeEdit = image.clone();
        Level level = manager.getCurrentLevel();
        LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                manager::applyMutationEffects);
        GameServices.zoneLayoutMutationPipeline().applyImmediately(
                ctx -> ctx.surface().setBlockInMap(0, 5, 3, 0x44), context);
        manager.ensureForegroundTilemapData();
        assertFalse(Arrays.equals(beforeEdit, image), "the edit is drawn during the life");

        manager.respawnPlayer();
        LevelTilemapManager second = manager.getTilemapManager();
        assertNotSame(first, second, "a reload installs a new tilemap manager");
        assertNotSame(level, manager.getCurrentLevel(), "a reload decodes a new level");
        assertEquals(1, second.foregroundAdoptedImageCount);
        int fullBuilds = second.foregroundFullBuildCount;
        manager.ensureForegroundTilemapData();
        assertSame(image, second.getForegroundTilemapData(), "the drawn image is kept");
        assertFalse(second.isForegroundStreaming(), "the kept image was complete");
        assertEquals(fullBuilds, second.foregroundFullBuildCount, "no whole-level build");
        assertEquals(1, second.foregroundCellRedrawCount);
        assertTrue(second.foregroundRedrawnCellCount >= 1 && second.foregroundRedrawnCellCount <= 2,
                "only the edited cell is redrawn, redrew " + second.foregroundRedrawnCellCount);
        assertArrayEquals(manager.buildForegroundImageForComparison(), image);
        assertArrayEquals(beforeEdit, image, "the reloaded layout no longer has the edit");
    }
}

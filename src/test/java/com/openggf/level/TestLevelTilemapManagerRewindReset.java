package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the post-rewind-restore reset hooks on {@link LevelTilemapManager}.
 * <p>
 * After a rewind restore the AIZ2 FG ship-loop "ring" and the BG incremental-shift
 * baseline are stale (they were filled incrementally against the discarded forward
 * timeline). Both are pure functions of (restored camera-X + static layout), so the
 * reset hooks must invalidate the seed/baseline latches and mark the tilemaps dirty
 * so the next {@code ensure*TilemapData} performs a full rebuild rather than the
 * cheap incremental fill/shift.
 * <p>
 * Headless: uses {@code GraphicsManager.initHeadless()} (no OpenGL, no GPU upload)
 * and a synthetic {@link StubLevel} (no ROM). Asserts private latch fields via
 * reflection because they are the contract the integrator relies on.
 */
public class TestLevelTilemapManagerRewindReset {

    private static final int BLOCK_PX = 128;
    private static final int MAP_WIDTH_BLOCKS = 16;
    private static final int MAP_HEIGHT_BLOCKS = 2;
    private static final int BG_WIDTH_PX = MAP_WIDTH_BLOCKS * BLOCK_PX;
    private static final int BG_HEIGHT_PX = MAP_HEIGHT_BLOCKS * BLOCK_PX;
    private static final int BG_CONTIGUOUS_PX = 1024;
    private static final int PERIOD_PX = 512;

    private GraphicsManager graphicsManager;
    private StubLevel level;
    private LevelGeometry geometry;
    private LevelTilemapManager.BlockLookup blockLookup;

    @BeforeEach
    public void setUp() {
        GraphicsManager.destroyForReinit();
        TestEnvironment.resetAll();
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();

        level = new StubLevel(MAP_HEIGHT_BLOCKS);
        geometry = new LevelGeometry(level,
                BG_WIDTH_PX, BG_HEIGHT_PX,
                BG_WIDTH_PX, BG_CONTIGUOUS_PX, BG_HEIGHT_PX,
                BLOCK_PX, 8);
        blockLookup = lookupFor(level, BG_WIDTH_PX, BG_HEIGHT_PX);
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    // ── BG incremental-shift baseline ─────────────────────────────────────────

    @Test
    public void resetBgIncrementalShiftBaselineInvalidatesBuildSnapshotAndForcesFullRebuild()
            throws Exception {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false, false);
        LevelTilemapManager manager = new LevelTilemapManager(geometry, graphicsManager, null);
        manager.setCurrentBgPeriodWidth(PERIOD_PX);
        manager.setBgTilemapBaseX(496);
        manager.ensureBackgroundTilemapData(blockLookup, zfp, 0, null, false);
        assertNotNull(manager.getBackgroundTilemapData());

        // A clean window step would normally take the cheap incremental shift path.
        manager.requestBgWindowBaseX(512);
        assertTrue(getBoolean(manager, "bgWindowShiftCandidate"),
                "fixture sanity: a clean window step is a shift candidate");
        assertTrue(getBoolean(manager, "bgLastBuildValid"),
                "fixture sanity: full build recorded a valid snapshot");

        // Rewind restore: the retained bytes reflect the discarded forward window.
        manager.resetBgIncrementalShiftBaseline();

        assertFalse(getBoolean(manager, "bgLastBuildValid"),
                "shift snapshot must be invalidated");
        assertFalse(getBoolean(manager, "bgWindowShiftCandidate"),
                "shift candidacy must be cleared so the next build is a full rebuild");
        assertTrue(manager.isBackgroundTilemapDirty(),
                "background tilemap must be marked dirty");

        // Next ensure must take the FULL rebuild path, not the incremental shift.
        int fullBefore = manager.bgFullRebuildCount;
        int shiftBefore = manager.bgIncrementalShiftCount;
        manager.ensureBackgroundTilemapData(blockLookup, zfp, 0, null, false);
        assertEquals(fullBefore + 1, manager.bgFullRebuildCount,
                "post-reset ensure must full-rebuild");
        assertEquals(shiftBefore, manager.bgIncrementalShiftCount,
                "post-reset ensure must NOT take the incremental shift path");
    }

    // ── FG ship-loop ring ─────────────────────────────────────────────────────

    @Test
    public void resetForegroundRingForRewindClearsSeedAndForcesFullRebuild() throws Exception {
        ZoneFeatureProvider zfp = new StubZoneFeatures(false, false, true);
        LevelTilemapManager manager = new LevelTilemapManager(geometry, graphicsManager, null);
        // Push a camera position so the ring seeds its visible columns.
        manager.setForegroundRingCamera(800, 320);
        manager.ensureForegroundTilemapData(blockLookup, zfp, 0, null, false);

        assertNotNull(manager.getForegroundTilemapData());
        assertTrue(getBoolean(manager, "foregroundRingSeeded"),
                "fixture sanity: the ring must seed on the first fgWrap build");
        assertFalse(manager.isForegroundTilemapDirty(),
                "fixture sanity: the ring build clears the dirty flag");
        assertNotNull(getField(manager, "lastForegroundWrap"),
                "fixture sanity: the wrap-state latch is set after a build");

        // Rewind restore: ring cells retain forest columns from the discarded timeline.
        manager.resetForegroundRingForRewind();

        assertTrue(manager.isForegroundTilemapDirty(),
                "foreground tilemap must be marked dirty for a full rebuild");
        assertFalse(getBoolean(manager, "foregroundRingSeeded"),
                "ring seed latch must be cleared so the next build re-seeds");
        assertNull(getField(manager, "lastForegroundWrap"),
                "wrap-state latch must be cleared so the next ensure forces the full build");

        // Next ensure with fgWrap still true must re-seed the ring (full build path).
        manager.ensureForegroundTilemapData(blockLookup, zfp, 0, null, false);
        assertTrue(getBoolean(manager, "foregroundRingSeeded"),
                "post-reset ensure must re-seed the ring");
        assertFalse(manager.isForegroundTilemapDirty(),
                "post-reset ensure must clear the dirty flag again");
    }

    // ── Combined convenience hook ─────────────────────────────────────────────

    @Test
    public void resetTilemapsForRewindRestoreResetsBothFgRingAndBgBaseline() throws Exception {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false, true);
        LevelTilemapManager manager = new LevelTilemapManager(geometry, graphicsManager, null);
        manager.setCurrentBgPeriodWidth(PERIOD_PX);
        manager.setBgTilemapBaseX(496);
        manager.setForegroundRingCamera(800, 320);
        manager.ensureBackgroundTilemapData(blockLookup, zfp, 0, null, false);
        manager.ensureForegroundTilemapData(blockLookup, zfp, 0, null, false);

        assertTrue(getBoolean(manager, "bgLastBuildValid"));
        assertTrue(getBoolean(manager, "foregroundRingSeeded"));

        manager.resetTilemapsForRewindRestore();

        // BG baseline reset
        assertFalse(getBoolean(manager, "bgLastBuildValid"));
        assertFalse(getBoolean(manager, "bgWindowShiftCandidate"));
        assertTrue(manager.isBackgroundTilemapDirty());
        // FG ring reset
        assertFalse(getBoolean(manager, "foregroundRingSeeded"));
        assertTrue(manager.isForegroundTilemapDirty());
        assertNull(getField(manager, "lastForegroundWrap"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static LevelTilemapManager.BlockLookup lookupFor(StubLevel level, int widthPx, int heightPx) {
        return (layer, x, y) -> {
            int wrappedX = ((x % widthPx) + widthPx) % widthPx;
            int wrappedY = ((y % heightPx) + heightPx) % heightPx;
            int blockIndex = level.getMap().getValue(layer, wrappedX / BLOCK_PX, wrappedY / BLOCK_PX) & 0xFF;
            if (blockIndex >= level.getBlockCount()) {
                return null;
            }
            return level.getBlock(blockIndex);
        };
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        return (Boolean) getField(target, name);
    }

    // ── Stubs ───────────────────────────────────────────────────────────────

    /**
     * Synthetic level with deterministic, varied content for both FG and BG layers,
     * so a wrong column or stale ring cell would change bytes. Mirrors the fixture in
     * {@code TestIncrementalBgTilemapWindow} (single map height, no tall column).
     */
    private static final class StubLevel extends AbstractLevel {

        StubLevel(int mapHeightBlocks) {
            super(0);
            palettes = new Palette[4];
            for (int i = 0; i < 4; i++) {
                palettes[i] = new Palette();
            }
            patternCount = 64;
            patterns = new Pattern[0];

            chunkCount = 48;
            chunks = new Chunk[chunkCount];
            for (int c = 0; c < chunkCount; c++) {
                Chunk chunk = new Chunk();
                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) {
                        int patternIndex = (c * 4 + py * 2 + px) % 60 + 2;
                        int desc = patternIndex
                                | ((c % 4) << 13)
                                | ((c & 1) != 0 ? 0x800 : 0)
                                | ((c & 2) != 0 ? 0x1000 : 0)
                                | ((c % 5 == 0) ? 0x8000 : 0);
                        chunk.setPatternDesc(px, py, new PatternDesc(desc));
                    }
                }
                chunks[c] = chunk;
            }

            blockCount = 24;
            blocks = new Block[blockCount];
            for (int b = 0; b < blockCount; b++) {
                Block block = new Block(8);
                for (int cy = 0; cy < 8; cy++) {
                    for (int cx = 0; cx < 8; cx++) {
                        int chunkIndex = (b * 7 + cy * 8 + cx) % 52;
                        int descBits = chunkIndex
                                | (((b + cx) % 3 == 0) ? 0x400 : 0)
                                | (((b + cy) % 4 == 0) ? 0x800 : 0);
                        block.setChunkDesc(cx, cy, new ChunkDesc(descBits));
                    }
                }
                blocks[b] = block;
            }

            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            // layer 0 (FG) and layer 1 (BG) both populated from the same map values.
            map = new Map(2, MAP_WIDTH_BLOCKS, mapHeightBlocks);
            for (int my = 0; my < mapHeightBlocks; my++) {
                for (int mx = 0; mx < MAP_WIDTH_BLOCKS; mx++) {
                    int blockIndex = (mx * 5 + my * 11) % 26;
                    if (blockIndex >= blockCount) {
                        blockIndex = 0xFF;
                    }
                    map.setValue(0, mx, my, (byte) blockIndex);
                    map.setValue(1, mx, my, (byte) blockIndex);
                }
            }
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = BG_WIDTH_PX;
            minY = 0;
            maxY = mapHeightBlocks * BLOCK_PX;
        }

        @Override
        public SolidTile getSolidTile(int index) {
            return null;
        }

        @Override
        public List<ObjectSpawn> getObjects() {
            return List.of();
        }

        @Override
        public List<RingSpawn> getRings() {
            return List.of();
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }
    }

    /** ZoneFeatureProvider with configurable BG and FG wrap behavior. */
    private static final class StubZoneFeatures implements ZoneFeatureProvider {
        private final boolean bgWraps;
        private final boolean linearOverflow;
        private final boolean fgWraps;

        StubZoneFeatures(boolean bgWraps, boolean linearOverflow, boolean fgWraps) {
            this.bgWraps = bgWraps;
            this.linearOverflow = linearOverflow;
            this.fgWraps = fgWraps;
        }

        @Override
        public boolean bgWrapsHorizontally() {
            return bgWraps;
        }

        @Override
        public boolean foregroundWrapsHorizontally() {
            return fgWraps;
        }

        @Override
        public boolean useLinearBackgroundLayoutOverflow(int zoneIndex) {
            return linearOverflow;
        }

        @Override
        public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) {
        }

        @Override
        public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        }

        @Override
        public void reset() {
        }

        @Override
        public boolean hasCollisionFeatures(int zoneIndex) {
            return false;
        }

        @Override
        public boolean hasWater(int zoneIndex) {
            return false;
        }

        @Override
        public int getWaterLevel(int zoneIndex, int actIndex) {
            return 0;
        }

        @Override
        public void render(Camera camera, int frameCounter) {
        }

        @Override
        public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
            return baseIndex;
        }
    }
}

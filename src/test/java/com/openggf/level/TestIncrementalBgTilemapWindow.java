package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the incremental BG tilemap window shift is byte-identical to a full
 * rebuild for single 16px base-X steps in both directions, and that every
 * unproven precondition (wrap-boundary crossing, multi-column jumps, generic
 * invalidations, non-wrapping zones, runtime tilemap overlay writes) falls
 * back to the full rebuild path.
 */
public class TestIncrementalBgTilemapWindow {

    private static final int BLOCK_PX = 128;
    // BG layout: 16 blocks wide (2048px) x 2 blocks tall (256px).
    private static final int MAP_WIDTH_BLOCKS = 16;
    private static final int MAP_HEIGHT_BLOCKS = 2;
    private static final int BG_WIDTH_PX = MAP_WIDTH_BLOCKS * BLOCK_PX;
    private static final int BG_HEIGHT_PX = MAP_HEIGHT_BLOCKS * BLOCK_PX;
    // Contiguous BG data narrower than the layout so base-X wrapping is exercised.
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

        level = new StubLevel(MAP_HEIGHT_BLOCKS, false);
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

    // ── Equivalence: single-column steps in both directions ────────────────

    @Test
    public void advanceAndRetreatByOneColumnAreByteIdenticalToFullRebuild() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = newManager(496, zfp);
        assertEquals(1, manager.bgFullRebuildCount);
        assertEquals(0, manager.bgIncrementalShiftCount);

        // Advance one column (rightward scroll)
        manager.requestBgWindowBaseX(512);
        ensure(manager, zfp);
        assertEquals(1, manager.bgFullRebuildCount, "advance should take the incremental path");
        assertEquals(1, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(512, zfp), manager.getBackgroundTilemapData(),
                "advance shift must be byte-identical to a full rebuild");

        // Retreat one column (leftward scroll)
        manager.requestBgWindowBaseX(496);
        ensure(manager, zfp);
        assertEquals(1, manager.bgFullRebuildCount, "retreat should take the incremental path");
        assertEquals(2, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(496, zfp), manager.getBackgroundTilemapData(),
                "retreat shift must be byte-identical to a full rebuild");
    }

    @Test
    public void consecutiveAdvancesAcrossManyColumnsStayByteIdentical() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = newManager(0, zfp);
        for (int base = 16; base <= 320; base += 16) {
            manager.requestBgWindowBaseX(base);
            ensure(manager, zfp);
            assertArrayEquals(fullRebuildAt(base, zfp), manager.getBackgroundTilemapData(),
                    "diverged at base " + base);
        }
        assertEquals(1, manager.bgFullRebuildCount);
        assertEquals(20, manager.bgIncrementalShiftCount);
    }

    @Test
    public void linearRowOverflowModeShiftsAreByteIdenticalToFullRebuild() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, true);
        LevelTilemapManager manager = newManager(496, zfp);

        manager.requestBgWindowBaseX(512);
        ensure(manager, zfp);
        assertEquals(1, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(512, zfp), manager.getBackgroundTilemapData());

        manager.requestBgWindowBaseX(496);
        ensure(manager, zfp);
        assertEquals(2, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(496, zfp), manager.getBackgroundTilemapData());
    }

    // ── Fallbacks ───────────────────────────────────────────────────────────

    @Test
    public void wrapBoundaryCrossingFallsBackToFullRebuild() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        // Base 1008 → wrapped offset 1008; base 1024 → wrapped offset 0.
        // The effective offset jump is not a single column, so the shift must decline.
        LevelTilemapManager manager = newManager(1008, zfp);
        manager.requestBgWindowBaseX(1024);
        ensure(manager, zfp);
        assertEquals(2, manager.bgFullRebuildCount, "wrap-boundary step must full-rebuild");
        assertEquals(0, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(1024, zfp), manager.getBackgroundTilemapData());

        // Stepping back across the boundary must also full-rebuild.
        manager.requestBgWindowBaseX(1008);
        ensure(manager, zfp);
        assertEquals(3, manager.bgFullRebuildCount);
        assertEquals(0, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(1008, zfp), manager.getBackgroundTilemapData());
    }

    @Test
    public void multiColumnJumpFallsBackToFullRebuild() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = newManager(496, zfp);
        manager.requestBgWindowBaseX(560); // +64px jump
        ensure(manager, zfp);
        assertEquals(2, manager.bgFullRebuildCount);
        assertEquals(0, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(560, zfp), manager.getBackgroundTilemapData());
    }

    @Test
    public void accumulatedStepsWithoutRebuildFallBackToFullRebuild() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = newManager(496, zfp);
        // Two window steps land before the next ensure. The rejection criterion is
        // not the step count: the shift compares the effective x-query offset against
        // the last BUILT snapshot, and the accumulated net movement (496 -> 528 =
        // +32px) is not exactly one 16px column, so the incremental path must decline.
        manager.requestBgWindowBaseX(512);
        manager.requestBgWindowBaseX(528);
        ensure(manager, zfp);
        assertEquals(2, manager.bgFullRebuildCount);
        assertEquals(0, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(528, zfp), manager.getBackgroundTilemapData());
    }

    @Test
    public void genericInvalidationForcesFullRebuildOnNextWindowStep() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = newManager(496, zfp);

        manager.invalidateAllTilemaps();
        manager.requestBgWindowBaseX(512);
        ensure(manager, zfp);
        assertEquals(2, manager.bgFullRebuildCount, "invalidateAllTilemaps must force full rebuild");
        assertEquals(0, manager.bgIncrementalShiftCount);

        manager.setBackgroundTilemapDirty(true);
        manager.requestBgWindowBaseX(528);
        ensure(manager, zfp);
        assertEquals(3, manager.bgFullRebuildCount, "generic dirty must force full rebuild");
        assertEquals(0, manager.bgIncrementalShiftCount);
        assertArrayEquals(fullRebuildAt(528, zfp), manager.getBackgroundTilemapData());
    }

    @Test
    public void nonWrappingZoneNeverTakesIncrementalPath() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(false, false);
        LevelTilemapManager manager = newManager(0, zfp);
        manager.requestBgWindowBaseX(16);
        ensure(manager, zfp);
        assertEquals(2, manager.bgFullRebuildCount);
        assertEquals(0, manager.bgIncrementalShiftCount);
    }

    @Test
    public void runtimeTilemapOverlayWriteForcesFullRebuildOnNextWindowStep() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = newManager(496, zfp);

        // Runtime BG overlay write (e.g. AIZ-style direct tile rewrites).
        assertTrue(manager.setBackgroundTileDescriptorAtTilemapCell(3, 3, 0x1234));

        manager.requestBgWindowBaseX(512);
        ensure(manager, zfp);
        assertEquals(2, manager.bgFullRebuildCount,
                "live-array overlay write must invalidate the shift snapshot");
        assertEquals(0, manager.bgIncrementalShiftCount);
        // Full rebuild discards the overlay write, exactly as before this change.
        assertArrayEquals(fullRebuildAt(512, zfp), manager.getBackgroundTilemapData());
    }

    @Test
    public void loopBandWindowShiftsAreByteIdenticalToFullRebuild() {
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);
        LevelTilemapManager manager = new LevelTilemapManager(geometry, graphicsManager, null);
        manager.setCurrentBgPeriodWidth(PERIOD_PX);
        manager.setBgLoopBandBaseY(0);
        manager.setBgTilemapBaseX(496);
        ensure(manager, zfp);
        assertEquals(LevelTilemapManager.BG_LOOP_BAND_HEIGHT_PX / Pattern.PATTERN_HEIGHT,
                manager.getBackgroundTilemapHeightTiles());

        manager.requestBgWindowBaseX(512);
        ensure(manager, zfp);
        assertEquals(1, manager.bgIncrementalShiftCount);

        LevelTilemapManager reference = new LevelTilemapManager(geometry, graphicsManager, null);
        reference.setCurrentBgPeriodWidth(PERIOD_PX);
        reference.setBgLoopBandBaseY(0);
        reference.setBgTilemapBaseX(512);
        ensure(reference, zfp);
        assertArrayEquals(reference.getBackgroundTilemapData(), manager.getBackgroundTilemapData());
    }

    // ── Texture upload contract ─────────────────────────────────────────────

    /**
     * Pins the texture↔shader addressing contract: the parallax shader samples the
     * BG FBO/texture base-anchored (local column c shows world content at
     * {@code c*8 + xQueryOffset} for the CURRENT base), so a base-X step
     * re-addresses every texture column. The incremental shift must therefore
     * register the FULL shifted array for upload — identical at every column to an
     * independent full rebuild at the new base — never a column-only update.
     */
    @Test
    public void incrementalShiftRegistersFullShiftedArrayForUpload() throws Exception {
        TilemapGpuRenderer renderer = new TilemapGpuRenderer();
        injectRenderer(renderer);
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);

        LevelTilemapManager manager = newManager(496, zfp);
        assertTrue(getBoolean(renderer, "backgroundDirty"), "initial build registers a full upload");

        // Simulate render() having consumed the pending upload (render() needs GL).
        setField(renderer, "backgroundDirty", false);

        manager.requestBgWindowBaseX(512);
        ensure(manager, zfp);
        assertEquals(1, manager.bgIncrementalShiftCount, "shift path must engage");

        // The shift must re-register a FULL background upload...
        assertTrue(getBoolean(renderer, "backgroundDirty"),
                "incremental shift must mark the full background texture for re-upload");
        byte[] registered = (byte[]) getField(renderer, "backgroundData");
        assertSame(manager.getBackgroundTilemapData(), registered,
                "renderer must receive the live shifted array");
        assertEquals(manager.getBackgroundTilemapWidthTiles(), getInt(renderer, "backgroundWidthTiles"));
        assertEquals(manager.getBackgroundTilemapHeightTiles(), getInt(renderer, "backgroundHeightTiles"));

        // ...whose content at EVERY local column matches an independent full rebuild
        // at the new base (full-array comparison, not just the entering column).
        assertArrayEquals(fullRebuildAt(512, zfp), registered);

        // Retreat re-registers the full array as well.
        setField(renderer, "backgroundDirty", false);
        manager.requestBgWindowBaseX(496);
        ensure(manager, zfp);
        assertEquals(2, manager.bgIncrementalShiftCount);
        assertTrue(getBoolean(renderer, "backgroundDirty"));
        assertArrayEquals(fullRebuildAt(496, zfp), (byte[]) getField(renderer, "backgroundData"));
    }

    // ── VDP wrap height across shifts ───────────────────────────────────────

    /**
     * The detected BG data height can change with the window position (a column
     * with art below tile row 32 scrolling in/out). The incremental path must
     * detect the same height/VDP-wrap state as a full rebuild.
     */
    @Test
    public void enteringColumnHeightChangeMatchesFullRebuildDetectedHeight() {
        useTallLevel();
        ZoneFeatureProvider zfp = new StubZoneFeatures(true, false);

        // Base 256: window covers world 256..767 — the tall column (768..895,
        // art below tile row 32) is just outside, so the 32-row VDP wrap applies.
        LevelTilemapManager manager = newManager(256, zfp);
        assertEquals(32, manager.getBackgroundVdpWrapHeightTiles(),
                "fixture sanity: no tall column in the initial window");

        // Advance one column: world 768..783 (tall) enters at the right edge.
        manager.requestBgWindowBaseX(272);
        ensure(manager, zfp);
        assertEquals(1, manager.bgIncrementalShiftCount, "shift path must engage");
        LevelTilemapManager referenceIn = referenceManagerAt(272, zfp);
        assertEquals(referenceIn.getBackgroundVdpWrapHeightTiles(),
                manager.getBackgroundVdpWrapHeightTiles());
        assertEquals(0, manager.getBackgroundVdpWrapHeightTiles(),
                "tall entering column must disable the 32-row VDP wrap, as a full rebuild would");
        assertArrayEquals(referenceIn.getBackgroundTilemapData(), manager.getBackgroundTilemapData());

        // Retreat: the tall column leaves again; detected height must shrink back.
        manager.requestBgWindowBaseX(256);
        ensure(manager, zfp);
        assertEquals(2, manager.bgIncrementalShiftCount);
        LevelTilemapManager referenceOut = referenceManagerAt(256, zfp);
        assertEquals(referenceOut.getBackgroundVdpWrapHeightTiles(),
                manager.getBackgroundVdpWrapHeightTiles());
        assertEquals(32, manager.getBackgroundVdpWrapHeightTiles());
        assertArrayEquals(referenceOut.getBackgroundTilemapData(), manager.getBackgroundTilemapData());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private LevelTilemapManager newManager(int baseX, ZoneFeatureProvider zfp) {
        LevelTilemapManager manager = new LevelTilemapManager(geometry, graphicsManager, null);
        manager.setCurrentBgPeriodWidth(PERIOD_PX);
        manager.setBgTilemapBaseX(baseX);
        ensure(manager, zfp);
        assertNotNull(manager.getBackgroundTilemapData());
        return manager;
    }

    private void ensure(LevelTilemapManager manager, ZoneFeatureProvider zfp) {
        manager.ensureBackgroundTilemapData(blockLookup, zfp, 0, null, false);
    }

    private LevelTilemapManager referenceManagerAt(int baseX, ZoneFeatureProvider zfp) {
        LevelTilemapManager reference = new LevelTilemapManager(geometry, graphicsManager, null);
        reference.setCurrentBgPeriodWidth(PERIOD_PX);
        reference.setBgTilemapBaseX(baseX);
        ensure(reference, zfp);
        assertNotNull(reference.getBackgroundTilemapData());
        return reference;
    }

    private byte[] fullRebuildAt(int baseX, ZoneFeatureProvider zfp) {
        return referenceManagerAt(baseX, zfp).getBackgroundTilemapData().clone();
    }

    /** Swaps in the taller fixture (512px BG, art below tile row 32 only at block column 6). */
    private void useTallLevel() {
        level = new StubLevel(4, true);
        geometry = new LevelGeometry(level,
                BG_WIDTH_PX, 4 * BLOCK_PX,
                BG_WIDTH_PX, BG_WIDTH_PX, 4 * BLOCK_PX,
                BLOCK_PX, 8);
        blockLookup = lookupFor(level, BG_WIDTH_PX, 4 * BLOCK_PX);
    }

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

    private void injectRenderer(TilemapGpuRenderer renderer) throws Exception {
        Field field = GraphicsManager.class.getDeclaredField("tilemapGpuRenderer");
        field.setAccessible(true);
        field.set(graphicsManager, renderer);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        return (Boolean) getField(target, name);
    }

    private static int getInt(Object target, String name) throws Exception {
        return (Integer) getField(target, name);
    }

    // ── Stubs ───────────────────────────────────────────────────────────────

    /**
     * Synthetic level with deterministic, varied BG content: blocks/chunks/flips/
     * palettes/priority all vary with position so a wrong column is guaranteed to
     * change bytes. Includes empty cells (block 0xFF), out-of-range chunk indices,
     * and flipped chunk descriptors. In the tall variant, block rows below 256px
     * are empty except at block column 6, so the detected BG data height changes
     * with the window position.
     */
    private static final class StubLevel extends AbstractLevel {

        StubLevel(int mapHeightBlocks, boolean tallColumnVariant) {
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
                                | ((c % 4) << 13)            // palette line
                                | ((c & 1) != 0 ? 0x800 : 0)  // h flip
                                | ((c & 2) != 0 ? 0x1000 : 0) // v flip
                                | ((c % 5 == 0) ? 0x8000 : 0); // priority
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
                        int chunkIndex = (b * 7 + cy * 8 + cx) % 52; // some out of range (>= 48)
                        int descBits = chunkIndex
                                | (((b + cx) % 3 == 0) ? 0x400 : 0)  // x flip
                                | (((b + cy) % 4 == 0) ? 0x800 : 0); // y flip
                        block.setChunkDesc(cx, cy, new ChunkDesc(descBits));
                    }
                }
                blocks[b] = block;
            }

            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            map = new Map(2, MAP_WIDTH_BLOCKS, mapHeightBlocks);
            for (int my = 0; my < mapHeightBlocks; my++) {
                for (int mx = 0; mx < MAP_WIDTH_BLOCKS; mx++) {
                    int blockIndex;
                    if (tallColumnVariant && my >= 2) {
                        // Rows below 256px: empty everywhere except block column 6,
                        // so detected BG art height depends on the window position.
                        blockIndex = mx == 6 ? 1 : 0xFF;
                    } else {
                        blockIndex = (mx * 5 + my * 11) % 26; // varies per cell; some empty (>= 24)
                        if (blockIndex >= blockCount) {
                            blockIndex = 0xFF; // null block → empty chunk path
                        }
                    }
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

    /** Minimal ZoneFeatureProvider with configurable BG wrap behavior. */
    private static final class StubZoneFeatures implements ZoneFeatureProvider {
        private final boolean wraps;
        private final boolean linearOverflow;

        StubZoneFeatures(boolean wraps, boolean linearOverflow) {
            this.wraps = wraps;
            this.linearOverflow = linearOverflow;
        }

        @Override
        public boolean bgWrapsHorizontally() {
            return wraps;
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

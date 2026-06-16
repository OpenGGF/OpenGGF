package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.TilemapGpuRenderer;

import java.util.logging.Logger;

/**
 * Manages the build, cache, upload, and invalidation lifecycle of GPU tilemap
 * data (foreground and background layers) derived from the loaded {@link Level}.
 * <p>
 * Owns tilemap byte arrays, dirty flags, pattern lookup data, and prebuilt
 * transition tilemaps. LevelManager delegates tilemap operations here and
 * reads back data via getters for its GL command lambdas.
 * <p>
 * <b>Lifecycle:</b> entirely gameplay-disposable. All state is derived from
 * the {@link Level} (which is owned by {@code WorldSession}) and is rebuilt
 * each time a new {@code LevelTilemapManager} is constructed — typically
 * during {@code LevelManager.loadLevelData(...)}. After an editor mode swap
 * that destroys the gameplay-mode managers, a fresh tilemap manager is
 * rebuilt over the same surviving {@code Level}, so no tilemap state needs
 * to persist outside the gameplay mode context.
 */
public class LevelTilemapManager {
    private static final Logger LOGGER = Logger.getLogger(LevelTilemapManager.class.getName());

    // VDP plane size for Sonic 2 normal levels: 64x32 cells = 512x256 pixels.
    // The background tilemap wraps at this width for Sonic 2's redraw-style pipeline.
    static final int VDP_BG_PLANE_WIDTH_PX = 512;
    private static final int VDP_BG_PLANE_HEIGHT_TILES = 32; // VDP 64x32 nametable
    // Height of a fixed BG loop band (in pixels), matching the VDP plane B height.
    // S3K CNZ1BGE_Boss fills Plane B with $10 (16) chunks = 256px and loops that band
    // via the VDP vertical scroll register (docs/skdisasm/sonic3k.asm:107498-107507).
    static final int BG_LOOP_BAND_HEIGHT_PX = VDP_BG_PLANE_HEIGHT_TILES * Pattern.PATTERN_HEIGHT;
    // Tile columns spanned by one 16px chunk column (the BG window step granularity).
    static final int CHUNK_TILE_SPAN = LevelConstants.CHUNK_WIDTH / Pattern.PATTERN_WIDTH;

    // --- Dependencies ---
    private LevelGeometry geometry;
    private final GraphicsManager graphicsManager;
    private final GameStateManager gameState;

    // --- Background tilemap data ---
    private byte[] backgroundTilemapData;
    private int backgroundTilemapWidthTiles;
    private int backgroundTilemapHeightTiles;
    private boolean backgroundTilemapDirty = true;
    private Boolean lastRequiresFullWidthBgTilemap;
    private int backgroundVdpWrapHeightTiles = 0; // 0 = disabled
    // X offset (in pixels, 512-aligned) for BG tilemap building.
    // Wide BG maps (> 512px) need tiles from the correct region, not always from position 0.
    private int bgTilemapBaseX = 0;
    // Base Y (in BG-layout pixels) for a fixed-height BG loop band, or -1 when the
    // full BG height is built. S3K CNZ's miniboss (CNZ1BGE_Boss) sets this to $200
    // so the boss-room BG loops only the 256px carnival band and never reaches the
    // room floor below it.
    private int bgLoopBandBaseY = -1;
    private int currentBgPeriodWidth = VDP_BG_PLANE_WIDTH_PX;

    // --- Incremental BG window shift state ---
    // True only when the pending backgroundTilemapDirty was caused exclusively by
    // requestBgWindowBaseX (a wrapped-BG window base-X step). Any other invalidation
    // clears it, forcing the full rebuild path.
    private boolean bgWindowShiftCandidate = false;
    // Snapshot of the inputs that produced the current backgroundTilemapData via a
    // full build (or a subsequent in-place shift). Invalid whenever the live array
    // may no longer match a from-scratch build with the recorded parameters.
    private boolean bgLastBuildValid = false;
    private Level bgLastBuildLevel;
    private int bgLastBuildBlockPixelSize;
    private int bgLastBuildBgContiguousWidthPx;
    private BgBuildParams bgLastBuildParams;
    // Diagnostics for tests: count full BG rebuilds vs incremental column shifts.
    int bgFullRebuildCount = 0;
    int bgIncrementalShiftCount = 0;

    // --- Foreground tilemap data ---
    private byte[] foregroundTilemapData;
    private int foregroundTilemapWidthTiles;
    private int foregroundTilemapHeightTiles;
    private boolean foregroundTilemapDirty = true;
    // AIZ2 ship-loop persistent FG ring ($200-wide Plane A nametable analog).
    // While active, the FG tilemap is a $200-wide ring whose cells RETAIN the
    // last forest column drawn into them at the camera's leading edge, giving a
    // natural column-by-column reveal AND a seamless $200 loop (s3.asm:70956).
    // A state change (full-width <-> ring) forces a rebuild.
    private Boolean lastForegroundWrap;
    // Whether the persistent ring has been seeded for the current loop activation.
    private boolean foregroundRingSeeded;
    // Camera world X / screen width pushed each frame (by LevelManager) so the
    // ring fills the leading-edge column at the camera's current position.
    private int foregroundRingCameraX;
    private int foregroundRingScreenWidthPx;
    private static final int FG_RING_WIDTH_PX = VDP_BG_PLANE_WIDTH_PX;

    // --- Pattern lookup data ---
    private byte[] patternLookupData;
    private int patternLookupSize;
    private boolean patternLookupDirty = true;
    private boolean multiAtlasWarningLogged = false;

    // --- Pre-built tilemap data for stutter-free terrain transitions (AIZ intro) ---
    private byte[] prebuiltFgTilemap;
    private int prebuiltFgWidth;
    private int prebuiltFgHeight;
    private byte[] prebuiltBgTilemap;
    private int prebuiltBgWidth;
    private int prebuiltBgHeight;

    // Reusable PatternDesc to avoid per-iteration allocations in tight loops
    private final PatternDesc reusablePatternDesc = new PatternDesc();

    /**
     * Functional interface for block lookups, bridging to LevelManager's getBlockAtPosition.
     */
    @FunctionalInterface
    public interface BlockLookup {
        Block lookup(byte layer, int x, int y);
    }

    /**
     * Immutable record holding the data produced by {@link #buildTilemapData}.
     */
    record TilemapData(byte[] data, int widthTiles, int heightTiles) {
    }

    /**
     * Derived build parameters for one tilemap layer. For the background layer these
     * capture everything (besides the level data itself) that determines the built
     * bytes, so an unchanged-except-base-X comparison can prove a one-column window
     * shift is byte-equivalent to a full rebuild.
     */
    private record BgBuildParams(
            boolean bgWrap,
            boolean bgLinearRowOverflow,
            int xQueryOffset,
            int yQueryOffset,
            int builtWidthPx,
            int builtHeightPx) {
    }

    /**
     * Creates a new LevelTilemapManager.
     *
     * @param geometry        level geometry snapshot (dimensions, level reference)
     * @param graphicsManager graphics manager for pattern atlas access
     * @param gameState       gameplay-mode game state; reserved for future
     *                        cross-zone tilemap-upload decisions, may be null
     */
    public LevelTilemapManager(LevelGeometry geometry, GraphicsManager graphicsManager, GameStateManager gameState) {
        this.geometry = geometry;
        this.graphicsManager = graphicsManager;
        this.gameState = gameState;
    }

    // -----------------------------------------------------------------------
    // Geometry updates
    // -----------------------------------------------------------------------

    /**
     * Updates the geometry reference after a seamless level transition.
     */
    public void updateGeometry(LevelGeometry geometry) {
        this.geometry = geometry;
    }

    // -----------------------------------------------------------------------
    // Ensure methods — lazy build + GPU upload
    // -----------------------------------------------------------------------

    /**
     * Ensures background tilemap data is built and uploaded to the GPU renderer.
     *
     * @param blockLookup       block lookup function
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     */
    public void ensureBackgroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        boolean requiresFullWidthBgTilemap = zoneRuntimeRequiresFullWidthBgTilemap();
        if (lastRequiresFullWidthBgTilemap != null
                && lastRequiresFullWidthBgTilemap != requiresFullWidthBgTilemap) {
            backgroundTilemapDirty = true;
            bgWindowShiftCandidate = false;
        }
        if (!backgroundTilemapDirty && backgroundTilemapData != null && patternLookupData != null) {
            lastRequiresFullWidthBgTilemap = requiresFullWidthBgTilemap;
            // Tilemap data already up to date — but still push VDP wrap height
            // to the renderer in case it was null during the initial build.
            TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
            if (renderer != null && backgroundVdpWrapHeightTiles > 0) {
                renderer.setBgVdpWrapHeight(backgroundVdpWrapHeightTiles);
            }
            return;
        }
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return;
        }

        // Pure base-X window step: shift the retained bytes one chunk column and
        // rebuild only the entering column on the CPU, skipping the full
        // block/chunk/pattern rebuild loop. Any unproven precondition falls back
        // to the full rebuild below. The GPU upload is always the FULL array:
        // the renderer/shader address the texture relative to the current base X
        // (local column c samples world column c*8 + xQueryOffset), so a base
        // step re-addresses every texture column — a column-only upload would
        // leave the other columns holding the previous window's content.
        boolean shifted = bgWindowShiftCandidate
                && tryIncrementalBgWindowShift(blockLookup, zoneFeatureProvider, currentZone);
        bgWindowShiftCandidate = false;
        if (!shifted) {
            buildBackgroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                    parallaxManager, verticalWrapEnabled);
        }
        lastRequiresFullWidthBgTilemap = requiresFullWidthBgTilemap;
        backgroundTilemapDirty = false;

        ensurePatternLookupData();
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND, backgroundTilemapData,
                    backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
            renderer.setBgVdpWrapHeight(backgroundVdpWrapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }
    }

    /**
     * Ensures foreground tilemap data is built and uploaded to the GPU renderer.
     *
     * @param blockLookup       block lookup function
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     */
    public void ensureForegroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        // Detect AIZ2 ship-loop FG ring state change (full-width <-> $200 ring)
        // and force a rebuild on the transition. While the ring is active, the
        // FG tilemap is NOT fully rebuilt each frame (that would snap the whole
        // canopy in); instead it is a persistent $200 ring whose leading-edge
        // column is filled incrementally as the camera advances (natural reveal),
        // with all other cells retained (seamless $200 wrap).
        boolean fgWrap = zoneFeatureProvider != null && zoneFeatureProvider.foregroundWrapsHorizontally();
        if (lastForegroundWrap != null && lastForegroundWrap != fgWrap) {
            foregroundTilemapDirty = true;
            foregroundRingSeeded = false;
        }
        lastForegroundWrap = fgWrap;

        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return;
        }

        // FG ring active and already built: incremental leading-edge fill only.
        if (fgWrap && !foregroundTilemapDirty && foregroundTilemapData != null
                && patternLookupData != null && foregroundRingSeeded) {
            if (foregroundRingFillLeadingEdge(blockLookup, level)) {
                uploadForegroundTilemap();
            }
            return;
        }

        if (!foregroundTilemapDirty && foregroundTilemapData != null && patternLookupData != null) {
            return;
        }

        buildForegroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        foregroundTilemapDirty = false;
        // On a fresh $200-ring build, seed the currently-visible columns from the
        // flat layout so the screen is populated, then hand off to the
        // incremental leading-edge fill on subsequent frames.
        if (fgWrap) {
            foregroundRingSeed(blockLookup, level);
            foregroundRingSeeded = true;
        }
        uploadForegroundTilemap();
    }

    /**
     * Ensures the pattern lookup table is built from the pattern atlas.
     */
    public void ensurePatternLookupData() {
        if (!patternLookupDirty && patternLookupData != null) {
            return;
        }
        Level level = geometry.level();
        if (level == null) {
            return;
        }
        int patternCount = level.getPatternCount();
        patternLookupSize = Math.max(1, patternCount);
        patternLookupData = new byte[patternLookupSize * 4];
        for (int i = 0; i < patternCount; i++) {
            PatternAtlas.Entry entry = graphicsManager.getPatternAtlasEntry(i);
            int offset = i * 4;
            if (entry != null) {
                patternLookupData[offset] = (byte) entry.tileX();
                patternLookupData[offset + 1] = (byte) entry.tileY();
                patternLookupData[offset + 2] = (byte) entry.atlasIndex();
                patternLookupData[offset + 3] = (byte) 255;
            } else {
                patternLookupData[offset] = 0;
                patternLookupData[offset + 1] = 0;
                patternLookupData[offset + 2] = 0;
                patternLookupData[offset + 3] = 0;
            }
        }
        PatternAtlas atlas = graphicsManager.getPatternAtlas();
        if (!multiAtlasWarningLogged && atlas != null && atlas.getAtlasCount() > 1) {
            LOGGER.warning("Pattern atlas overflow: using multiple atlases (count="
                    + atlas.getAtlasCount()
                    + ", slotsPerAtlas=" + atlas.getMaxSlotsPerAtlas()
                    + ", atlasSize=" + atlas.getAtlasWidth() + "x" + atlas.getAtlasHeight()
                    + ") for this level.");
            multiAtlasWarningLogged = true;
        }
        patternLookupDirty = false;
    }

    // -----------------------------------------------------------------------
    // Build methods
    // -----------------------------------------------------------------------

    private void buildBackgroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        BgBuildParams params = computeBuildParams((byte) 1, zoneFeatureProvider, currentZone);
        TilemapData data = buildTilemapData((byte) 1, params, blockLookup);
        backgroundTilemapData = data.data;
        backgroundTilemapWidthTiles = data.widthTiles;
        backgroundTilemapHeightTiles = data.heightTiles;

        bgLastBuildValid = true;
        bgLastBuildLevel = geometry.level();
        bgLastBuildBlockPixelSize = geometry.blockPixelSize();
        bgLastBuildBgContiguousWidthPx = geometry.bgContiguousWidthPx();
        bgLastBuildParams = params;
        bgFullRebuildCount++;

        int actualHeightTiles = recomputeBgVdpWrapHeight();
        LOGGER.fine("BG tilemap " + backgroundTilemapWidthTiles + "x" + backgroundTilemapHeightTiles
                + " actualDataHeight=" + actualHeightTiles
                + " VDPWrapHeight=" + backgroundVdpWrapHeightTiles);
    }

    /**
     * Rescans the live BG tilemap bytes for the VDP wrap height. For VDP wrap height
     * detection, only scan the contiguous BG data region (columns 0-N). HTZ has
     * earthquake cave data at distant columns (54+, rows 48+) that must not inflate
     * the data height beyond 32 — the normal sky BG wraps at 32 rows.
     *
     * @return the detected actual data height in tiles (for logging)
     */
    private int recomputeBgVdpWrapHeight() {
        int scanWidthTiles = Math.min(backgroundTilemapWidthTiles,
                geometry.bgContiguousWidthPx() / Pattern.PATTERN_WIDTH);
        int actualHeightTiles = findActualBgTilemapDataHeight(backgroundTilemapData,
                backgroundTilemapWidthTiles, backgroundTilemapHeightTiles, scanWidthTiles);
        backgroundVdpWrapHeightTiles = (actualHeightTiles > 0
                && actualHeightTiles <= VDP_BG_PLANE_HEIGHT_TILES)
                ? VDP_BG_PLANE_HEIGHT_TILES : 0;
        return actualHeightTiles;
    }

    /**
     * Attempts an in-place one-chunk-column shift of the retained BG tilemap bytes
     * for a pure base-X window step. Every precondition that could change column
     * content must provably match the snapshot of the last full build; otherwise
     * this declines and the caller performs the full rebuild.
     *
     * @return true when the shift was performed (backgroundTilemapData now holds
     *         bytes identical to a full rebuild at the new base X)
     */
    private boolean tryIncrementalBgWindowShift(BlockLookup blockLookup,
                                                ZoneFeatureProvider zoneFeatureProvider,
                                                int currentZone) {
        if (!bgLastBuildValid || bgLastBuildParams == null || backgroundTilemapData == null) {
            return false;
        }
        Level level = geometry.level();
        if (level == null
                || level != bgLastBuildLevel
                || geometry.blockPixelSize() != bgLastBuildBlockPixelSize
                || geometry.bgContiguousWidthPx() != bgLastBuildBgContiguousWidthPx) {
            return false;
        }
        BgBuildParams params = computeBuildParams((byte) 1, zoneFeatureProvider, currentZone);
        BgBuildParams last = bgLastBuildParams;
        if (!params.bgWrap()
                || !last.bgWrap()
                || params.bgLinearRowOverflow() != last.bgLinearRowOverflow()
                || params.yQueryOffset() != last.yQueryOffset()
                || params.builtWidthPx() != last.builtWidthPx()
                || params.builtHeightPx() != last.builtHeightPx()) {
            return false;
        }
        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        int step = params.xQueryOffset() - last.xQueryOffset();
        if (step != chunkWidth && step != -chunkWidth) {
            return false;
        }
        int widthTiles = params.builtWidthPx() / Pattern.PATTERN_WIDTH;
        int heightTiles = params.builtHeightPx() / Pattern.PATTERN_HEIGHT;
        if (widthTiles != backgroundTilemapWidthTiles
                || heightTiles != backgroundTilemapHeightTiles
                || params.builtWidthPx() % chunkWidth != 0
                || params.builtHeightPx() % LevelConstants.CHUNK_HEIGHT != 0
                || backgroundTilemapData.length != widthTiles * heightTiles * 4) {
            return false;
        }

        byte[] data = backgroundTilemapData;
        int rowBytes = widthTiles * 4;
        int shiftBytes = CHUNK_TILE_SPAN * 4;
        int enteringLocalX;
        if (step > 0) {
            // Window advanced right: drop the leftmost chunk column, rebuild the rightmost.
            for (int ty = 0; ty < heightTiles; ty++) {
                int rowOffset = ty * rowBytes;
                System.arraycopy(data, rowOffset + shiftBytes, data, rowOffset, rowBytes - shiftBytes);
            }
            enteringLocalX = params.builtWidthPx() - chunkWidth;
        } else {
            // Window retreated left: drop the rightmost chunk column, rebuild the leftmost.
            for (int ty = 0; ty < heightTiles; ty++) {
                int rowOffset = ty * rowBytes;
                System.arraycopy(data, rowOffset, data, rowOffset + shiftBytes, rowBytes - shiftBytes);
            }
            enteringLocalX = 0;
        }
        fillChunkColumn(data, widthTiles, heightTiles, (byte) 1, enteringLocalX, params,
                blockLookup, level, geometry.blockPixelSize());
        bgLastBuildParams = params;
        bgIncrementalShiftCount++;
        recomputeBgVdpWrapHeight();
        return true;
    }

    /**
     * Scan the BG tilemap data bottom-up to find the last row containing
     * actual art (pattern index >= 2).  Pattern 0 is VDP-transparent and
     * pattern 1 is the default fill tile produced by block 0, so both are
     * excluded.  Real level art starts at pattern index 2+.
     * <p>
     * Only scans the first {@code scanWidthTiles} columns.  This excludes
     * distant earthquake columns (e.g., HTZ BG column 54+) from inflating
     * the detected height beyond the VDP plane size.
     * <p>
     * This lets us distinguish HTZ (real art in rows 0-31 only, fill beyond)
     * from MCZ (real art extending to row 85+).
     */
    private int findActualBgTilemapDataHeight(byte[] data, int widthTiles, int heightTiles,
            int scanWidthTiles) {
        int scanW = Math.min(scanWidthTiles, widthTiles);
        for (int y = heightTiles - 1; y >= 0; y--) {
            for (int x = 0; x < scanW; x++) {
                int offset = (y * widthTiles + x) * 4;
                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int patternIndex = r + ((g & 0x07) << 8);
                if (patternIndex >= 2) {
                    return y + 1;
                }
            }
        }
        return 0;
    }

    private void buildForegroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        TilemapData data = buildTilemapData((byte) 0, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        foregroundTilemapData = data.data;
        foregroundTilemapWidthTiles = data.widthTiles;
        foregroundTilemapHeightTiles = data.heightTiles;
    }

    private static boolean zoneRuntimeRequiresFullWidthBgTilemap() {
        ZoneRuntimeRegistry registry = GameServices.zoneRuntimeRegistryOrNull();
        if (registry == null) {
            return false;
        }
        ZoneRuntimeState state = registry.current();
        return state != null && state.requiresFullWidthBgTilemap();
    }

    private BgBuildParams computeBuildParams(byte layerIndex,
                                             ZoneFeatureProvider zoneFeatureProvider,
                                             int currentZone) {
        int layerLevelWidth = getLayerLevelWidthPx(layerIndex);
        int levelHeight = getLayerLevelHeightPx(layerIndex);

        // Keep Sonic 2's 512px BG wrap behavior (VDP plane redraw model).
        // S3K uses a different background data flow in AIZ intro and needs full-width BG data.
        // HTZ earthquake needs full-width BG data because high-priority BG tiles (cave ceiling)
        // are rendered as a direct overlay between FG-low and FG-high passes, and they span the
        // full BG map. The FBO/parallax path still caps its period at 512px.
        boolean bgWrap = layerIndex == 1
                && zoneFeatureProvider != null
                && zoneFeatureProvider.bgWrapsHorizontally()
                && !zoneRuntimeRequiresFullWidthBgTilemap();
        boolean bgLinearRowOverflow = bgWrap
                && zoneFeatureProvider.useLinearBackgroundLayoutOverflow(currentZone);

        // FOREGROUND (Plane A) horizontal wrap at the VDP plane width ($200). Used
        // by AIZ2's post-bombing ship loop. The texture is allocated $200-wide
        // here; its cell content is filled INCREMENTALLY by the persistent-ring
        // seed + per-frame leading-edge fill (see ensureForegroundTilemapData),
        // NOT by this full build (which would snap the whole canopy in). The
        // engine analog of the ROM's $200 Plane A nametable ring; s3.asm:70956.
        boolean fgWrap = layerIndex == 0
                && zoneFeatureProvider != null
                && zoneFeatureProvider.foregroundWrapsHorizontally();

        // Use the currently selected BG period width. LevelManager may widen this
        // beyond the scroll handler's nominal period when the renderer needs the
        // full BG strip instead of a 64-cell wrapped cache (for example MGZ2
        // state 8's per-line rebuild path).
        int bgPeriodWidth = currentBgPeriodWidth;
        int levelWidth = bgWrap ? bgPeriodWidth
                : fgWrap ? VDP_BG_PLANE_WIDTH_PX
                : layerLevelWidth;

        // For BG layers wider than 512px (e.g., SBZ 15360px), the 64-tile tilemap
        // must contain tiles from the correct BG map region, not always from position 0.
        // bgTilemapBaseX is the 16px-aligned offset into the BG map (matching the
        // BG camera X from the scroll handler). The shader uses this same offset
        // (via ScrollMidpoint → fboWorldOffsetX) to index into the tilemap correctly.
        // When using the 512px window, wrap the base offset at the contiguous BG data extent
        // so that large camera X positions map back to valid BG columns (not empty map regions).
        int bgXQueryOffset = 0;
        int bgContiguousWidthPx = geometry.bgContiguousWidthPx();
        if (layerIndex == 1 && bgWrap && bgLinearRowOverflow) {
            bgXQueryOffset = bgTilemapBaseX;
        } else if (layerIndex == 1 && bgWrap && bgContiguousWidthPx > 0) {
            bgXQueryOffset = ((bgTilemapBaseX % bgContiguousWidthPx) + bgContiguousWidthPx)
                    % bgContiguousWidthPx;
        }

        // S3K CNZ miniboss (CNZ1BGE_Boss) loops a fixed 256px BG band drawn from layout
        // Y=$200; the carnival tunnel repeats inside it while the room floor lives below.
        // Build only that band (Y-anchored at bgLoopBandBaseY) so the looping scroll wraps
        // inside it and never reaches the floor. Other layers/zones build the full height.
        boolean bgLoopBand = layerIndex == 1 && bgWrap && bgLoopBandBaseY >= 0;
        int bgYQueryOffset = bgLoopBand ? bgLoopBandBaseY : 0;
        int builtHeight = bgLoopBand ? BG_LOOP_BAND_HEIGHT_PX : levelHeight;

        return new BgBuildParams(bgWrap, bgLinearRowOverflow, bgXQueryOffset, bgYQueryOffset,
                levelWidth, builtHeight);
    }

    private TilemapData buildTilemapData(byte layerIndex, BlockLookup blockLookup,
                                         ZoneFeatureProvider zoneFeatureProvider,
                                         int currentZone,
                                         ParallaxManager parallaxManager,
                                         boolean verticalWrapEnabled) {
        return buildTilemapData(layerIndex,
                computeBuildParams(layerIndex, zoneFeatureProvider, currentZone), blockLookup);
    }

    private TilemapData buildTilemapData(byte layerIndex, BgBuildParams params, BlockLookup blockLookup) {
        Level level = geometry.level();
        int blockPixelSize = geometry.blockPixelSize();

        int widthTiles = params.builtWidthPx() / Pattern.PATTERN_WIDTH;
        int heightTiles = params.builtHeightPx() / Pattern.PATTERN_HEIGHT;
        byte[] data = new byte[widthTiles * heightTiles * 4];

        for (int x = 0; x < params.builtWidthPx(); x += LevelConstants.CHUNK_WIDTH) {
            fillChunkColumn(data, widthTiles, heightTiles, layerIndex, x, params,
                    blockLookup, level, blockPixelSize);
        }

        return new TilemapData(data, widthTiles, heightTiles);
    }

    /**
     * Fills one 16px-wide chunk column of tilemap data at local pixel X
     * {@code localX}. Shared by the full build loop and the incremental BG window
     * shift so both produce byte-identical column content.
     */
    private void fillChunkColumn(byte[] data, int widthTiles, int heightTiles, byte layerIndex,
                                 int localX, BgBuildParams params, BlockLookup blockLookup,
                                 Level level, int blockPixelSize) {
        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        int chunkHeight = LevelConstants.CHUNK_HEIGHT;
        int chunkX = localX / chunkWidth;
        // Query the map at the offset position (wrapping handled by blockLookup)
        int queryX = localX + params.xQueryOffset();

        for (int y = 0; y < params.builtHeightPx(); y += chunkHeight) {
            int chunkY = y / chunkHeight;
            // queryY anchors the BG loop band at its base layout Y (0 for normal builds).
            int queryY = y + params.yQueryOffset();

            Block block = params.bgLinearRowOverflow()
                    ? lookupBackgroundBlockWithLinearRowOverflow(level, queryX, queryY, blockPixelSize)
                    : blockLookup.lookup(layerIndex, queryX, queryY);
            if (block == null) {
                writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                continue;
            }

            // xBlockBit uses the query position to select the correct chunk within the block.
            int xBlockBit = (queryX % blockPixelSize) / chunkWidth;
            int yBlockBit = (queryY % blockPixelSize) / chunkHeight;
            ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);
            int chunkIndex = chunkDesc.getChunkIndex();

            if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
                writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                continue;
            }

            Chunk chunk = level.getChunk(chunkIndex);
            if (chunk == null) {
                writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                continue;
            }

            boolean chunkHFlip = chunkDesc.getHFlip();
            boolean chunkVFlip = chunkDesc.getVFlip();

            for (int cY = 0; cY < 2; cY++) {
                for (int cX = 0; cX < 2; cX++) {
                    int logicalX = chunkHFlip ? 1 - cX : cX;
                    int logicalY = chunkVFlip ? 1 - cY : cY;

                    PatternDesc patternDesc = chunk.getPatternDesc(logicalX, logicalY);
                    int newIndex = patternDesc.get();
                    if (chunkHFlip) {
                        newIndex ^= 0x800;
                    }
                    if (chunkVFlip) {
                        newIndex ^= 0x1000;
                    }
                    reusablePatternDesc.set(newIndex);

                    int tileX = chunkX * 2 + cX;
                    int tileY = chunkY * 2 + cY;
                    writeTileDescriptor(data, widthTiles, heightTiles, tileX, tileY, reusablePatternDesc);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // AIZ2 ship-loop persistent foreground ring (Plane A nametable analog)
    // -----------------------------------------------------------------------

    /**
     * Pushes the current camera world X and screen width for the persistent FG
     * ring's leading-edge fill. Called each frame (by {@code LevelManager}) while
     * the AIZ2 forest loop is active, mirroring the BG window base-X push.
     */
    public void setForegroundRingCamera(int cameraX, int screenWidthPx) {
        this.foregroundRingCameraX = cameraX;
        this.foregroundRingScreenWidthPx = screenWidthPx;
    }

    /**
     * Seeds the persistent FG ring's currently-visible columns from the flat
     * layout at the camera's position, so the screen is populated on the frame
     * the loop activates. The leading-edge fill then advances it incrementally.
     *
     * <p>ROM: Plane A holds whatever {@code DrawTilesAsYouMove} last wrote; the
     * engine seeds the visible window here so there is no empty frame, then draws
     * the entering column at the leading edge each frame (s3.asm:70638,70680).
     */
    private void foregroundRingSeed(BlockLookup blockLookup, Level level) {
        if (foregroundTilemapData == null) {
            return;
        }
        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        // Seed all 64 ring cells from the flat layout over the $200 span ending at
        // the camera's leading edge. Every cell c == worldX mod $200 is hit once,
        // matching what the flat-sample would show at the current camera position
        // (so the onset is identical to the pre-fix natural scroll-in). The
        // leading-edge fill then advances forest into the cells frame-by-frame.
        int lead = foregroundRingCameraX + foregroundRingScreenWidthPx;
        int rightCol = Math.floorDiv(lead, chunkWidth) * chunkWidth;
        for (int worldX = rightCol - FG_RING_WIDTH_PX + chunkWidth; worldX <= rightCol; worldX += chunkWidth) {
            fillForegroundRingColumnAtWorld(blockLookup, level, worldX);
        }
    }

    /**
     * Fills the persistent FG ring's leading-edge column(s) (the rightmost
     * on-screen chunk columns at the camera's current position) from the flat
     * layout, retaining all other cells. As the camera advances into the forest
     * band, the entering columns draw forest into their ring cells; the wrapped
     * camera then re-shows those retained cells (seamless $200 loop), matching
     * the ROM Plane A ring. Returns true if any cell was rewritten.
     */
    private boolean foregroundRingFillLeadingEdge(BlockLookup blockLookup, Level level) {
        if (foregroundTilemapData == null) {
            return false;
        }
        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        // Redraw the leading edge region each frame: the rightmost ~2 chunk
        // columns of the visible window. This is idempotent (re-drawing forest
        // already there) and guarantees newly-entered columns are filled as the
        // camera advances up to 4px/frame.
        int lead = foregroundRingCameraX + foregroundRingScreenWidthPx;
        int rightCol = Math.floorDiv(lead, chunkWidth) * chunkWidth;
        for (int worldX = rightCol - chunkWidth; worldX <= rightCol + chunkWidth; worldX += chunkWidth) {
            fillForegroundRingColumnAtWorld(blockLookup, level, worldX);
        }
        return true;
    }

    /**
     * Writes the flat-layout chunk column at world X {@code worldX} into the FG
     * ring cell {@code worldX mod $200}, so the shader's {@code floor(worldX/8)
     * mod 64} lookup finds it. Reuses {@link #fillChunkColumn} via a per-column
     * query offset.
     */
    private void fillForegroundRingColumnAtWorld(BlockLookup blockLookup, Level level, int worldX) {
        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        int worldChunkX = Math.floorDiv(worldX, chunkWidth) * chunkWidth;
        int localX = Math.floorMod(worldChunkX, FG_RING_WIDTH_PX);
        int xQueryOffset = worldChunkX - localX;
        int widthTiles = foregroundTilemapWidthTiles;
        int heightTiles = foregroundTilemapHeightTiles;
        BgBuildParams ringParams = new BgBuildParams(false, false, xQueryOffset, 0,
                FG_RING_WIDTH_PX, heightTiles * Pattern.PATTERN_HEIGHT);
        fillChunkColumn(foregroundTilemapData, widthTiles, heightTiles, (byte) 0, localX,
                ringParams, blockLookup, level, geometry.blockPixelSize());
    }

    private Block lookupBackgroundBlockWithLinearRowOverflow(Level level, int x, int y, int blockPixelSize) {
        Map map = level.getMap();
        int layerWidthCells = Math.max(1, getLayerLevelWidthPx((byte) 1) / blockPixelSize);
        int layerHeightCells = Math.max(1, getLayerLevelHeightPx((byte) 1) / blockPixelSize);
        int layerCellCount = layerWidthCells * layerHeightCells;
        int wrappedY = ((y % (layerHeightCells * blockPixelSize)) + layerHeightCells * blockPixelSize)
                % (layerHeightCells * blockPixelSize);
        int linearCell = (wrappedY / blockPixelSize) * layerWidthCells + Math.floorDiv(x, blockPixelSize);
        linearCell = ((linearCell % layerCellCount) + layerCellCount) % layerCellCount;
        int mapX = linearCell % layerWidthCells;
        int mapY = linearCell / layerWidthCells;
        int blockIndex = map.getValue(1, mapX, mapY) & 0xFF;
        if (blockIndex >= level.getBlockCount()) {
            return null;
        }
        return level.getBlock(blockIndex);
    }

    // -----------------------------------------------------------------------
    // Tile write helpers
    // -----------------------------------------------------------------------

    private void writeEmptyChunk(byte[] data, int widthTiles, int heightTiles, int chunkX, int chunkY) {
        for (int cY = 0; cY < 2; cY++) {
            for (int cX = 0; cX < 2; cX++) {
                int tileX = chunkX * 2 + cX;
                int tileY = chunkY * 2 + cY;
                writeEmptyTile(data, widthTiles, heightTiles, tileX, tileY);
            }
        }
    }

    private void writeEmptyTile(byte[] data, int widthTiles, int heightTiles, int tileX, int tileY) {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles
                || tileY >= heightTiles) {
            return;
        }
        int offset = (tileY * widthTiles + tileX) * 4;
        data[offset] = 0;
        data[offset + 1] = 0;
        data[offset + 2] = 0;
        data[offset + 3] = 0;
    }

    private void writeTileDescriptor(byte[] data, int widthTiles, int heightTiles, int tileX, int tileY,
            PatternDesc desc) {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles || tileY >= heightTiles) {
            return;
        }
        int offset = (tileY * widthTiles + tileX) * 4;
        int patternIndex = desc.getPatternIndex();
        int paletteIndex = desc.getPaletteIndex();
        boolean hFlip = desc.getHFlip();
        boolean vFlip = desc.getVFlip();
        boolean priority = desc.getPriority();

        int r = patternIndex & 0xFF;
        int g = ((patternIndex >> 8) & 0x7)
                | ((paletteIndex & 0x3) << 3)
                | (hFlip ? 0x20 : 0)
                | (vFlip ? 0x40 : 0)
                | (priority ? 0x80 : 0);

        data[offset] = (byte) r;
        data[offset + 1] = (byte) g;
        data[offset + 2] = 0;
        data[offset + 3] = (byte) 255;
    }

    // -----------------------------------------------------------------------
    // Invalidation
    // -----------------------------------------------------------------------

    /**
     * Marks the foreground tilemap as dirty, forcing a rebuild on next render.
     * Call this after modifying the level layout (e.g., placing boss arena walls).
     * This is equivalent to setting Screen_redraw_flag in the original ROM.
     */
    public void invalidateForegroundTilemap() {
        foregroundTilemapDirty = true;
    }

    /**
     * Requests a BG tilemap window base-X change driven purely by the wrapped-BG
     * camera window stepping to a new 16px-aligned base. Unlike
     * {@link #setBgTilemapBaseX(int)} + {@link #setBackgroundTilemapDirty(boolean)},
     * this marks the rebuild as a window-only change, allowing the next
     * {@link #ensureBackgroundTilemapData} to take the incremental one-column
     * shift path when all other build inputs are provably unchanged.
     */
    public void requestBgWindowBaseX(int newBase) {
        if (newBase == bgTilemapBaseX) {
            return;
        }
        // Only a clean (or already window-only-dirty) tilemap can stay a shift
        // candidate; mixing with any other pending invalidation forces full rebuild.
        bgWindowShiftCandidate = !backgroundTilemapDirty || bgWindowShiftCandidate;
        bgTilemapBaseX = newBase;
        backgroundTilemapDirty = true;
    }

    /**
     * Handles dirty block/map-cell updates from a MutableLevel.
     * Currently triggers a full foreground tilemap rebuild; incremental
     * partial updates can be added later if needed for performance.
     *
     * @param dirtyBlocks  block indices that changed (unused for now)
     * @param dirtyMapCells linearized map cell indices that changed (unused for now)
     * @param level         the current level
     */
    public void rebuildDirtyRegions(java.util.BitSet dirtyBlocks,
                                    java.util.BitSet dirtyMapCells,
                                    Level level) {
        // For now, a full rebuild is acceptable. The editor doesn't need
        // incremental tile updates in Phase 3 — correctness is the priority.
        foregroundTilemapDirty = true;
        backgroundTilemapDirty = true;
        bgWindowShiftCandidate = false;
    }

    /**
     * Marks background/foreground tilemaps and pattern lookup as dirty.
     * Use this after runtime terrain art/chunk overlays so the GPU tilemap
     * data is rebuilt on the next render.
     */
    public void invalidateAllTilemaps() {
        backgroundTilemapDirty = true;
        bgWindowShiftCandidate = false;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
    }

    // -----------------------------------------------------------------------
    // Upload
    // -----------------------------------------------------------------------

    /**
     * Uploads the current foreground tilemap bytes to the GPU renderer (if active).
     * No-op in headless mode.
     */
    public void uploadForegroundTilemap() {
        if (foregroundTilemapData == null) {
            return;
        }
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }
        ensurePatternLookupData();
        renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, foregroundTilemapData,
                foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
        renderer.setPatternLookupData(patternLookupData, patternLookupSize);
    }

    /**
     * Uploads the current background tilemap bytes to the GPU renderer (if active).
     * No-op in headless mode.
     */
    public void uploadBackgroundTilemap() {
        if (backgroundTilemapData == null) {
            return;
        }
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }
        ensurePatternLookupData();
        renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND, backgroundTilemapData,
                backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
        renderer.setPatternLookupData(patternLookupData, patternLookupSize);
    }

    /**
     * Overwrites one background tile descriptor in the live BG tilemap buffer by tilemap cell.
     * Call {@link #uploadBackgroundTilemap()} once after batching writes.
     *
     * @return true if tilemap bytes changed
     */
    public boolean setBackgroundTileDescriptorAtTilemapCell(int tileX, int tileY, int descriptor) {
        if (backgroundTilemapData == null
                || tileX < 0 || tileY < 0
                || tileX >= backgroundTilemapWidthTiles
                || tileY >= backgroundTilemapHeightTiles) {
            return false;
        }
        int offset = (tileY * backgroundTilemapWidthTiles + tileX) * 4;
        boolean changed = writeTilemapDescriptor(backgroundTilemapData, offset, descriptor);
        if (changed) {
            // The live array no longer matches a from-scratch build; a later window
            // step must take the full rebuild path (which, as today, discards these
            // runtime overlay writes) rather than shifting the modified bytes.
            bgLastBuildValid = false;
            bgWindowShiftCandidate = false;
        }
        return changed;
    }

    // -----------------------------------------------------------------------
    // Foreground tile descriptor access (world-coordinate tilemap writes)
    // -----------------------------------------------------------------------

    /**
     * Overwrites one foreground tile descriptor at world coordinates in the live FG tilemap buffer.
     * Call {@link #uploadForegroundTilemap()} once after batching writes.
     *
     * @param blockLookup       block lookup function (for ensureForegroundTilemapData)
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     * @return true if tilemap bytes changed
     */
    public boolean setForegroundTileDescriptorAtWorld(int worldX, int worldY, int descriptor,
                                                      BlockLookup blockLookup,
                                                      ZoneFeatureProvider zoneFeatureProvider,
                                                      int currentZone,
                                                      ParallaxManager parallaxManager,
                                                      boolean verticalWrapEnabled) {
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return false;
        }

        ensureForegroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        if (foregroundTilemapData == null) {
            return false;
        }

        int levelWidth = getLayerLevelWidthPx((byte) 0);
        int levelHeight = getLayerLevelHeightPx((byte) 0);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return false;
        }

        int wrappedX = Math.floorMod(worldX, levelWidth);
        int wrappedY = worldY;
        if (verticalWrapEnabled) {
            wrappedY = Math.floorMod(worldY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return false;
        }

        int tileX = wrappedX / Pattern.PATTERN_WIDTH;
        int tileY = wrappedY / Pattern.PATTERN_HEIGHT;
        if (tileX < 0 || tileY < 0
                || tileX >= foregroundTilemapWidthTiles
                || tileY >= foregroundTilemapHeightTiles) {
            return false;
        }

        int offset = (tileY * foregroundTilemapWidthTiles + tileX) * 4;
        return writeTilemapDescriptor(foregroundTilemapData, offset, descriptor);
    }

    private static boolean writeTilemapDescriptor(byte[] tilemapData, int offset, int descriptor) {
        int patternIndex = descriptor & 0x7FF;
        int paletteIndex = (descriptor >> 13) & 0x3;
        int g = ((patternIndex >> 8) & 0x7)
                | ((paletteIndex & 0x3) << 3)
                | ((descriptor & 0x800) != 0 ? 0x20 : 0)
                | ((descriptor & 0x1000) != 0 ? 0x40 : 0)
                | ((descriptor & 0x8000) != 0 ? 0x80 : 0);
        byte rByte = (byte) (patternIndex & 0xFF);
        byte gByte = (byte) g;

        if (tilemapData[offset] == rByte
                && tilemapData[offset + 1] == gByte
                && tilemapData[offset + 2] == 0
                && tilemapData[offset + 3] == (byte) 0xFF) {
            return false;
        }

        tilemapData[offset] = rByte;
        tilemapData[offset + 1] = gByte;
        tilemapData[offset + 2] = 0;
        tilemapData[offset + 3] = (byte) 0xFF;
        return true;
    }

    /**
     * Reads a foreground tile descriptor from the live foreground tilemap buffer at world coordinates.
     * Unlike level-data-based descriptor reads, this returns the currently visible
     * descriptor after runtime tilemap writes.
     *
     * @param blockLookup       block lookup function (for ensureForegroundTilemapData)
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     */
    public int getForegroundTileDescriptorFromTilemapAtWorld(int worldX, int worldY,
                                                             BlockLookup blockLookup,
                                                             ZoneFeatureProvider zoneFeatureProvider,
                                                             int currentZone,
                                                             ParallaxManager parallaxManager,
                                                             boolean verticalWrapEnabled) {
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return 0;
        }

        ensureForegroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        if (foregroundTilemapData == null) {
            return 0;
        }

        int levelWidth = getLayerLevelWidthPx((byte) 0);
        int levelHeight = getLayerLevelHeightPx((byte) 0);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return 0;
        }

        int wrappedX = Math.floorMod(worldX, levelWidth);
        int wrappedY = worldY;
        if (verticalWrapEnabled) {
            wrappedY = Math.floorMod(worldY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return 0;
        }

        int tileX = wrappedX / Pattern.PATTERN_WIDTH;
        int tileY = wrappedY / Pattern.PATTERN_HEIGHT;
        if (tileX < 0 || tileY < 0
                || tileX >= foregroundTilemapWidthTiles
                || tileY >= foregroundTilemapHeightTiles) {
            return 0;
        }

        int offset = (tileY * foregroundTilemapWidthTiles + tileX) * 4;
        int r = foregroundTilemapData[offset] & 0xFF;
        int g = foregroundTilemapData[offset + 1] & 0xFF;
        int patternIndex = r | ((g & 0x7) << 8);
        int paletteIdx = (g >> 3) & 0x3;

        int desc = patternIndex | (paletteIdx << 13);
        if ((g & 0x20) != 0) {
            desc |= 0x800;
        }
        if ((g & 0x40) != 0) {
            desc |= 0x1000;
        }
        if ((g & 0x80) != 0) {
            desc |= 0x8000;
        }
        return desc & 0xFFFF;
    }

    // -----------------------------------------------------------------------
    // Prebuilt transition tilemaps
    // -----------------------------------------------------------------------

    /**
     * Pre-builds FG and BG tilemap data from the current level state.
     * The pre-built data can later be swapped in via {@link #swapToPrebuiltTilemaps()}
     * to avoid the expensive full-level tilemap rebuild on the transition frame.
     */
    public void prebuildTransitionTilemaps(BlockLookup blockLookup,
                                           ZoneFeatureProvider zoneFeatureProvider,
                                           int currentZone,
                                           ParallaxManager parallaxManager,
                                           boolean verticalWrapEnabled) {
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return;
        }
        TilemapData fg = buildTilemapData((byte) 0, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        prebuiltFgTilemap = fg.data.clone();
        prebuiltFgWidth = fg.widthTiles;
        prebuiltFgHeight = fg.heightTiles;

        TilemapData bg = buildTilemapData((byte) 1, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        prebuiltBgTilemap = bg.data.clone();
        prebuiltBgWidth = bg.widthTiles;
        prebuiltBgHeight = bg.heightTiles;
    }

    /**
     * Swaps pre-built tilemap data into the live arrays, uploads to GPU,
     * and clears FG/BG dirty flags. Still marks pattern lookup dirty
     * (cheap rebuild, needed if pattern count changed from the overlay).
     *
     * @return true if pre-built data was available and swapped in
     */
    public boolean swapToPrebuiltTilemaps() {
        if (prebuiltFgTilemap == null || prebuiltBgTilemap == null) {
            return false;
        }

        foregroundTilemapData = prebuiltFgTilemap;
        foregroundTilemapWidthTiles = prebuiltFgWidth;
        foregroundTilemapHeightTiles = prebuiltFgHeight;
        foregroundTilemapDirty = false;

        backgroundTilemapData = prebuiltBgTilemap;
        backgroundTilemapWidthTiles = prebuiltBgWidth;
        backgroundTilemapHeightTiles = prebuiltBgHeight;
        lastRequiresFullWidthBgTilemap = zoneRuntimeRequiresFullWidthBgTilemap();
        backgroundTilemapDirty = false;
        // Prebuilt data may have been built under different window settings; the
        // next window step must full-rebuild rather than shift.
        bgLastBuildValid = false;
        bgWindowShiftCandidate = false;

        patternLookupDirty = true;

        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            ensurePatternLookupData();
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND,
                    foregroundTilemapData, foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND,
                    backgroundTilemapData, backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }

        // Release pre-built data (one-shot use)
        prebuiltFgTilemap = null;
        prebuiltBgTilemap = null;
        return true;
    }

    /**
     * Returns whether pre-built transition tilemap data is available.
     */
    public boolean hasPrebuiltTilemaps() {
        return prebuiltFgTilemap != null && prebuiltBgTilemap != null;
    }

    // -----------------------------------------------------------------------
    // State reset
    // -----------------------------------------------------------------------

    /**
     * Clears all tilemap arrays, dirty flags, and prebuilt data.
     */
    public void resetState() {
        backgroundTilemapData = null;
        foregroundTilemapData = null;
        patternLookupData = null;
        prebuiltFgTilemap = null;
        prebuiltBgTilemap = null;
        backgroundTilemapDirty = true;
        lastRequiresFullWidthBgTilemap = null;
        lastForegroundWrap = null;
        foregroundRingSeeded = false;
        foregroundRingCameraX = 0;
        foregroundRingScreenWidthPx = 0;
        bgTilemapBaseX = 0;
        currentBgPeriodWidth = VDP_BG_PLANE_WIDTH_PX;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
        multiAtlasWarningLogged = false;
        bgWindowShiftCandidate = false;
        bgLastBuildValid = false;
        bgLastBuildLevel = null;
        bgLastBuildParams = null;
    }

    // -----------------------------------------------------------------------
    // Getters (used by LevelManager's pre-allocated GL command lambdas)
    // -----------------------------------------------------------------------

    public byte[] getBackgroundTilemapData() {
        return backgroundTilemapData;
    }

    public int getBackgroundTilemapWidthTiles() {
        return backgroundTilemapWidthTiles;
    }

    public int getBackgroundTilemapHeightTiles() {
        return backgroundTilemapHeightTiles;
    }

    public int getBackgroundVdpWrapHeightTiles() {
        return backgroundVdpWrapHeightTiles;
    }

    public byte[] getForegroundTilemapData() {
        return foregroundTilemapData;
    }

    public int getForegroundTilemapWidthTiles() {
        return foregroundTilemapWidthTiles;
    }

    public int getForegroundTilemapHeightTiles() {
        return foregroundTilemapHeightTiles;
    }

    public byte[] getPatternLookupData() {
        return patternLookupData;
    }

    public int getPatternLookupSize() {
        return patternLookupSize;
    }

    public int getBgTilemapBaseX() {
        return bgTilemapBaseX;
    }

    /**
     * Directly sets the BG window base X and marks the background tilemap dirty
     * for a full rebuild (full-width selection, resets, fresh builds). For the
     * camera-driven 16px window steps that may take the incremental shift path,
     * use {@link #requestBgWindowBaseX(int)} instead.
     */
    public void setBgTilemapBaseX(int bgTilemapBaseX) {
        // Direct base writes are not window-only steps; clear any pending shift
        // candidacy and force a full rebuild.
        this.bgWindowShiftCandidate = false;
        if (this.bgTilemapBaseX != bgTilemapBaseX) {
            this.backgroundTilemapDirty = true;
        }
        this.bgTilemapBaseX = bgTilemapBaseX;
    }

    public int getBgLoopBandBaseY() {
        return bgLoopBandBaseY;
    }

    public void setBgLoopBandBaseY(int bgLoopBandBaseY) {
        this.bgLoopBandBaseY = bgLoopBandBaseY;
    }

    public int getCurrentBgPeriodWidth() {
        return currentBgPeriodWidth;
    }

    public void setCurrentBgPeriodWidth(int currentBgPeriodWidth) {
        this.currentBgPeriodWidth = currentBgPeriodWidth;
    }

    public boolean isBackgroundTilemapDirty() {
        return backgroundTilemapDirty;
    }

    public void setBackgroundTilemapDirty(boolean dirty) {
        if (dirty) {
            // Generic invalidation: only requestBgWindowBaseX may mark a
            // window-only change eligible for the incremental shift path.
            this.bgWindowShiftCandidate = false;
        }
        this.backgroundTilemapDirty = dirty;
    }

    public boolean isForegroundTilemapDirty() {
        return foregroundTilemapDirty;
    }

    public void setForegroundTilemapDirty(boolean dirty) {
        this.foregroundTilemapDirty = dirty;
    }

    public void setPatternLookupDirty(boolean dirty) {
        this.patternLookupDirty = dirty;
    }

    public void setMultiAtlasWarningLogged(boolean logged) {
        this.multiAtlasWarningLogged = logged;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private int getLayerLevelWidthPx(byte layer) {
        Level level = geometry.level();
        if (level == null) {
            return geometry.blockPixelSize();
        }
        int widthBlocks = Math.max(1, level.getLayerWidthBlocks(layer));
        return widthBlocks * geometry.blockPixelSize();
    }

    private int getLayerLevelHeightPx(byte layer) {
        Level level = geometry.level();
        if (level == null) {
            return geometry.blockPixelSize();
        }
        int heightBlocks = Math.max(1, level.getLayerHeightBlocks(layer));
        return heightBlocks * geometry.blockPixelSize();
    }
}

package com.openggf.game.sonic2;

import com.openggf.game.sonic2.constants.Sonic2Constants;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.*;
import com.openggf.level.resources.LevelResourcePlan;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tools.KosinskiReader;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;

import com.openggf.data.RomManager;
import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class Sonic2Level extends AbstractLevel {
    private static final int MAP_LAYERS = 2;
    private static final int MAP_HEIGHT = 16;
    private static final int MAP_WIDTH = 128;

    private static final boolean KOS_DEBUG_LOG = false;

    private static final Logger LOG = Logger.getLogger(Sonic2Level.class.getName());

    public Sonic2Level(Rom rom,
            int zoneIndex,
            int characterPaletteAddr,
            int levelPalettesAddr,
            int levelPalettesSize,
            int patternsAddr,
            int chunksAddr,
            int blocksAddr,
            int mapAddr,
            int collisionsAddr,
            int altCollisionsAddr,
            int solidTileHeightsAddr,
            int solidTileWidthsAddr,
            int solidTilesAngleAddr,
            List<ObjectSpawn> objectSpawns,
            List<RingSpawn> ringSpawns,
            RingSpriteSheet ringSpriteSheet,
            int levelBoundariesAddr) throws IOException {
        super(zoneIndex);
        loadPalettes(rom, characterPaletteAddr, levelPalettesAddr, levelPalettesSize);
        loadPatterns(rom, patternsAddr);
        loadSolidTiles(rom, solidTileHeightsAddr, solidTileWidthsAddr, solidTilesAngleAddr);
        loadChunks(rom, chunksAddr, collisionsAddr, altCollisionsAddr);
        loadBlocks(rom, blocksAddr);
        loadMap(rom, mapAddr);
        this.objects = List.copyOf(objectSpawns);
        this.rings = List.copyOf(ringSpawns);
        this.ringSpriteSheet = ringSpriteSheet;
        loadBoundaries(rom, levelBoundariesAddr);
    }

    /** Starts strict construction of a Sonic 2-shaped level from mod-owned data. */
    public static InMemoryBuilder inMemoryBuilder(int zoneIndex, LevelResourcePlan resourcePlan) {
        return new InMemoryBuilder(zoneIndex, resourcePlan);
    }

    /** Starts construction from already bounded, validated raw export payloads. */
    public static InMemoryBuilder inMemoryBuilder(int zoneIndex, byte[] patterns,
                                                   byte[] chunks, byte[] blocks) {
        return new InMemoryBuilder(zoneIndex, patterns, chunks, blocks);
    }

    private Sonic2Level(InMemoryBuilder source) throws IOException {
        super(source.zoneIndex);
        copyInMemoryLevel(source.delegate.build());
    }

    private void copyInMemoryLevel(ModLevel decoded) {
        palettes = new Palette[decoded.getPaletteCount()];
        for (int i = 0; i < palettes.length; i++) palettes[i] = decoded.getPalette(i);
        patternCount = decoded.getPatternCount();
        patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) patterns[i] = decoded.getPattern(i);
        chunkCount = decoded.getChunkCount();
        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) chunks[i] = decoded.getChunk(i);
        blockCount = decoded.getBlockCount();
        blocks = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) blocks[i] = decoded.getBlock(i);
        solidTileCount = decoded.getSolidTileCount();
        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) solidTiles[i] = decoded.getSolidTile(i);
        map = decoded.getMap();
        objects = decoded.getObjects();
        rings = decoded.getRings();
        ringSpriteSheet = decoded.getRingSpriteSheet();
        minX = decoded.getMinX();
        maxX = decoded.getMaxX();
        minY = decoded.getMinY();
        maxY = decoded.getMaxY();
    }

    /**
     * Builder for the exact uncompressed v1 level payload shapes used by mod zones.
     * Every array is copied when supplied so authoring buffers cannot mutate a live level.
     */
    public static final class InMemoryBuilder {
        private final int zoneIndex;
        private final ModLevel.Builder delegate;

        private InMemoryBuilder(int zoneIndex, LevelResourcePlan resourcePlan) {
            this.zoneIndex = zoneIndex;
            delegate = ModLevel.builder(zoneIndex, 8, resourcePlan);
        }

        private InMemoryBuilder(int zoneIndex, byte[] patterns, byte[] chunks, byte[] blocks) {
            this.zoneIndex = zoneIndex;
            delegate = ModLevel.builder(zoneIndex, 8, patterns, chunks, blocks);
        }

        public InMemoryBuilder layout(int width, int height, byte[] foreground, byte[] background) {
            delegate.layout(width, height, foreground, background);
            return this;
        }

        public InMemoryBuilder paletteLines(byte[][] lines) {
            delegate.paletteLines(lines);
            return this;
        }

        public InMemoryBuilder solidProfiles(byte[] heights, byte[] widths, byte[] angles) {
            delegate.solidProfiles(heights, widths, angles);
            return this;
        }

        public InMemoryBuilder collisionIndices(int[] primary, int[] secondary) {
            delegate.collisionIndices(primary, secondary);
            return this;
        }

        public InMemoryBuilder boundaries(int minX, int maxX, int minY, int maxY) {
            delegate.boundaries(minX, maxX, minY, maxY);
            return this;
        }

        public InMemoryBuilder spawns(List<ObjectSpawn> objects, List<RingSpawn> rings,
                                      RingSpriteSheet ringSheet) {
            delegate.spawns(objects, rings, ringSheet);
            return this;
        }

        public Sonic2Level build() throws IOException {
            return new Sonic2Level(this);
        }
    }

    /**
     * Creates a Sonic2Level using a LevelResourcePlan for overlay-based resource loading.
     *
     * <p>This constructor supports zones that compose resources from multiple sources,
     * such as Hill Top Zone which overlays HTZ-specific patterns and blocks on top
     * of shared EHZ/HTZ base data.
     *
     * @param rom                    The ROM to load from
     * @param zoneIndex              Zone index (ROM zone ID)
     * @param characterPaletteAddr   Address of character palette
     * @param levelPalettesAddr      Address of level palettes
     * @param levelPalettesSize      Size of level palette data
     * @param resourcePlan           Resource plan defining pattern/block/chunk/collision loading
     * @param mapAddr                Address of level layout
     * @param solidTileHeightsAddr   Address of solid tile heights
     * @param solidTileWidthsAddr    Address of solid tile widths
     * @param solidTilesAngleAddr    Address of solid tile angles
     * @param objectSpawns           Object spawn data
     * @param ringSpawns             Ring spawn data
     * @param ringSpriteSheet        Ring sprite sheet
     * @param levelBoundariesAddr    Address of level boundaries
     */
    public Sonic2Level(Rom rom,
            int zoneIndex,
            int characterPaletteAddr,
            int levelPalettesAddr,
            int levelPalettesSize,
            LevelResourcePlan resourcePlan,
            int mapAddr,
            int solidTileHeightsAddr,
            int solidTileWidthsAddr,
            int solidTilesAngleAddr,
            List<ObjectSpawn> objectSpawns,
            List<RingSpawn> ringSpawns,
            RingSpriteSheet ringSpriteSheet,
            int levelBoundariesAddr) throws IOException {
        super(zoneIndex);
        loadPalettes(rom, characterPaletteAddr, levelPalettesAddr, levelPalettesSize);
        loadPatternsWithPlan(rom, resourcePlan);
        loadSolidTiles(rom, solidTileHeightsAddr, solidTileWidthsAddr, solidTilesAngleAddr);
        loadChunksWithPlan(rom, resourcePlan);
        loadBlocksWithPlan(rom, resourcePlan);
        loadMap(rom, mapAddr);
        this.objects = List.copyOf(objectSpawns);
        this.rings = List.copyOf(ringSpawns);
        this.ringSpriteSheet = ringSpriteSheet;
        loadBoundaries(rom, levelBoundariesAddr);
    }

    private void loadPalettes(Rom rom, int characterPaletteAddr, int levelPalettesAddr, int levelPalettesSize)
            throws IOException {
        byte[][] lines = new byte[PALETTE_COUNT][Palette.PALETTE_SIZE_IN_ROM];
        lines[0] = rom.readBytes(characterPaletteAddr, Palette.PALETTE_SIZE_IN_ROM);
        byte[] levelBytes = rom.readBytes(levelPalettesAddr, levelPalettesSize);
        int loadedPalettes = Math.min(PALETTE_COUNT - 1,
                levelBytes.length / Palette.PALETTE_SIZE_IN_ROM);
        for (int i = 0; i < loadedPalettes; i++) {
            lines[i + 1] = Arrays.copyOfRange(levelBytes,
                    i * Palette.PALETTE_SIZE_IN_ROM, (i + 1) * Palette.PALETTE_SIZE_IN_ROM);
        }
        decodePaletteLines(lines, true, true);
    }

    private void decodePaletteLines(byte[][] lines, boolean applyCrossGamePalette,
                                    boolean cacheImmediately) {
        palettes = new Palette[PALETTE_COUNT];
        GraphicsManager graphicsMan = cacheImmediately ? GameServices.graphics() : null;
        for (int i = 0; i < PALETTE_COUNT; i++) {
            palettes[i] = new Palette();
            palettes[i].fromSegaFormat(lines[i]);
        }

        // "Knuckles in Sonic 2" lock-on: replace palette line 0 with the
        // S2-compatible Knuckles palette from the S3K ROM (0x060BEA).
        // Only indices 2-5 differ (Knuckles' reds vs Sonic's blues);
        // indices 0-1 and 6-15 are identical to S2's Pal_SonicTails.
        if (applyCrossGamePalette && com.openggf.game.CrossGameFeatureProvider.isActive()) {
            String mainChar = ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration());
            Palette hostPal = GameServices.crossGameFeatures()
                    .loadHostCompatiblePalette(mainChar);
            if (hostPal != null) {
                palettes[0] = hostPal;
            }
        }

        if (graphicsMan != null && graphicsMan.isGlInitialized()) {
            for (int i = 0; i < palettes.length; i++) {
                graphicsMan.cachePaletteTexture(palettes[i], i);
            }
        }

    }

    private void loadPatterns(Rom rom, int patternsAddr) throws IOException {
        byte[] result;
        synchronized (rom) {
            FileChannel channel = rom.getFileChannel();
            channel.position(patternsAddr);
            result = KosinskiReader.decompress(channel, KOS_DEBUG_LOG);
        }

        decodePatterns(result, false, true);
        LOG.fine("Pattern count: " + patternCount + " (" + result.length + " bytes)");
    }

    // Loads chunks with both primary and secondary collision indices.
    private void loadChunks(Rom rom, int chunksAddr, int collisionAddr, int altCollisionAddr) throws IOException {
        final int CHUNK_BUFFER_SIZE = 0xFFFF; // 64KB
        final int SOLID_TILE_REF_BUFFER_LENGTH = 0x300;

        byte[] chunkBuffer;
        byte[] solidTileRefBuffer;
        byte[] solidTileAltRefBuffer;
        synchronized (rom) {
            FileChannel channel = rom.getFileChannel();
            channel.position(chunksAddr);
            chunkBuffer = KosinskiReader.decompress(channel, KOS_DEBUG_LOG);

            channel.position(collisionAddr);
            solidTileRefBuffer = KosinskiReader.decompress(channel, KOS_DEBUG_LOG);

            channel.position(altCollisionAddr);
            solidTileAltRefBuffer = KosinskiReader.decompress(channel, KOS_DEBUG_LOG);
        }
        chunkBuffer = applyAnimatedPatternMappings(rom, chunkBuffer);

        int decodedChunkCount = chunkBuffer.length / Chunk.CHUNK_SIZE_IN_ROM;
        decodeChunks(chunkBuffer, unsignedBytes(solidTileRefBuffer, decodedChunkCount),
                unsignedBytes(solidTileAltRefBuffer, decodedChunkCount));

        LOG.fine("Chunk count: " + chunkCount + " (" + chunkBuffer.length + " bytes)");
    }

    private byte[] applyAnimatedPatternMappings(Rom rom, byte[] chunkBuffer) throws IOException {
        if (chunkBuffer == null || chunkBuffer.length == 0) {
            return chunkBuffer;
        }
        if (zoneIndex < 0 || zoneIndex >= 0x11) {
            return chunkBuffer;
        }
        int tableAddr = Sonic2Constants.ANIM_PAT_MAPS_ADDR;
        int offset = rom.read16BitAddr(tableAddr + zoneIndex * 2);
        if (offset == 0) {
            return chunkBuffer;
        }
        int listAddr = tableAddr + offset;
        int destOffset = rom.read16BitAddr(listAddr);
        if (destOffset == 0) {
            return chunkBuffer;
        }
        int wordCount = rom.read16BitAddr(listAddr + 2);
        int wordsToCopy = wordCount + 1; // bytesToWcnt(n) = n/2 - 1
        int srcAddr = listAddr + 4;
        int maxBytes = wordsToCopy * 2;
        int requiredSize = destOffset + maxBytes;
        if (requiredSize > chunkBuffer.length) {
            chunkBuffer = Arrays.copyOf(chunkBuffer, requiredSize);
        }
        int available = Math.min(maxBytes, chunkBuffer.length - destOffset);
        if (available <= 0) {
            return chunkBuffer;
        }
        wordsToCopy = available / 2;
        for (int i = 0; i < wordsToCopy; i++) {
            int value = rom.read16BitAddr(srcAddr + i * 2L);
            int dest = destOffset + i * 2;
            chunkBuffer[dest] = (byte) ((value >> 8) & 0xFF);
            chunkBuffer[dest + 1] = (byte) (value & 0xFF);
        }
        return chunkBuffer;
    }

    /**
     * @param rom
     * @param tileHeightsAddr
     * @param anglesAddr
     * @throws IOException
     */
    private void loadSolidTiles(Rom rom, int tileHeightsAddr, int tileWidthsAddr, int anglesAddr) throws IOException {
        byte[] solidTileHeightsBuffer = rom.readBytes(tileHeightsAddr, Sonic2Constants.SOLID_TILE_MAP_SIZE);
        byte[] solidTileWidthsBuffer = rom.readBytes(tileWidthsAddr, Sonic2Constants.SOLID_TILE_MAP_SIZE);
        int count = solidTileHeightsBuffer.length / SolidTile.TILE_SIZE_IN_ROM;
        byte[] angles = new byte[count];
        for (int i = 0; i < count; i++) {
            angles[i] = rom.readByte(anglesAddr + i);
        }
        decodeSolidProfiles(solidTileHeightsBuffer, solidTileWidthsBuffer, angles);
    }

    private void decodeSolidProfiles(byte[] heights, byte[] widths, byte[] angles) throws IOException {
        if (heights.length == 0 || heights.length % SolidTile.TILE_SIZE_IN_ROM != 0
                || widths.length != heights.length
                || angles.length != heights.length / SolidTile.TILE_SIZE_IN_ROM) {
            throw new IOException("Inconsistent SolidTile data");
        }
        solidTileCount = angles.length;
        LOG.fine("how many solid tiles fit?:" + solidTileCount);
        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) {
            byte[] heightProfile = Arrays.copyOfRange(heights,
                    i * SolidTile.TILE_SIZE_IN_ROM, (i + 1) * SolidTile.TILE_SIZE_IN_ROM);
            byte[] widthProfile = Arrays.copyOfRange(widths,
                    i * SolidTile.TILE_SIZE_IN_ROM, (i + 1) * SolidTile.TILE_SIZE_IN_ROM);
            solidTiles[i] = new SolidTile(i, heightProfile, widthProfile, angles[i]);
        }

        LOG.fine("SolidTiles loaded");

    }

    private void loadBlocks(Rom rom, int blocksAddr) throws IOException {
        byte[] blockBuffer;
        synchronized (rom) {
            FileChannel channel = rom.getFileChannel();
            channel.position(blocksAddr);
            blockBuffer = KosinskiReader.decompress(channel, KOS_DEBUG_LOG);
        }

        decodeBlocks(blockBuffer);
        LOG.fine("Block count: " + blockCount + " (" + blockBuffer.length + " bytes)");

    }

    private void loadMap(Rom rom, int mapAddr) throws IOException {
        final int MAP_BUFFER_SIZE = 0xFFFF; // 64KB

        byte[] buffer;
        synchronized (rom) {
            FileChannel channel = rom.getFileChannel();
            channel.position(mapAddr);
            buffer = KosinskiReader.decompress(channel, KOS_DEBUG_LOG);
        }

        if (buffer.length != MAP_LAYERS * MAP_HEIGHT * MAP_WIDTH) {
            throw new IOException("Inconsistent map data");
        }

        decodeInterleavedMap(MAP_WIDTH, MAP_HEIGHT, buffer);

        LOG.fine("Map loaded successfully. Byte count: " + buffer.length);
    }

    private void loadBoundaries(Rom rom, int levelBoundariesAddr) throws IOException {
        // Each entry is 8 bytes:
        // 0-1: minX (unsigned)
        // 2-3: maxX (unsigned)
        // 4-5: minY (signed)
        // 6-7: maxY (signed)

        setBoundaries(rom.read16BitAddr(levelBoundariesAddr),
                rom.read16BitAddr(levelBoundariesAddr + 2),
                (short) rom.read16BitAddr(levelBoundariesAddr + 4),
                (short) rom.read16BitAddr(levelBoundariesAddr + 6));
    }

    private void decodeLayerMaps(int width, int height, byte[] foreground, byte[] background) {
        byte[] interleaved = new byte[Math.multiplyExact(Math.multiplyExact(width, height), MAP_LAYERS)];
        for (int y = 0; y < height; y++) {
            int sourceRow = y * width;
            int destinationRow = y * width * MAP_LAYERS;
            System.arraycopy(foreground, sourceRow, interleaved, destinationRow, width);
            System.arraycopy(background, sourceRow, interleaved, destinationRow + width, width);
        }
        decodeInterleavedMap(width, height, interleaved);
    }

    private void decodeInterleavedMap(int width, int height, byte[] interleaved) {
        map = new Map(MAP_LAYERS, width, height, interleaved);
    }

    private void setBoundaries(int minX, int maxX, int minY, int maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    // ===== Resource Plan-based loading methods =====

    /**
     * Loads patterns using a LevelResourcePlan, supporting overlay composition.
     *
     * <p>For zones like HTZ, this loads the base EHZ_HTZ patterns first, then
     * overlays HTZ-specific patterns at the specified offset (0x3F80 bytes).
     *
     * <p>HTZ also requires extended pattern space for dynamic art (mountains/clouds)
     * which are normally loaded by Dynamic_HTZ at runtime. We pre-fill these with
     * sky blue placeholder patterns to avoid garbled rendering.
     */
    private void loadPatternsWithPlan(Rom rom, LevelResourcePlan plan) throws IOException {
        loadPatternsWithPlan(new ResourceLoader(rom), plan, true);
    }

    private void loadPatternsWithPlan(ResourceLoader loader, LevelResourcePlan plan,
                                      boolean stockHtzExtensions) throws IOException {
        // Use a large initial buffer - will be trimmed to actual size
        byte[] result = loader.loadWithOverlays(plan.getPatternOps(), 0x10000);
        decodePatterns(result, stockHtzExtensions && zoneIndex == Sonic2Constants.ZONE_HTZ, true);

        if (plan.hasPatternOverlays()) {
            LOG.info("Pattern count: " + patternCount + " (" + result.length + " bytes) [with overlays]");
        } else {
            LOG.fine("Pattern count: " + patternCount + " (" + result.length + " bytes)");
        }
    }

    /**
     * PatchHTZTiles equivalent: fills HTZ dynamic art pattern slots with actual
     * decompressed cliff art and uncompressed cloud art from the ROM.
     *
     * <p>Reference: s2.asm PatchHTZTiles (line 86777). The ROM decompresses
     * ArtNem_HTZCliffs and scatters 128-byte chunks to VRAM tile slots. The
     * first 24 tiles (6 strips × 4 tiles) go to $0500-$0517 (mountains).
     * Cloud art (ArtUnc_HTZClouds, 1024 bytes = 32 tiles) fills $0518-$051F
     * with the initial 8 tiles.
     *
     * <p>Dynamic_HTZ then streams position-specific mountain/cloud art every
     * frame at runtime, overwriting these initial values.
     */
    private void fillHtzDynamicArtPatterns(GraphicsManager graphicsMan, int startIndex) {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) {
                fillEmptyPatterns(graphicsMan, startIndex);
                return;
            }

            // 1. Decompress cliff art (ArtNem_HTZCliffs → ~6KB)
            byte[] cliffArt;
            synchronized (rom) {
                FileChannel ch = rom.getFileChannel();
                ch.position(Sonic2Constants.ART_NEM_HTZ_CLIFFS_ADDR);
                cliffArt = NemesisReader.decompress(ch);
            }

            // 2. Fill mountain tile slots ($0500-$0517) with the first 24 tiles
            //    from decompressed cliff art (matching PatchHTZTiles' initial frame).
            int destTile = Sonic2Constants.HTZ_MOUNTAINS_TILE_INDEX;
            int srcOff = 0;
            int mountainsFilled = 0;
            for (int i = 0; i < Sonic2Constants.HTZ_MOUNTAINS_TILE_COUNT && destTile < patternCount; i++, destTile++) {
                patterns[destTile] = new Pattern();
                if (srcOff + Pattern.PATTERN_SIZE_IN_ROM <= cliffArt.length) {
                    byte[] tile = new byte[Pattern.PATTERN_SIZE_IN_ROM];
                    System.arraycopy(cliffArt, srcOff, tile, 0, Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[destTile].fromSegaFormat(tile);
                    srcOff += Pattern.PATTERN_SIZE_IN_ROM;
                    mountainsFilled++;
                } else {
                    patterns[destTile].fromSegaFormat(new byte[Pattern.PATTERN_SIZE_IN_ROM]);
                }
                if (graphicsMan.isGlInitialized()) {
                    graphicsMan.cachePatternTexture(patterns[destTile], destTile);
                }
            }

            // 3. Load uncompressed cloud art and fill cloud tile slots ($0518-$051F)
            byte[] cloudArt = new byte[Sonic2Constants.ART_UNC_HTZ_CLOUDS_SIZE];
            synchronized (rom) {
                FileChannel ch = rom.getFileChannel();
                ch.position(Sonic2Constants.ART_UNC_HTZ_CLOUDS_ADDR);
                ByteBuffer buf = ByteBuffer.wrap(cloudArt);
                while (buf.hasRemaining()) {
                    if (ch.read(buf) < 0) break;
                }
            }
            destTile = Sonic2Constants.HTZ_CLOUDS_TILE_INDEX;
            srcOff = 0;
            int cloudsFilled = 0;
            for (int i = 0; i < Sonic2Constants.HTZ_CLOUDS_TILE_COUNT && destTile < patternCount; i++, destTile++) {
                patterns[destTile] = new Pattern();
                if (srcOff + Pattern.PATTERN_SIZE_IN_ROM <= cloudArt.length) {
                    byte[] tile = new byte[Pattern.PATTERN_SIZE_IN_ROM];
                    System.arraycopy(cloudArt, srcOff, tile, 0, Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[destTile].fromSegaFormat(tile);
                    srcOff += Pattern.PATTERN_SIZE_IN_ROM;
                    cloudsFilled++;
                } else {
                    patterns[destTile].fromSegaFormat(new byte[Pattern.PATTERN_SIZE_IN_ROM]);
                }
                if (graphicsMan.isGlInitialized()) {
                    graphicsMan.cachePatternTexture(patterns[destTile], destTile);
                }
            }

            // 4. Fill any remaining slots between cloud end and patternCount with empty
            for (int i = Math.max(startIndex, Sonic2Constants.HTZ_DYNAMIC_TILES_END); i < patternCount; i++) {
                if (patterns[i] == null) {
                    patterns[i] = new Pattern();
                    patterns[i].fromSegaFormat(new byte[Pattern.PATTERN_SIZE_IN_ROM]);
                    if (graphicsMan.isGlInitialized()) {
                        graphicsMan.cachePatternTexture(patterns[i], i);
                    }
                }
            }
            // Also fill gaps between startIndex and mountain range
            for (int i = startIndex; i < Sonic2Constants.HTZ_MOUNTAINS_TILE_INDEX && i < patternCount; i++) {
                if (patterns[i] == null) {
                    patterns[i] = new Pattern();
                    patterns[i].fromSegaFormat(new byte[Pattern.PATTERN_SIZE_IN_ROM]);
                    if (graphicsMan.isGlInitialized()) {
                        graphicsMan.cachePatternTexture(patterns[i], i);
                    }
                }
            }

            LOG.info("HTZ PatchHTZTiles: filled " + mountainsFilled + " mountain tiles from cliff art ("
                    + cliffArt.length + " bytes), " + cloudsFilled + " cloud tiles from cloud art");

        } catch (IOException e) {
            LOG.warning("Failed to load HTZ dynamic art, using empty placeholders: " + e.getMessage());
            fillEmptyPatterns(graphicsMan, startIndex);
        }
    }

    private void fillEmptyPatterns(GraphicsManager graphicsMan, int startIndex) {
        byte[] empty = new byte[Pattern.PATTERN_SIZE_IN_ROM];
        for (int i = startIndex; i < patternCount; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(empty);
            if (graphicsMan.isGlInitialized()) {
                graphicsMan.cachePatternTexture(patterns[i], i);
            }
        }
    }

    /**
     * Loads chunks (16x16 tile mappings) using a LevelResourcePlan, supporting overlay composition.
     *
     * <p>For zones like HTZ, this loads the base EHZ chunks first, then overlays
     * HTZ-specific chunks at the specified offset (0x0980 bytes). This also
     * loads the collision indices from the plan.
     */
    private void loadChunksWithPlan(Rom rom, LevelResourcePlan plan) throws IOException {
        ResourceLoader loader = new ResourceLoader(rom);

        // Load chunk data (usually single source)
        byte[] chunkBuffer = loader.loadWithOverlays(plan.getChunkOps(), 0x10000);
        chunkBuffer = applyAnimatedPatternMappings(rom, chunkBuffer);

        chunkCount = chunkBuffer.length / Chunk.CHUNK_SIZE_IN_ROM;
        if (chunkBuffer.length % Chunk.CHUNK_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent chunk data");
        }

        // Load collision indices from the plan
        byte[] solidTileRefBuffer;
        byte[] solidTileAltRefBuffer;

        LoadOp primaryCollision = plan.getPrimaryCollision();
        LoadOp secondaryCollision = plan.getSecondaryCollision();

        if (primaryCollision != null) {
            solidTileRefBuffer = loader.loadSingle(primaryCollision);
        } else {
            solidTileRefBuffer = new byte[0];
        }

        if (secondaryCollision != null) {
            solidTileAltRefBuffer = loader.loadSingle(secondaryCollision);
        } else {
            solidTileAltRefBuffer = new byte[0];
        }

        decodeChunks(chunkBuffer, unsignedBytes(solidTileRefBuffer, chunkCount),
                unsignedBytes(solidTileAltRefBuffer, chunkCount));

        LOG.fine("Chunk count: " + chunkCount + " (" + chunkBuffer.length + " bytes)");
    }

    /**
     * Loads blocks (128x128 tile mappings) using a LevelResourcePlan, supporting overlay composition.
     *
     * <p>Most zones use shared block data without overlays (e.g., HTZ uses shared EHZ_HTZ blocks).
     * This method supports overlays for future zones that may need them.
     */
    private void loadBlocksWithPlan(Rom rom, LevelResourcePlan plan) throws IOException {
        loadBlocksWithPlan(new ResourceLoader(rom), plan);
    }

    private void loadBlocksWithPlan(ResourceLoader loader, LevelResourcePlan plan) throws IOException {

        // Load and compose block data with overlays, aligned to block size
        byte[] blockBuffer = loader.loadWithOverlaysAligned(
                plan.getBlockOps(), 0x10000, LevelConstants.BLOCK_SIZE_IN_ROM);

        decodeBlocks(blockBuffer);

        if (plan.hasBlockOverlays()) {
            LOG.info("Block count: " + blockCount + " (" + blockBuffer.length + " bytes) [with overlays]");
        } else {
            LOG.fine("Block count: " + blockCount + " (" + blockBuffer.length + " bytes)");
        }
    }

    private void decodeChunks(byte[] chunkBuffer, int[] primaryCollision,
                              int[] secondaryCollision) throws IOException {
        if (chunkBuffer.length % Chunk.CHUNK_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent chunk data");
        }
        chunkCount = chunkBuffer.length / Chunk.CHUNK_SIZE_IN_ROM;
        if (primaryCollision.length != chunkCount || secondaryCollision.length != chunkCount) {
            throw new IllegalArgumentException("Collision index counts must exactly match chunk count " + chunkCount);
        }
        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            chunks[i] = new Chunk();
            byte[] data = Arrays.copyOfRange(chunkBuffer, i * Chunk.CHUNK_SIZE_IN_ROM,
                    (i + 1) * Chunk.CHUNK_SIZE_IN_ROM);
            chunks[i].fromSegaFormat(data, primaryCollision[i], secondaryCollision[i]);
        }
    }

    private void decodePatterns(byte[] data, boolean extendHtzDynamicArt,
                                boolean cacheImmediately) throws IOException {
        if (data.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent pattern data");
        }
        int loadedCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        patternCount = extendHtzDynamicArt
                ? Math.max(loadedCount, Sonic2Constants.HTZ_DYNAMIC_TILES_END)
                : loadedCount;
        patterns = new Pattern[patternCount];
        GraphicsManager graphicsMan = cacheImmediately || extendHtzDynamicArt
                ? GameServices.graphics() : null;
        for (int i = 0; i < loadedCount; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(Arrays.copyOfRange(data,
                    i * Pattern.PATTERN_SIZE_IN_ROM, (i + 1) * Pattern.PATTERN_SIZE_IN_ROM));
            if (graphicsMan != null && cacheImmediately && graphicsMan.isGlInitialized()) {
                graphicsMan.cachePatternTexture(patterns[i], i);
            }
        }
        if (extendHtzDynamicArt && patternCount > loadedCount) {
            fillHtzDynamicArtPatterns(graphicsMan, loadedCount);
        }
    }

    private void publishGraphicsCaches() {
        GraphicsManager graphics = GameServices.graphics();
        if (!graphics.isGlInitialized()) {
            return;
        }
        for (int i = 0; i < patternCount; i++) {
            graphics.cachePatternTexture(patterns[i], i);
        }
        for (int i = 0; i < palettes.length; i++) {
            graphics.cachePaletteTexture(palettes[i], i);
        }
    }

    private void decodeBlocks(byte[] data) throws IOException {
        if (data.length % LevelConstants.BLOCK_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent block data");
        }
        blockCount = data.length / LevelConstants.BLOCK_SIZE_IN_ROM;
        blocks = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) {
            blocks[i] = new Block();
            blocks[i].fromSegaFormat(Arrays.copyOfRange(data,
                    i * LevelConstants.BLOCK_SIZE_IN_ROM, (i + 1) * LevelConstants.BLOCK_SIZE_IN_ROM));
        }
        if (blockCount > 0) {
            blocks[0] = new Block();
        }
    }

    private static int[] unsignedBytes(byte[] data, int count) {
        int[] decoded = new int[count];
        for (int i = 0; i < Math.min(data.length, count); i++) {
            decoded[i] = Byte.toUnsignedInt(data[i]);
        }
        return decoded;
    }

}

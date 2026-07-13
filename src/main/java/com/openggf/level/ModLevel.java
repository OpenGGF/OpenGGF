package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.LevelResourcePlan;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ModLevelInputLimits;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** A ROM-free level decoded entirely from bounded mod-owned resources. */
public final class ModLevel extends AbstractLevel {
    private static final int MAP_LAYERS = 2;

    private final int blockGridSide;

    public static Builder builder(int zoneIndex, int blockGridSide, LevelResourcePlan resourcePlan) {
        return new Builder(zoneIndex, blockGridSide, resourcePlan);
    }

    public static Builder builder(int zoneIndex, int blockGridSide, byte[] patterns,
                                  byte[] chunks, byte[] blocks) {
        return new Builder(zoneIndex, blockGridSide, patterns, chunks, blocks);
    }

    private ModLevel(Builder source) throws IOException {
        super(source.zoneIndex);
        blockGridSide = source.blockGridSide;
        source.requireComplete();
        RawResources raw = source.rawResources != null
                ? validatePreparedResources(source)
                : readAndValidateResources(source, ResourceLoader.forModAssetsOnly());
        decodePatterns(raw.patterns());
        decodeChunks(raw.chunks(), source.primaryCollisionIndices, source.secondaryCollisionIndices);
        decodeBlocks(raw.blocks());
        decodePaletteLines(source.paletteLines);
        decodeSolidProfiles(source.solidHeights, source.solidWidths, source.solidAngles);
        decodeLayerMaps(source.mapWidth, source.mapHeight, source.foregroundMap, source.backgroundMap);
        objects = source.objectSpawns;
        rings = source.ringSpawns;
        ringSpriteSheet = source.ringSpriteSheet;
        minX = source.minX;
        maxX = source.maxX;
        minY = source.minY;
        maxY = source.maxY;
        publishGraphicsCaches();
    }

    @Override
    public int getChunksPerBlockSide() {
        return blockGridSide;
    }

    @Override
    public int getBlockPixelSize() {
        return Math.multiplyExact(blockGridSide, Chunk.CHUNK_WIDTH);
    }

    private record RawResources(byte[] patterns, byte[] chunks, byte[] blocks) {}

    private static RawResources validatePreparedResources(Builder source) {
        byte[] patterns = source.rawResources.patterns().clone();
        byte[] chunks = source.rawResources.chunks().clone();
        byte[] blocks = source.rawResources.blocks().clone();
        int patternCount = exactRecordCount(patterns, Pattern.PATTERN_SIZE_IN_ROM, 2048, "pattern");
        int chunkCount = exactRecordCount(chunks, Chunk.CHUNK_SIZE_IN_ROM, 1024, "chunk");
        int blockCount = exactRecordCount(blocks, source.blockRecordSize(), 256, "block");
        validateRawReferences(source, chunks, blocks, patternCount, chunkCount, blockCount);
        return new RawResources(patterns, chunks, blocks);
    }

    private static RawResources readAndValidateResources(Builder source, ResourceLoader loader)
            throws IOException {
        ArrayList<LoadOp> allOps = new ArrayList<>();
        allOps.addAll(source.resourcePlan.getPatternOps());
        allOps.addAll(source.resourcePlan.getChunkOps());
        allOps.addAll(source.resourcePlan.getBlockOps());
        if (source.resourcePlan.getPrimaryCollision() != null) {
            allOps.add(source.resourcePlan.getPrimaryCollision());
        }
        if (source.resourcePlan.getSecondaryCollision() != null) {
            allOps.add(source.resourcePlan.getSecondaryCollision());
        }
        loader.preflightSources(allOps);
        byte[] patterns = loader.loadWithOverlays(source.resourcePlan.getPatternOps(), 0);
        byte[] chunks = loader.loadWithOverlays(source.resourcePlan.getChunkOps(), 0);
        byte[] blocks = loader.loadWithOverlays(source.resourcePlan.getBlockOps(), 0);
        int patternCount = exactRecordCount(patterns, Pattern.PATTERN_SIZE_IN_ROM, 2048, "pattern");
        int chunkCount = exactRecordCount(chunks, Chunk.CHUNK_SIZE_IN_ROM, 1024, "chunk");
        int blockCount = exactRecordCount(blocks, source.blockRecordSize(), 256, "block");
        validateRawReferences(source, chunks, blocks, patternCount, chunkCount, blockCount);
        return new RawResources(patterns, chunks, blocks);
    }

    private static int exactRecordCount(byte[] data, int recordSize, int maximum, String label) {
        if (data.length == 0 || data.length % recordSize != 0) {
            throw new IllegalArgumentException("v1 " + label + " data must contain exact "
                    + recordSize + "-byte records");
        }
        int count = data.length / recordSize;
        if (count > maximum) {
            throw new IllegalArgumentException("v1 " + label + " count exceeds " + maximum);
        }
        return count;
    }

    private static void validateRawReferences(Builder source, byte[] chunks, byte[] blocks,
                                              int patternCount, int chunkCount, int blockCount) {
        for (int offset = 0; offset < chunks.length; offset += 2) {
            int pattern = unsigned16(chunks, offset) & 0x7FF;
            if (pattern >= patternCount) {
                throw new IllegalArgumentException("Chunk descriptor references missing pattern " + pattern);
            }
        }
        for (int offset = 0; offset < blocks.length; offset += 2) {
            int chunk = unsigned16(blocks, offset) & 0x3FF;
            if (chunk >= chunkCount) {
                int blockIndex = offset / source.blockRecordSize();
                throw new IllegalArgumentException("Block " + blockIndex
                        + " references missing chunk " + chunk);
            }
        }
        int solidCount = source.solidAngles.length;
        validateCollisionReferences(source.primaryCollisionIndices, chunkCount, solidCount, "primary");
        validateCollisionReferences(source.secondaryCollisionIndices, chunkCount, solidCount, "secondary");
        validateMapReferences(source.foregroundMap, blockCount, "foreground");
        validateMapReferences(source.backgroundMap, blockCount, "background");
    }

    private static void validateCollisionReferences(int[] values, int chunkCount,
                                                    int solidCount, String path) {
        if (values.length != chunkCount) {
            throw new IllegalArgumentException(path + " collision count must exactly match chunk count "
                    + chunkCount);
        }
        for (int value : values) {
            if (value >= solidCount) {
                throw new IllegalArgumentException(path + " collision index " + value
                        + " exceeds solid profile count " + solidCount);
            }
        }
    }

    private static void validateMapReferences(byte[] values, int blockCount, String layer) {
        for (byte value : values) {
            int block = Byte.toUnsignedInt(value);
            if (block >= blockCount) {
                throw new IllegalArgumentException(layer + " map references missing block " + block);
            }
        }
    }

    private static int unsigned16(byte[] data, int offset) {
        return (Byte.toUnsignedInt(data[offset]) << 8) | Byte.toUnsignedInt(data[offset + 1]);
    }

    private void decodePatterns(byte[] data) {
        patternCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(Arrays.copyOfRange(data,
                    i * Pattern.PATTERN_SIZE_IN_ROM, (i + 1) * Pattern.PATTERN_SIZE_IN_ROM));
        }
    }

    private void decodeChunks(byte[] data, int[] primary, int[] secondary) {
        chunkCount = data.length / Chunk.CHUNK_SIZE_IN_ROM;
        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            chunks[i] = new Chunk();
            chunks[i].fromSegaFormat(Arrays.copyOfRange(data,
                    i * Chunk.CHUNK_SIZE_IN_ROM, (i + 1) * Chunk.CHUNK_SIZE_IN_ROM),
                    primary[i], secondary[i]);
        }
    }

    private void decodeBlocks(byte[] data) {
        int recordSize = blockGridSide * blockGridSide * LevelConstants.BYTES_PER_CHUNK;
        blockCount = data.length / recordSize;
        blocks = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) {
            blocks[i] = new Block(blockGridSide);
            blocks[i].fromSegaFormat(Arrays.copyOfRange(data, i * recordSize, (i + 1) * recordSize),
                    blockGridSide * blockGridSide);
        }
        if (blockCount > 0) {
            blocks[0] = new Block(blockGridSide);
        }
    }

    private void decodePaletteLines(byte[][] lines) {
        palettes = new Palette[PALETTE_COUNT];
        for (int i = 0; i < PALETTE_COUNT; i++) {
            palettes[i] = new Palette();
            palettes[i].fromSegaFormat(lines[i]);
        }
    }

    private void decodeSolidProfiles(byte[] heights, byte[] widths, byte[] angles) {
        solidTileCount = angles.length;
        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) {
            solidTiles[i] = new SolidTile(i,
                    Arrays.copyOfRange(heights, i * SolidTile.TILE_SIZE_IN_ROM,
                            (i + 1) * SolidTile.TILE_SIZE_IN_ROM),
                    Arrays.copyOfRange(widths, i * SolidTile.TILE_SIZE_IN_ROM,
                            (i + 1) * SolidTile.TILE_SIZE_IN_ROM), angles[i]);
        }
    }

    private void decodeLayerMaps(int width, int height, byte[] foreground, byte[] background) {
        byte[] interleaved = new byte[Math.multiplyExact(Math.multiplyExact(width, height), MAP_LAYERS)];
        for (int y = 0; y < height; y++) {
            int sourceRow = y * width;
            int destinationRow = sourceRow * MAP_LAYERS;
            System.arraycopy(foreground, sourceRow, interleaved, destinationRow, width);
            System.arraycopy(background, sourceRow, interleaved, destinationRow + width, width);
        }
        map = new Map(MAP_LAYERS, width, height, interleaved);
    }

    private void publishGraphicsCaches() {
        GraphicsManager graphics = GameServices.graphics();
        if (!graphics.isGlInitialized()) return;
        for (int i = 0; i < patternCount; i++) graphics.cachePatternTexture(patterns[i], i);
        for (int i = 0; i < palettes.length; i++) graphics.cachePaletteTexture(palettes[i], i);
    }

    /** Strict builder for the uncompressed v1 level payload. Supplied arrays are copied. */
    public static final class Builder {
        private final int zoneIndex;
        private final int blockGridSide;
        private final LevelResourcePlan resourcePlan;
        private final RawResources rawResources;
        private int mapWidth;
        private int mapHeight;
        private byte[] foregroundMap;
        private byte[] backgroundMap;
        private byte[][] paletteLines;
        private byte[] solidHeights;
        private byte[] solidWidths;
        private byte[] solidAngles;
        private int[] primaryCollisionIndices;
        private int[] secondaryCollisionIndices;
        private int minX;
        private int maxX;
        private int minY;
        private int maxY;
        private boolean boundariesSet;
        private List<ObjectSpawn> objectSpawns;
        private List<RingSpawn> ringSpawns;
        private RingSpriteSheet ringSpriteSheet;

        private Builder(int zoneIndex, int blockGridSide, LevelResourcePlan resourcePlan) {
            validateIdentity(zoneIndex, blockGridSide);
            this.zoneIndex = zoneIndex;
            this.blockGridSide = blockGridSide;
            this.resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            rawResources = null;
        }

        private Builder(int zoneIndex, int blockGridSide, byte[] patterns, byte[] chunks, byte[] blocks) {
            validateIdentity(zoneIndex, blockGridSide);
            this.zoneIndex = zoneIndex;
            this.blockGridSide = blockGridSide;
            resourcePlan = null;
            Objects.requireNonNull(blocks, "blocks");
            if (blocks.length == 0 || blocks.length % blockRecordSize() != 0) {
                throw new IllegalArgumentException("v1 block data must contain exact "
                        + blockRecordSize() + "-byte records");
            }
            rawResources = new RawResources(Objects.requireNonNull(patterns, "patterns").clone(),
                    Objects.requireNonNull(chunks, "chunks").clone(),
                    blocks.clone());
        }

        public Builder layout(int width, int height, byte[] foreground, byte[] background) {
            int cells = checkedMapCells(width, height);
            requireExactLength(foreground, cells, "Foreground map");
            if (background != null) requireExactLength(background, cells, "Background map");
            mapWidth = width;
            mapHeight = height;
            foregroundMap = foreground.clone();
            backgroundMap = background == null ? new byte[cells] : background.clone();
            return this;
        }

        public Builder paletteLines(byte[][] lines) {
            Objects.requireNonNull(lines, "lines");
            if (lines.length != PALETTE_COUNT) {
                throw new IllegalArgumentException("Exactly four palette lines are required");
            }
            paletteLines = new byte[PALETTE_COUNT][];
            for (int i = 0; i < PALETTE_COUNT; i++) {
                requireExactLength(lines[i], Palette.PALETTE_SIZE_IN_ROM, "Palette line " + i);
                paletteLines[i] = lines[i].clone();
            }
            return this;
        }

        public Builder solidProfiles(byte[] heights, byte[] widths, byte[] angles) {
            Objects.requireNonNull(heights, "heights");
            Objects.requireNonNull(widths, "widths");
            Objects.requireNonNull(angles, "angles");
            if (heights.length == 0 || heights.length % SolidTile.TILE_SIZE_IN_ROM != 0) {
                throw new IllegalArgumentException("Solid heights require one exact 16-byte record per profile");
            }
            if (widths.length != heights.length) {
                throw new IllegalArgumentException("Solid width and height profile counts must match");
            }
            int count = heights.length / SolidTile.TILE_SIZE_IN_ROM;
            if (count > 256 || angles.length != count) {
                throw new IllegalArgumentException("Solid angles must match the 1..256 profile count");
            }
            solidHeights = heights.clone();
            solidWidths = widths.clone();
            solidAngles = angles.clone();
            return this;
        }

        public Builder collisionIndices(int[] primary, int[] secondary) {
            validateUnsigned16(primary, "Primary collision indices");
            validateUnsigned16(secondary, "Secondary collision indices");
            primaryCollisionIndices = primary.clone();
            secondaryCollisionIndices = secondary.clone();
            return this;
        }

        public Builder boundaries(int minX, int maxX, int minY, int maxY) {
            if (minX < 0 || maxX > 0xFFFF || minX > maxX) {
                throw new IllegalArgumentException("Horizontal boundaries must be ordered unsigned 16-bit values");
            }
            if (minY < Short.MIN_VALUE || maxY > Short.MAX_VALUE || minY > maxY) {
                throw new IllegalArgumentException("Vertical boundaries must be ordered signed 16-bit values");
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            boundariesSet = true;
            return this;
        }

        public Builder spawns(List<ObjectSpawn> objects, List<RingSpawn> rings, RingSpriteSheet ringSheet) {
            Objects.requireNonNull(objects, "objects");
            Objects.requireNonNull(rings, "rings");
            ModLevelInputLimits limits = ModLevelInputLimits.production();
            if (objects.size() > limits.maxLevelObjects()) {
                throw new IllegalArgumentException("Object count exceeds production mod limit "
                        + limits.maxLevelObjects());
            }
            if (rings.size() > limits.maxLevelRings()) {
                throw new IllegalArgumentException("Ring count exceeds production mod limit "
                        + limits.maxLevelRings());
            }
            objectSpawns = List.copyOf(objects);
            ringSpawns = List.copyOf(rings);
            ringSpriteSheet = Objects.requireNonNull(ringSheet, "ringSheet");
            return this;
        }

        public ModLevel build() throws IOException {
            return new ModLevel(this);
        }

        private int blockRecordSize() {
            return Math.multiplyExact(Math.multiplyExact(blockGridSide, blockGridSide),
                    LevelConstants.BYTES_PER_CHUNK);
        }

        private void requireComplete() {
            if (foregroundMap == null || paletteLines == null || solidHeights == null
                    || primaryCollisionIndices == null || !boundariesSet || objectSpawns == null) {
                throw new IllegalStateException("In-memory level builder is incomplete");
            }
        }

        private static void validateIdentity(int zoneIndex, int blockGridSide) {
            if (zoneIndex < 0) throw new IllegalArgumentException("Zone index must not be negative");
            if (blockGridSide != 8 && blockGridSide != 16) {
                throw new IllegalArgumentException("blockGridSide must be 8 or 16");
            }
        }

        private static int checkedMapCells(int width, int height) {
            ModLevelInputLimits limits = ModLevelInputLimits.production();
            if (width <= 0 || width > limits.maxMapWidth()) {
                throw new IllegalArgumentException("Map width must be in 1.." + limits.maxMapWidth());
            }
            if (height <= 0 || height > limits.maxMapHeight()) {
                throw new IllegalArgumentException("Map height must be in 1.." + limits.maxMapHeight());
            }
            long cells = (long) width * height;
            if (cells > limits.maxMapCells()) {
                throw new IllegalArgumentException("Map cell count exceeds production mod limit "
                        + limits.maxMapCells());
            }
            return (int) cells;
        }

        private static void requireExactLength(byte[] data, int expected, String label) {
            Objects.requireNonNull(data, label);
            if (data.length != expected) {
                throw new IllegalArgumentException(label + " must contain exactly " + expected + " bytes");
            }
        }

        private static void validateUnsigned16(int[] values, String label) {
            Objects.requireNonNull(values, label);
            for (int value : values) {
                if (value < 0 || value > 0xFFFF) {
                    throw new IllegalArgumentException(label + " contain a value outside unsigned 16-bit range");
                }
            }
        }
    }
}

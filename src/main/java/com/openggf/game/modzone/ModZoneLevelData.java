package com.openggf.game.modzone;

import com.openggf.game.ModApi;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.List;
import java.util.Optional;

/** Immutable game-owned view of one fully validated additive-zone payload. */
@ModApi
public final class ModZoneLevelData {
    private final int formatVersion;
    private final int zoneIndex;
    private final int blockGridSide;
    private final int width;
    private final int height;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final byte[] patterns;
    private final byte[] chunks;
    private final byte[] blocks;
    private final byte[] foregroundMap;
    private final byte[] backgroundMap;
    private final byte[] solidHeights;
    private final byte[] solidWidths;
    private final byte[] solidAngles;
    private final int[] primaryCollision;
    private final int[] secondaryCollision;
    private final byte[][] paletteLines;
    private final Optional<ModZoneHostMetadata> hostMetadata;
    private final List<ModPaletteClaim> paletteClaims;
    private final List<ObjectSpawn> objects;
    private final List<RingSpawn> rings;
    private final int patternCount;
    private final int chunkCount;
    private final int blockCount;

    public ModZoneLevelData(int formatVersion, int zoneIndex, int blockGridSide,
                            int width, int height, int minX, int maxX, int minY, int maxY,
                            byte[] patterns, byte[] chunks, byte[] blocks,
                            byte[] foregroundMap, byte[] backgroundMap,
                            byte[] solidHeights, byte[] solidWidths, byte[] solidAngles,
                            int[] primaryCollision, int[] secondaryCollision,
                            byte[][] paletteLines, Optional<ModZoneHostMetadata> hostMetadata,
                            List<ModPaletteClaim> paletteClaims, List<ObjectSpawn> objects,
                            List<RingSpawn> rings, int patternCount, int chunkCount,
                            int blockCount) {
        this.formatVersion = formatVersion;
        this.zoneIndex = zoneIndex;
        this.blockGridSide = blockGridSide;
        this.width = width;
        this.height = height;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.patterns = patterns.clone();
        this.chunks = chunks.clone();
        this.blocks = blocks.clone();
        this.foregroundMap = foregroundMap.clone();
        this.backgroundMap = backgroundMap == null ? null : backgroundMap.clone();
        this.solidHeights = solidHeights.clone();
        this.solidWidths = solidWidths.clone();
        this.solidAngles = solidAngles.clone();
        this.primaryCollision = primaryCollision.clone();
        this.secondaryCollision = secondaryCollision.clone();
        this.paletteLines = clone2d(paletteLines);
        this.hostMetadata = java.util.Objects.requireNonNull(hostMetadata, "hostMetadata");
        this.paletteClaims = List.copyOf(paletteClaims);
        this.objects = List.copyOf(objects);
        this.rings = List.copyOf(rings);
        this.patternCount = patternCount;
        this.chunkCount = chunkCount;
        this.blockCount = blockCount;
    }

    public int formatVersion() { return formatVersion; }
    public int zoneIndex() { return zoneIndex; }
    public int blockGridSide() { return blockGridSide; }
    public int width() { return width; }
    public int height() { return height; }
    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public byte[] patternBytes() { return patterns.clone(); }
    public byte[] chunkBytes() { return chunks.clone(); }
    public byte[] blockBytes() { return blocks.clone(); }
    public byte[] foregroundMap() { return foregroundMap.clone(); }
    public Optional<byte[]> backgroundMap() {
        return backgroundMap == null ? Optional.empty() : Optional.of(backgroundMap.clone());
    }
    public byte[] solidHeights() { return solidHeights.clone(); }
    public byte[] solidWidths() { return solidWidths.clone(); }
    public byte[] solidAngles() { return solidAngles.clone(); }
    public int[] primaryCollisionIndices() { return primaryCollision.clone(); }
    public int[] secondaryCollisionIndices() { return secondaryCollision.clone(); }
    public byte[][] paletteLines() { return clone2d(paletteLines); }
    public Optional<ModZoneHostMetadata> hostMetadata() { return hostMetadata; }
    public List<ModPaletteClaim> paletteClaims() { return paletteClaims; }
    public List<ObjectSpawn> objects() { return objects; }
    public List<RingSpawn> rings() { return rings; }
    public int patternCount() { return patternCount; }
    public int chunkCount() { return chunkCount; }
    public int blockCount() { return blockCount; }

    private static byte[][] clone2d(byte[][] source) {
        byte[][] copy = new byte[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
        return copy;
    }
}

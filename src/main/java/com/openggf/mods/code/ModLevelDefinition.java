package com.openggf.mods.code;

import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneHostMetadata;
import com.openggf.mods.TrackKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, fully validated representation of a baked level export. */
@com.openggf.game.ModApi
public final class ModLevelDefinition {
    @com.openggf.game.ModApi
    public record Bounds(int minX, int maxX, int minY, int maxY) {}
    @com.openggf.game.ModApi
    public record Start(int x, int y) {}
    @com.openggf.game.ModApi
    public sealed interface Music permits StockMusic, TrackMusic {}
    @com.openggf.game.ModApi
    public record StockMusic(int stockId) implements Music {}
    @com.openggf.game.ModApi
    public record TrackMusic(TrackKey trackKey) implements Music {
        public TrackMusic { Objects.requireNonNull(trackKey, "trackKey"); }
    }

    @com.openggf.game.ModApi
    public sealed interface ObjectEntry permits StockObjectSpawn, KeyedObjectSpawn {
        int placementId(); int x(); int y(); int subtype(); int renderFlags();
        boolean respawnTracked(); int rawYWord();
    }
    @com.openggf.game.ModApi
    public record StockObjectSpawn(int placementId, int x, int y, int stockObjectId,
                                   int subtype, int renderFlags, boolean respawnTracked,
                                   int rawYWord) implements ObjectEntry {}
    @com.openggf.game.ModApi
    public record KeyedObjectSpawn(int placementId, int x, int y, String objectKey,
                                   int subtype, int renderFlags, boolean respawnTracked,
                                   int rawYWord) implements ObjectEntry {
        public KeyedObjectSpawn { Objects.requireNonNull(objectKey, "objectKey"); }
    }
    @com.openggf.game.ModApi
    public record RingEntry(int placementId, int x, int y) {}

    private final int formatVersion;
    private final String zoneName;
    private final int zoneIndex;
    private final int levelIndex;
    private final int blockGridSide;
    private final int width;
    private final int height;
    private final Bounds bounds;
    private final Start start;
    private final Music music;
    private final List<ObjectEntry> objects;
    private final List<RingEntry> rings;
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
    private final ModZoneHostMetadata hostMetadata;
    private final List<ModPaletteClaim> paletteClaims;
    private final int patternCount;
    private final int chunkCount;
    private final int blockCount;
    private final int solidProfileCount;

    ModLevelDefinition(int formatVersion, String zoneName, int zoneIndex, int levelIndex,
                       int blockGridSide, int width, int height, Bounds bounds, Start start,
                       Music music, List<ObjectEntry> objects, List<RingEntry> rings,
                       byte[] patterns, byte[] chunks, byte[] blocks, byte[] foregroundMap,
                       byte[] backgroundMap, byte[] solidHeights, byte[] solidWidths,
                       byte[] solidAngles, int[] primaryCollision, int[] secondaryCollision,
                       byte[][] paletteLines, int patternCount, int chunkCount, int blockCount,
                       int solidProfileCount) {
        this(formatVersion, zoneName, zoneIndex, levelIndex, blockGridSide, width, height, bounds,
                start, music, objects, rings, patterns, chunks, blocks, foregroundMap,
                backgroundMap, solidHeights, solidWidths, solidAngles, primaryCollision,
                secondaryCollision, paletteLines, patternCount, chunkCount, blockCount,
                solidProfileCount, null, List.of());
    }

    ModLevelDefinition(int formatVersion, String zoneName, int zoneIndex, int levelIndex,
                       int blockGridSide, int width, int height, Bounds bounds, Start start,
                       Music music, List<ObjectEntry> objects, List<RingEntry> rings,
                       byte[] patterns, byte[] chunks, byte[] blocks, byte[] foregroundMap,
                       byte[] backgroundMap, byte[] solidHeights, byte[] solidWidths,
                       byte[] solidAngles, int[] primaryCollision, int[] secondaryCollision,
                       byte[][] paletteLines, int patternCount, int chunkCount, int blockCount,
                       int solidProfileCount, ModZoneHostMetadata hostMetadata,
                       List<ModPaletteClaim> paletteClaims) {
        this.formatVersion = formatVersion;
        this.zoneName = Objects.requireNonNull(zoneName, "zoneName");
        this.zoneIndex = zoneIndex;
        this.levelIndex = levelIndex;
        this.blockGridSide = blockGridSide;
        this.width = width;
        this.height = height;
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.start = Objects.requireNonNull(start, "start");
        this.music = Objects.requireNonNull(music, "music");
        this.objects = List.copyOf(objects);
        this.rings = List.copyOf(rings);
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
        this.hostMetadata = hostMetadata;
        this.paletteClaims = List.copyOf(paletteClaims);
        this.patternCount = patternCount;
        this.chunkCount = chunkCount;
        this.blockCount = blockCount;
        this.solidProfileCount = solidProfileCount;
    }

    public int formatVersion() { return formatVersion; }
    public String zoneName() { return zoneName; }
    public int zoneIndex() { return zoneIndex; }
    public int levelIndex() { return levelIndex; }
    public int blockGridSide() { return blockGridSide; }
    public int width() { return width; }
    public int height() { return height; }
    public Bounds bounds() { return bounds; }
    public Start start() { return start; }
    public Music music() { return music; }
    public List<ObjectEntry> objects() { return objects; }
    public List<RingEntry> rings() { return rings; }
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
    public Optional<ModZoneHostMetadata> hostMetadata() {
        return Optional.ofNullable(hostMetadata);
    }
    public List<ModPaletteClaim> paletteClaims() { return paletteClaims; }
    public int patternCount() { return patternCount; }
    public int chunkCount() { return chunkCount; }
    public int blockCount() { return blockCount; }
    public int solidProfileCount() { return solidProfileCount; }

    private static byte[][] clone2d(byte[][] source) {
        byte[][] copy = new byte[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
        return copy;
    }
}

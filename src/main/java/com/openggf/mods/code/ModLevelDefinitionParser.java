package com.openggf.mods.code;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.ModKeySyntax;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.TrackKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Strict bounded reader for the canonical ModLevelDefinition v1 export. */
public final class ModLevelDefinitionParser {
    private static final Set<String> ROOT = Set.of("formatVersion", "zoneName", "zoneIndex",
            "levelIndex", "blockGridSide", "width", "height", "bounds", "start", "music",
            "assets", "objects", "rings");
    private static final Set<String> BOUNDS = Set.of("minX", "maxX", "minY", "maxY");
    private static final Set<String> START = Set.of("x", "y");
    private static final Set<String> MUSIC = Set.of("stockId", "trackKey");
    private static final Set<String> TRACK = Set.of("modId", "name");
    private static final Set<String> ASSETS = Set.of("patterns", "chunks", "blocks",
            "foregroundMap", "backgroundMap", "solidHeights", "solidWidths", "solidAngles",
            "collisionPrimary", "collisionSecondary", "palettes");
    private static final Set<String> STOCK_OBJECT = Set.of("placementId", "x", "y",
            "stockObjectId", "subtype", "renderFlags", "respawnTracked", "rawYWord");
    private static final Set<String> KEYED_OBJECT = Set.of("placementId", "x", "y",
            "objectKey", "subtype", "renderFlags", "respawnTracked", "rawYWord");
    private static final Set<String> RING = Set.of("placementId", "x", "y");

    private ModLevelDefinitionParser() {}

    public static ModLevelDefinition read(ModAssetRoot root, BakedLevelRef reference) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(reference, "reference");
        ModInputLimits limits = root.limits();
        byte[] json = root.readBounded(reference.levelJsonEntry(), limits.maxMetadataBytes());
        JsonNode metadata = parseJson(json, limits);
        only(metadata, ROOT, "level.json");

        int version = integer(required(metadata, "formatVersion"), "formatVersion");
        if (version != 1) fail("Unsupported level formatVersion: " + version);
        String zoneName = nonblank(required(metadata, "zoneName"), "zoneName", limits);
        int zoneIndex = ranged(required(metadata, "zoneIndex"), 0x40, Integer.MAX_VALUE, "zoneIndex");
        int levelIndex = ranged(required(metadata, "levelIndex"), 0x400, Integer.MAX_VALUE, "levelIndex");
        int blockGridSide = ranged(required(metadata, "blockGridSide"), 8, 16, "blockGridSide");
        if (blockGridSide != 8 && blockGridSide != 16) fail("blockGridSide must be 8 or 16");
        int width = ranged(required(metadata, "width"), 1, limits.maxMapWidth(), "width");
        int height = ranged(required(metadata, "height"), 1, limits.maxMapHeight(), "height");
        long cells = (long) width * height;
        if (cells > limits.maxMapCells()) fail("Map cell count exceeds limit");

        ModLevelDefinition.Bounds bounds = parseBounds(required(metadata, "bounds"));
        ModLevelDefinition.Start start = parseStart(required(metadata, "start"), bounds);
        ModLevelDefinition.Music music = parseMusic(required(metadata, "music"), limits);
        AssetNames names = parseAssets(required(metadata, "assets"), reference, limits);
        List<ModLevelDefinition.ObjectEntry> objects = parseObjects(required(metadata, "objects"), limits);
        List<ModLevelDefinition.RingEntry> rings = parseRings(required(metadata, "rings"), limits);

        // Nothing is published until every bounded read, header, and cross-reference is valid.
        Records patterns = fixedRecords(root, names.patterns(), "GPTN", 32, 2048, "patterns");
        Records chunks = fixedRecords(root, names.chunks(), "GCHK", 8, 1024, "chunks");
        Blocks blocks = blocks(root, names.blocks(), blockGridSide);
        LayerMap foreground = map(root, names.foregroundMap(), "foreground map");
        LayerMap background = names.backgroundMap().isEmpty() ? null
                : map(root, names.backgroundMap().orElseThrow(), "background map");
        Records heights = fixedRecords(root, names.solidHeights(), "GSHG", 16, 256,
                "solid heights");
        Records widths = fixedRecords(root, names.solidWidths(), "GSWD", 16, 256,
                "solid widths");
        Records angles = fixedRecords(root, names.solidAngles(), "GSAN", 1, 256,
                "solid angles");
        Collision primary = collision(root, names.primaryCollision(), 0, "primary collision");
        Collision secondary = collision(root, names.secondaryCollision(), 1, "secondary collision");
        byte[][] palettes = palettes(root, names.palettes());

        if (foreground.width() != width || foreground.height() != height
                || (background != null && (background.width() != width || background.height() != height))) {
            fail("Map header dimensions do not match level.json dimensions");
        }
        if (heights.count() != widths.count() || heights.count() != angles.count()) {
            fail("Solid height, width, and angle profile counts must match");
        }
        if (primary.count() != chunks.count() || secondary.count() != chunks.count()) {
            fail("Collision count must match chunk count");
        }
        validateReferences(patterns.count(), chunks, blocks, heights.count(), primary, secondary,
                foreground, background);

        return new ModLevelDefinition(1, zoneName, zoneIndex, levelIndex, blockGridSide, width,
                height, bounds, start, music, objects, rings, patterns.payload(), chunks.payload(),
                blocks.payload(), foreground.cells(), background == null ? null : background.cells(),
                heights.payload(), widths.payload(), angles.payload(), primary.indices(),
                secondary.indices(), palettes, patterns.count(), chunks.count(), blocks.count(),
                heights.count());
    }

    private static JsonNode parseJson(byte[] json, ModInputLimits limits) throws IOException {
        if (json.length > limits.maxMetadataBytes()) fail("level.json exceeds metadata byte limit");
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(json));
        } catch (CharacterCodingException error) {
            throw new IOException("level.json is not valid UTF-8", error);
        }
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(limits.maxYamlDepth())
                .maxDocumentLength(limits.maxDocumentCodePoints())
                .maxTokenCount(Math.max(128L, limits.maxCollectionEntries() * 16L))
                .maxStringLength(limits.maxStringChars()).maxNameLength(limits.maxStringChars())
                .maxNumberLength(limits.maxNumericDigits()).build();
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(constraints).build();
        ObjectMapper mapper = new ObjectMapper(factory);
        try (JsonParser parser = mapper.createParser(json)) {
            JsonNode node = mapper.readTree(parser);
            if (node == null || !node.isObject()) fail("level.json root must be an object");
            if (parser.nextToken() != null) fail("Trailing JSON token is not allowed");
            return node;
        } catch (IOException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("level.json")) throw error;
            throw new IOException("Invalid level.json: " + error.getMessage(), error);
        }
    }

    private static ModLevelDefinition.Bounds parseBounds(JsonNode node) throws IOException {
        object(node, "bounds"); only(node, BOUNDS, "bounds");
        int minX = ranged(required(node, "minX"), 0, 0xFFFF, "bounds.minX");
        int maxX = ranged(required(node, "maxX"), 0, 0xFFFF, "bounds.maxX");
        int minY = ranged(required(node, "minY"), Short.MIN_VALUE, Short.MAX_VALUE, "bounds.minY");
        int maxY = ranged(required(node, "maxY"), Short.MIN_VALUE, Short.MAX_VALUE, "bounds.maxY");
        if (minX > maxX || minY > maxY) fail("bounds must be ordered");
        return new ModLevelDefinition.Bounds(minX, maxX, minY, maxY);
    }

    private static ModLevelDefinition.Start parseStart(JsonNode node, ModLevelDefinition.Bounds bounds)
            throws IOException {
        object(node, "start"); only(node, START, "start");
        int x = ranged(required(node, "x"), 0, 0xFFFF, "start.x");
        int y = ranged(required(node, "y"), Short.MIN_VALUE, Short.MAX_VALUE, "start.y");
        if (x < bounds.minX() || x > bounds.maxX() || y < bounds.minY() || y > bounds.maxY()) {
            fail("start must lie within bounds");
        }
        return new ModLevelDefinition.Start(x, y);
    }

    private static ModLevelDefinition.Music parseMusic(JsonNode node, ModInputLimits limits)
            throws IOException {
        object(node, "music"); only(node, MUSIC, "music");
        boolean stock = node.has("stockId"), track = node.has("trackKey");
        if (stock == track) fail("music must contain exactly one of stockId or trackKey");
        if (stock) return new ModLevelDefinition.StockMusic(
                ranged(required(node, "stockId"), 0, 0xFFFF, "music.stockId"));
        JsonNode key = required(node, "trackKey"); object(key, "trackKey"); only(key, TRACK, "trackKey");
        try {
            return new ModLevelDefinition.TrackMusic(new TrackKey(
                    text(required(key, "modId"), "trackKey.modId", limits),
                    text(required(key, "name"), "trackKey.name", limits)));
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid trackKey: " + error.getMessage(), error);
        }
    }

    private static AssetNames parseAssets(JsonNode node, BakedLevelRef ref, ModInputLimits limits)
            throws IOException {
        object(node, "assets"); only(node, ASSETS, "assets");
        return new AssetNames(asset(node, "patterns", ref, limits), asset(node, "chunks", ref, limits),
                asset(node, "blocks", ref, limits), asset(node, "foregroundMap", ref, limits),
                node.has("backgroundMap") ? Optional.of(asset(node, "backgroundMap", ref, limits))
                        : Optional.empty(),
                asset(node, "solidHeights", ref, limits), asset(node, "solidWidths", ref, limits),
                asset(node, "solidAngles", ref, limits), asset(node, "collisionPrimary", ref, limits),
                asset(node, "collisionSecondary", ref, limits), asset(node, "palettes", ref, limits));
    }

    private static String asset(JsonNode node, String field, BakedLevelRef ref, ModInputLimits limits)
            throws IOException {
        String value = text(required(node, field), "assets." + field, limits);
        try {
            String safe = ModAssetRoot.requireNormalizedEntry(value);
            if (safe.getBytes(StandardCharsets.UTF_8).length > limits.maxEntryNameBytes()) {
                fail("asset name exceeds UTF-8 byte limit: " + field);
            }
            return ref.resolveAsset(safe);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid asset name for " + field, error);
        }
    }

    private static List<ModLevelDefinition.ObjectEntry> parseObjects(JsonNode node,
            ModInputLimits limits)
            throws IOException {
        if (!node.isArray() || node.size() > limits.maxLevelObjects()) {
            fail("objects must be an array within the object count limit");
        }
        List<ModLevelDefinition.ObjectEntry> result = new ArrayList<>(node.size());
        Set<Integer> placements = new HashSet<>();
        for (JsonNode value : node) {
            object(value, "object entry");
            boolean stock = value.has("stockObjectId"), keyed = value.has("objectKey");
            if (stock == keyed) fail("Object entry must contain exactly one of stockObjectId or objectKey");
            only(value, stock ? STOCK_OBJECT : KEYED_OBJECT, "object entry");
            int placement = ranged(required(value, "placementId"), 0, Integer.MAX_VALUE,
                    "object placementId");
            if (!placements.add(placement)) fail("Duplicate object placementId: " + placement);
            // Sonic 2 reserves x=$FFFF as the placement-list terminator and stores flags
            // in the high nibble of the 12-bit y word.
            int x = ranged(required(value, "x"), 0, 0xFFFE, "object x");
            int y = ranged(required(value, "y"), 0, 0x0FFF, "object y");
            int subtype = ranged(required(value, "subtype"), 0, 0xFF, "object subtype");
            int render = ranged(required(value, "renderFlags"), 0, 3, "object renderFlags");
            boolean respawn = bool(required(value, "respawnTracked"), "object respawnTracked");
            int rawY = ranged(required(value, "rawYWord"), 0, 0xFFFF, "object rawYWord");
            int expectedRawY = y | (render << 13) | (respawn ? 0x8000 : 0);
            if (rawY != expectedRawY) {
                fail("object rawYWord is inconsistent with y, renderFlags, and respawnTracked");
            }
            if (stock) {
                result.add(new ModLevelDefinition.StockObjectSpawn(placement, x, y,
                        ranged(required(value, "stockObjectId"), 0, 0xFF, "stockObjectId"),
                        subtype, render, respawn, rawY));
            } else {
                String key = text(required(value, "objectKey"), "objectKey", limits);
                try { key = ModKeySyntax.requireDisplayKey(key); }
                catch (IllegalArgumentException error) { throw new IOException("Invalid objectKey", error); }
                result.add(new ModLevelDefinition.KeyedObjectSpawn(placement, x, y, key,
                        subtype, render, respawn, rawY));
            }
        }
        return List.copyOf(result);
    }

    private static List<ModLevelDefinition.RingEntry> parseRings(JsonNode node,
            ModInputLimits limits) throws IOException {
        if (!node.isArray() || node.size() > limits.maxLevelRings()) {
            fail("rings must be an array within the ring count limit");
        }
        Set<Integer> placements = new HashSet<>();
        List<ModLevelDefinition.RingEntry> result = new ArrayList<>(node.size());
        for (JsonNode value : node) {
            object(value, "ring entry"); only(value, RING, "ring entry");
            int placement = ranged(required(value, "placementId"), 0, Integer.MAX_VALUE,
                    "ring placementId");
            if (!placements.add(placement)) fail("Duplicate ring placementId: " + placement);
            result.add(new ModLevelDefinition.RingEntry(placement,
                    ranged(required(value, "x"), 0, 0xFFFF, "ring x"),
                    ranged(required(value, "y"), 0, 0xFFFF, "ring y")));
        }
        return List.copyOf(result);
    }

    private static Records fixedRecords(ModAssetRoot root, String path, String magic,
                                        int expectedSize, int maximum, String label) throws IOException {
        ByteBuffer data = bytes(root, path, 12, 12L + (long) maximum * expectedSize, label);
        header(data, magic, label);
        int version = u16(data), recordSize = u16(data);
        long countLong = u32(data);
        if (version != 1) fail(label + " version must be 1");
        if (recordSize != expectedSize) fail(label + " record size must be " + expectedSize);
        if (countLong > maximum) fail(label + " count exceeds " + maximum);
        int count = (int) countLong;
        int expected = Math.toIntExact(12L + (long) count * recordSize);
        exactLength(data.capacity(), expected, label);
        byte[] payload = new byte[count * recordSize]; data.get(payload);
        return new Records(count, payload);
    }

    private static Blocks blocks(ModAssetRoot root, String path, int expectedSide) throws IOException {
        ByteBuffer data = bytes(root, path, 12, 12L + 256L * expectedSide * expectedSide * 2,
                "blocks"); header(data, "GBLK", "blocks");
        int version = u16(data), side = u8(data), reserved = u8(data); long countLong = u32(data);
        if (version != 1) fail("blocks version must be 1");
        if (side != expectedSide || (side != 8 && side != 16)) fail("blocks grid side mismatch");
        if (reserved != 0) fail("blocks reserved field must be zero");
        if (countLong > 256) fail("blocks count exceeds 256");
        int count = (int) countLong;
        int bytesPer = Math.multiplyExact(Math.multiplyExact(side, side), 2);
        exactLength(data.capacity(), Math.toIntExact(12L + (long) count * bytesPer), "blocks");
        byte[] payload = new byte[count * bytesPer]; data.get(payload);
        return new Blocks(count, side, payload);
    }

    private static LayerMap map(ModAssetRoot root, String path, String label) throws IOException {
        ByteBuffer data = bytes(root, path, 16, 16L + root.limits().maxMapCells(), label);
        header(data, "GMAP", label);
        int version = u16(data), width = u16(data), height = u16(data), layers = u16(data);
        long cellCount = u32(data), calculated = (long) width * height;
        if (version != 1) fail(label + " version must be 1");
        if (width == 0 || height == 0 || layers != 1 || cellCount != calculated) {
            fail(label + " dimensions/layers/cell count are inconsistent");
        }
        exactLength(data.capacity(), Math.toIntExact(16L + cellCount), label);
        byte[] cells = new byte[(int) cellCount]; data.get(cells);
        return new LayerMap(width, height, cells);
    }

    private static Collision collision(ModAssetRoot root, String path, int expectedPath, String label)
            throws IOException {
        ByteBuffer data = bytes(root, path, 12, 12L + 1024L * 2, label);
        header(data, "GCOL", label);
        int version = u16(data), collisionPath = u8(data), recordSize = u8(data);
        long countLong = u32(data);
        if (version != 1) fail(label + " version must be 1");
        if (collisionPath != expectedPath) fail(label + " path must be " + expectedPath);
        if (recordSize != 2) fail(label + " record size must be 2");
        if (countLong > 1024) fail(label + " count exceeds 1024");
        int count = (int) countLong;
        exactLength(data.capacity(), Math.toIntExact(12L + countLong * 2), label);
        int[] indices = new int[count]; for (int i = 0; i < count; i++) indices[i] = u16(data);
        return new Collision(count, indices);
    }

    private static byte[][] palettes(ModAssetRoot root, String path) throws IOException {
        ByteBuffer data = bytes(root, path, 12, 12L + 4L * 16 * 2, "palettes");
        header(data, "GPAL", "palettes");
        int version = u16(data), lineCount = u16(data), colors = u16(data), reserved = u16(data);
        if (version != 1) fail("palettes version must be 1");
        if (lineCount < 1 || lineCount > 4) fail("palette line count must be 1..4");
        if (colors != 16 || reserved != 0) fail("palettes colors/reserved header is invalid");
        exactLength(data.capacity(), 12 + lineCount * 32, "palettes");
        byte[][] lines = new byte[4][32];
        for (int i = 0; i < lineCount; i++) {
            data.get(lines[i]);
            ByteBuffer colorsInLine = ByteBuffer.wrap(lines[i]).order(ByteOrder.BIG_ENDIAN);
            while (colorsInLine.hasRemaining()) {
                int color = Short.toUnsignedInt(colorsInLine.getShort());
                if ((color & ~0x0EEE) != 0) {
                    fail("Genesis color contains bits that the engine would mask");
                }
            }
        }
        return lines;
    }

    private static ByteBuffer bytes(ModAssetRoot root, String path, int minimum, long formatMaximum,
                                    String label)
            throws IOException {
        long cap = Math.min(root.limits().maxAssetBytes(), formatMaximum);
        byte[] value = root.readBounded(path, cap);
        if (value.length < minimum) fail(label + " is shorter than its header");
        return ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
    }

    private static void header(ByteBuffer data, String expected, String label) throws IOException {
        byte[] magic = new byte[4]; data.get(magic);
        if (!expected.equals(new String(magic, StandardCharsets.US_ASCII))) {
            fail(label + " magic must be " + expected);
        }
    }

    private static void validateReferences(int patternCount, Records chunks, Blocks blocks,
            int profiles, Collision primary, Collision secondary, LayerMap foreground,
            LayerMap background) throws IOException {
        ByteBuffer chunkData = ByteBuffer.wrap(chunks.payload()).order(ByteOrder.BIG_ENDIAN);
        while (chunkData.hasRemaining()) {
            int pattern = Short.toUnsignedInt(chunkData.getShort()) & 0x7FF;
            if (pattern >= patternCount) fail("Chunk descriptor references missing pattern " + pattern);
        }
        ByteBuffer blockData = ByteBuffer.wrap(blocks.payload()).order(ByteOrder.BIG_ENDIAN);
        while (blockData.hasRemaining()) {
            int chunk = Short.toUnsignedInt(blockData.getShort()) & 0x3FF;
            if (chunk >= chunks.count()) fail("Block descriptor references missing chunk " + chunk);
        }
        validateCollisionRefs(primary, profiles, "primary");
        validateCollisionRefs(secondary, profiles, "secondary");
        validateMapRefs(foreground, blocks.count(), "foreground");
        if (background != null) validateMapRefs(background, blocks.count(), "background");
    }

    private static void validateCollisionRefs(Collision collision, int profiles, String label)
            throws IOException {
        for (int index : collision.indices()) {
            if (index >= profiles) fail(label + " collision references missing solid profile " + index);
        }
    }

    private static void validateMapRefs(LayerMap map, int blocks, String label) throws IOException {
        for (byte cell : map.cells()) {
            int index = Byte.toUnsignedInt(cell);
            if (index >= blocks) fail(label + " map references missing block " + index);
        }
    }

    private static void exactLength(int actual, int expected, String label) throws IOException {
        if (actual != expected) fail(label + " length must be exactly " + expected + " bytes");
    }
    private static int u8(ByteBuffer data) { return Byte.toUnsignedInt(data.get()); }
    private static int u16(ByteBuffer data) { return Short.toUnsignedInt(data.getShort()); }
    private static long u32(ByteBuffer data) { return Integer.toUnsignedLong(data.getInt()); }
    private static void object(JsonNode node, String field) throws IOException {
        if (!node.isObject()) fail(field + " must be an object");
    }
    private static JsonNode required(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) fail("Missing or null field: " + field);
        return value;
    }
    private static void only(JsonNode node, Set<String> allowed, String context) throws IOException {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) { String name = names.next(); if (!allowed.contains(name)) {
            fail("Unknown " + context + " field: " + name);
        }}
    }
    private static int integer(JsonNode node, String field) throws IOException {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) fail(field + " must be an integer");
        return node.intValue();
    }
    private static int ranged(JsonNode node, int min, int max, String field) throws IOException {
        int value = integer(node, field);
        if (value < min || value > max) fail(field + " must be in " + min + ".." + max);
        return value;
    }
    private static String text(JsonNode node, String field, ModInputLimits limits) throws IOException {
        if (!node.isTextual()) fail(field + " must be a string");
        String value = node.textValue();
        if (value.length() > limits.maxStringChars()) fail(field + " exceeds string limit");
        return value;
    }
    private static String nonblank(JsonNode node, String field, ModInputLimits limits) throws IOException {
        String value = text(node, field, limits);
        if (value.isBlank()) fail(field + " must not be blank");
        return value;
    }
    private static boolean bool(JsonNode node, String field) throws IOException {
        if (!node.isBoolean()) fail(field + " must be a boolean"); return node.booleanValue();
    }
    private static void fail(String message) throws IOException { throw new IOException(message); }

    private record AssetNames(String patterns, String chunks, String blocks, String foregroundMap,
            Optional<String> backgroundMap, String solidHeights, String solidWidths,
            String solidAngles, String primaryCollision, String secondaryCollision, String palettes) {}
    private record Records(int count, byte[] payload) {}
    private record Blocks(int count, int side, byte[] payload) {}
    private record LayerMap(int width, int height, byte[] cells) {}
    private record Collision(int count, int[] indices) {}
}

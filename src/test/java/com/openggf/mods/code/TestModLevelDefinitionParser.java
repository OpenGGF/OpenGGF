package com.openggf.mods.code;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.TrackKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestModLevelDefinitionParser {
    @TempDir Path temp;

    @Test
    void v2ParsesTypedS3kMetadataAndSparseClaims() throws Exception {
        ModLevelDefinition level = readFixture("s3k-v2-valid");

        assertEquals(2, level.formatVersion());
        assertEquals(ModLevelDefinition.S3kObjectZoneSet.S3KL,
                level.s3kMetadata().orElseThrow().objectZoneSet());
        assertEquals(List.of(new ModPaletteClaim(1, 0, 0x0EEE)), level.paletteClaims());
        assertEquals(0, level.paletteLines().length);
    }

    @Test
    void v2RejectsUnknownZoneSetDuplicateClaimAndLineZero() {
        assertFormatError("s3k-v2-unknown-set", "objectZoneSet");
        assertFormatError("s3k-v2-duplicate-claim", "Duplicate palette claim");
        assertFormatError("s3k-v2-line-zero", "creator palette line must be 1..3");
    }

    @Test
    void v1StillProducesFourCompleteLegacyPaletteLines() throws Exception {
        ModLevelDefinition level = readFixture("s2-v1-valid");

        assertEquals(1, level.formatVersion());
        assertEquals(4, level.paletteLines().length);
        assertTrue(level.s3kMetadata().isEmpty());
        assertTrue(level.paletteClaims().isEmpty());
    }

    @Test
    void versionsEnforceSeparateExactRootAssetAndMetadataKeys() throws Exception {
        String v1 = fixtureJson("s2-v1-valid").replace("  \"objects\": []",
                "  \"hostMetadata\": {\"s3k\": {\"objectZoneSet\": \"S3KL\"}},\n"
                        + "  \"objects\": []");
        assertFixtureJsonRejected(v1, "Unknown level.json field: hostMetadata");

        String v2 = fixtureJson("s3k-v2-valid");
        assertFixtureJsonRejected(v2.replace("\"collisionSecondary\": \"collision-secondary.bin\"",
                "\"collisionSecondary\": \"collision-secondary.bin\", \"palettes\": \"palettes.bin\""),
                "Unknown assets field: palettes");
        assertFixtureJsonRejected(v2.replace("\"hostMetadata\": {\"s3k\": {",
                "\"hostMetadata\": {\"otherHost\": {}, \"s3k\": {"),
                "Unknown hostMetadata field: otherHost");
        assertFixtureJsonRejected(v2.replace("\"objectZoneSet\": \"S3KL\"",
                "\"objectZoneSet\": \"S3KL\", \"surprise\": true"),
                "Unknown hostMetadata.s3k field: surprise");
        assertFixtureJsonRejected(v2.replace("\"sega\": 3822",
                "\"sega\": 3822, \"surprise\": true"),
                "Unknown palette claim field: surprise");
    }

    @Test
    void readsExactV1LevelAndDefensivelyOwnsDecodedPayloads() throws Exception {
        writeMinimalLevel(validJson(false));
        try (ModAssetRoot root = openRoot()) {
            ModLevelDefinition level = ModLevelDefinitionParser.read(
                    root, new BakedLevelRef("levels/demo/level.json"));

            assertEquals("Demo Zone", level.zoneName());
            assertEquals(0x40, level.zoneIndex());
            assertEquals(0x400, level.levelIndex());
            assertEquals(new ModLevelDefinition.Bounds(0, 127, -32, 96), level.bounds());
            assertEquals(new ModLevelDefinition.Start(32, 48), level.start());
            assertEquals(new TrackKey("example", "music/demo"),
                    ((ModLevelDefinition.TrackMusic) level.music()).trackKey());
            assertEquals(1, level.patternCount());
            assertEquals(1, level.chunkCount());
            assertEquals(1, level.blockCount());
            assertEquals(1, level.solidProfileCount());
            assertArrayEquals(new byte[32], level.patternBytes());
            assertArrayEquals(new byte[] {0}, level.foregroundMap());
            assertTrue(level.backgroundMap().isEmpty());
            assertEquals(2, level.objects().size());
            assertInstanceOf(ModLevelDefinition.StockObjectSpawn.class, level.objects().get(0));
            assertEquals("example:objects/demo",
                    ((ModLevelDefinition.KeyedObjectSpawn) level.objects().get(1)).objectKey());
            assertEquals(1, level.rings().size());

            byte[] patterns = level.patternBytes();
            patterns[0] = 99;
            assertEquals(0, level.patternBytes()[0]);
            assertThrows(UnsupportedOperationException.class,
                    () -> level.objects().add(level.objects().get(0)));
        }
    }

    @Test
    void acceptsStockMusicAndOptionalBackground() throws Exception {
        String json = validJson(true).replace(
                "\"trackKey\":{\"modId\":\"example\",\"name\":\"music/demo\"}",
                "\"stockId\":129");
        writeMinimalLevel(json);
        try (ModAssetRoot root = openRoot()) {
            ModLevelDefinition level = ModLevelDefinitionParser.read(
                    root, new BakedLevelRef("levels/demo/level.json"));
            assertEquals(129, ((ModLevelDefinition.StockMusic) level.music()).stockId());
            assertArrayEquals(new byte[] {0}, level.backgroundMap().orElseThrow());
        }
    }

    @Test
    void rejectsUnknownKeysAndInvalidTaggedUnions() throws Exception {
        assertJsonRejected(validJson(false).replace("\"formatVersion\":1,",
                "\"formatVersion\":1,\"surprise\":true,"), "Unknown");
        assertJsonRejected(validJson(false).replace("\"stockObjectId\":3,",
                "\"stockObjectId\":3,\"objectKey\":\"example:o\","), "exactly one");
        assertJsonRejected(validJson(false).replace(
                "\"trackKey\":{\"modId\":\"example\",\"name\":\"music/demo\"}",
                "\"stockId\":1,\"trackKey\":{\"modId\":\"example\",\"name\":\"music/demo\"}"),
                "exactly one");
    }

    @Test
    void rejectsValuesThatEngineTypesWouldMask() throws Exception {
        assertJsonRejected(validJson(false).replace("\"stockObjectId\":3", "\"stockObjectId\":256"),
                "stockObjectId");
        assertJsonRejected(validJson(false).replace("\"renderFlags\":0", "\"renderFlags\":4"),
                "renderFlags");
        assertJsonRejected(validJson(false).replace("\"x\":16,\"y\":32", "\"x\":65536,\"y\":32"),
                "x");
        assertJsonRejected(validJson(false).replace("\"placementId\":0", "\"placementId\":-1"),
                "placementId");
        assertJsonRejected(validJson(false).replace("\"zoneIndex\":64", "\"zoneIndex\":11"),
                "zoneIndex");
        assertJsonRejected(validJson(false).replace("\"levelIndex\":1024", "\"levelIndex\":11"),
                "levelIndex");
    }

    @Test
    void parserAcceptsGenericSixteenBySixteenBlockGrid() throws Exception {
        writeMinimalLevel(validJson(false).replace("\"blockGridSide\":8", "\"blockGridSide\":16"));
        overwrite("blocks.bin", blocksFile(16, 0, 1));
        try (ModAssetRoot root = openRoot()) {
            assertEquals(16, ModLevelDefinitionParser.read(root,
                    new BakedLevelRef("levels/demo/level.json")).blockGridSide());
        }
    }

    @Test
    void rejectsUnsafeAssetNamesBeforeReadingPayloads() throws Exception {
        assertJsonRejected(validJson(false).replace("\"patterns.bin\"", "\"../patterns.bin\""),
                "asset");
        assertThrows(IllegalArgumentException.class, () -> new BakedLevelRef("../level.json"));
        assertThrows(IllegalArgumentException.class, () -> new BakedLevelRef("levels/demo/not-level.json"));
    }

    @Test
    void rejectsWrongHeadersVersionsReservedFieldsAndTrailingBytes() throws Exception {
        writeMinimalLevel(validJson(false));
        overwrite("patterns.bin", patternFile("BAD!", 1, 32, 1, new byte[32], false));
        assertReadRejected("GPTN");

        writeMinimalLevel(validJson(false));
        overwrite("patterns.bin", patternFile("GPTN", 2, 32, 1, new byte[32], false));
        assertReadRejected("version");

        writeMinimalLevel(validJson(false));
        overwrite("blocks.bin", blocksFile(8, 1, 1));
        assertReadRejected("reserved");

        writeMinimalLevel(validJson(false));
        overwrite("patterns.bin", patternFile("GPTN", 1, 32, 1, new byte[32], true));
        assertReadRejected("length");
    }

    @Test
    void rejectsHeaderCountsThatDoNotMatchPayloadOrRelatedFiles() throws Exception {
        writeMinimalLevel(validJson(false));
        overwrite("patterns.bin", patternFile("GPTN", 1, 32, 2, new byte[32], false));
        assertReadRejected("length");

        writeMinimalLevel(validJson(false));
        overwrite("solid-angles.bin", simpleRecords("GSAN", 1, 1, 2, new byte[] {0, 0}));
        assertReadRejected("profile count");

        writeMinimalLevel(validJson(false));
        overwrite("collision-primary.bin", collisionFile(0, 2, new int[] {0, 0}));
        assertReadRejected("chunk count");

        writeMinimalLevel(validJson(false));
        overwrite("fg-map.bin", mapFile(2, 1, new byte[] {0, 0}));
        assertReadRejected("level.json dimensions");
    }

    @Test
    void rejectsOutOfRangeDescriptorAndCollisionReferences() throws Exception {
        writeMinimalLevel(validJson(false));
        overwrite("chunks.bin", chunksFile(1, new int[] {1, 0, 0, 0}));
        assertReadRejected("pattern");

        writeMinimalLevel(validJson(false));
        overwrite("blocks.bin", blocksFile(8, 0, 1));
        byte[] block = Files.readAllBytes(asset("blocks.bin"));
        block[12] = 0x00;
        block[13] = 0x01;
        overwrite("blocks.bin", block);
        assertReadRejected("chunk");

        writeMinimalLevel(validJson(false));
        overwrite("collision-secondary.bin", collisionFile(1, 1, new int[] {1}));
        assertReadRejected("solid profile");

        writeMinimalLevel(validJson(false));
        overwrite("fg-map.bin", mapFile(1, 1, new byte[] {1}));
        assertReadRejected("block");

        writeMinimalLevel(validJson(false));
        byte[] palette = paletteFile(4);
        palette[12] = (byte) 0xF0;
        overwrite("palettes.bin", palette);
        assertReadRejected("Genesis color");
    }

    @Test
    void rejectsDuplicatePlacementIdentitiesAndInvalidBounds() throws Exception {
        assertJsonRejected(validJson(false).replace(
                "{\"placementId\":2,\"x\":24,",
                "{\"placementId\":0,\"x\":24,"), "placementId");
        assertJsonRejected(validJson(false).replace("\"minX\":0,\"maxX\":127",
                "\"minX\":128,\"maxX\":127"), "bounds");
        assertJsonRejected(validJson(false).replace("\"x\":32,\"y\":48},\"music\"",
                "\"x\":128,\"y\":48},\"music\""), "start");
    }

    @Test
    void honorsLoweredRootLevelLimits() throws Exception {
        writeMinimalLevel(validJson(false));
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxLevelObjects(1).build();
        try (ModAssetRoot root = ModAssetRoot.directory(temp, temp, limits, DirectoryAccess.TEST)) {
            IOException error = assertThrows(IOException.class, () -> ModLevelDefinitionParser.read(
                    root, new BakedLevelRef("levels/demo/level.json")));
            assertTrue(error.getMessage().contains("object count"), error::getMessage);
        }
    }

    @Test
    void enforcesLosslessSonic2ObjectCoordinateAndRawWordBoundaries() throws Exception {
        String maxX = validJson(false)
                .replace("\"maxX\":127", "\"maxX\":65534")
                .replace("\"x\":16,\"y\":32,\"stockObjectId\"",
                        "\"x\":65534,\"y\":32,\"stockObjectId\"");
        assertJsonAccepted(maxX);
        assertJsonRejected(maxX.replace("\"maxX\":65534", "\"maxX\":65535")
                .replace("\"x\":65534,\"y\":32,\"stockObjectId\"",
                        "\"x\":65535,\"y\":32,\"stockObjectId\""), "object x");

        String maxY = validJson(false)
                .replace("\"maxY\":96", "\"maxY\":4095")
                .replace("\"x\":16,\"y\":32,\"stockObjectId\"",
                        "\"x\":16,\"y\":4095,\"stockObjectId\"")
                .replace("\"rawYWord\":32800", "\"rawYWord\":36863");
        assertJsonAccepted(maxY);
        assertJsonRejected(maxY.replace("\"maxY\":4095", "\"maxY\":4096")
                .replace("\"y\":4095,\"stockObjectId\"", "\"y\":4096,\"stockObjectId\"")
                .replace("\"rawYWord\":36863", "\"rawYWord\":36864"), "object y");

        assertJsonRejected(validJson(false).replace("\"rawYWord\":32800", "\"rawYWord\":32"),
                "rawYWord");
    }

    private void assertJsonAccepted(String json) throws Exception {
        writeMinimalLevel(json);
        try (ModAssetRoot root = openRoot()) {
            assertNotNull(ModLevelDefinitionParser.read(
                    root, new BakedLevelRef("levels/demo/level.json")));
        }
    }

    private ModLevelDefinition readFixture(String name) throws Exception {
        String json = fixtureJson(name);
        writeMinimalLevel(json);
        if (isV2Json(json)) Files.deleteIfExists(asset("palettes.bin"));
        try (ModAssetRoot root = openRoot()) {
            return ModLevelDefinitionParser.read(root, new BakedLevelRef("levels/demo/level.json"));
        }
    }

    private String fixtureJson(String name) throws IOException {
        String resource = "/mods/formats/" + name + "/level.json";
        try (var input = TestModLevelDefinitionParser.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing test fixture: " + resource);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void assertFormatError(String name, String messagePart) {
        IOException error = assertThrows(IOException.class, () -> readFixture(name));
        assertTrue(error.getMessage().contains(messagePart), error::getMessage);
    }

    private void assertFixtureJsonRejected(String json, String messagePart) throws Exception {
        writeMinimalLevel(json);
        if (isV2Json(json)) Files.deleteIfExists(asset("palettes.bin"));
        assertReadRejected(messagePart);
    }

    private static boolean isV2Json(String json) {
        return json.matches("(?s).*\\\"formatVersion\\\"\\s*:\\s*2(?:\\s*[,}]).*");
    }

    private void assertJsonRejected(String json, String messagePart) throws Exception {
        writeMinimalLevel(json);
        assertReadRejected(messagePart);
    }

    private void assertReadRejected(String messagePart) throws Exception {
        try (ModAssetRoot root = openRoot()) {
            IOException error = assertThrows(IOException.class, () -> ModLevelDefinitionParser.read(
                    root, new BakedLevelRef("levels/demo/level.json")));
            assertTrue(error.getMessage().toLowerCase().contains(messagePart.toLowerCase()), error::getMessage);
        }
    }

    private ModAssetRoot openRoot() throws IOException {
        return ModAssetRoot.directory(temp, temp, ModInputLimits.production(), DirectoryAccess.TEST);
    }

    private Path asset(String name) { return temp.resolve("levels/demo").resolve(name); }
    private void overwrite(String name, byte[] bytes) throws IOException { Files.write(asset(name), bytes); }

    private void writeMinimalLevel(String json) throws IOException {
        Path dir = temp.resolve("levels/demo");
        Files.createDirectories(dir);
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("level.json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assets.put("patterns.bin", patternFile("GPTN", 1, 32, 1, new byte[32], false));
        assets.put("chunks.bin", chunksFile(1, new int[] {0, 0, 0, 0}));
        assets.put("blocks.bin", blocksFile(8, 0, 1));
        assets.put("fg-map.bin", mapFile(1, 1, new byte[] {0}));
        assets.put("bg-map.bin", mapFile(1, 1, new byte[] {0}));
        assets.put("solid-heights.bin", simpleRecords("GSHG", 1, 16, 1, new byte[16]));
        assets.put("solid-widths.bin", simpleRecords("GSWD", 1, 16, 1, new byte[16]));
        assets.put("solid-angles.bin", simpleRecords("GSAN", 1, 1, 1, new byte[] {0}));
        assets.put("collision-primary.bin", collisionFile(0, 1, new int[] {0}));
        assets.put("collision-secondary.bin", collisionFile(1, 1, new int[] {0}));
        assets.put("palettes.bin", paletteFile(4));
        for (var entry : assets.entrySet()) Files.write(dir.resolve(entry.getKey()), entry.getValue());
    }

    private static String validJson(boolean withBackground) {
        return "{" +
                "\"formatVersion\":1," +
                "\"zoneName\":\"Demo Zone\"," +
                "\"zoneIndex\":64," +
                "\"levelIndex\":1024," +
                "\"blockGridSide\":8," +
                "\"width\":1," +
                "\"height\":1," +
                "\"bounds\":{\"minX\":0,\"maxX\":127,\"minY\":-32,\"maxY\":96}," +
                "\"start\":{\"x\":32,\"y\":48}," +
                "\"music\":{\"trackKey\":{\"modId\":\"example\",\"name\":\"music/demo\"}}," +
                "\"assets\":{" +
                "\"patterns\":\"patterns.bin\",\"chunks\":\"chunks.bin\",\"blocks\":\"blocks.bin\"," +
                "\"foregroundMap\":\"fg-map.bin\"," +
                (withBackground ? "\"backgroundMap\":\"bg-map.bin\"," : "") +
                "\"solidHeights\":\"solid-heights.bin\",\"solidWidths\":\"solid-widths.bin\"," +
                "\"solidAngles\":\"solid-angles.bin\",\"collisionPrimary\":\"collision-primary.bin\"," +
                "\"collisionSecondary\":\"collision-secondary.bin\",\"palettes\":\"palettes.bin\"}," +
                "\"objects\":[" +
                "{\"placementId\":0,\"x\":16,\"y\":32,\"stockObjectId\":3,\"subtype\":4," +
                "\"renderFlags\":0,\"respawnTracked\":true,\"rawYWord\":32800}," +
                "{\"placementId\":2,\"x\":24,\"y\":32,\"objectKey\":\"example:objects/demo\",\"subtype\":0," +
                "\"renderFlags\":1,\"respawnTracked\":false,\"rawYWord\":8224}]," +
                "\"rings\":[{\"placementId\":1,\"x\":48,\"y\":32}]}";
    }

    private static byte[] patternFile(String magic, int version, int recordSize, int count,
                                      byte[] payload, boolean trailing) throws IOException {
        return binary(out -> { out.writeBytes(magic); out.writeShort(version); out.writeShort(recordSize);
            out.writeInt(count); out.write(payload); if (trailing) out.writeByte(0); });
    }

    private static byte[] chunksFile(int count, int[] descriptors) throws IOException {
        return binary(out -> { out.writeBytes("GCHK"); out.writeShort(1); out.writeShort(8); out.writeInt(count);
            for (int value : descriptors) out.writeShort(value); });
    }

    private static byte[] blocksFile(int side, int reserved, int count) throws IOException {
        return binary(out -> { out.writeBytes("GBLK"); out.writeShort(1); out.writeByte(side); out.writeByte(reserved);
            out.writeInt(count); for (int i = 0; i < side * side * count; i++) out.writeShort(0); });
    }

    private static byte[] mapFile(int width, int height, byte[] cells) throws IOException {
        return binary(out -> { out.writeBytes("GMAP"); out.writeShort(1); out.writeShort(width); out.writeShort(height);
            out.writeShort(1); out.writeInt(cells.length); out.write(cells); });
    }

    private static byte[] simpleRecords(String magic, int version, int size, int count, byte[] payload)
            throws IOException {
        return binary(out -> { out.writeBytes(magic); out.writeShort(version); out.writeShort(size);
            out.writeInt(count); out.write(payload); });
    }

    private static byte[] collisionFile(int path, int count, int[] values) throws IOException {
        return binary(out -> { out.writeBytes("GCOL"); out.writeShort(1); out.writeByte(path); out.writeByte(2);
            out.writeInt(count); for (int value : values) out.writeShort(value); });
    }

    private static byte[] paletteFile(int lines) throws IOException {
        return binary(out -> { out.writeBytes("GPAL"); out.writeShort(1); out.writeShort(lines); out.writeShort(16);
            out.writeShort(0); for (int i = 0; i < lines * 16; i++) out.writeShort(0); });
    }

    private static byte[] binary(IoConsumer<DataOutputStream> writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { writer.accept(out); }
        return bytes.toByteArray();
    }

    @FunctionalInterface private interface IoConsumer<T> { void accept(T value) throws IOException; }
}

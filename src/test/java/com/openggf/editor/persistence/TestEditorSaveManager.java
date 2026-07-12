package com.openggf.editor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.openggf.game.GameId;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.render.EditorTextRenderer;
import com.openggf.editor.render.EditorToolbarRenderer;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestEditorSaveManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void saveAndApplyRoundTripsModifiedBlockChunkAndMapCell() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setChunkInBlock(1, 0, 0, new ChunkDesc(2));
        edited.setBlockInMap(1, 2, 1, 2);
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        EditorSaveManager.SaveResult save = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, edited);
        MutableLevel fresh = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, fresh);

        assertTrue(save.ok());
        assertEquals(EditorSaveManager.ApplyResult.APPLIED, result);
        assertEquals(2, fresh.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(2, Byte.toUnsignedInt(fresh.getMap().getValue(1, 2, 1)));
        assertTrue(fresh.modifiedBlocksSinceBaseline().get(1));
        assertTrue(fresh.modifiedMapCellsSinceBaseline().get(1 * 12 + 1 * 4 + 2));
        assertFalse(fresh.isModifiedSinceLastSave());
    }

    @Test
    void saveOmitsBlockChunkAndMapCellEditsRevertedToBaseline() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setChunkInBlock(1, 0, 0, new ChunkDesc(2));
        edited.setChunkInBlock(1, 0, 0, new ChunkDesc(0));
        edited.setBlockInMap(1, 2, 1, 2);
        edited.setBlockInMap(1, 2, 1, 0);
        edited.setPatternDescInChunk(2, 0, 0, new PatternDesc(7));
        edited.setPatternDescInChunk(2, 0, 0, new PatternDesc(0));
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        EditorSaveManager.SaveResult save = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, edited);
        EditorSaveEnvelope envelope = MAPPER.readValue(save.file().toFile(), EditorSaveEnvelope.class);

        assertTrue(save.ok());
        assertTrue(envelope.payload().blocks().isEmpty());
        assertTrue(envelope.payload().chunks().isEmpty());
        assertTrue(envelope.payload().mapCells().isEmpty());
        assertFalse(edited.isModifiedSinceLastSave());
    }

    @Test
    void saveOmitsRuntimeTerrainMutationsAppliedThroughMutationSurface() throws Exception {
        MutableLevel edited = createMutableLevel();
        LevelMutationSurface surface = LevelMutationSurface.forLevel(edited);

        surface.setBlockInMap(0, 1, 1, 2);
        surface.restoreBlockState(1, new int[] { 2 });
        surface.restoreChunkState(2, chunkState(7));
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        EditorSaveManager.SaveResult save = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, edited);
        EditorSaveEnvelope envelope = MAPPER.readValue(save.file().toFile(), EditorSaveEnvelope.class);

        assertEquals(2, Byte.toUnsignedInt(edited.getMap().getValue(0, 1, 1)),
                "runtime mutation must still update live gameplay terrain");
        assertEquals(2, edited.getBlock(1).getChunkDesc(0, 0).getChunkIndex(),
                "runtime block mutation must still update the live block");
        assertEquals(7, edited.getChunk(2).getPatternDesc(0, 0).getPatternIndex(),
                "runtime chunk mutation must still update the live chunk");
        assertTrue(envelope.payload().blocks().isEmpty());
        assertTrue(envelope.payload().chunks().isEmpty());
        assertTrue(envelope.payload().mapCells().isEmpty());
    }

    @Test
    void saveKeepsEditorIntentWhenRuntimeMutationTouchesAlreadyEditedTerrain() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 1);
        edited.setChunkInBlock(1, 0, 0, new ChunkDesc(1));
        edited.setPatternDescInChunk(2, 0, 0, new PatternDesc(7));
        LevelMutationSurface surface = LevelMutationSurface.forLevel(edited);

        surface.setBlockInMap(0, 1, 1, 2);
        surface.restoreBlockState(1, new int[] { 2 });
        surface.restoreChunkState(2, chunkState(8));
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        EditorSaveManager.SaveResult save = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, edited);
        EditorSaveEnvelope envelope = MAPPER.readValue(save.file().toFile(), EditorSaveEnvelope.class);

        assertEquals(2, Byte.toUnsignedInt(edited.getMap().getValue(0, 1, 1)),
                "runtime mutation must stay present in the live level");
        assertEquals(2, edited.getBlock(1).getChunkDesc(0, 0).getChunkIndex(),
                "runtime block mutation must stay present in the live level");
        assertEquals(8, edited.getChunk(2).getPatternDesc(0, 0).getPatternIndex(),
                "runtime chunk mutation must stay present in the live level");
        assertEquals(List.of(new EditorSavePayload.MapCell(0, 1, 1, 1)), envelope.payload().mapCells());
        assertEquals(1, envelope.payload().blocks().size());
        assertEquals(1, envelope.payload().blocks().get(0).state()[0]);
        assertEquals(1, envelope.payload().chunks().size());
        assertEquals(7, envelope.payload().chunks().get(0).state()[0]);
    }

    @Test
    void missingFileReturnsNone() {
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        assertEquals(EditorSaveManager.ApplyResult.NONE,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, createMutableLevel()));
    }

    @Test
    void keyedObjectUsesV3TaggedIdentityAndRoundTripsOrSkipsWithOneFinding() throws Exception {
        MutableLevel edited = createMutableLevel();
        ObjectSpawn keyed = new ObjectSpawn(32, 48, 0, 2, 0, false, 48, 77,
                "owner", "owner:badnik");
        edited.replaceObjectSpawnsPersisted(List.of(keyed));
        EditorSaveManager writer = new EditorSaveManager(tempDir);
        Path file = writer.save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, edited).file();
        var tree = MAPPER.readTree(file.toFile());
        assertEquals(3, tree.get("version").intValue());
        assertEquals("owner:badnik", tree.at("/payload/objects/0/objectKey").textValue());
        assertTrue(tree.at("/payload/objects/0/stockObjectId").isMissingNode());

        MutableLevel restored = createMutableLevel();
        EditorSaveManager reader = new EditorSaveManager(tempDir,
                "owner:badnik"::equals, finding -> {});
        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                reader.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, restored));
        assertEquals("owner:badnik", restored.getObjects().getFirst().objectKey());

        List<EditorSaveManager.EditorApplyFinding> findings = new java.util.ArrayList<>();
        MutableLevel missing = createMutableLevel();
        EditorSaveManager missingReader = new EditorSaveManager(tempDir, key -> false, findings::add);
        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                missingReader.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, missing));
        assertTrue(missing.getObjects().isEmpty());
        assertEquals(1, findings.size());
        assertEquals(1, findings.getFirst().skippedCount());
    }

    @Test
    void explicitV1V2MigrationProducesOnlyStockTaggedV3Spawns() {
        EditorSavePayload.ObjectState stock = new EditorSavePayload.ObjectState(1, 2, 3, 4, 5, 0, false, 3);
        EditorSavePayloadV3 fromV2 = EditorSavePayloadMigrator.fromV2(
                new EditorSavePayload(List.of(), List.of(), List.of(), List.of(stock), List.of()));
        assertEquals(4, fromV2.objects().getFirst().stockObjectId());
        assertEquals(null, fromV2.objects().getFirst().objectKey());
        assertTrue(EditorSavePayloadMigrator.fromV1(
                new EditorSavePayloadV1(List.of(), List.of(), List.of())).objects().isEmpty());
    }

    @Test
    void missingKeySkipsExactlyItsBackedRingsAndReportsCombinedCount() throws Exception {
        MutableLevel edited = createMutableLevel();
        ObjectSpawn backing = new ObjectSpawn(32, 48, 0, 0, 0, false, 48, 77,
                "owner", "owner:ring-backing");
        RingSpawn backed = new RingSpawn(32, 48, 90);
        RingSpawn independent = new RingSpawn(64, 48, 91);
        edited.replaceSpawnsPersisted(List.of(backing), List.of(backed, independent),
                java.util.Map.of(backing, List.of(backed)));
        new EditorSaveManager(tempDir).save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, edited);

        List<EditorSaveManager.EditorApplyFinding> findings = new java.util.ArrayList<>();
        MutableLevel restored = createMutableLevel();
        EditorSaveManager reader = new EditorSaveManager(tempDir, key -> false, findings::add);
        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                reader.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, restored));
        assertEquals(List.of(independent), restored.getRings());
        assertEquals(2, findings.getFirst().skippedCount());
    }

    @Test
    void legacyV2ReaderQuarantinesV3InsteadOfInterpretingKeyAsNumericFallback() throws Exception {
        Path legacyRoot = tempDir.resolve("legacy-reader");
        MutableLevel keyed = createMutableLevel();
        keyed.replaceObjectSpawnsPersisted(List.of(new ObjectSpawn(
                32, 48, 0, 0, 0, false, 48, 9, "owner", "owner:key")));
        EditorSaveManager current = new EditorSaveManager(legacyRoot);
        Path file = current.save(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, keyed).file();

        EditorSaveManager legacy = EditorSaveManager.legacyV2Reader(legacyRoot);
        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED,
                legacy.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0,
                        createMutableLevel()));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void mismatchedZoneReturnsMismatchWithoutQuarantine() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 2);
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, edited).file();
        Path mismatchedPath = manager.editPath(GameId.S2, 1, 1);
        Files.createDirectories(mismatchedPath.getParent());
        Files.copy(file, mismatchedPath);

        MutableLevel fresh = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 1, fresh);

        assertEquals(EditorSaveManager.ApplyResult.MISMATCH, result);
        assertTrue(Files.exists(mismatchedPath));
        assertEquals(0, Byte.toUnsignedInt(fresh.getMap().getValue(0, 1, 1)));
    }

    @Test
    void tamperedPayloadIsQuarantined() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 2);
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, edited).file();
        String json = Files.readString(file);
        Files.writeString(file, json.replace("\"blockIndex\":2", "\"blockIndex\":1"));

        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, createMutableLevel());

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void quarantinePreservesExistingEditorRecoveryCopies() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 2);
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, edited).file();
        Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt");
        Path nextCorrupt = file.resolveSibling(file.getFileName() + ".corrupt.1");
        Files.writeString(corrupt, "old");
        Files.writeString(file, "{ not-json");

        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, createMutableLevel());

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertFalse(Files.exists(file));
        assertEquals("old", Files.readString(corrupt));
        assertEquals("{ not-json", Files.readString(nextCorrupt));
    }

    @Test
    void transientReadIOExceptionLeavesEditorSaveInPlace() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 2);
        EditorSaveManager writer = new EditorSaveManager(tempDir);
        Path file = writer.save(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, edited).file();
        String original = Files.readString(file);

        EditorSaveManager reader = new EditorSaveManager(tempDir, path -> {
            throw new java.io.IOException("temporary lock");
        });
        MutableLevel fresh = createMutableLevel();
        EditorSaveManager.ApplyResult result = reader.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, fresh);

        assertEquals(EditorSaveManager.ApplyResult.TRANSIENT_FAILURE, result);
        assertTrue(Files.exists(file), "transient I/O must not quarantine the editor save");
        assertEquals(original, Files.readString(file));
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
        assertEquals(0, Byte.toUnsignedInt(fresh.getMap().getValue(0, 1, 1)));
    }

    @Test
    void saveUsesAtomicMoveFallbackWhenFilesystemDoesNotSupportIt() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/editor/persistence/EditorSaveManager.java"));

        assertTrue(source.contains("catch (AtomicMoveNotSupportedException"),
                "editor saves must retry without ATOMIC_MOVE on filesystems that do not support it");
        assertTrue(source.contains("Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);"),
                "fallback move must still replace the target save file");
    }

    @Test
    void outOfRangePersistedMapBlockIsQuarantined() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSavePayload payload = new EditorSavePayload(
                List.of(),
                List.of(),
                List.of(new EditorSavePayload.MapCell(0, 1, 1, 99)));
        EditorSaveEnvelope envelope = new EditorSaveEnvelope(
                1,
                GameId.S2.code(),
                1,
                0,
                "2026-05-09T00:00:00Z",
                payload,
                sha256(MAPPER.writeValueAsString(payload)));
        Path file = manager.editPath(GameId.S2, 1, 0);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), envelope);

        MutableLevel level = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, level);

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 1)));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void invalidLaterPayloadEntryDoesNotPartiallyMutateLevel() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSavePayload payload = new EditorSavePayload(
                List.of(),
                List.of(),
                List.of(
                        new EditorSavePayload.MapCell(0, 1, 1, 2),
                        new EditorSavePayload.MapCell(0, 2, 1, 99)));
        EditorSaveEnvelope envelope = new EditorSaveEnvelope(
                1,
                GameId.S2.code(),
                1,
                0,
                "2026-05-09T00:00:00Z",
                payload,
                sha256(MAPPER.writeValueAsString(payload)));
        Path file = manager.editPath(GameId.S2, 1, 0);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), envelope);

        MutableLevel level = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, level);

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 1)),
                "valid earlier map edit must not be applied before later invalid entry quarantines");
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void wrongLengthChunkStateQuarantinesWithoutPartialMapApply() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSavePayload payload = new EditorSavePayload(
                List.of(),
                List.of(new EditorSavePayload.ChunkState(1, new int[] { 7 })),
                List.of(new EditorSavePayload.MapCell(0, 1, 1, 2)));
        EditorSaveEnvelope envelope = new EditorSaveEnvelope(
                1,
                GameId.S2.code(),
                1,
                0,
                "2026-05-09T00:00:00Z",
                payload,
                sha256(MAPPER.writeValueAsString(payload)));
        Path file = manager.editPath(GameId.S2, 1, 0);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), envelope);

        MutableLevel level = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, level);

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertEquals(0, level.getChunk(1).getPatternDesc(0, 0).getPatternIndex());
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 1)),
                "valid map edit must not be applied when a chunk state is invalid");
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void wrongLengthBlockStateQuarantinesWithoutPartialMapApply() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSavePayload payload = new EditorSavePayload(
                List.of(new EditorSavePayload.BlockState(1, new int[] { 2, 3 })),
                List.of(),
                List.of(new EditorSavePayload.MapCell(0, 1, 1, 2)));
        EditorSaveEnvelope envelope = new EditorSaveEnvelope(
                1,
                GameId.S2.code(),
                1,
                0,
                "2026-05-09T00:00:00Z",
                payload,
                sha256(MAPPER.writeValueAsString(payload)));
        Path file = manager.editPath(GameId.S2, 1, 0);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), envelope);

        MutableLevel level = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, level);

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertEquals(0, level.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 1)),
                "valid map edit must not be applied when a block state is invalid");
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void genuineV1FixtureVerifiesHistoricalRawTreeHashAndLeavesSpawnsUntouched() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.editPath(GameId.S2, 1, 0);
        Files.createDirectories(file.getParent());
        Files.copy(Path.of("src/test/resources/editor/genuine-v1-save.json"), file);
        var raw = MAPPER.readTree(file.toFile());
        assertEquals("864b06384b5030d5520162eb3d3b10dd3f8b8079bc8e458adf0be3b427e37084",
                sha256(MAPPER.writeValueAsString(raw.get("payload"))));
        MutableLevel level = createMutableLevel();
        ObjectSpawn object = new CommonObjectPlacementEncoding().create(32, 48, 0x26, 3, 2, true, 7);
        RingSpawn ring = new RingSpawn(64, 80, 8);
        level.replaceSpawnsPersisted(List.of(object), List.of(ring), java.util.Map.of());

        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 1, 0, level));
        assertEquals(List.of(object), level.getObjects());
        assertEquals(List.of(ring), level.getRings());
    }

    @Test
    void inconsistentV2StockRawWordQuarantinesWithoutMutating() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSavePayload payload = new EditorSavePayload(List.of(), List.of(),
                List.of(new EditorSavePayload.MapCell(0, 0, 0, 0)),
                List.of(new EditorSavePayload.ObjectState(7, 32, 48, 3, 0, 0, false, 49)), List.of());
        Path file = manager.editPath(GameId.S2, 2, 0);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), new EditorSaveEnvelope(2, "s2", 2, 0,
                "2026-05-09T00:00:00Z", payload, sha256(MAPPER.writeValueAsString(payload))));
        MutableLevel level = createMutableLevel();
        ObjectSpawn existing = new CommonObjectPlacementEncoding().create(16, 16, 1, 0, 0, false, 1);
        level.replaceObjectSpawnsPersisted(List.of(existing));

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 2, 0, level));
        assertEquals(List.of(existing), level.getObjects());
    }

    @Test
    void outOfRangeTerrainEntryQuarantinesBeforeValidTerrainMutation() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSavePayload payload = new EditorSavePayload(List.of(),
                List.of(new EditorSavePayload.ChunkState(99, new Chunk().saveState())),
                List.of(new EditorSavePayload.MapCell(0, 0, 0, 1)));
        Path file = manager.editPath(GameId.S2, 2, 1);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), new EditorSaveEnvelope(2, "s2", 2, 1,
                "2026-05-09T00:00:00Z", payload, sha256(MAPPER.writeValueAsString(payload))));
        MutableLevel level = createMutableLevel();

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 2, 1, level));
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
    }

    @Test
    void v2RoundTripsS2RawPlacementFieldsStableIdsAndDuplicateBytes() throws Exception {
        MutableLevel edited = createMutableLevel();
        CommonObjectPlacementEncoding encoding = new CommonObjectPlacementEncoding();
        ObjectSpawn first = encoding.create(0x120, 0x234, 0xA5, 0x7E, 3, true, 41);
        ObjectSpawn duplicateBytes = encoding.create(0x120, 0x234, 0xA5, 0x7E, 3, true, 42);
        List<RingSpawn> rings = List.of(new RingSpawn(0x180, 0x250, 51));
        edited.replaceSpawnsPersisted(List.of(first, duplicateBytes), rings, java.util.Map.of());
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 2, 0, edited);
        MutableLevel fresh = createMutableLevel();
        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 2, 0, fresh));

        assertEquals(List.of(first, duplicateBytes), fresh.getObjects());
        assertEquals(rings, fresh.getRings());
        assertEquals(first.rawYWord(), fresh.getObjects().get(0).rawYWord());
        assertTrue(fresh.consumeObjectsDirty());
        assertTrue(fresh.consumeRingsDirty());
        assertFalse(fresh.isModifiedSinceLastSave());
    }

    @Test
    void v2RoundTripsS1RawPlacementAndObjectBackedRingMapping() throws Exception {
        MutableLevel edited = createMutableLevel();
        Sonic1ObjectPlacementEncoding encoding = new Sonic1ObjectPlacementEncoding();
        ObjectSpawn tracked = encoding.create(0x180, 0x300, 0x44, 0x7F, 1, true, 60);
        ObjectSpawn backing = encoding.create(0x200, 0x345, 0x25, 0x11, 2, false, 61);
        List<RingSpawn> rings = List.of(new RingSpawn(0x200, 0x345, 71), new RingSpawn(0x218, 0x345, 72));
        edited.replaceSpawnsPersisted(List.of(tracked, backing), rings, java.util.Map.of(backing, rings));
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        manager.save(GameId.S1, new Sonic1ObjectPlacementEncoding(), 3, 0, edited);
        MutableLevel fresh = createMutableLevel();
        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                manager.tryApplyEdits(GameId.S1, new Sonic1ObjectPlacementEncoding(), 3, 0, fresh));

        assertEquals(List.of(tracked, backing), fresh.getObjects());
        assertEquals(rings, fresh.getRings());
        assertEquals(rings, fresh.ringObjectPlacementMapping().get(backing));
        assertTrue(fresh.getObjects().get(0).respawnTracked());
        assertEquals(tracked.rawYWord(), fresh.getObjects().get(0).rawYWord());
    }

    @Test
    void v2EmptySpawnTablesClearExistingSpawns() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        manager.save(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, createMutableLevel());
        MutableLevel fresh = createMutableLevel();
        ObjectSpawn object = new CommonObjectPlacementEncoding().create(32, 48, 1, 0, 0, false, 1);
        fresh.replaceSpawnsPersisted(List.of(object), List.of(new RingSpawn(64, 80, 2)), java.util.Map.of());

        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 0, fresh));
        assertTrue(fresh.getObjects().isEmpty());
        assertTrue(fresh.getRings().isEmpty());
    }

    @Test
    void v2MissingSpawnTablesAlsoMeanEmptyReplacement() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.editPath(GameId.S2, 4, 1);
        Files.createDirectories(file.getParent());
        String rawPayload = "{\"blocks\":[],\"chunks\":[],\"mapCells\":[]}";
        String historicalRawPayloadSha256 =
                "864b06384b5030d5520162eb3d3b10dd3f8b8079bc8e458adf0be3b427e37084";
        assertEquals(historicalRawPayloadSha256, sha256(rawPayload));
        Files.writeString(file, "{\"version\":2,\"gameCode\":\"s2\",\"zone\":4,\"act\":1,"
                + "\"savedAt\":\"2026-05-09T00:00:00Z\",\"payload\":" + rawPayload
                + ",\"hash\":\"" + historicalRawPayloadSha256 + "\"}");
        MutableLevel fresh = createMutableLevel();
        ObjectSpawn object = new CommonObjectPlacementEncoding().create(32, 48, 1, 0, 0, false, 1);
        fresh.replaceSpawnsPersisted(List.of(object), List.of(new RingSpawn(64, 80, 2)), java.util.Map.of());

        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 1, fresh));
        assertTrue(fresh.getObjects().isEmpty());
        assertTrue(fresh.getRings().isEmpty());
    }

    @Test
    void canonicalFiveFieldV2WriterHashIsPinnedWithRepresentativeStockSpawn() throws Exception {
        MutableLevel level = createMutableLevel();
        ObjectSpawn stock = new CommonObjectPlacementEncoding().create(32, 48, 3, 2, 0, false, 7);
        level.replaceSpawnsPersisted(List.of(stock), List.of(new RingSpawn(64, 80, 9)), java.util.Map.of());
        Path file = new EditorSaveManager(tempDir).save(
                GameId.S2, new CommonObjectPlacementEncoding(), 4, 3, level).file();
        JsonNode payload = MAPPER.readTree(file.toFile()).get("payload");
        assertEquals(5, payload.size());
        assertTrue(payload.has("blocks") && payload.has("chunks") && payload.has("mapCells")
                && payload.has("objects") && payload.has("rings"));
        assertEquals("b2756d3a521ec85f6b495cc6a4733bfff1dff38dc13ccd76f655365f4682397b",
                sha256(MAPPER.writeValueAsString(payload)));
    }

    @Test
    void malformedLiveKeyedPlacementFailsBeforePublicationAndRemainsDirty() {
        MutableLevel level = createMutableLevel();
        level.addObjectSpawn(new ObjectSpawn(32, 48, 0, 0, 0, false, 49, 77,
                "owner", "owner:badnik"));
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        assertThrows(IllegalArgumentException.class, () -> manager.save(
                GameId.S2, new CommonObjectPlacementEncoding(), 4, 4, level));
        assertTrue(level.isModifiedSinceLastSave());
        assertFalse(Files.exists(manager.editPath(GameId.S2, 4, 4)));
    }

    @Test
    void literalHistoricalV1RawPayloadRetainsLegacySpawnSemantics() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.editPath(GameId.S2, 4, 2);
        Files.createDirectories(file.getParent());
        String rawPayload = "{\"blocks\":[],\"chunks\":[],\"mapCells\":[]}";
        String historicalRawPayloadSha256 =
                "864b06384b5030d5520162eb3d3b10dd3f8b8079bc8e458adf0be3b427e37084";
        assertEquals(historicalRawPayloadSha256, sha256(rawPayload));
        Files.writeString(file, "{\"version\":1,\"gameCode\":\"s2\",\"zone\":4,\"act\":2,"
                + "\"savedAt\":\"2026-05-09T00:00:00Z\",\"payload\":" + rawPayload
                + ",\"hash\":\"" + historicalRawPayloadSha256 + "\"}");
        MutableLevel fresh = createMutableLevel();
        ObjectSpawn existing = new CommonObjectPlacementEncoding().create(32, 48, 1, 0, 0, false, 1);
        fresh.replaceSpawnsPersisted(List.of(existing), List.of(), java.util.Map.of());

        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 4, 2, fresh));
        assertEquals(List.of(existing), fresh.getObjects());
    }

    @Test
    void futureV3EnvelopeIsQuarantined() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.editPath(GameId.S2, 5, 0);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"version":4,"gameCode":"s2","zone":5,"act":0,"savedAt":"2026-05-09T00:00:00Z",
                 "payload":{"blocks":[],"chunks":[],"mapCells":[],"objects":[],"rings":[]},"hash":"ignored"}
                """);

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED,
                manager.tryApplyEdits(GameId.S2, new CommonObjectPlacementEncoding(), 5, 0, createMutableLevel()));
        assertFalse(Files.exists(file));
    }

    @Test
    void futureS3kEnvelopeIsQuarantinedBeforeUnsupportedApplyDecision() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.editPath(GameId.S3K, 5, 0);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"version\":4,\"gameCode\":\"s3k\",\"zone\":5,\"act\":0,"
                + "\"savedAt\":\"2026-05-09T00:00:00Z\",\"payload\":{},\"hash\":\"ignored\"}");

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED,
                manager.tryApplyEdits(GameId.S3K, new CommonObjectPlacementEncoding(), 5, 0, createMutableLevel()));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    @Test
    void savingS3kKeepsUnsupportedStatusVisibleInToolbar() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        EditorSaveManager.SaveResult save = manager.save(GameId.S3K, new CommonObjectPlacementEncoding(), 1, 0, createMutableLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(createMutableLevel());
        controller.setPersistenceStatus(save.persistenceStatus());

        assertEquals(EditorSaveManager.ApplyResult.UNSUPPORTED, save.persistenceStatus());
        EditorTextRenderer.TextCommand command = new InspectableToolbar(controller).commands().getFirst();
        String prefix = "S3K SAVE UNSUPPORTED | ";
        assertTrue(command.text().startsWith(prefix));
        assertTrue(command.x() + new PixelFontTextRenderer().measureWidth(prefix) <= 316,
                "unsupported-save indicator must fit inside the visible toolbar before variable state text");
    }

    @Test
    void unsupportedPersistedS3kStatusIsControllerVisibleInToolbar() throws Exception {
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        manager.save(GameId.S3K, new CommonObjectPlacementEncoding(), 1, 0, createMutableLevel());
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(
                GameId.S3K, new CommonObjectPlacementEncoding(), 1, 0, createMutableLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(createMutableLevel());
        controller.setPersistenceStatus(result);

        assertEquals(EditorSaveManager.ApplyResult.UNSUPPORTED, result);
        assertTrue(new InspectableToolbar(controller).lines().getFirst().startsWith("S3K SAVE UNSUPPORTED | "));
    }

    private static final class InspectableToolbar extends EditorToolbarRenderer {
        InspectableToolbar(LevelEditorController controller) {
            super(controller, GraphicsManager.getInstance());
        }
        List<String> lines() { return buildStateLines(); }
        List<EditorTextRenderer.TextCommand> commands() { return buildToolbarTextCommands(); }
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MutableLevel createMutableLevel() {
        return MutableLevel.snapshot(new SyntheticLevel());
    }

    private static int[] chunkState(int pattern00) {
        int[] state = new Chunk().saveState();
        state[0] = pattern00;
        return state;
    }

    private static final class SyntheticLevel extends AbstractLevel {
        private SyntheticLevel() {
            super(0);
            patternCount = 1;
            patterns = new Pattern[] { new Pattern() };
            chunkCount = 3;
            chunks = new Chunk[] { new Chunk(), new Chunk(), new Chunk() };
            blockCount = 3;
            blocks = new Block[] { new Block(1), new Block(1), new Block(1) };
            solidTileCount = 0;
            solidTiles = new SolidTile[0];
            map = new Map(2, 4, 3);
            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < PALETTE_COUNT; i++) {
                palettes[i] = new Palette();
            }
            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 128;
            minY = 0;
            maxY = 96;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 1;
        }

        @Override
        public int getBlockPixelSize() {
            return 32;
        }
    }
}

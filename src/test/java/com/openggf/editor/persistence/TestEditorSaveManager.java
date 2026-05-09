package com.openggf.editor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameId;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
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

        EditorSaveManager.SaveResult save = manager.save(GameId.S2, 4, 0, edited);
        MutableLevel fresh = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, 4, 0, fresh);

        assertTrue(save.ok());
        assertEquals(EditorSaveManager.ApplyResult.APPLIED, result);
        assertEquals(2, fresh.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(2, Byte.toUnsignedInt(fresh.getMap().getValue(1, 2, 1)));
        assertTrue(fresh.modifiedBlocksSinceBaseline().get(1));
        assertTrue(fresh.modifiedMapCellsSinceBaseline().get(1 * 12 + 1 * 4 + 2));
        assertFalse(fresh.isModifiedSinceLastSave());
    }

    @Test
    void missingFileReturnsNone() {
        EditorSaveManager manager = new EditorSaveManager(tempDir);

        assertEquals(EditorSaveManager.ApplyResult.NONE,
                manager.tryApplyEdits(GameId.S2, 1, 0, createMutableLevel()));
    }

    @Test
    void mismatchedZoneReturnsMismatchWithoutQuarantine() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 2);
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.save(GameId.S2, 1, 0, edited).file();
        Path mismatchedPath = manager.editPath(GameId.S2, 1, 1);
        Files.createDirectories(mismatchedPath.getParent());
        Files.copy(file, mismatchedPath);

        MutableLevel fresh = createMutableLevel();
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, 1, 1, fresh);

        assertEquals(EditorSaveManager.ApplyResult.MISMATCH, result);
        assertTrue(Files.exists(mismatchedPath));
        assertEquals(0, Byte.toUnsignedInt(fresh.getMap().getValue(0, 1, 1)));
    }

    @Test
    void tamperedPayloadIsQuarantined() throws Exception {
        MutableLevel edited = createMutableLevel();
        edited.setBlockInMap(0, 1, 1, 2);
        EditorSaveManager manager = new EditorSaveManager(tempDir);
        Path file = manager.save(GameId.S2, 1, 0, edited).file();
        String json = Files.readString(file);
        Files.writeString(file, json.replace("\"blockIndex\":2", "\"blockIndex\":1"));

        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, 1, 0, createMutableLevel());

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
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
        EditorSaveManager.ApplyResult result = manager.tryApplyEdits(GameId.S2, 1, 0, level);

        assertEquals(EditorSaveManager.ApplyResult.QUARANTINED, result);
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 1)));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(file.resolveSibling(file.getFileName() + ".corrupt")));
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MutableLevel createMutableLevel() {
        return MutableLevel.snapshot(new SyntheticLevel());
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

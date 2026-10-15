package com.openggf.editor.project;

import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameId;
import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RingObjectPlacementMapping;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestEditorProjects {
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void fullProjectRoundTripRetainsEveryDataChannelAndMissingDependencies(GameId game) throws Exception {
        Fixture source = new Fixture(game);
        EditorProjectDocument document = EditorProjectDocument.create(metadata(game, source), source);
        document.level().getPattern(1).setPixel(7, 6, (byte) 13);
        document.level().getPalette(2).getColor(11).r = 17;
        document.level().getSolidTile(1).widths[6] = -9;
        document.level().setBlockInMap(1, 2, 3, 1);
        Path file = directory.resolve(game.code() + ".ogfproject");
        EditorProjectStore store = new EditorProjectStore();
        store.save(file, document);
        EditorProjectDocument reopened = store.open(file);
        assertEquals(document.metadata(), reopened.metadata());
        assertEquals(13, reopened.level().getPattern(1).getPixel(7, 6));
        assertEquals(17, reopened.level().getPalette(2).getColor(11).r);
        assertEquals(-9, reopened.level().getSolidTile(1).widths[6]);
        assertEquals(1, reopened.level().getMap().getValue(1, 2, 3));
        assertEquals(0x1801, reopened.level().getChunk(1).getPatternDesc(0, 0).get());
        assertEquals(0xE801, reopened.level().getBlock(1).getChunkDesc(0, 0).get());
        assertEquals(source.getObjects(), reopened.level().getObjects());
        assertEquals(source.getRings(), reopened.level().getRings());
        assertEquals("absent-mod:crate", reopened.level().getObjects().get(1).objectKey());
        assertEquals(7, reopened.level().ringBackingObject(reopened.level().getRings().getFirst()).layoutIndex());
        assertFalse(reopened.isModified());
        assertEquals(file.toAbsolutePath(), reopened.path());
        assertEquals(game == GameId.S1 ? 256 : 128, reopened.level().getBlockPixelSize());
        source.getPattern(1).setPixel(7, 6, (byte) 5);
        assertEquals(13, document.level().getPattern(1).getPixel(7, 6));
    }

    @Test
    void dirtyStateCoversDirectAssetEditsAndUndoToSavedContent() throws Exception {
        Fixture source = new Fixture(GameId.S2);
        EditorProjectDocument document = EditorProjectDocument.create(metadata(GameId.S2, source), source);
        assertTrue(document.isModified(), "new projects have not been saved");
        new EditorProjectStore().save(directory.resolve("saved.ogfproject"), document);
        assertFalse(document.isModified());
        document.level().getPattern(0).setPixel(0, 0, (byte) 3);
        assertTrue(document.isModified());
        document.level().getPattern(0).setPixel(0, 0, (byte) 0);
        assertFalse(document.isModified(), "undoing to the savepoint must clear the marker");
        EditorProjectMetadata saved = document.metadata();
        document.setMetadata(saved.withName("Changed"));
        assertTrue(document.isModified());
        document.setMetadata(saved);
        assertFalse(document.isModified());
        document.level().getSolidTile(0).heights[0] = 4;
        assertTrue(document.isModified());
    }

    @Test
    void blankLevelUsesEmptyDefinitionsAndHasSpareCompositionSlots() {
        Fixture source = new Fixture(GameId.S1);
        EditorProjectDocument blank = EditorProjectDocument.blank(metadata(GameId.S1, source), source, 6, 2);
        assertEquals(6, blank.level().getMap().getWidth());
        assertEquals(2, blank.level().getMap().getHeight());
        assertTrue(blank.level().getObjects().isEmpty());
        assertTrue(blank.level().getRings().isEmpty());
        assertTrue(blank.level().getBlockCount() > source.getBlockCount());
        assertTrue(blank.level().getChunkCount() > source.getChunkCount());
        int block = Byte.toUnsignedInt(blank.level().getMap().getValue(0, 0, 0));
        int chunk = blank.level().getBlock(block).getChunkDesc(0, 0).getChunkIndex();
        int pattern = blank.level().getChunk(chunk).getPatternDesc(0, 0).getPatternIndex();
        assertEquals(0, blank.level().getPattern(pattern).getPixel(0, 0));
        assertEquals(13, blank.level().getPattern(1).getPixel(3, 3));
    }

    @Test
    void cloneRejectsNativeCollisionRemapsWhileBlankCanReuseTheAssets() {
        Fixture remapped = new Fixture(GameId.S1) {
            @Override public int resolveCollisionBlockIndex(int block, int x, int y) {
                return x == 1 && y == 1 ? block + 1 : block;
            }
        };
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EditorProjectLevel.copyOf(remapped));
        assertTrue(error.getMessage().contains("collision"));
        assertDoesNotThrow(() -> EditorProjectLevel.blank(remapped, 3, 2));
    }

    static EditorProjectMetadata metadata(GameId game, Level source) {
        return new EditorProjectMetadata(game, "Example " + game.code(), 2, 1, "rom-sha1-example", 64, 64,
                source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY(),
                new FullLevelExporter.ExportMusic.Stock(0x81), game == GameId.S3K ? ModObjectZoneSet.SKL : null);
    }

    static class Fixture extends AbstractLevel implements RingObjectPlacementMapping {
        final int side;
        Fixture(GameId game) {
            super(2);
            side = game == GameId.S1 ? 16 : 8;
            patterns = new Pattern[] {new Pattern(), new Pattern()}; patternCount = 2;
            patterns[1].setPixel(3, 3, (byte) 13);
            chunks = new Chunk[] {new Chunk(), new Chunk()}; chunkCount = 2;
            chunks[1].restoreState(new int[] {0x1801, 0, 1, 0, 1, 0});
            blocks = new Block[] {new Block(side), new Block(side)}; blockCount = 2;
            blocks[1].setChunkDesc(0, 0, new ChunkDesc(0xE801));
            solidTiles = new SolidTile[] {new SolidTile(0, new byte[16], new byte[16], (byte) 0),
                    new SolidTile(1, new byte[16], new byte[16], (byte) 0x40)};
            solidTileCount = 2;
            solidTiles[1].heights[5] = 9;
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            palettes[1].getColor(8).b = (byte) 218;
            map = new com.openggf.level.Map(2, 4, 4); map.setValue(0, 1, 1, (byte) 1);
            objects = List.of(new ObjectSpawn(32, 48, game == GameId.S1 ? 0x25 : 3, 0, 0, false, 48, 7),
                    new ObjectSpawn(80, 64, 0, 3, 1, true,
                            game == GameId.S1 ? 0x4040 : 0xA040, 9, "absent-mod", "absent-mod:crate"));
            rings = List.of(new RingSpawn(32, 48, 4));
            minX = 0; maxX = side * 16 * 4 - 1; minY = 0; maxY = side * 16 * 4 - 1;
        }
        @Override public int getChunksPerBlockSide() { return side; }
        @Override public int getBlockPixelSize() { return side * 16; }
        @Override public java.util.Map<ObjectSpawn, List<RingSpawn>> ringObjectPlacementMapping() {
            return java.util.Map.of(objects.getFirst(), rings);
        }
    }
}

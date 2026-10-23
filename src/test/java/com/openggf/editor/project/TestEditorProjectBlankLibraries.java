package com.openggf.editor.project;

import com.openggf.game.GameId;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Blank extraction has its own geometry and retains only resolvable native definitions. */
class TestEditorProjectBlankLibraries {
    @ParameterizedTest
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void blankDoesNotImportNativeStorageDimensionsCameraBoundsOrPlacements(GameId game) {
        var source = new TestEditorProjects.Fixture(game) {{
            map = new com.openggf.level.Map(2, 600, 1);
            minX = 32; maxX = 60_000; minY = -128; maxY = 4000;
        }};
        assertThrows(IllegalArgumentException.class, () -> EditorProjectLevel.copyOf(source));
        var blank = EditorProjectLevel.blank(source, 3, 2);
        assertEquals(3, blank.getMap().getWidth());
        assertEquals(2, blank.getMap().getHeight());
        assertEquals(0, blank.getMinX()); assertEquals(3 * blank.getBlockPixelSize() - 1, blank.getMaxX());
        assertEquals(0, blank.getMinY()); assertEquals(2 * blank.getBlockPixelSize() - 1, blank.getMaxY());
        assertTrue(blank.getObjects().isEmpty()); assertTrue(blank.getRings().isEmpty());
        assertTrue(blank.ringObjectPlacementMapping().isEmpty());
        assertDoesNotThrow(() -> EditorProjectLevel.copyOf(blank));
    }

    @Test
    void unavailableArtDropsDependentDefinitionsBeforeAddingBlankImportSlots() {
        var source = partialLibrary();
        assertThrows(IllegalArgumentException.class, () -> EditorProjectLevel.copyOf(source));
        var blank = EditorProjectLevel.blank(source, 2, 2);
        assertBlankImportSlots(source, blank);
        assertEquals(1, blank.omittedNativeChunkCount());
        assertEquals(1, blank.omittedNativeBlockCount());
        assertTrue(blank.blankLibraryNotice().contains("Choose another asset library"));
        int descriptor = blank.getBlock(1).getChunkDesc(0, 0).get();
        assertEquals(0xF802, descriptor, "compaction must preserve native flip and collision bits");
        assertArrayEquals(source.getChunk(3).saveState(), blank.getChunk(2).saveState());
        assertEquals(13, blank.getPattern(1).getPixel(3, 3));
        source.getPattern(1).setPixel(3, 3, (byte) 0);
        assertEquals(13, blank.getPattern(1).getPixel(3, 3));
        assertDoesNotThrow(() -> EditorProjectState.capture(blank));
    }

    @Test
    void entirelyUnavailableDefinitionsProduceAnExplicitEmptyAuthoredCanvas() {
        var source = new TestEditorProjects.Fixture(GameId.S2) {{
            chunks[0].restoreState(new int[] {2, 2, 2, 2, 0, 0});
            chunks[1].restoreState(new int[] {2, 2, 2, 2, 0, 0});
        }};
        var blank = EditorProjectLevel.blank(source, 2, 1);
        assertEquals(2, blank.omittedNativeChunkCount()); assertEquals(2, blank.omittedNativeBlockCount());
        assertBlankImportSlots(source, blank);
        int block = Byte.toUnsignedInt(blank.getMap().getValue(0, 0, 0));
        var descriptor = blank.getBlock(block).getChunkDesc(0, 0);
        assertFalse(descriptor.hasPrimarySolidity()); assertFalse(descriptor.hasSecondarySolidity());
        var pattern = blank.getPattern(blank.getChunk(descriptor.getChunkIndex()).getPatternDesc(0, 0).getPatternIndex());
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertEquals(0, pattern.getPixel(x, y));
        assertDoesNotThrow(() -> EditorProjectState.capture(blank));
    }

    @Test
    void blankStillRejectsMalformedAvailablePixelsAndExactSnapshotsRemainStrict() {
        var malformed = new TestEditorProjects.Fixture(GameId.S2);
        malformed.getPattern(1).setPixel(0, 0, (byte) 16);
        assertThrows(IllegalArgumentException.class, () -> EditorProjectLevel.blank(malformed, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> EditorProjectState.capture(partialLibrary()));
    }

    private static void assertBlankImportSlots(Level source, Level blank) {
        assertTrue(blank.getPatternCount() >= source.getPatternCount(), "native art must be retained");
        assertTrue(blank.getPatternCount() <= 2048, "spare slots must fit native terrain descriptors");
        for (int i = 0; i < blank.getPatternCount(); i++)
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                assertEquals(i < source.getPatternCount() ? source.getPattern(i).getPixel(x, y) : 0,
                        blank.getPattern(i).getPixel(x, y), "spares cannot invent missing native art");
    }

    private static TestEditorProjects.Fixture partialLibrary() {
        return new TestEditorProjects.Fixture(GameId.S2) {{
            chunks = new Chunk[] {new Chunk(), new Chunk(), new Chunk(), new Chunk()}; chunkCount = chunks.length;
            chunks[1].restoreState(new int[] {1, 0, 1, 0, 1, 0});
            chunks[2].restoreState(new int[] {2, 0, 1, 0, 1, 0});
            chunks[3].restoreState(new int[] {0x1801, 0, 1, 0, 1, 0});
            blocks = new Block[] {new Block(8), new Block(8), new Block(8)}; blockCount = blocks.length;
            blocks[1].setChunkDesc(0, 0, new ChunkDesc(0xF802));
            blocks[2].setChunkDesc(0, 0, new ChunkDesc(0xF803));
        }};
    }
}

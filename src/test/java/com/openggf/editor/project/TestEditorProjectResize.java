package com.openggf.editor.project;

import com.openggf.game.GameId;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestEditorProjectResize {
    @TempDir Path directory;

    @Test
    void enlargementPreservesBothLayersAndNeverSilentlyDropsPlacements() {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S2);
        EditorProjectDocument document = EditorProjectDocument.cloneLevel(TestEditorProjects.metadata(GameId.S2, source), source);
        document.level().setBlockInMap(1, 3, 3, 1);
        document.level().addObjectSpawn(new ObjectSpawn(4000, 64, 3, 0, 0, false, 64, 15));
        document.resize(6, 5, false);
        assertEquals(1, document.level().getMap().getValue(0, 1, 1));
        assertEquals(1, document.level().getMap().getValue(1, 3, 3));
        assertTrue(document.level().getObjects().stream().anyMatch(o -> o.layoutIndex() == 15),
                "growth is not crop authorization for records already outside the old canvas");
        assertEquals(767, document.metadata().maxX());
        assertEquals(639, document.metadata().maxY());
    }

    @Test
    void enlargingBlankAddsEmptyTerrainEvenWhenLibraryIndexZeroIsNotEmpty() {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S2);
        source.getPattern(0).setPixel(0, 0, (byte) 5);
        EditorProjectDocument document = EditorProjectDocument.blank(TestEditorProjects.metadata(GameId.S2, source), source, 2, 2);
        int blockCount = document.level().getBlockCount(), chunkCount = document.level().getChunkCount();
        document.resize(4, 3, false);
        int block = Byte.toUnsignedInt(document.level().getMap().getValue(0, 3, 2));
        int chunk = document.level().getBlock(block).getChunkDesc(0, 0).getChunkIndex();
        int pattern = document.level().getChunk(chunk).getPatternDesc(0, 0).getPatternIndex();
        assertEquals(0, document.level().getPattern(pattern).getPixel(0, 0));
        assertEquals(blockCount, document.level().getBlockCount());
        assertEquals(chunkCount, document.level().getChunkCount(), "resize must reuse the existing empty definitions");
    }

    @Test
    void cropRequiresExplicitChoiceAndRemovesWholeBackedRingGroups() {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S2) {
            { rings = List.of(new RingSpawn(32, 48, 4), new RingSpawn(400, 48, 5)); }
        };
        EditorProjectDocument document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, source).withStart(400, 64), source);
        MutableLevel original = document.level();
        assertThrows(IllegalArgumentException.class, () -> document.resize(2, 4, false));
        assertSame(original, document.level());
        assertEquals(400, document.metadata().startX());
        document.resize(2, 4, true);
        assertTrue(document.level().getRings().isEmpty());
        assertEquals(List.of(9), document.level().getObjects().stream().map(ObjectSpawn::layoutIndex).toList());
        assertEquals(255, document.metadata().startX());
        assertEquals(255, document.metadata().maxX());
    }

    @Test
    void replaceSupportsUndoToSavedCompleteDocumentWithoutAliasing() throws Exception {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S2);
        EditorProjectDocument document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, source), source);
        EditorProjectStore store = new EditorProjectStore(); store.save(directory.resolve("before.ogfproject"), document);
        EditorProjectLevel before = document.snapshot(); EditorProjectMetadata metadata = document.metadata();
        document.resize(5, 5, false);
        document.level().getPalette(1).getColor(0).r = 12;
        assertTrue(document.isModified());
        document.replace(metadata, before);
        assertFalse(document.isModified());
        before.getPattern(0).setPixel(0, 0, (byte) 8);
        assertEquals(0, document.level().getPattern(0).getPixel(0, 0));
        assertFalse(document.isModified());
    }

    @Test
    void invalidDimensionsAndBoundsLeaveDocumentUnchanged() {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S1);
        EditorProjectDocument document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S1, source), source);
        Level original = document.level();
        assertThrows(IllegalArgumentException.class, () -> document.resize(257, 4, false));
        assertThrows(IllegalArgumentException.class, () -> document.resize(4, 129, false));
        assertThrows(IllegalArgumentException.class, () -> document.resize(0, 4, false));
        assertThrows(IllegalArgumentException.class, () -> document.setMetadata(document.metadata().withBounds(0, 4000, 0, 100)));
        assertSame(original, document.level());
    }
}

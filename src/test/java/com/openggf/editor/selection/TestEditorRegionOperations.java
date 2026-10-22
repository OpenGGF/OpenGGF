package com.openggf.editor.selection;

import com.openggf.editor.EditorCommand;
import com.openggf.editor.EditorHistory;
import com.openggf.editor.commands.LocalCompositionCommand;
import com.openggf.editor.EditorPlacementIdAllocator;
import com.openggf.editor.EditorSpawnFactory;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RingObjectPlacementMapping;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorRegionOperations {
    @Test
    void overlapUsesSnapshotAndOneHistoryEntryRestoresTerrainObjectsAndRings() {
        var source = new FixtureLevel(4, 2);
        var encoding = new CommonObjectPlacementEncoding();
        var object = encoding.create(8, 8, 0x26, 7, 3, true, 4);
        var ring = new RingSpawn(40, 8, 6);
        source.placements(List.of(object), List.of(ring));
        MutableLevel level = MutableLevel.snapshot(source);
        level.setBlockInMap(0, 0, 0, 0);
        level.setBlockInMap(0, 1, 0, 2);
        level.setBlockInMap(0, 2, 0, 1);
        var operations = operations(level, encoding);
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 2, 1));
        var before = level.snapshotObjectBackedRingState();
        var history = new EditorHistory();
        EditorCommand paste = operations.paste(clipboard, 0, 1, 0);
        history.execute(paste);
        assertEquals(List.of(0, 0, 2, 0), row(level, 0));
        assertEquals(2, level.getObjects().size());
        assertEquals(2, level.getRings().size());
        ObjectSpawn copied = level.getObjects().stream().filter(o -> o.layoutIndex() != object.layoutIndex()).findFirst().orElseThrow();
        assertEquals(40, copied.x());
        assertEquals(object.rawFlags() | copied.y(), copied.rawYWord());
        assertEquals(object.subtype(), copied.subtype());
        assertEquals(object.respawnTracked(), copied.respawnTracked());
        var pasted = level.snapshotObjectBackedRingState();
        history.undo();
        assertEquals(List.of(0, 2, 1, 0), row(level, 0));
        assertEquals(before, level.snapshotObjectBackedRingState());
        assertFalse(history.hasUndoEntries());
        history.redo();
        assertEquals(pasted, level.snapshotObjectBackedRingState(), "redo reuses allocated placement identities");
        assertEquals(0, level.getMap().getValue(1, 1, 0));
        assertEquals(0, level.getBlock(0).getChunkDesc(0, 0).getChunkIndex(), "shared definitions stay untouched");
    }

    @Test
    void cutSelectsEntireS1NativeRingGroupAndUndoRestoresOrderAndMapping() {
        var source = new FixtureLevel(5, 2);
        var encoding = new Sonic1ObjectPlacementEncoding();
        ObjectSpawn backing = encoding.create(24, 8, 0x25, 0x12, 2, true, 7);
        RingSpawn first = new RingSpawn(24, 8, 11), second = new RingSpawn(48, 8, 12);
        ObjectSpawn other = encoding.create(120, 8, 1, 0, 0, false, 8);
        source.placements(List.of(backing, other), List.of(first, second));
        source.groups.put(backing, List.of(first, second));
        MutableLevel level = MutableLevel.snapshot(source);
        var operations = operations(level, encoding);
        var before = level.snapshotObjectBackedRingState();
        var cut = operations.cut(0, new CellRectangle(1, 0, 1, 1));
        assertEquals(List.of(first, second), cut.clipboard().rings(), "intersecting a member includes the native group");
        cut.command().apply();
        assertEquals(List.of(other), level.getObjects());
        assertTrue(level.getRings().isEmpty());
        assertTrue(level.ringObjectPlacementMapping().isEmpty());
        assertEquals(1, level.getMap().getValue(0, 1, 0));
        cut.command().undo();
        assertEquals(before, level.snapshotObjectBackedRingState());
        cut.command().apply();
        cut.command().undo();
        assertEquals(before, level.snapshotObjectBackedRingState());

        EditorCommand paste = operations.paste(cut.clipboard(), 0, 2, 0);
        paste.apply();
        var pasted = level.ringObjectPlacementMapping().entrySet().stream()
                .filter(e -> e.getKey().layoutIndex() != backing.layoutIndex()).findFirst().orElseThrow();
        assertEquals(56, pasted.getKey().x());
        assertEquals(backing.subtype(), pasted.getKey().subtype());
        assertEquals(backing.rawFlags(), pasted.getKey().rawFlags());
        assertEquals(List.of(56, 80), pasted.getValue().stream().map(RingSpawn::x).toList());
        assertTrue(pasted.getValue().stream().noneMatch(r -> r.placementId() == 11 || r.placementId() == 12));
        var after = level.snapshotObjectBackedRingState();
        paste.undo();
        assertEquals(before, level.snapshotObjectBackedRingState());
        paste.apply();
        assertEquals(after, level.snapshotObjectBackedRingState());
    }

    @Test
    void invalidPasteAndGhostNeverPartiallyMutateOrAllocateIds() {
        var source = new FixtureLevel(4, 130);
        var encoding = new CommonObjectPlacementEncoding();
        source.placements(List.of(encoding.create(8, 8, 1, 0, 0, false, 9)), List.of(new RingSpawn(8, 8, 10)));
        MutableLevel level = MutableLevel.snapshot(source);
        var operations = operations(level, encoding);
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
        var before = level.snapshotObjectBackedRingState();
        level.markSaved();
        assertThrows(IllegalArgumentException.class, () -> operations.paste(clipboard, 0, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> operations.paste(clipboard, 0, 0, 128),
                "object native Y limit is stricter than this tall map");
        assertThrows(IllegalArgumentException.class, () -> operations.paste(clipboard, 2, 0, 0));
        for (int i = 0; i < 3; i++) {
            var preview = operations.preview(clipboard, 0, 1, 0);
            assertEquals(40, preview.objects().getFirst().x());
            assertEquals(40, preview.rings().getFirst().x());
        }
        assertEquals(before, level.snapshotObjectBackedRingState());
        assertFalse(level.isModifiedSinceLastSave());
        operations.paste(clipboard, 0, 1, 0).apply();
        assertEquals(List.of(9, 10), level.getObjects().stream().map(ObjectSpawn::layoutIndex).toList());
        assertEquals(List.of(10, 11), level.getRings().stream().map(RingSpawn::placementId).toList());
    }

    @Test
    void pasteValidatesObjectsAndActualBoundaryCrossingGroupBeforeWriting() {
        var source = new FixtureLevel(4, 2);
        ObjectSpawn invalid = new ObjectSpawn(8, 8, 0x80, 0, 0, false, 8, 1);
        source.placements(List.of(invalid), List.of());
        MutableLevel level = MutableLevel.snapshot(source);
        var operations = operations(level, new Sonic1ObjectPlacementEncoding());
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> operations.paste(clipboard, 0, 1, 0));
        assertEquals(List.of(invalid), level.getObjects());
        assertEquals(List.of(0, 0, 0, 0), row(level, 0));

        var grouped = new FixtureLevel(4, 2);
        ObjectSpawn backing = new Sonic1ObjectPlacementEncoding().create(24, 8, 0x25, 0x10, 0, false, 1);
        var rings = List.of(new RingSpawn(24, 8, 1), new RingSpawn(48, 8, 2));
        grouped.placements(List.of(backing), rings);
        grouped.groups.put(backing, rings);
        MutableLevel groupLevel = MutableLevel.snapshot(grouped);
        var groupOperations = operations(groupLevel, new Sonic1ObjectPlacementEncoding());
        var selected = groupOperations.copy(0, new CellRectangle(1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> groupOperations.paste(selected, 0, 0, 0),
                "group backing would translate outside left edge although target rectangle is in bounds");
        assertEquals(List.of(backing), groupLevel.getObjects());
    }

    @Test
    void standaloneRingsRetainFullHeightWhereObjectEncodingCannot() {
        var source = new FixtureLevel(4, 260);
        source.placements(List.of(), List.of(new RingSpawn(8, 8, 2)));
        MutableLevel level = MutableLevel.snapshot(source);
        var operations = operations(level, new CommonObjectPlacementEncoding());
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
        var command = operations.paste(clipboard, 0, 1, 256);
        command.apply();
        assertEquals(8200, level.getRings().get(1).y());
        command.undo();
        assertEquals(source.getRings(), level.getRings());
    }

    @Test
    void floodAndRectangleFillStayInLayerAndUndoWithoutEditingDefinitions() {
        MutableLevel level = MutableLevel.snapshot(new FixtureLevel(4, 3));
        for (int y = 0; y < 3; y++) level.setBlockInMap(0, 2, y, 2);
        level.setBlockInMap(0, 0, 0, 2); // isolated diagonal must not join flood
        var operations = operations(level, new CommonObjectPlacementEncoding());
        var fill = operations.floodFill(0, 0, 1, 1);
        fill.apply();
        assertEquals(List.of(2, 1, 2, 0), row(level, 0));
        assertEquals(List.of(1, 1, 2, 0), row(level, 1));
        assertEquals(0, level.getMap().getValue(1, 0, 1));
        fill.undo();
        assertEquals(List.of(0, 0, 2, 0), row(level, 1));
        fill.apply();
        var rectangle = operations.rectangleFill(1, new CellRectangle(1, 1, 3, 2), 2);
        rectangle.apply();
        assertEquals(2, level.getMap().getValue(1, 3, 2));
        rectangle.undo();
        assertEquals(0, level.getMap().getValue(1, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> operations.rectangleFill(0, new CellRectangle(3, 0, 2, 1), 1));
        assertThrows(IllegalArgumentException.class, () -> operations.floodFill(0, 0, 0, 256));
        assertEquals(0, level.getBlock(0).getChunkDesc(0, 0).getChunkIndex());
    }

    @Test
    void deleteFindsTransparentNoncollidingBlockInsteadOfAssumingZero() {
        MutableLevel level = MutableLevel.snapshot(new FixtureLevel(3, 2));
        var operations = operations(level, new CommonObjectPlacementEncoding());
        assertEquals(1, operations.findEmptyBlock());
        var delete = operations.delete(0, new CellRectangle(1, 0, 2, 1));
        delete.apply();
        assertEquals(List.of(0, 1, 1), row(level, 0));
        delete.undo();
        assertEquals(List.of(0, 0, 0), row(level, 0));
        level.setPattern(1, level.getPattern(0));
        level.markSaved();
        assertThrows(IllegalStateException.class, () -> operations.delete(0, new CellRectangle(0, 0, 1, 1)));
        assertFalse(level.isModifiedSinceLastSave());
    }

    @Test
    void staleCommandsRejectBeforeAnyWritesAndClipboardCannotCrossLibraries() {
        MutableLevel level = MutableLevel.snapshot(new FixtureLevel(4, 2));
        var operations = operations(level, new CommonObjectPlacementEncoding());
        var fill = operations.rectangleFill(0, new CellRectangle(0, 0, 2, 1), 1);
        level.setBlockInMap(0, 1, 0, 2);
        level.markSaved();
        assertThrows(IllegalStateException.class, fill::apply);
        assertEquals(List.of(0, 2, 0, 0), row(level, 0));
        assertFalse(level.isModifiedSinceLastSave());
        var deletion = operations.delete(0, new CellRectangle(0, 0, 1, 1));
        level.addRingSpawn(new RingSpawn(100, 8, 1));
        assertThrows(IllegalStateException.class, deletion::apply);
        assertEquals(0, level.getMap().getValue(0, 0, 0));
        var other = operations(MutableLevel.snapshot(new FixtureLevel(4, 2)), new CommonObjectPlacementEncoding());
        assertThrows(IllegalArgumentException.class, () -> other.paste(operations.copy(0, new CellRectangle(0, 0, 1, 1)), 0, 0, 0));
    }

    @Test
    void copySurvivesSourceCompositionUndoAndPasteImportsExactSnapshotWithUndoRedo() {
        MutableLevel level = MutableLevel.snapshot(new FixtureLevel(4, 2, 5, 5));
        int[] oldBlock = level.getBlock(1).saveState();
        int[] oldChunk = level.getChunk(3).saveState();
        int[] authoredChunk = {0xE001, 0xC001, 1, 0, 1, 0};
        int[] authoredBlock = {0xB403, 0x7803, 3, 3};
        var author = new LocalCompositionCommand(level, 0, 0, 0, 1, authoredBlock, 3, authoredChunk);
        author.apply();
        var operations = operations(level, new CommonObjectPlacementEncoding());
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
        author.undo();
        assertArrayEquals(authoredBlock, clipboard.library().getBlock(1).saveState(), "ghost library survives undo");
        assertArrayEquals(authoredChunk, clipboard.library().getChunk(3).saveState());
        assertEquals(List.of(1), operations.preview(clipboard, 0, 1, 0).blocks(), "ghost retains captured library indices");
        var paste = operations.paste(clipboard, 0, 1, 0);
        assertArrayEquals(oldBlock, level.getBlock(1).saveState(), "planning imports never writes live definitions");
        paste.apply();
        int pastedBlock = level.getMap().getValue(0, 1, 0) & 0xFF;
        int pastedChunk = level.getBlock(pastedBlock).getChunkDesc(0, 0).getChunkIndex();
        assertArrayEquals(authoredChunk, level.getChunk(pastedChunk).saveState());
        for (int i = 0; i < authoredBlock.length; i++) {
            assertEquals(authoredBlock[i] & ~0x3FF, level.getBlock(pastedBlock).saveState()[i] & ~0x3FF,
                    "collision and flip flags remain native after chunk remapping");
        }
        int[] importedBlock = level.getBlock(pastedBlock).saveState();
        assertEquals(0, level.getMap().getValue(0, 0, 0), "undone source terrain stays undone");
        paste.undo();
        assertEquals(0, level.getMap().getValue(0, 1, 0));
        assertArrayEquals(oldBlock, level.getBlock(1).saveState());
        assertArrayEquals(oldChunk, level.getChunk(3).saveState());
        paste.apply();
        assertEquals(pastedBlock, level.getMap().getValue(0, 1, 0) & 0xFF);
        assertArrayEquals(importedBlock, level.getBlock(pastedBlock).saveState());
        assertArrayEquals(authoredChunk, level.getChunk(pastedChunk).saveState());
    }

    @Test
    void pasteReusesIdenticalReferencedCompositionRatherThanRewritingSharedDefinitions() {
        MutableLevel level = MutableLevel.snapshot(new FixtureLevel(4, 2, 5, 5));
        int[] desiredChunk = {1, 0x2001, 0, 1, 1, 0};
        int[] desiredBlock = {0x1403, 3, 3, 3};
        var author = new LocalCompositionCommand(level, 0, 0, 0, 1, desiredBlock, 3, desiredChunk);
        author.apply();
        var operations = operations(level, new CommonObjectPlacementEncoding());
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
        author.undo();
        level.restoreChunkState(4, desiredChunk);
        int[] equivalentBlock = desiredBlock.clone();
        for (int i = 0; i < equivalentBlock.length; i++) equivalentBlock[i] = (equivalentBlock[i] & ~0x3FF) | 4;
        level.restoreBlockState(2, equivalentBlock);
        level.setBlockInMap(0, 3, 0, 2);
        var paste = operations.paste(clipboard, 0, 1, 0);
        paste.apply();
        assertEquals(2, level.getMap().getValue(0, 1, 0));
        assertArrayEquals(equivalentBlock, level.getBlock(2).saveState());
        assertArrayEquals(desiredChunk, level.getChunk(4).saveState());
        paste.undo();
        assertEquals(2, level.getMap().getValue(0, 3, 0));
        assertArrayEquals(equivalentBlock, level.getBlock(2).saveState());
        assertArrayEquals(desiredChunk, level.getChunk(4).saveState());
    }

    @Test
    void exhaustedChunkOrBlockBanksRejectPasteBeforeAnyMutation() {
        for (boolean exhaustChunks : new boolean[]{true, false}) {
            MutableLevel level = MutableLevel.snapshot(new FixtureLevel(4, 2));
            var operations = operations(level, new CommonObjectPlacementEncoding());
            int[] originalChunk = level.getChunk(0).saveState();
            int[] originalBlock = level.getBlock(0).saveState();
            if (exhaustChunks) level.restoreChunkState(0, new int[]{1, 0, 0, 0, 0, 0});
            else level.restoreBlockState(0, new int[]{0x1400, 0, 0, 0});
            var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
            level.restoreChunkState(0, originalChunk);
            level.restoreBlockState(0, originalBlock);
            if (!exhaustChunks) {
                level.setBlockInMap(0, 2, 0, 1);
                level.setBlockInMap(0, 3, 0, 2);
            }
            var mapBefore = level.getMap().getData().clone();
            int[][] blocksBefore = java.util.stream.IntStream.range(0, 3).mapToObj(i -> level.getBlock(i).saveState()).toArray(int[][]::new);
            int[][] chunksBefore = java.util.stream.IntStream.range(0, 3).mapToObj(i -> level.getChunk(i).saveState()).toArray(int[][]::new);
            level.markSaved();
            var failure = assertThrows(IllegalStateException.class, () -> operations.paste(clipboard, 0, 1, 0));
            assertTrue(failure.getMessage().contains(exhaustChunks ? "chunk" : "block"));
            assertArrayEquals(mapBefore, level.getMap().getData());
            for (int i = 0; i < 3; i++) {
                assertArrayEquals(blocksBefore[i], level.getBlock(i).saveState());
                assertArrayEquals(chunksBefore[i], level.getChunk(i).saveState());
            }
            assertFalse(level.isModifiedSinceLastSave());
        }
    }

    @Test
    void newlyReferencedImportSlotsRejectStaleApplyAndUndoBeforeChangingDefinitions() {
        MutableLevel level = MutableLevel.snapshot(new FixtureLevel(4, 2, 5, 5));
        int[] desiredChunk = {0x8001, 1, 1, 0, 0, 1};
        int[] desiredBlock = {3, 3, 3, 0xF803};
        var author = new LocalCompositionCommand(level, 0, 0, 0, 1, desiredBlock, 3, desiredChunk);
        author.apply();
        var operations = operations(level, new CommonObjectPlacementEncoding());
        var clipboard = operations.copy(0, new CellRectangle(0, 0, 1, 1));
        author.undo();
        int[] chunkBefore = level.getChunk(3).saveState();
        var paste = operations.paste(clipboard, 0, 1, 0);
        level.setBlockInMap(0, 3, 0, 1); // formerly free planned block slot is now shared
        assertThrows(IllegalStateException.class, paste::apply);
        assertArrayEquals(chunkBefore, level.getChunk(3).saveState());
        assertEquals(0, level.getMap().getValue(0, 1, 0));
        level.setBlockInMap(0, 3, 0, 0);
        paste.apply();
        int imported = level.getMap().getValue(0, 1, 0) & 0xFF;
        level.setBlockInMap(0, 3, 0, imported);
        assertThrows(IllegalStateException.class, paste::undo);
        assertArrayEquals(desiredChunk, level.getChunk(3).saveState());
        assertEquals(imported, level.getMap().getValue(0, 1, 0) & 0xFF);
    }

    private static EditorRegionOperations operations(MutableLevel level, ObjectPlacementEncoding encoding) {
        return new EditorRegionOperations(level, new EditorSpawnFactory(encoding, new EditorPlacementIdAllocator(level)));
    }

    private static List<Integer> row(MutableLevel level, int y) {
        return java.util.stream.IntStream.range(0, level.getMap().getWidth())
                .mapToObj(x -> level.getMap().getValue(0, x, y) & 0xFF).toList();
    }

    private static final class FixtureLevel extends AbstractLevel implements RingObjectPlacementMapping {
        final java.util.Map<ObjectSpawn, List<RingSpawn>> groups = new LinkedHashMap<>();
        FixtureLevel(int width, int height) { this(width, height, 3, 3); }
        FixtureLevel(int width, int height, int blockSlots, int chunkSlots) {
            super(0);
            patternCount = 2;
            patterns = new Pattern[] { new Pattern(), new Pattern() };
            patterns[0].setPixel(0, 0, (byte) 1);
            chunkCount = chunkSlots;
            chunks = new Chunk[chunkCount];
            java.util.Arrays.setAll(chunks, ignored -> new Chunk());
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                chunks[1].setPatternDesc(x, y, new PatternDesc(1));
                chunks[2].setPatternDesc(x, y, new PatternDesc(1));
            }
            chunks[2].setSolidTileIndex(1);
            blockCount = blockSlots;
            blocks = new Block[blockCount];
            java.util.Arrays.setAll(blocks, ignored -> new Block(2));
            for (int i = 0; i < blockCount; i++) for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                blocks[i].setChunkDesc(x, y, new ChunkDesc(i < 3 ? i : 0));
            }
            solidTileCount = 2;
            solidTiles = new SolidTile[] {
                    new SolidTile(0, new byte[16], new byte[16], (byte) 0),
                    new SolidTile(1, new byte[16], new byte[16], (byte) 0) };
            palettes = new Palette[PALETTE_COUNT];
            java.util.Arrays.setAll(palettes, i -> new Palette());
            map = new com.openggf.level.Map(2, width, height);
            objects = List.of();
            rings = List.of();
        }
        void placements(List<ObjectSpawn> objectSpawns, List<RingSpawn> ringSpawns) {
            objects = objectSpawns;
            rings = ringSpawns;
        }
        @Override public int getBlockPixelSize() { return 32; }
        @Override public int getChunksPerBlockSide() { return 2; }
        @Override public java.util.Map<ObjectSpawn, List<RingSpawn>> ringObjectPlacementMapping() { return groups; }
    }
}

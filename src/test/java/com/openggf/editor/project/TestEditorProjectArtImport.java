package com.openggf.editor.project;

import com.openggf.editor.EditorCommand;
import com.openggf.editor.EditorHistory;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free end-to-end authoring imports, including ownership of transparent, unused tiles. */
class TestEditorProjectArtImport {
    @TempDir Path directory;

    @Test void spareTilesAppendWithinDescriptorCapacityWithoutChangingExistingAssets() {
        for (int count : List.of(2, 2000, 2048, 2100)) {
            var source = new TestEditorProjects.Fixture(GameId.S2) {
                { patterns = new Pattern[count]; patternCount = count;
                  for (int i = 0; i < count; i++) { patterns[i] = new Pattern(); patterns[i].setPixel(0, 0, (byte) (i % 15 + 1)); } }
            };
            var clone = EditorProjectDocument.cloneLevel(TestEditorProjects.metadata(GameId.S2, source), source).level();
            assertEquals(count < 2048 ? Math.min(2048, count + 256) : count, clone.getPatternCount());
            for (int i = 0; i < count; i++) assertEquals(source.getPattern(i).getPixel(0, 0), clone.getPattern(i).getPixel(0, 0));
            for (int i = count; i < clone.getPatternCount(); i++) assertEquals(0, clone.getPattern(i).getPixel(0, 0));
            assertArrayEquals(source.getChunk(1).saveState(), clone.getChunk(1).saveState());
            assertEquals(source.getBlock(1).getChunkDesc(0, 0).get(), clone.getBlock(1).getChunkDesc(0, 0).get());
            assertTrue(source.getPalette(1).dataEquals(clone.getPalette(1)));
            assertEquals(source.getObjects(), clone.getObjects());
            assertEquals(source.getRings(), clone.getRings());
            assertNotSame(source.getPattern(1), clone.getPattern(1));
        }
    }

    @Test void transparentImportOwnershipSurvivesSaveOpenDuplicateAndStructuralReplacement() throws Exception {
        inputs(false);
        var document = document();
        var store = new EditorProjectStore();
        Path file = directory.resolve("owned.ogfproject");
        store.save(file, document);
        var history = new EditorHistory();
        var prepared = prepare(document);
        assertEquals(List.of(2), prepared.patternIndices());
        assertTrue(document.importedPatternSlots().isEmpty());
        history.execute(prepared.command());
        assertTrue(document.isModified(), "even an unused all-transparent import owns a durable slot");
        assertEquals(0, document.level().getPattern(2).getPixel(0, 0));
        assertTrue(document.importedPatternSlots().get(2));
        assertTrue(history.undo());
        assertFalse(document.isModified(), "undo restores the ownership savepoint even when pixel bytes were unchanged");
        assertFalse(history.undo(), "one import is one history step");
        assertTrue(history.redo());
        store.save(file, document);
        assertFalse(document.isModified());
        var reopened = store.open(file);
        assertEquals(document.importedPatternSlots(), reopened.importedPatternSlots());
        assertEquals(List.of(3), prepare(reopened).patternIndices(), "a transparent imported slot cannot be recycled");
        var duplicate = reopened.duplicate(reopened.metadata().withName("Copy"));
        assertEquals(reopened.importedPatternSlots(), duplicate.importedPatternSlots());
        assertNull(duplicate.path());
        assertTrue(duplicate.isModified());
        var replacement = reopened.copyForReplacement();
        replacement.resize(5, 4, false);
        assertEquals(reopened.importedPatternSlots(), replacement.importedPatternSlots());
        var leaked = reopened.importedPatternSlots(); leaked.clear();
        assertTrue(reopened.importedPatternSlots().get(2), "callers cannot mutate ownership snapshots");
    }

    @Test void importedPixelsPaletteAndReservationsUndoRedoToSavedContentExactly() throws Exception {
        inputs(true);
        var document = document();
        var palette = document.level().getPalette(2).deepCopy();
        var refreshes = new ArrayList<List<Integer>>();
        var prepared = document.prepareArtImport(directory, Path.of("art.png"), Path.of("sheet.yaml"), true,
                (l, c) -> true, line -> {}, slots -> refreshes.add(List.copyOf(slots)));
        var history = new EditorHistory();
        history.execute(prepared.command());
        int slot = prepared.patternIndices().getFirst();
        assertEquals(1, document.level().getPattern(slot).getPixel(1, 0));
        assertEquals(255, Byte.toUnsignedInt(document.level().getPalette(2).getColor(1).r));
        new EditorProjectStore().save(directory.resolve("imported.ogfproject"), document);
        history.undo();
        assertEquals(0, document.level().getPattern(slot).getPixel(1, 0));
        assertTrue(document.level().getPalette(2).dataEquals(palette));
        assertTrue(document.importedPatternSlots().isEmpty());
        assertTrue(document.isModified());
        history.redo();
        assertEquals(1, document.level().getPattern(slot).getPixel(1, 0));
        assertFalse(document.isModified());
        assertEquals(List.of(List.of(slot), List.of(slot), List.of(slot)), refreshes);
    }

    @Test void failedArtRefreshRestoresPixelsPaletteAndReservationsWithoutHistoryEntry() throws Exception {
        inputs(true);
        var document = document();
        var before = document.snapshot();
        var prepared = document.prepareArtImport(directory, Path.of("art.png"), Path.of("sheet.yaml"), true,
                (l, c) -> true, line -> {}, slots -> { throw new IllegalStateException("atlas upload failed"); });
        var history = new EditorHistory();
        assertEquals("atlas upload failed", assertThrows(IllegalStateException.class,
                () -> history.execute(prepared.command())).getMessage());
        assertTrue(before.contentEquals(document.level()));
        assertTrue(document.importedPatternSlots().isEmpty());
        assertFalse(history.hasUndoEntries());
    }

    @Test void preparedImportRejectsReplacementLibraryAndChangedReservationsBeforeMutation() throws Exception {
        inputs(true);
        var document = document();
        var stale = prepare(document);
        var other = prepare(document);
        other.command().apply();
        var after = document.snapshot();
        assertThrows(IllegalStateException.class, stale.command()::apply);
        assertTrue(after.contentEquals(document.level()));
        other.command().undo();
        document.replace(document.metadata(), document.level());
        assertThrows(IllegalStateException.class, stale.command()::apply);
        assertTrue(document.importedPatternSlots().isEmpty());
    }

    @Test void malformedPersistedSlotOwnershipIsRejectedWithValidChecksum() throws Exception {
        inputs(false);
        var document = document();
        var store = new EditorProjectStore();
        Path file = directory.resolve("invalid.ogfproject");
        store.save(file, document);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readAllBytes(file));
        for (String invalid : List.of("[0]", "[2048]", "[300]", "[2,2]", "[3,2]", "null", "[\"2\"]", "[2.5]", "[true]")) {
            var changed = original.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("payload").path("assets"))
                    .set("importedPatterns", mapper.readTree(invalid));
            // The checksum is recomputed, so this exercises schema/range validation.
            TestEditorProjectValidation.resign(changed);
            byte[] resigned = mapper.writeValueAsBytes(changed);
            assertThrows(java.io.IOException.class, () -> store.decode(resigned), invalid);
        }
    }

    @Test void hudFormAndSharedCommandImportThroughOrdinaryHistoryAndRejectStaleDocument() throws Exception {
        inputs(true);
        var host = new Host();
        var document = document();
        new EditorProjectStore().save(directory.resolve("active.ogfproject"), document);
        host.workspace.openProject(document);
        assertTrue(host.workspace.commands().stream().anyMatch(c -> c.label().equals("Import art")));
        host.workspace.importArtForm();
        assertEquals("Import art", host.workspace.menu().title());
        host.workspace.menu().setValue("folder", directory.toString());
        host.workspace.menu().setValue("png", "art.png");
        host.workspace.menu().setValue("sheet", "sheet.yaml");
        host.workspace.menu().setValue("palette", "2");
        host.workspace.menu().submit();
        assertNotNull(host.workspace.menu().error());
        assertFalse(host.history.hasUndoEntries());
        host.workspace.menu().setValue("palette", "0");
        host.workspace.menu().submit();
        assertFalse(host.workspace.menu().isOpen());
        assertTrue(document.importedPatternSlots().get(2));
        assertEquals(1, host.executed);
        assertEquals(List.of(2), host.lastSlots);
        assertTrue(host.history.undo());
        assertTrue(document.importedPatternSlots().isEmpty());
        assertFalse(host.history.undo());
        assertTrue(host.history.redo());
        new EditorProjectStore().save(directory.resolve("active.ogfproject"), document);
        host.workspace.openProject(document());
        assertNotSame(document, host.workspace.document());
        assertThrows(IllegalStateException.class, () -> host.workspace.importArt(document, directory,
                Path.of("art.png"), Path.of("sheet.yaml"), false));
        assertThrows(IllegalStateException.class, host.history::undo, "a queued history command cannot edit another active document");
        assertTrue(document.importedPatternSlots().get(2));
    }

    @Test void folderEscapeAndHostPalettePolicyRejectBeforeWorkspaceHistory() throws Exception {
        inputs(true);
        var host = new Host();
        var document = document();
        host.workspace.openProject(document);
        var before = document.snapshot();
        assertThrows(java.io.IOException.class, () -> host.workspace.importArt(document, directory,
                Path.of("../art.png"), Path.of("sheet.yaml"), false));
        assertThrows(IllegalArgumentException.class, () -> host.workspace.importArt(document, directory,
                Path.of("art.png"), Path.of("sheet.yaml"), true));
        assertTrue(before.contentEquals(document.level()));
        assertTrue(document.importedPatternSlots().isEmpty());
        assertEquals(0, host.executed);
    }

    @Test void clipboardRejectsAnUndoneImportWhoseReleasedTileSlotWasReused() throws Exception {
        inputs(true);
        var document = document();
        var level = document.level();
        level.restoreObjectBackedRingState(new com.openggf.level.MutableLevel.ObjectBackedRingState(
                List.of(), List.of(), java.util.Map.of()));
        var history = new EditorHistory();
        var first = prepare(document);
        history.execute(first.command());
        int tile = first.patternIndices().getFirst();
        int[] block = new int[64]; java.util.Arrays.fill(block, 2);
        int[] chunk = {tile, tile, tile, tile, 0, 0};
        history.execute(new com.openggf.editor.commands.LocalCompositionCommand(level, 0, 0, 0, 2, block, 2, chunk));
        var operations = new com.openggf.editor.selection.EditorRegionOperations(level,
                new com.openggf.editor.EditorSpawnFactory(new com.openggf.game.common.CommonObjectPlacementEncoding(),
                        new com.openggf.editor.EditorPlacementIdAllocator(level)));
        var clipboard = operations.copy(0, new com.openggf.editor.selection.CellRectangle(0, 0, 1, 1));
        assertTrue(history.undo()); // Undo the composition using the imported art.
        assertTrue(history.undo()); // Undo the original art import, releasing its blank slot.
        inputs(false);
        var second = prepare(document);
        assertEquals(first.patternIndices(), second.patternIndices());
        history.execute(second.command());
        var before = document.snapshot();
        assertEquals(1, clipboard.library().getPattern(tile).getPixel(1, 0), "ghost retains original art");
        assertEquals(0, level.getPattern(tile).getPixel(1, 0), "new import owns different bytes in the same slot");
        assertDoesNotThrow(() -> operations.preview(clipboard, 0, 1, 0), "the frozen ghost remains available");
        assertTrue(assertThrows(IllegalStateException.class, () -> operations.paste(clipboard, 0, 1, 0))
                .getMessage().contains("re-copy"));
        assertTrue(before.contentEquals(level), "stale paste cannot write cells or definitions");

        var original = new Pattern(); original.copyFrom(clipboard.library().getPattern(tile));
        level.setPattern(tile, original);
        var queued = operations.paste(clipboard, 0, 1, 0);
        level.setPattern(tile, new Pattern());
        assertThrows(IllegalStateException.class, queued::apply, "queued apply/redo revalidates art before mutation");
        assertTrue(before.contentEquals(level));
    }

    private EditorProjectDocument.ArtImport prepare(EditorProjectDocument document) throws Exception {
        return document.prepareArtImport(directory, Path.of("art.png"), Path.of("sheet.yaml"), false,
                (l, c) -> false, line -> {}, slots -> {});
    }

    private static EditorProjectDocument document() {
        var source = new TestEditorProjects.Fixture(GameId.S2);
        return EditorProjectDocument.cloneLevel(TestEditorProjects.metadata(GameId.S2, source), source);
    }

    private void inputs(boolean colored) throws Exception {
        var image = PixelImage.blank(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            image.setRGB(x, y, colored && x % 2 != 0 ? 0xffffffff : 0xff000000);
        PngCodec.write(directory.resolve("art.png"), image);
        Files.writeString(directory.resolve("sheet.yaml"), """
                formatVersion: 1
                paletteLine: 2
                palette: ["#000000", "#FFFFFF", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000"]
                frames:
                  - delay: 0
                    pieces:
                      - sourceX: 0
                        sourceY: 0
                        widthPixels: 8
                        heightPixels: 8
                        xOffset: 0
                        yOffset: 0
                        hFlip: false
                        vFlip: false
                        paletteIndex: 2
                        priority: false
                """);
    }

    private static final class Host implements EditorProjectWorkspace.Host {
        final EditorHistory history = new EditorHistory();
        final EditorProjectWorkspace workspace = new EditorProjectWorkspace(this);
        final GameModule module = new Sonic2GameModule();
        int executed;
        List<Integer> lastSlots = List.of();
        @Override public GameModule selectGame(GameId game) { return module; }
        @Override public GameModule currentModule() { return module; }
        @Override public Level loadLibrary(int zone, int act) { throw new AssertionError("import does not load a ROM"); }
        @Override public String sourceFingerprint() { return "art-test"; }
        @Override public EditorCursorState cursor() { return new EditorCursorState(0, 0); }
        @Override public void finishPendingEdits() { }
        @Override public void showDocument(EditorProjectDocument document) { }
        @Override public void playtest(EditorProjectDocument document, boolean cursor) { throw new AssertionError(); }
        @Override public void closeDocument() { }
        @Override public void menuClosed(EditorProjectDocument document) { }
        @Override public void execute(EditorCommand command) { history.execute(command); executed++; }
        @Override public void status(String message) { }
        @Override public void artImported(EditorProjectDocument document, List<Integer> slots) { lastSlots = List.copyOf(slots); }
    }
}

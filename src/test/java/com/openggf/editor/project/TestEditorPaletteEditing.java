package com.openggf.editor.project;

import com.openggf.editor.EditorHistory;
import com.openggf.editor.palette.EditorPaletteEditing;
import com.openggf.game.GameId;
import com.openggf.game.sonic1.Sonic1EditorPalettePolicy;
import com.openggf.game.sonic2.Sonic2EditorPalettePolicy;
import com.openggf.game.sonic3k.Sonic3kEditorPalettePolicy;
import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorPaletteEditing {
    @TempDir Path directory;

    @Test void nativeSaveWorkflowCannotOfferPaletteEditsThatItsFormatDoesNotPersist() {
        var controller = new com.openggf.editor.LevelEditorController();
        controller.attachLevel(com.openggf.level.MutableLevel.snapshot(new TestEditorProjects.Fixture(GameId.S2)));
        var registry = new com.openggf.game.sonic2.objects.Sonic2ObjectRegistry();
        var encoding = new com.openggf.game.common.CommonObjectPlacementEncoding();
        controller.configureSpawnEditing(registry, encoding);
        assertTrue(controller.isPaletteCellEditable(2, 3));
        controller.configureNativeSpawnEditing(registry, encoding);
        assertFalse(controller.isPaletteCellEditable(2, 3));
        assertThrows(IllegalArgumentException.class, () -> controller.editPaletteColor(2, 3, 0, 0, 0));
        assertFalse(controller.isModified());
    }

    @Test void eachCellEditUndoesExactPriorRgbAndRedoesIndependentCopies() {
        var source = new TestEditorProjects.Fixture(GameId.S2);
        source.getPalette(2).setColor(3, new Palette.Color((byte) 17, (byte) 218, (byte) 129));
        var refreshes = new ArrayList<Integer>();
        var history = new EditorHistory();
        var command = EditorPaletteEditing.command(source, 2, 3, 36, 146, 255,
                Sonic2EditorPalettePolicy::isEditable, refreshes::add);
        history.execute(command);
        assertColor(source.getPalette(2).getColor(3), 36, 146, 255);
        assertTrue(history.undo());
        assertColor(source.getPalette(2).getColor(3), 17, 218, 129);
        // Neither an old Color reference nor an externally changed result may corrupt redo.
        source.getPalette(2).getColor(3).r = 99;
        assertTrue(history.redo());
        assertColor(source.getPalette(2).getColor(3), 36, 146, 255);
        assertEquals(List.of(2, 2, 2), refreshes);
        assertTrue(history.undo());
        assertColor(source.getPalette(2).getColor(3), 17, 218, 129);
        assertFalse(history.undo(), "one cell edit must occupy exactly one history entry");
    }

    @Test void rejectsNonGenesisColorsAndUnavailableOrHostOwnedCellsWithoutMutation() {
        var source = new TestEditorProjects.Fixture(GameId.S3K);
        var before = source.getPalette(1).deepCopy();
        var refreshes = new ArrayList<Integer>();
        assertThrows(IllegalArgumentException.class, () -> EditorPaletteEditing.command(
                source, 1, 1, 0, 0, 0, Sonic3kEditorPalettePolicy::isEditable, refreshes::add));
        for (int bad : new int[] {-1, 1, 35, 218, 256}) {
            assertThrows(IllegalArgumentException.class, () -> EditorPaletteEditing.command(
                    source, 1, 2, bad, 0, 0, Sonic3kEditorPalettePolicy::isEditable, refreshes::add));
        }
        for (int[] cell : new int[][] {{-1, 0}, {4, 0}, {1, -1}, {1, 16}}) {
            assertThrows(IllegalArgumentException.class, () -> EditorPaletteEditing.command(
                    source, cell[0], cell[1], 0, 0, 0, (line, color) -> true, refreshes::add));
        }
        assertTrue(before.dataEquals(source.getPalette(1)));
        assertTrue(refreshes.isEmpty());
    }

    @Test void hostPoliciesMatchCharacterAndHudOwnership() {
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) {
            assertEquals(line != 0, Sonic1EditorPalettePolicy.isEditable(line, color));
            assertEquals(line != 0, Sonic2EditorPalettePolicy.isEditable(line, color));
            boolean hudReserved = line == 1 && List.of(1, 5, 12, 14, 15).contains(color);
            assertEquals(line != 0 && !hudReserved, Sonic3kEditorPalettePolicy.isEditable(line, color));
        }
        for (int[] cell : new int[][] {{-1, 0}, {4, 0}, {1, -1}, {1, 16}}) {
            assertFalse(Sonic1EditorPalettePolicy.isEditable(cell[0], cell[1]));
            assertFalse(Sonic2EditorPalettePolicy.isEditable(cell[0], cell[1]));
            assertFalse(Sonic3kEditorPalettePolicy.isEditable(cell[0], cell[1]));
        }
    }

    @Test void genesisStepConversionIsExactAndBounded() {
        int[] values = {0, 36, 73, 109, 146, 182, 219, 255};
        for (int step = 0; step < values.length; step++) {
            assertEquals(values[step], EditorPaletteEditing.componentFromStep(step));
            assertEquals(step, EditorPaletteEditing.stepFromComponent(values[step]));
        }
        assertThrows(IllegalArgumentException.class, () -> EditorPaletteEditing.componentFromStep(-1));
        assertThrows(IllegalArgumentException.class, () -> EditorPaletteEditing.componentFromStep(8));
        assertThrows(IllegalArgumentException.class, () -> EditorPaletteEditing.stepFromComponent(218));
    }

    @Test void failedRefreshRollsBackDocumentAndDoesNotPublishHistoryEntry() throws Exception {
        var document = document();
        var store = new EditorProjectStore();
        store.save(directory.resolve("unchanged.ogfproject"), document);
        var history = new EditorHistory();
        var error = assertThrows(IllegalStateException.class, () -> history.execute(
                EditorPaletteEditing.command(document.level(), 2, 3, 36, 146, 255,
                        Sonic2EditorPalettePolicy::isEditable, line -> { throw new IllegalStateException("upload"); })));
        assertEquals("upload", error.getMessage());
        assertFalse(document.isModified());
        assertFalse(history.hasUndoEntries());
    }

    @Test void failedUndoRetainsTheAppliedCellAndItsHistoryEntry() {
        var source = new TestEditorProjects.Fixture(GameId.S2);
        var history = new EditorHistory();
        boolean[] rejectRefresh = {false};
        history.execute(EditorPaletteEditing.command(source, 2, 3, 36, 146, 255,
                Sonic2EditorPalettePolicy::isEditable, line -> {
                    if (rejectRefresh[0]) throw new IllegalStateException("upload");
                }));
        rejectRefresh[0] = true;
        assertThrows(IllegalStateException.class, history::undo);
        assertColor(source.getPalette(2).getColor(3), 36, 146, 255);
        assertTrue(history.hasUndoEntries());
        rejectRefresh[0] = false;
        assertTrue(history.undo());
        assertColor(source.getPalette(2).getColor(3), 0, 0, 0);
    }

    @Test void projectSaveReopenAndUndoRetainExactSavedColors() throws Exception {
        var document = document();
        var store = new EditorProjectStore();
        Path file = directory.resolve("palette.ogfproject");
        store.save(file, document);
        var before = document.level().getPalette(2).deepCopy();
        var history = new EditorHistory();
        history.execute(EditorPaletteEditing.command(document.level(), 2, 3, 36, 146, 255,
                Sonic2EditorPalettePolicy::isEditable, line -> {}));
        assertTrue(document.isModified());
        assertTrue(history.undo());
        assertFalse(document.isModified());
        assertTrue(before.dataEquals(document.level().getPalette(2)));
        assertTrue(history.redo());
        store.save(file, document);
        assertFalse(document.isModified());
        var reopened = store.open(file);
        assertFalse(reopened.isModified());
        for (int line = 0; line < 4; line++)
            assertTrue(document.level().getPalette(line).dataEquals(reopened.level().getPalette(line)));
        assertColor(reopened.level().getPalette(2).getColor(3), 36, 146, 255);
        assertTrue(history.undo());
        assertTrue(document.isModified(), "undo after saving the new RGB differs from that savepoint");
        assertTrue(history.redo());
        assertFalse(document.isModified());
    }

    @Test void editedColorsExportAndParseAsExactGenesisWords() throws Exception {
        var fixture = new TestEditorProjects.Fixture(GameId.S2);
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++)
            fixture.getPalette(line).setColor(color, new Palette.Color((byte) 0, (byte) 0, (byte) 0));
        var level = com.openggf.level.MutableLevel.snapshot(fixture);
        EditorPaletteEditing.command(level, 2, 3, 36, 146, 255,
                Sonic2EditorPalettePolicy::isEditable, line -> {}).apply();
        var output = directory.resolve("exported");
        var request = new com.openggf.editor.persistence.FullLevelExporter.ExportRequest(output, "Palette", 64, 1024, 64, 64,
                new com.openggf.editor.persistence.FullLevelExporter.ExportMusic.Stock(129), GameId.S2, null);
        var exporter = new com.openggf.editor.persistence.FullLevelExporter(staging -> {});
        exporter.export(level, request);
        try (var assets = com.openggf.io.ModAssetRoot.directory(directory, output,
                com.openggf.io.ModInputLimits.production(), com.openggf.io.DirectoryAccess.TEST)) {
            var parsed = com.openggf.mods.code.ModLevelDefinitionParser.read(assets, new com.openggf.mods.code.BakedLevelRef("level.json"));
            byte[] palette = parsed.paletteLines()[2];
            int word = Byte.toUnsignedInt(palette[6]) << 8 | Byte.toUnsignedInt(palette[7]);
            assertEquals(0xE82, word);
            var color = new Palette.Color(); color.fromSegaFormat(word);
            assertColor(color, 36, 146, 255);
        }
    }

    private static EditorProjectDocument document() {
        var source = new TestEditorProjects.Fixture(GameId.S2);
        return EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, source), source);
    }

    private static void assertColor(Palette.Color color, int red, int green, int blue) {
        assertArrayEquals(new int[] {red, green, blue}, new int[] {
                Byte.toUnsignedInt(color.r), Byte.toUnsignedInt(color.g), Byte.toUnsignedInt(color.b)});
    }
}

package com.openggf.editor.assets;

import com.openggf.editor.EditorHistory;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.SolidTile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorArtImport {
    @TempDir Path project;

    @Test void convertsThroughBoundedAssetPipelineAndInstallsAsOneExactUndoStep() throws Exception {
        inputs(8, 8, 0xff000000, 0xffffffff);
        var level = level();
        level.getPalette(2).getColor(3).r = 17; // Preserve legacy input bytes on undo.
        var reserved = new BitSet(); reserved.set(1);
        var refreshes = new ArrayList<Integer>();
        var imported = EditorArtImport.prepare(project, Path.of("art.png"), Path.of("sheet.yaml"), level,
                reserved, true, (line, color) -> true, refreshes::add);
        assertEquals(List.of(4), imported.patternIndices(), "skip reserved, referenced and nonblank library slots");
        assertEquals(1, imported.frameCount());
        assertEquals(2, imported.paletteLine());
        assertTrue(imported.importsPalette());
        assertEquals(0, level.getPattern(4).getPixel(1, 0), "preparation never edits the document");
        var history = new EditorHistory();
        history.execute(imported.command());
        assertEquals(1, level.getPattern(4).getPixel(1, 0));
        assertEquals(255, Byte.toUnsignedInt(level.getPalette(2).getColor(1).r));
        assertTrue(history.undo());
        assertEquals(0, level.getPattern(4).getPixel(1, 0));
        assertEquals(17, level.getPalette(2).getColor(3).r);
        assertFalse(history.undo(), "an import is one history entry");
        assertTrue(history.redo());
        assertEquals(1, level.getPattern(4).getPixel(1, 0));
        assertEquals(List.of(2, 2, 2), refreshes);
        assertEquals(13, level.getPattern(3).getPixel(3, 3), "unused nonblank stock art is preserved");
        try (var files = Files.list(project)) { assertEquals(2, files.count(), "converted output is temporary"); }
    }

    @Test void tileOnlyImportKeepsExistingPaletteAndPublishesIndependentTiles() throws Exception {
        inputs(8, 8, 0xff000000, 0xffffffff);
        var level = level();
        var palette = level.getPalette(2).deepCopy();
        var imported = EditorArtImport.prepare(project, project.resolve("art.png"), project.resolve("sheet.yaml"),
                level, new BitSet(), false, (line, color) -> false, line -> fail("palette should not refresh"));
        imported.command().apply();
        assertTrue(palette.dataEquals(level.getPalette(2)));
        level.getPattern(1).setPixel(1, 0, (byte) 9);
        assertThrows(IllegalStateException.class, imported.command()::undo, "stale mutable tile edits are not discarded");
        level.getPattern(1).setPixel(1, 0, (byte) 1);
        imported.command().undo();
        imported.command().apply();
        assertEquals(1, level.getPattern(1).getPixel(1, 0), "redo retains its own decoded tile snapshot");
    }

    @Test void rejectsPaletteOwnershipAndInsufficientSlotsBeforeMutation() throws Exception {
        inputs(8, 8, 0xff000000, 0xffffffff);
        var level = level();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> EditorArtImport.prepare(project,
                Path.of("art.png"), Path.of("sheet.yaml"), level, new BitSet(), true,
                (line, color) -> color != 1, line -> {})).getMessage().contains("host-owned"));
        var unavailable = new BitSet(); unavailable.set(0, 8);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> EditorArtImport.prepare(project,
                Path.of("art.png"), Path.of("sheet.yaml"), level, unavailable, false,
                (line, color) -> true, line -> {})).getMessage().contains("Insufficient unused blank"));
        assertEquals(0, level.getPattern(1).getPixel(1, 0));
        assertEquals(0, level.getPalette(2).getColor(1).r);
    }

    @Test void failedPaletteRefreshRollsBackBothTilesAndPaletteWithoutHistoryEntry() throws Exception {
        inputs(8, 8, 0xff000000, 0xffffffff);
        var level = level();
        var palette = level.getPalette(2).deepCopy();
        var prepared = EditorArtImport.prepare(project, Path.of("art.png"), Path.of("sheet.yaml"), level,
                new BitSet(), true, (line, color) -> true, line -> { throw new IllegalStateException("upload failed"); });
        var history = new EditorHistory();
        assertEquals("upload failed", assertThrows(IllegalStateException.class,
                () -> history.execute(prepared.command())).getMessage());
        assertFalse(history.hasUndoEntries());
        assertEquals(0, level.getPattern(1).getPixel(1, 0));
        assertTrue(palette.dataEquals(level.getPalette(2)));
    }

    @Test void rejectsStaleAllocatedSlotsAndPaletteBeforeChangingAnyData() throws Exception {
        inputs(8, 8, 0xff000000, 0xffffffff);
        var level = level();
        var prepared = EditorArtImport.prepare(project, Path.of("art.png"), Path.of("sheet.yaml"), level,
                new BitSet(), true, (line, color) -> true, line -> {});
        level.setPatternDescInChunk(0, 0, 0, new PatternDesc(1));
        assertThrows(IllegalStateException.class, prepared.command()::apply);
        level.setPatternDescInChunk(0, 0, 0, new PatternDesc(0));
        level.getPalette(2).getColor(5).b = 1;
        assertThrows(IllegalStateException.class, prepared.command()::apply);
        assertEquals(0, level.getPattern(1).getPixel(1, 0));
    }

    @Test void converterErrorsIdentifyTileAlignmentAndColorsAndUnknownSheetFields() throws Exception {
        inputs(9, 8, 0xff000000, 0xffffffff);
        assertTrue(assertThrows(IllegalArgumentException.class, this::prepare).getMessage().contains("multiple of 8"));
        inputs(8, 8, 0xff000000, 0xff123456);
        assertTrue(assertThrows(IllegalArgumentException.class, this::prepare).getMessage().contains("16-color"));
        inputs(8, 8, 0xff000000, 0xffffffff);
        Files.writeString(project.resolve("sheet.yaml"), Files.readString(project.resolve("sheet.yaml")) + "unknown: true\n");
        assertThrows(IOException.class, this::prepare);
    }

    @Test void rejectsEscapingAndSymlinkedSourcesAndDoesNotOverwriteFiles() throws Exception {
        inputs(8, 8, 0xff000000, 0xffffffff);
        Path outside = Files.createTempFile(project.getParent(), "outside-art-", ".png");
        try {
            Files.copy(project.resolve("art.png"), outside, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            assertTrue(assertThrows(IOException.class, () -> EditorArtImport.prepare(project, outside,
                    Path.of("sheet.yaml"), level(), new BitSet(), false, (l, c) -> true, l -> {}))
                    .getMessage().contains("inside the selected project"));
            Path link = project.resolve("linked.png");
            Files.createSymbolicLink(link, project.resolve("art.png"));
            assertTrue(assertThrows(IOException.class, () -> EditorArtImport.prepare(project, link,
                    Path.of("sheet.yaml"), level(), new BitSet(), false, (l, c) -> true, l -> {}))
                    .getMessage().contains("symbolic links"));
            assertTrue(Files.size(project.resolve("art.png")) > 0);
        } finally { Files.deleteIfExists(outside); }
    }

    private EditorArtImport.PreparedImport prepare() throws IOException {
        return EditorArtImport.prepare(project, Path.of("art.png"), Path.of("sheet.yaml"), level(),
                new BitSet(), true, (line, color) -> true, line -> {});
    }

    private void inputs(int width, int height, int first, int second) throws IOException {
        var image = PixelImage.blank(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, x % 2 == 0 ? first : second);
        PngCodec.write(project.resolve("art.png"), image);
        Files.writeString(project.resolve("sheet.yaml"), """
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

    private static MutableLevel level() { return MutableLevel.snapshot(new Fixture()); }

    private static final class Fixture extends AbstractLevel {
        Fixture() {
            super(0);
            patterns = new Pattern[8]; patternCount = 8;
            for (int i = 0; i < patterns.length; i++) patterns[i] = new Pattern();
            patterns[3].setPixel(3, 3, (byte) 13);
            chunks = new Chunk[] {new Chunk()}; chunkCount = 1;
            chunks[0].setPatternDesc(1, 1, new PatternDesc(2));
            blocks = new Block[] {new Block(8)}; blockCount = 1;
            solidTiles = new SolidTile[] {new SolidTile(0, new byte[16], new byte[16], (byte) 0)}; solidTileCount = 1;
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            map = new com.openggf.level.Map(2, 1, 1);
            objects = List.of(); rings = List.of();
        }
        @Override public int getChunksPerBlockSide() { return 8; }
        @Override public int getBlockPixelSize() { return 128; }
    }
}

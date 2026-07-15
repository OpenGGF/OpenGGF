package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.*;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModLevelExportStagingValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestLevelConverter {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path temp;

    @Test
    void validatesAndCopiesExactEditorExportWithoutAlternativeMetadata() throws Exception {
        Path source = temp.resolve("source");
        MutableLevel level = MutableLevel.snapshot(new MinimalLevel());
        new FullLevelExporter(new ModLevelExportStagingValidator()).export(level,
                new FullLevelExporter.ExportRequest(source, "TEST", 0x40, 0x400,
                        0, 0, new FullLevelExporter.ExportMusic.Stock(0)));
        Path output = temp.resolve("baked");

        LevelConverter.Result result = new LevelConverter().convert(source, output);

        assertEquals(Files.readAllBytes(source.resolve("level.json")).length,
                Files.readAllBytes(output.resolve("level.json")).length);
        assertFalse(Files.exists(output.resolve("level.yaml")));
        try (ModAssetRoot root = ModAssetRoot.directory(temp, output, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var definition = ModLevelDefinitionParser.read(root, new BakedLevelRef("level.json"));
            assertEquals("TEST", definition.zoneName());
            assertEquals(1, definition.formatVersion());
        }
        assertTrue(Files.exists(output.resolve("palettes.bin")));
        assertTrue(result.filesCopied() >= 11);
    }

    @Test
    void v1ConverterInventoryRequiresTheLegacyPaletteAsset() throws Exception {
        Path source = temp.resolve("source-without-palette");
        MutableLevel level = MutableLevel.snapshot(new MinimalLevel());
        new FullLevelExporter(new ModLevelExportStagingValidator()).export(level,
                new FullLevelExporter.ExportRequest(source, "TEST", 0x40, 0x400,
                        0, 0, new FullLevelExporter.ExportMusic.Stock(0)));
        Files.delete(source.resolve("palettes.bin"));
        Path output = temp.resolve("missing-palette-output");

        assertThrows(Exception.class, () -> new LevelConverter().convert(source, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void v2ConverterInventoryForbidsTheLegacyPaletteAsset() throws Exception {
        Path source = createV2Export("v2-with-palette");
        Files.write(source.resolve("palettes.bin"), new byte[] {0});
        Path output = temp.resolve("v2-with-palette-output");

        assertThrows(Exception.class, () -> new LevelConverter().convert(source, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void v2ConverterAcceptsTheExactPaletteClaimInventory() throws Exception {
        Path source = createV2Export("v2-exact");
        Path output = temp.resolve("v2-exact-output");

        LevelConverter.Result result = new LevelConverter().convert(source, output);

        assertFalse(Files.exists(output.resolve("palettes.bin")));
        assertEquals(2, JSON.readTree(output.resolve("level.json").toFile())
                .get("formatVersion").intValue());
        assertTrue(result.filesCopied() >= 10);
    }

    @Test
    void rejectsInvalidExportBeforePublishingOutput() throws Exception {
        Path source = temp.resolve("bad"); Files.createDirectories(source);
        Files.writeString(source.resolve("level.json"), "{}");
        Path output = temp.resolve("output");
        assertThrows(Exception.class, () -> new LevelConverter().convert(source, output));
        assertFalse(Files.exists(output));
    }

    @Test void rejectsNonContractExtrasInsteadOfPublishingThem() throws Exception {
        Path source=temp.resolve("source-extra");
        MutableLevel level=MutableLevel.snapshot(new MinimalLevel());
        new FullLevelExporter(new ModLevelExportStagingValidator()).export(level,
                new FullLevelExporter.ExportRequest(source,"TEST",0x40,0x400,0,0,
                        new FullLevelExporter.ExportMusic.Stock(0)));
        Files.writeString(source.resolve("notes.yaml"),"not part of Task 14");
        Path output=temp.resolve("extra-output");
        assertThrows(Exception.class,()->new LevelConverter().convert(source,output));
        assertFalse(Files.exists(output));
    }

    @Test void publishesRetainedSnapshotWhenOriginalMutatesAfterValidation() throws Exception {
        Path source=temp.resolve("race-source");MutableLevel level=MutableLevel.snapshot(new MinimalLevel());
        new FullLevelExporter(new ModLevelExportStagingValidator()).export(level,
                new FullLevelExporter.ExportRequest(source,"TEST",0x40,0x400,0,0,
                        new FullLevelExporter.ExportMusic.Stock(0)));
        byte[] before=Files.readAllBytes(source.resolve("patterns.bin"));Path output=temp.resolve("race-output");
        new LevelConverter(()->{try{Files.write(source.resolve("patterns.bin"),new byte[]{9,9,9});}
            catch(Exception error){throw new RuntimeException(error);}}).convert(source,output);
        assertArrayEquals(before,Files.readAllBytes(output.resolve("patterns.bin")));
        assertArrayEquals(new byte[]{9,9,9},Files.readAllBytes(source.resolve("patterns.bin")));
    }

    private Path createV2Export(String directory) throws Exception {
        Path source = temp.resolve(directory);
        MutableLevel level = MutableLevel.snapshot(new MinimalLevel());
        new FullLevelExporter(new ModLevelExportStagingValidator()).export(level,
                new FullLevelExporter.ExportRequest(source, "TEST", 0x40, 0x400,
                        0, 0, new FullLevelExporter.ExportMusic.Stock(0)));
        ObjectNode metadata = (ObjectNode) JSON.readTree(source.resolve("level.json").toFile());
        metadata.put("formatVersion", 2);
        ((ObjectNode) metadata.get("assets")).remove("palettes");
        metadata.putObject("hostMetadata").putObject("s3k").put("objectZoneSet", "S3KL");
        metadata.putArray("paletteClaims");
        JSON.writeValue(source.resolve("level.json").toFile(), metadata);
        Files.delete(source.resolve("palettes.bin"));
        return source;
    }

    private static final class MinimalLevel extends AbstractLevel {
        MinimalLevel() {
            super(0);
            patternCount=1; patterns=new Pattern[]{new Pattern()};
            chunkCount=1; chunks=new Chunk[]{new Chunk()};
            blockCount=1; blocks=new Block[]{new Block(8)};
            solidTileCount=1; solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)};
            map=new com.openggf.level.Map(1,1,1);
            palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            objects=List.of(); rings=List.of();
            minX=0;maxX=256;minY=0;maxY=256;
        }
    }
}

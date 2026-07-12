package com.openggf.tools.modsdk;

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
            assertEquals("TEST", ModLevelDefinitionParser.read(root,
                    new BakedLevelRef("level.json")).zoneName());
        }
        assertTrue(result.filesCopied() >= 11);
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

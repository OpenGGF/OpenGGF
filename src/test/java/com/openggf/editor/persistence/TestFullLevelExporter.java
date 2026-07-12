package com.openggf.editor.persistence;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModLevelExportStagingValidator;
import com.openggf.mods.code.ModZoneLoader;
import com.openggf.mods.code.PreparedModZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestFullLevelExporter {
    private static final Map<String, String> MINIMAL_GOLDEN = Map.ofEntries(
            Map.entry("bg-map.bin", "ae23f5e27438bdb2aea3e94f133d920bf3b42805296a7ad56c3882efca5fb6b7"),
            Map.entry("blocks.bin", "b2fa3122e2254f40cff009a88eca96b26c6ef24254e7b63a9d91318fa14dceb9"),
            Map.entry("chunks.bin", "e874fe5c805428ee382be0fb8ee3172521e5962e0fd95adbf34266622fb4b52d"),
            Map.entry("collision-primary.bin", "32398230375c2994ce0d0c927d70700d03806ad48b7df171658fd4d22d25b88d"),
            Map.entry("collision-secondary.bin", "dc82733a3c19954e416dd9503c46529a52a94c07fa5eedb2dc24232dae2b2083"),
            Map.entry("fg-map.bin", "ae23f5e27438bdb2aea3e94f133d920bf3b42805296a7ad56c3882efca5fb6b7"),
            Map.entry("level.json", "3fe466d11acf6bbfb8d4efb75f1cdd0a175a3f1cf189323ac078e3518f248121"),
            Map.entry("palettes.bin", "e1691d9e33ff920a9b0c07fd0c13c0c566e20ab7f87eb37cf7cf439b2b2af0c6"),
            Map.entry("patterns.bin", "06d7059be17b0e5593e6676efc4401d9d6e5c4a7248a8ab3acdd7019738fd9b9"),
            Map.entry("solid-angles.bin", "dcd9dd3681084b66177d9516b4f55319b410e20dd4b4b91e12a93b8bea3a796d"),
            Map.entry("solid-heights.bin", "14b9d02245a625c5eb9edcaac809bb5674fb95908747c5a5cd8476d04f67a566"),
            Map.entry("solid-widths.bin", "e7892d5d324f5d47549fae89473f6f310fdf06c03437d260069d91656174d0d9"));
    private static final Map<String, String> EMPTY_GOLDEN = emptyGolden();

    @TempDir Path temp;

    @Test
    void completeExportIsParserAcceptedTaggedAndDeterministic() throws Exception {
        MutableLevel level = MutableLevel.snapshot(new ExportFixtureLevel());
        FullLevelExporter exporter = new FullLevelExporter(new ModLevelExportStagingValidator());
        var first = new FullLevelExporter.ExportRequest(temp.resolve("first"), "Creator Zone",
                0x40, 0x400, 16, 32, new FullLevelExporter.ExportMusic.Track("owner", "theme"));
        var second = new FullLevelExporter.ExportRequest(temp.resolve("second"), "Creator Zone",
                0x40, 0x400, 16, 32, new FullLevelExporter.ExportMusic.Track("owner", "theme"));
        exporter.export(level, first);
        exporter.export(level, second);

        assertEquals(MINIMAL_GOLDEN, sha256ByFile(first.outputDirectory()));
        MutableLevel empty = MutableLevel.snapshot(new ExportFixtureLevel());
        empty.replaceSpawnsPersisted(List.of(), List.of(), Map.of());
        Path emptyDirectory = temp.resolve("empty");
        exporter.export(empty, new FullLevelExporter.ExportRequest(emptyDirectory, "Empty Creator Zone",
                0x41, 0x401, 16, 32, new FullLevelExporter.ExportMusic.Stock(1)));
        assertEquals(EMPTY_GOLDEN, sha256ByFile(emptyDirectory));

        try (ModAssetRoot root = ModAssetRoot.directory(temp, first.outputDirectory(),
                ModInputLimits.production(), DirectoryAccess.TEST)) {
            ModLevelDefinition parsed = ModLevelDefinitionParser.read(root, new BakedLevelRef("level.json"));
            assertEquals(1, parsed.patternCount());
            assertEquals(1, parsed.chunkCount());
            assertEquals(1, parsed.blockCount());
            assertEquals(2, parsed.objects().size());
            assertInstanceOf(ModLevelDefinition.StockObjectSpawn.class, parsed.objects().getFirst());
            assertEquals("owner:badnik",
                    ((ModLevelDefinition.KeyedObjectSpawn) parsed.objects().getLast()).objectKey());
            assertEquals(1, parsed.rings().size());

            PreparedModZone prepared = new PreparedModZone("owner", "exported-zone", "mtz3",
                    parsed, null, parsed.zoneName(), parsed.levelIndex(), parsed.zoneIndex(),
                    parsed.start().x(), parsed.start().y());
            var ringSheet = new com.openggf.level.rings.RingSpriteSheet(
                    new Pattern[0], List.of(), 1, 8, 0, 0);
            Level loaded = ModZoneLoader.load(prepared, ringSheet);
            assertEquals(parsed.patternCount(), loaded.getPatternCount());
            assertEquals(parsed.chunkCount(), loaded.getChunkCount());
            assertEquals(parsed.blockCount(), loaded.getBlockCount());
            assertEquals(parsed.objects().size(), loaded.getObjects().size());
            assertEquals(parsed.rings().size(), loaded.getRings().size());
            assertEquals("owner:badnik", loaded.getObjects().getLast().objectKey());
        }
        List<String> names;
        List<String> secondNames;
        try (var files = Files.list(first.outputDirectory())) {
            names = files.map(path -> path.getFileName().toString()).sorted().toList();
        }
        try (var files = Files.list(second.outputDirectory())) {
            secondNames = files.map(path -> path.getFileName().toString()).sorted().toList();
        }
        assertEquals(names, secondNames);
        for (String name : names) assertArrayEquals(Files.readAllBytes(first.outputDirectory().resolve(name)),
                Files.readAllBytes(second.outputDirectory().resolve(name)), name);
    }

    @Test
    void ringBackingObjectIsOmittedWhileItsExpandedRingsRemain() throws Exception {
        MutableLevel level = MutableLevel.snapshot(new ExportFixtureLevel());
        ObjectSpawn backing = level.getObjects().getFirst();
        ObjectSpawn ordinary = level.getObjects().getLast();
        List<RingSpawn> rings = level.getRings();
        level.replaceSpawnsPersisted(List.of(backing, ordinary), rings, Map.of(backing, rings));

        Path output = temp.resolve("ring-backing");
        FullLevelExporter.ExportResult result = new FullLevelExporter(new ModLevelExportStagingValidator()).export(level,
                new FullLevelExporter.ExportRequest(output, "Creator Zone", 0x40, 0x400,
                        16, 32, new FullLevelExporter.ExportMusic.Stock(1)));

        try (ModAssetRoot root = ModAssetRoot.directory(temp, output,
                ModInputLimits.production(), DirectoryAccess.TEST)) {
            ModLevelDefinition parsed = ModLevelDefinitionParser.read(root, new BakedLevelRef("level.json"));
            assertEquals(1, parsed.objects().size());
            assertEquals("owner:badnik",
                    ((ModLevelDefinition.KeyedObjectSpawn) parsed.objects().getFirst()).objectKey());
            assertEquals(1, parsed.rings().size());
        }
        assertEquals(1, result.objectCount());
        assertEquals(1, result.ringCount());
    }

    private static Map<String, String> sha256ByFile(Path directory) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths.sorted().toList();
        }
        for (Path file : files) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            result.put(file.getFileName().toString(), HexFormat.of().formatHex(hash));
        }
        return result;
    }

    private static Map<String, String> emptyGolden() {
        Map<String, String> result = new LinkedHashMap<>(MINIMAL_GOLDEN);
        result.put("level.json", "9d73d62811aa03eb60d13d8d4f63d8390708d597b0bb3d6e6c5c533d1b7ffe40");
        return Map.copyOf(result);
    }

    @Test
    void controllerPublishesConfiguredExportResult() throws Exception {
        MutableLevel level = MutableLevel.snapshot(new ExportFixtureLevel());
        com.openggf.editor.LevelEditorController controller = new com.openggf.editor.LevelEditorController();
        controller.attachLevel(level);
        var request = new FullLevelExporter.ExportRequest(temp.resolve("controller"), "Creator Zone",
                0x40, 0x400, 16, 32, new FullLevelExporter.ExportMusic.Stock(1));
        var result = controller.exportLevel(new FullLevelExporter(new ModLevelExportStagingValidator()), request);
        assertEquals(result.directory(), controller.lastExportDirectory());
        assertTrue(Files.exists(result.directory().resolve("level.json")));
    }

    @Test
    void preflightAcceptsUnsignedMapBlock255AndRejectsLossySpawnBeforeValidator() throws Exception {
        MutableLevel unsigned = MutableLevel.snapshot(new UnsignedMapFixtureLevel());
        java.util.concurrent.atomic.AtomicInteger validations = new java.util.concurrent.atomic.AtomicInteger();
        Path accepted = temp.resolve("unsigned-map");
        new FullLevelExporter(path -> validations.incrementAndGet()).export(unsigned,
                new FullLevelExporter.ExportRequest(accepted, "Unsigned Map", 0x40, 0x400,
                        16, 32, new FullLevelExporter.ExportMusic.Stock(1)));
        assertEquals(1, validations.get());
        assertEquals(255, Byte.toUnsignedInt(Files.readAllBytes(accepted.resolve("fg-map.bin"))[16]));

        MutableLevel invalid = MutableLevel.snapshot(new ExportFixtureLevel());
        invalid.replaceObjectSpawnsPersisted(List.of(new ObjectSpawn(8, 16, 3, 0, 0,
                false, 17, 1)));
        Path rejected = temp.resolve("lossy");
        assertThrows(IllegalArgumentException.class, () ->
                new FullLevelExporter(path -> validations.incrementAndGet()).export(invalid,
                        new FullLevelExporter.ExportRequest(rejected, "Lossy", 0x40, 0x400,
                                16, 32, new FullLevelExporter.ExportMusic.Stock(1))));
        assertEquals(1, validations.get(), "strict validator must not run after preflight rejection");
        assertFalse(Files.exists(rejected));
    }

    @Test
    void hostileRawDescriptorsAndCollisionIndicesFailBeforeStagingValidation() {
        java.util.concurrent.atomic.AtomicInteger validations = new java.util.concurrent.atomic.AtomicInteger();
        FullLevelExporter exporter = new FullLevelExporter(path -> validations.incrementAndGet());

        MutableLevel highChunk = MutableLevel.snapshot(new ExportFixtureLevel());
        highChunk.getChunk(0).setPatternDesc(0, 0, new PatternDesc(0x1_0000));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(highChunk,
                request(temp.resolve("high-chunk"))));

        MutableLevel negativeBlock = MutableLevel.snapshot(new ExportFixtureLevel());
        negativeBlock.getBlock(0).setChunkDesc(0, 0, new ChunkDesc(-1));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(negativeBlock,
                request(temp.resolve("negative-block"))));

        MutableLevel highCollision = MutableLevel.snapshot(new ExportFixtureLevel());
        highCollision.getChunk(0).setSolidTileIndex(0x1_0000);
        assertThrows(IllegalArgumentException.class, () -> exporter.export(highCollision,
                request(temp.resolve("high-collision"))));
        assertEquals(0, validations.get());
    }

    @Test
    void concurrentPublicationHasOneWinnerAndOneReservationFailure() throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        FullLevelExporter slow = new FullLevelExporter(path -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
            }
        });
        Path target = temp.resolve("concurrent");
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> slow.export(MutableLevel.snapshot(new ExportFixtureLevel()),
                    request(target)));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.io.IOException.class, () ->
                    new FullLevelExporter(path -> {}).export(MutableLevel.snapshot(new ExportFixtureLevel()),
                            request(target)));
            release.countDown();
            assertEquals(target.toAbsolutePath().normalize(), first.get().directory());
        }
    }

    @Test
    void externalTargetCreatedAfterValidationIsPreservedAndTemporaryStateIsCleaned() throws Exception {
        Path target = temp.resolve("external-race");
        FullLevelExporter exporter = new FullLevelExporter(staging -> {
            Files.createDirectory(target);
            Files.writeString(target.resolve("external-marker.txt"), "external owner");
        });

        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> exporter.export(
                MutableLevel.snapshot(new ExportFixtureLevel()), request(target)));
        assertEquals("external owner", Files.readString(target.resolve("external-marker.txt")));
        assertFalse(Files.exists(temp.resolve("external-race.export-lock")));
        try (var children = Files.list(temp)) {
            assertTrue(children.map(path -> path.getFileName().toString())
                    .noneMatch(name -> name.startsWith("external-race.tmp-")));
        }
    }

    @Test
    void exporterAndGenericParserAcceptSixteenGridLevel() throws Exception {
        Path output = temp.resolve("grid16");
        new FullLevelExporter(new ModLevelExportStagingValidator()).export(
                MutableLevel.snapshot(new Grid16FixtureLevel()), request(output));
        try (ModAssetRoot root = ModAssetRoot.directory(temp, output,
                ModInputLimits.production(), DirectoryAccess.TEST)) {
            assertEquals(16, ModLevelDefinitionParser.read(root,
                    new BakedLevelRef("level.json")).blockGridSide());
        }
    }

    private FullLevelExporter.ExportRequest request(Path target) {
        return new FullLevelExporter.ExportRequest(target, "Creator Zone", 0x40, 0x400,
                16, 32, new FullLevelExporter.ExportMusic.Stock(1));
    }

    private static final class ExportFixtureLevel extends AbstractLevel {
        ExportFixtureLevel() {
            super(0x40);
            patternCount=1; patterns=new Pattern[]{new Pattern()};
            chunkCount=1; chunks=new Chunk[]{new Chunk()};
            blockCount=1; blocks=new Block[]{new Block(8)};
            solidTileCount=1; solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)};
            map=new com.openggf.level.Map(2,1,1);
            palettes=new Palette[4]; for(int i=0;i<4;i++)palettes[i]=new Palette();
            objects=List.of(new ObjectSpawn(8,16,3,0,0,false,16,1),
                    new ObjectSpawn(24,32,0,0,0,false,32,2,"owner","owner:badnik"));
            rings=List.of(new RingSpawn(40,48,3));
            minX=0;maxX=127;minY=0;maxY=96;
        }
    }

    private static final class UnsignedMapFixtureLevel extends AbstractLevel {
        UnsignedMapFixtureLevel() {
            super(0x40);
            patternCount=1; patterns=new Pattern[]{new Pattern()};
            chunkCount=1; chunks=new Chunk[]{new Chunk()};
            blockCount=256; blocks=new Block[256];
            for (int i=0;i<blocks.length;i++) blocks[i]=new Block(8);
            solidTileCount=1; solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)};
            map=new com.openggf.level.Map(1,1,1); map.setValue(0,0,0,(byte)255);
            palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            objects=List.of(); rings=List.of();
            minX=0;maxX=127;minY=0;maxY=96;
        }
    }

    private static final class Grid16FixtureLevel extends AbstractLevel {
        Grid16FixtureLevel() {
            super(0x40);
            patternCount=1; patterns=new Pattern[]{new Pattern()};
            chunkCount=1; chunks=new Chunk[]{new Chunk()};
            blockCount=1; blocks=new Block[]{new Block(16)};
            solidTileCount=1; solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)};
            map=new com.openggf.level.Map(1,1,1);
            palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            objects=List.of(); rings=List.of(); minX=0;maxX=127;minY=0;maxY=96;
        }
        @Override public int getChunksPerBlockSide() { return 16; }
    }
}

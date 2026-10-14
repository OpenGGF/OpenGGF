package com.openggf;

import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectMetadata;
import com.openggf.editor.project.EditorProjectStore;
import com.openggf.game.GameId;
import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.io.ModInputLimits;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.ModStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real project/archive readers and production static validation; no ROM or creator execution. */
class TestContentImportService {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(value = GameId.class, names = {"S1", "S2", "S3K"})
    void preparesCompleteProjectsAndEachCanonicalLevelDistribution(GameId game) throws Exception {
        Fixture source = new Fixture(game);
        var metadata = metadata(game, source);
        var document = EditorProjectDocument.create(metadata, source);
        Path project = temporary.resolve(game.code() + ".ogfproject");
        new EditorProjectStore().save(project, document);
        ContentImportService service = service();
        try (var inspected = service.inspect(project)) {
            assertEquals(ContentImportService.Kind.PROJECT, inspected.kind());
            assertEquals(game, inspected.suggestedGame().orElseThrow());
            assertEquals(metadata, inspected.project().metadata());
            assertEquals(project, inspected.project().path());
            assertThrows(IllegalStateException.class, inspected::level);
        }
        Path directory = temporary.resolve("export-" + game.code());
        var request = new FullLevelExporter.ExportRequest(directory, "Example " + game.code(), 0x40, 0x400,
                32, 32, new FullLevelExporter.ExportMusic.Stock(129), game,
                game == GameId.S3K ? ModObjectZoneSet.S3KL : null);
        new FullLevelExporter(staging -> { }).export(MutableLevel.snapshot(source), request);
        Path rootZip = zipDirectory(directory, temporary.resolve("root.zip"), "");
        Path folderZip = zipDirectory(directory, temporary.resolve("folder.zip"), "level/");
        for (Path distribution : List.of(directory, directory.resolve("level.json"), rootZip, folderZip)) {
            try (var inspected = service.inspect(distribution)) {
                assertEquals(ContentImportService.Kind.LEVEL, inspected.kind());
                assertEquals(game, inspected.suggestedGame().orElseThrow());
                assertEquals("Example " + game.code(), inspected.displayName());
                assertEquals(game == GameId.S1 ? 16 : 8, inspected.level().blockGridSide());
                assertEquals(0, inspected.level().patternBytes()[0]);
            }
        }
        assertFalse(Files.exists(modRoot()), "inspection must not install content or create a repository");
    }

    @Test
    void installsOnlyConfirmedImmutableBytesWithoutPersistingEnablementOrTrust() throws Exception {
        Path source = modJar("download.jar", "example", Map.of());
        byte[] inspectedBytes = Files.readAllBytes(source);
        ContentImportService service = service();
        try (var inspected = service.inspect(source)) {
            assertEquals(ContentImportService.Kind.MOD, inspected.kind());
            assertEquals("Example Package", inspected.displayName());
            assertFalse(inspected.modDescriptor().containsCode());
            assertFalse(Files.exists(modRoot()));
            Files.writeString(source, "replaced since inspection");
            Path installed = service.installMod(inspected);
            assertEquals(modRoot().resolve("example.jar"), installed);
            assertArrayEquals(inspectedBytes, Files.readAllBytes(installed));
            assertFalse(Files.exists(modRoot().resolve("modstate.json")));
            assertTrue(new ModStateStore(modRoot()).load().state().entries().isEmpty());
            assertThrows(IOException.class, () -> service.installMod(inspected));
            assertArrayEquals(inspectedBytes, Files.readAllBytes(installed));
            try (var entries = Files.list(modRoot())) {
                assertEquals(List.of(installed), entries.toList(), "staging must be cleaned up");
            }
        }
    }

    @Test
    void acceptsPackedModZipAndOneJarWrappedInAZipWithoutExtractingPaths() throws Exception {
        Path jar = modJar("example.jar", "example", Map.of("level.json", bytes("{}")));
        byte[] original = Files.readAllBytes(jar);
        Path directZip = temporary.resolve("mod.zip");
        Files.write(directZip, original);
        try (var inspected = service().inspect(directZip)) {
            assertEquals(ContentImportService.Kind.MOD, inspected.kind());
            assertEquals("example", inspected.modDescriptor().manifest().id());
        }
        Path wrapped = zip(temporary.resolve("download.zip"), Map.of(
                "release/example.jar", original, "release/README.txt", bytes("Install using Import content")));
        try (var inspected = service().inspect(wrapped)) {
            Files.delete(wrapped);
            Path installed = service().installMod(inspected);
            assertArrayEquals(original, Files.readAllBytes(installed));
        }
        assertFalse(Files.exists(temporary.resolve("release")));
    }

    @Test
    void refusesAmbiguousOrUnsupportedZipLayoutsAndMalformedPackages() throws Exception {
        Path jar = modJar("example.jar", "example", Map.of());
        byte[] jarBytes = Files.readAllBytes(jar);
        for (Map<String, byte[]> entries : List.of(
                Map.of("one.jar", jarBytes, "two.jar", jarBytes),
                Map.of("level.json", bytes("{}"), "one.jar", jarBytes),
                Map.of("one/level.json", bytes("{}"), "two/level.json", bytes("{}")),
                Map.of("too/deep/level.json", bytes("{}")),
                Map.of("README.txt", bytes("No content")))) {
            Path archive = zip(temporary.resolve("unsupported.zip"), entries);
            assertThrows(IOException.class, () -> service().inspect(archive));
        }
        Path manifestMissing = zip(temporary.resolve("missing.jar"), Map.of("README.txt", bytes("No manifest")));
        assertTrue(assertThrows(IOException.class, () -> service().inspect(manifestMissing)).getMessage().contains("manifest"));
        Path malformed = zip(temporary.resolve("malformed.jar"), Map.of(
                "META-INF/openggf-mod.yaml", bytes("id: incomplete")));
        assertThrows(IOException.class, () -> service().inspect(malformed));
        Path fakeCode = modJar("bad-code.jar", "bad-code", Map.of("example/Bad.class", new byte[] {0, 1, 2}));
        assertThrows(IOException.class, () -> service().inspect(fakeCode));
        assertFalse(Files.exists(modRoot()));
    }

    @Test
    void refusesTraversalCaseCollisionsAndOversizedInflationBeforeContentParsing() throws Exception {
        Path traversal = zip(temporary.resolve("traversal.zip"), Map.of("../escape.txt", bytes("escape")));
        assertThrows(IOException.class, () -> service().inspect(traversal));
        Path collision = zip(temporary.resolve("collision.zip"), Map.of("Data.txt", bytes("one"), "data.txt", bytes("two")));
        assertThrows(IOException.class, () -> service().inspect(collision));
        Path inflated = zip(temporary.resolve("oversized.zip"), Map.of("huge.bin", new byte[1025]));
        ContentImportService bounded = new ContentImportService(modRoot(), Set.of(),
                ModInputLimits.loweringBuilder().maxAssetBytes(1024).build());
        assertThrows(IOException.class, () -> bounded.inspect(inflated));
        assertFalse(Files.exists(temporary.resolve("escape.txt")));
        assertFalse(Files.exists(modRoot()));
    }

    @Test
    void preservesExistingFilesAndRejectsDuplicateOrBundledIds() throws Exception {
        Files.createDirectory(modRoot());
        Path original = modJar("mods/custom-name.jar", "example", Map.of("assets/original.txt", bytes("keep")));
        byte[] originalBytes = Files.readAllBytes(original);
        Path download = modJar("download.jar", "example", Map.of());
        try (var inspected = service().inspect(download)) {
            assertThrows(IOException.class, () -> service().installMod(inspected));
        }
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        Path another = modJar("reserved.jar", "reserved", Map.of());
        var reserved = new java.util.concurrent.atomic.AtomicReference<>(Set.<String>of());
        var service = new ContentImportService(modRoot(), reserved::get);
        try (var inspected = service.inspect(another)) {
            reserved.set(Set.of("reserved"));
            assertThrows(IOException.class, () -> service.installMod(inspected));
        }
        assertFalse(Files.exists(modRoot().resolve("reserved.jar")));
    }

    @Test
    void rejectsOldPreferenceIdsRatherThanReactivatingRemovedContent() throws Exception {
        var store = new ModStateStore(modRoot());
        assertInstanceOf(ModStateSaveResult.Saved.class, store.save(new ModState(ModState.CURRENT_FORMAT_VERSION,
                List.of(new ModState.Entry("example", true, 0, false, null)))));
        byte[] preferences = Files.readAllBytes(store.statePath());
        Path download = modJar("download.jar", "example", Map.of());
        try (var inspected = service().inspect(download)) {
            assertTrue(assertThrows(IOException.class, () -> service().installMod(inspected)).getMessage().contains("Saved preferences"));
        }
        assertArrayEquals(preferences, Files.readAllBytes(store.statePath()));
        assertFalse(Files.exists(modRoot().resolve("example.jar")));
    }

    @Test
    void refusedImportPreservesCorruptPreferencesAtTheirOriginalPath() throws Exception {
        Files.createDirectory(modRoot());
        Path preferences = modRoot().resolve("modstate.json");
        byte[] corrupt = bytes("{\"formatVersion\":1,\"entries\":[broken creator preferences");
        Files.write(preferences, corrupt);
        Path download = modJar("download.jar", "example", Map.of());
        try (var inspected = service().inspect(download)) {
            assertTrue(assertThrows(IOException.class, () -> service().installMod(inspected))
                    .getMessage().contains("Cannot verify mod preferences"));
        }
        assertArrayEquals(corrupt, Files.readAllBytes(preferences));
        try (var entries = Files.list(modRoot())) {
            assertEquals(List.of(preferences), entries.toList(), "failed import must not quarantine live preferences or leave staging");
        }
    }

    @Test
    void refusesExistingDestinationAndClosedPreparationWithoutPartialInstallation() throws Exception {
        Files.createDirectory(modRoot());
        Path occupied = modRoot().resolve("example.jar");
        Files.writeString(occupied, "preserve an unrelated file");
        Path download = modJar("download.jar", "example", Map.of());
        var inspected = service().inspect(download);
        assertThrows(IOException.class, () -> service().installMod(inspected));
        assertEquals("preserve an unrelated file", Files.readString(occupied));
        inspected.close();
        inspected.close();
        assertThrows(IllegalStateException.class, () -> service().installMod(inspected));
        try (var entries = Files.list(modRoot())) { assertEquals(1, entries.count()); }
    }

    @Test
    void refusesRepositoryCountAndCombinedByteLimits() throws Exception {
        Files.createDirectory(modRoot());
        Path installed = modJar("mods/one.jar", "one", Map.of());
        Path download = modJar("two.jar", "two", Map.of());
        ContentImportService countBounded = new ContentImportService(modRoot(), Set.of(),
                ModInputLimits.loweringBuilder().maxModJars(1).build());
        try (var inspected = countBounded.inspect(download)) {
            assertThrows(IOException.class, () -> countBounded.installMod(inspected));
        }
        ContentImportService byteBounded = new ContentImportService(modRoot(), Set.of(),
                ModInputLimits.loweringBuilder().maxRepositoryValidationBytes(Files.size(installed) + Files.size(download) - 1).build());
        try (var inspected = byteBounded.inspect(download)) {
            assertThrows(IOException.class, () -> byteBounded.installMod(inspected));
        }
        assertFalse(Files.exists(modRoot().resolve("two.jar")));
    }

    @Test
    void cancelledInstallationLeavesNoDestinationOrPreferences() throws Exception {
        Path download = modJar("download.jar", "example", Map.of());
        try (var inspected = service().inspect(download)) {
            Thread.currentThread().interrupt();
            try { assertThrows(java.io.InterruptedIOException.class, () -> service().installMod(inspected)); }
            finally { Thread.interrupted(); }
        }
        assertFalse(Files.exists(modRoot()));
    }

    @Test
    void rejectsSymlinkSourcesAndRepositoryParents() throws Exception {
        Path download = modJar("download.jar", "example", Map.of());
        Path link = temporary.resolve("linked.jar");
        try { Files.createSymbolicLink(link, download); }
        catch (IOException | UnsupportedOperationException unavailable) { assumeTrue(false, "Symbolic links unavailable"); }
        assertThrows(IOException.class, () -> service().inspect(link));
        Path real = Files.createDirectory(temporary.resolve("real"));
        Path linkedDirectory = temporary.resolve("linked-directory");
        Files.createSymbolicLink(linkedDirectory, real);
        var linkedService = new ContentImportService(linkedDirectory.resolve("mods"));
        try (var inspected = linkedService.inspect(download)) {
            assertThrows(IOException.class, () -> linkedService.installMod(inspected));
        }
        assertFalse(Files.exists(real.resolve("mods")));
    }

    private ContentImportService service() { return new ContentImportService(modRoot()); }
    private Path modRoot() { return temporary.resolve("mods"); }

    private Path modJar(String name, String id, Map<String, byte[]> additional) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>(additional);
        entries.put("META-INF/openggf-mod.yaml", bytes("""
                formatVersion: 1
                id: %s
                name: Example Package
                version: 1.0.0
                authors: [Example Author]
                description: Example data-only package
                engineApiRange: '*'
                type: patch
                baseGame: s2
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """.formatted(id)));
        return zip(temporary.resolve(name), entries);
    }

    private static Path zip(Path target, Map<String, byte[]> entries) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(target))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return target;
    }

    private static Path zipDirectory(Path source, Path target, String prefix) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                entries.put(prefix + source.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        return zip(target, entries);
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    private static EditorProjectMetadata metadata(GameId game, Fixture level) {
        return new EditorProjectMetadata(game, "Project " + game.code(), 0, 0, "reference", 32, 32,
                0, level.getMaxX(), 0, level.getMaxY(), new FullLevelExporter.ExportMusic.Stock(129),
                game == GameId.S3K ? ModObjectZoneSet.S3KL : null);
    }

    private static final class Fixture extends AbstractLevel {
        private final int side;
        Fixture(GameId game) {
            super(0);
            side = game == GameId.S1 ? 16 : 8;
            patterns = new Pattern[] {new Pattern()}; patternCount = 1;
            chunks = new Chunk[] {new Chunk()}; chunkCount = 1;
            blocks = new Block[] {new Block(side)}; blockCount = 1;
            solidTiles = new SolidTile[] {new SolidTile(0, new byte[16], new byte[16], (byte) 0)}; solidTileCount = 1;
            palettes = new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
            map = new com.openggf.level.Map(2, 4, 4);
            objects = List.of(); rings = List.of();
            maxX = side * 16 * 4 - 1; maxY = side * 16 * 4 - 1;
        }
        @Override public int getChunksPerBlockSide() { return side; }
        @Override public int getBlockPixelSize() { return side * 16; }
    }
}

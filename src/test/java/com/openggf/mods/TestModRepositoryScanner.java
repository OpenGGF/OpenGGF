package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModRepositoryScanner {
    private static final String MANIFEST = "META-INF/openggf-mod.yaml";

    @TempDir
    Path temp;

    @Test
    void missingRootIsEmptyAndOnlyPackedJarsAreDiscoveredInStableFilenameOrder() throws Exception {
        ModRepositoryScanner scanner = new DefaultModRepositoryScanner(ModInputLimits.production());
        assertEquals(List.of(), scanner.scan(temp.resolve("missing").toAbsolutePath().normalize()));

        Files.writeString(temp.resolve("ignored.txt"), "not a mod");
        Files.createDirectory(temp.resolve("unpacked.jar.directory"));
        writeJar(temp.resolve("z-last.jar"), manifestEntries("z-last"));
        writeJar(temp.resolve("a-first.jar"), manifestEntries("a-first"));

        List<ModCatalogEntry> catalog = scanner.scan(temp.toAbsolutePath().normalize());
        assertEquals(List.of("a-first.jar", "z-last.jar"), catalog.stream()
                .map(entry -> entry.jarPath().getFileName().toString()).toList());
        assertTrue(catalog.stream().allMatch(ModDescriptor.class::isInstance));
        assertThrows(UnsupportedOperationException.class, () -> catalog.add(catalog.getFirst()));
    }

    @Test
    void normalizedRepositoryRootMustAlsoBeAbsolute() {
        assertThrows(IllegalArgumentException.class,
                () -> scanner().scan(Path.of("normalized-relative-root")));
    }

    @Test
    void malformedAndMissingManifestJarsAreRetainedAndScanningContinues() throws Exception {
        Files.write(temp.resolve("a-malformed.jar"), new byte[] {1, 2, 3});
        writeJar(temp.resolve("b-missing.jar"), Map.of("assets/readme.txt", bytes("hello")));
        writeJar(temp.resolve("c-invalid-manifest.jar"), Map.of(MANIFEST, bytes("id: nope")));
        writeJar(temp.resolve("d-valid.jar"), manifestEntries("valid"));

        List<ModCatalogEntry> catalog = scanner().scan(temp.toAbsolutePath().normalize());
        assertEquals(4, catalog.size());
        assertEquals("a-malformed.jar", assertInstanceOf(InvalidModEntry.class, catalog.get(0))
                .jarPath().getFileName().toString(), "invalid filename must remain renderable by the manager");
        assertEquals("MOD_JAR_INVALID", catalog.get(0).findings().getFirst().code());
        assertEquals("MANIFEST_MISSING", catalog.get(1).findings().getFirst().code());
        assertEquals("MANIFEST_INVALID", catalog.get(2).findings().getFirst().code());
        assertInstanceOf(ModDescriptor.class, catalog.get(3));
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.get(0).findings().add(catalog.get(0).findings().getFirst()));
    }

    @Test
    void validatedDirectoryDrivesCodeDetectionAndSnapshotSha256() throws Exception {
        Map<String, byte[]> entries = manifestEntries("code-mod");
        entries.put("fake.class/", new byte[0]);
        entries.put("com/example/Plugin.class", new byte[] {0, 1, 2});
        Path jar = writeJar(temp.resolve("code.jar"), entries);

        ModDescriptor descriptor = assertInstanceOf(ModDescriptor.class,
                scanner().scan(temp.toAbsolutePath().normalize()).getFirst());
        assertTrue(descriptor.containsCode());
        assertEquals(64, descriptor.sha256().length());
        assertTrue(descriptor.sha256().matches("[0-9a-f]{64}"));
        assertFalse(descriptor.hasErrors());
        assertEquals(jar, descriptor.jarPath());
    }

    @Test
    void duplicateIdsMarkEveryDescriptorWithoutFirstWins() throws Exception {
        writeJar(temp.resolve("a.jar"), manifestEntries("same-id"));
        writeJar(temp.resolve("b.jar"), manifestEntries("same-id"));
        writeJar(temp.resolve("c.jar"), manifestEntries("same-id"));

        List<ModCatalogEntry> catalog = scanner().scan(temp.toAbsolutePath().normalize());
        assertEquals(3, catalog.size());
        for (ModCatalogEntry entry : catalog) {
            ModDescriptor descriptor = assertInstanceOf(ModDescriptor.class, entry);
            assertTrue(descriptor.hasErrors());
            assertEquals(List.of("DUPLICATE_MOD_ID"), descriptor.findings().stream()
                    .map(ModFinding::code).toList());
        }
    }

    @Test
    void repositoryLimitsFailStructurallyBeforeAnyJarIsProcessed() throws Exception {
        Files.write(temp.resolve("a.jar"), new byte[] {1, 2, 3});
        Files.write(temp.resolve("b.jar"), new byte[] {4, 5, 6});
        Files.write(temp.resolve("c.jar"), new byte[] {7, 8, 9});

        ModInputLimits countLimits = ModInputLimits.loweringBuilder().maxModJars(2).build();
        List<ModCatalogEntry> countFailure = new DefaultModRepositoryScanner(countLimits)
                .scan(temp.toAbsolutePath().normalize());
        RepositoryScanFailure countEntry = assertInstanceOf(
                RepositoryScanFailure.class, countFailure.getFirst());
        assertFalse(((ModCatalogEntry) countEntry) instanceof InvalidModEntry,
                "repository failures must render as banners, never invalid-mod rows");
        assertEquals(1, countFailure.size());
        assertEquals(temp.toAbsolutePath().normalize(), countEntry.repositoryPath());
        assertEquals("REPOSITORY_JAR_LIMIT_EXCEEDED", countEntry.findings().getFirst().code());

        ModInputLimits byteLimits = ModInputLimits.loweringBuilder()
                .maxRepositoryValidationBytes(8).build();
        List<ModCatalogEntry> byteFailure = new DefaultModRepositoryScanner(byteLimits)
                .scan(temp.toAbsolutePath().normalize());
        RepositoryScanFailure byteEntry = assertInstanceOf(
                RepositoryScanFailure.class, byteFailure.getFirst());
        assertEquals(1, byteFailure.size());
        assertEquals(temp.toAbsolutePath().normalize(), byteEntry.sourcePath());
        assertEquals("REPOSITORY_VALIDATION_BYTES_EXCEEDED", byteEntry.findings().getFirst().code());
    }

    @Test
    void productionJarCountCapRejectsOneThousandTwentyFiveCandidatesWithoutOpeningThem() throws Exception {
        for (int i = 0; i <= ModInputLimits.DEFAULT_MAX_MOD_JARS; i++) {
            Files.createFile(temp.resolve("mod-%04d.jar".formatted(i)));
        }
        List<ModCatalogEntry> catalog = scanner().scan(temp.toAbsolutePath().normalize());
        assertEquals(1, catalog.size());
        assertEquals("REPOSITORY_JAR_LIMIT_EXCEEDED",
                assertInstanceOf(RepositoryScanFailure.class, catalog.getFirst())
                        .findings().getFirst().code());
    }

    @Test
    void loweredArchiveManifestAndInflationLimitsBecomeStructuredPerJarFailures() throws Exception {
        Map<String, byte[]> limitedEntries = manifestEntries("limited");
        limitedEntries.put("assets/data.bin", new byte[] {1});
        Path jar = writeJar(temp.resolve("mod.jar"), limitedEntries);

        assertJarInvalid(ModInputLimits.loweringBuilder().maxJarBytes(Files.size(jar) - 1).build());
        assertJarInvalid(ModInputLimits.loweringBuilder().maxJarEntries(1).build());
        assertJarInvalid(ModInputLimits.loweringBuilder().maxEntryNameBytes(10).build());
        assertJarInvalid(ModInputLimits.loweringBuilder().maxAssetBytes(32).build());
        assertJarInvalid(ModInputLimits.loweringBuilder().maxModValidationBytes(100).build());

        ModInputLimits manifestLimit = ModInputLimits.loweringBuilder().maxMetadataBytes(64).build();
        assertEquals("MANIFEST_INVALID", new DefaultModRepositoryScanner(manifestLimit)
                .scan(temp.toAbsolutePath().normalize()).getFirst().findings().getFirst().code());

        Path dishonest = writeJar(temp.resolve("dishonest.jar"), Map.of(
                MANIFEST, bytes(manifest("dishonest")), "assets/large.bin", new byte[256]));
        patchCentralDirectoryUncompressedSize(dishonest, "assets/large.bin", 1);
        ModInputLimits inflationLimit = ModInputLimits.loweringBuilder()
                .maxAssetBytes(128).maxModValidationBytes(512).build();
        assertTrue(new DefaultModRepositoryScanner(inflationLimit)
                .scan(temp.toAbsolutePath().normalize()).stream()
                .filter(entry -> entry.jarPath().equals(dishonest))
                .findFirst().orElseThrow() instanceof InvalidModEntry);
    }

    @Test
    void inflatedArchiveValidationAndManifestRereadShareOneAggregateBudget() throws Exception {
        Map<String, byte[]> entries = manifestEntries("shared-budget");
        entries.put("assets/data.bin", new byte[16]);
        writeJar(temp.resolve("shared-budget.jar"), entries);
        long inflatedBytes = entries.values().stream().mapToLong(bytes -> bytes.length).sum();
        long budgetAllowingInflationButNotManifestReread = inflatedBytes
                + entries.get(MANIFEST).length - 1;
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxModValidationBytes(budgetAllowingInflationButNotManifestReread).build();
        ModCatalogEntry entry = new DefaultModRepositoryScanner(limits)
                .scan(temp.toAbsolutePath().normalize()).getFirst();
        assertInstanceOf(InvalidModEntry.class, entry);
        assertEquals("MANIFEST_INVALID", entry.findings().getFirst().code());
    }

    @Test
    void centralDirectoryCaseCollisionAndAggregateNameLimitsAreRejected() throws Exception {
        Map<String, byte[]> collision = manifestEntries("collision");
        collision.put("assets/Thing.bin", new byte[] {1});
        collision.put("assets/thing.bin", new byte[] {2});
        writeJar(temp.resolve("collision.jar"), collision);
        assertEquals("MOD_JAR_INVALID", scanner().scan(temp.toAbsolutePath().normalize())
                .getFirst().findings().getFirst().code());

        Files.delete(temp.resolve("collision.jar"));
        writeJar(temp.resolve("names.jar"), manifestEntries("names"));
        ModInputLimits aggregateNames = ModInputLimits.loweringBuilder()
                .maxAggregateEntryNameBytes(MANIFEST.getBytes(StandardCharsets.UTF_8).length - 1).build();
        assertJarInvalid(aggregateNames);
    }

    @Test
    void exactDuplicateCentralDirectoryNamesAreRejected() throws Exception {
        Map<String, byte[]> entries = manifestEntries("duplicate-name");
        entries.put("assets/one.bin", new byte[] {1});
        entries.put("assets/two.bin", new byte[] {2});
        Path jar = writeJar(temp.resolve("duplicate.jar"), entries);
        patchCentralDirectoryName(jar, "assets/two.bin", "assets/one.bin");

        ModCatalogEntry entry = scanner().scan(temp.toAbsolutePath().normalize()).getFirst();
        assertInstanceOf(InvalidModEntry.class, entry);
        assertEquals("MOD_JAR_INVALID", entry.findings().getFirst().code());
    }

    @Test
    void symlinkedJarIsRejectedEvenWhenItsTargetIsInsideOrOutsideTheRoot() throws Exception {
        Path real = writeJar(temp.resolve("real.bin"), manifestEntries("real"));
        Path outside = writeJar(temp.resolveSibling(temp.getFileName() + "-outside.jar"),
                manifestEntries("outside"));
        try {
            createSymlinkOrAbort(temp.resolve("inside.jar"), real);
            createSymlinkOrAbort(temp.resolve("outside.jar"), outside);
            List<ModCatalogEntry> catalog = scanner().scan(temp.toAbsolutePath().normalize());
            assertEquals(2, catalog.size());
            assertTrue(catalog.stream().allMatch(InvalidModEntry.class::isInstance));
            assertTrue(catalog.stream().allMatch(entry ->
                    entry.findings().getFirst().code().equals("MOD_JAR_INVALID")));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void brokenSymlinkJarDoesNotSuppressAValidFollowingJar() throws Exception {
        createSymlinkOrAbort(temp.resolve("a-broken.jar"), temp.resolve("missing-target.jar"));
        writeJar(temp.resolve("b-valid.jar"), manifestEntries("valid-after-broken-link"));

        List<ModCatalogEntry> catalog = scanner().scan(temp.toAbsolutePath().normalize());
        assertEquals(List.of("a-broken.jar", "b-valid.jar"), catalog.stream()
                .map(entry -> entry.jarPath().getFileName().toString()).toList());
        assertEquals("MOD_JAR_INVALID", catalog.getFirst().findings().getFirst().code());
        assertInstanceOf(ModDescriptor.class, catalog.get(1));
    }

    @Test
    void jarGrowthAfterAggregatePreflightCannotEscapeReservedSnapshotSize() throws Exception {
        Path raced = writeJar(temp.resolve("a-raced.jar"), manifestEntries("raced"));
        writeJar(temp.resolve("b-valid.jar"), manifestEntries("valid-after-race"));
        long reservedSize = Files.size(raced);
        ModRepositoryScanner scanner = new DefaultModRepositoryScanner(
                ModInputLimits.production(), () -> {
                    try {
                        Files.write(raced, new byte[8192], StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });

        List<ModCatalogEntry> catalog = scanner.scan(temp.toAbsolutePath().normalize());
        assertTrue(Files.size(raced) > reservedSize);
        assertEquals(List.of("a-raced.jar", "b-valid.jar"), catalog.stream()
                .map(entry -> entry.jarPath().getFileName().toString()).toList());
        assertEquals("MOD_JAR_INVALID", catalog.getFirst().findings().getFirst().code());
        assertInstanceOf(InvalidModEntry.class, catalog.getFirst(),
                "grown source must not be hashed or described");
        assertInstanceOf(ModDescriptor.class, catalog.get(1));
    }

    @Test
    void validJarShrinkAfterPreflightIsRejectedBeforeDescriptionAndScanningContinues() throws Exception {
        Map<String, byte[]> originalEntries = manifestEntries("original-larger");
        originalEntries.put("assets/padding.bin", new byte[4096]);
        Path raced = writeJar(temp.resolve("a-shrunk.jar"), originalEntries);
        writeJar(temp.resolve("b-valid.jar"), manifestEntries("valid-after-shrink"));
        long reservedSize = Files.size(raced);
        ModRepositoryScanner scanner = new DefaultModRepositoryScanner(
                ModInputLimits.production(), () -> {
                    try {
                        writeJar(raced, manifestEntries("valid-smaller-replacement"));
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });

        List<ModCatalogEntry> catalog = scanner.scan(temp.toAbsolutePath().normalize());
        assertTrue(Files.size(raced) < reservedSize);
        assertEquals(List.of("a-shrunk.jar", "b-valid.jar"), catalog.stream()
                .map(entry -> entry.jarPath().getFileName().toString()).toList());
        assertInstanceOf(InvalidModEntry.class, catalog.getFirst(),
                "valid but shrunken replacement must not be hashed or described");
        assertEquals("MOD_JAR_INVALID", catalog.getFirst().findings().getFirst().code());
        ModDescriptor following = assertInstanceOf(ModDescriptor.class, catalog.get(1));
        assertEquals("valid-after-shrink", following.manifest().id());
    }

    @Test
    void valueContractsAndLimitsAreImmutableAndUpwardOverridesRemainRejected() throws Exception {
        ModFinding finding = new ModFinding(ModFindingSeverity.ERROR, "TEST_ERROR", "message", "asset");
        InvalidModEntry invalid = new InvalidModEntry(temp.resolve("bad.jar"), List.of(finding));
        assertThrows(UnsupportedOperationException.class, () -> invalid.findings().clear());
        assertThrows(IllegalArgumentException.class, () -> new ModFinding(
                ModFindingSeverity.ERROR, "EMPTY_MESSAGE", "  ", null));
        assertThrows(IllegalArgumentException.class, () -> new InvalidModEntry(
                temp.resolve("warning-only.jar"), List.of(new ModFinding(
                ModFindingSeverity.WARNING, "WARNING_ONLY", "warning", null))));
        assertThrows(IllegalArgumentException.class, () -> ModInputLimits.loweringBuilder()
                .maxModJars(ModInputLimits.DEFAULT_MAX_MOD_JARS + 1).build());
        assertThrows(IllegalArgumentException.class, () -> ModInputLimits.loweringBuilder()
                .maxRepositoryValidationBytes(ModInputLimits.DEFAULT_MAX_REPOSITORY_VALIDATION_BYTES + 1).build());
    }

    private ModRepositoryScanner scanner() {
        return new DefaultModRepositoryScanner(ModInputLimits.production());
    }

    private void assertJarInvalid(ModInputLimits limits) throws IOException {
        ModCatalogEntry entry = new DefaultModRepositoryScanner(limits)
                .scan(temp.toAbsolutePath().normalize()).getFirst();
        assertInstanceOf(InvalidModEntry.class, entry);
        assertEquals("MOD_JAR_INVALID", entry.findings().getFirst().code());
    }

    private static LinkedHashMap<String, byte[]> manifestEntries(String id) {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(MANIFEST, bytes(manifest(id)));
        return entries;
    }

    private static String manifest(String id) {
        return """
                formatVersion: 1
                id: %s
                name: Test Mod
                version: 1.0.0
                authors: [Test Author]
                description: Test mod.
                engineApiRange: "*"
                type: patch
                baseGame: s1
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """.formatted(id);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Path writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }

    private static void patchCentralDirectoryUncompressedSize(Path jar, String entryName, int size)
            throws IOException {
        byte[] bytes = Files.readAllBytes(jar);
        byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset <= bytes.length - 46 - name.length; offset++) {
            if (readInt(bytes, offset) == 0x02014b50
                    && matches(bytes, offset + 46, name)) {
                writeInt(bytes, offset + 24, size);
                Files.write(jar, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                return;
            }
        }
        throw new IOException("Central-directory entry not found: " + entryName);
    }

    private static void patchCentralDirectoryName(Path jar, String oldName, String newName)
            throws IOException {
        if (oldName.length() != newName.length()) {
            throw new IllegalArgumentException("Patched names must have equal encoded length");
        }
        byte[] bytes = Files.readAllBytes(jar);
        byte[] oldBytes = oldName.getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset <= bytes.length - 46 - oldBytes.length; offset++) {
            if (readInt(bytes, offset) == 0x02014b50 && matches(bytes, offset + 46, oldBytes)) {
                System.arraycopy(newBytes, 0, bytes, offset + 46, newBytes.length);
                Files.write(jar, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                return;
            }
        }
        throw new IOException("Central-directory entry not found: " + oldName);
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) {
            bytes[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    private static boolean matches(byte[] bytes, int offset, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) return false;
        }
        return true;
    }

    private static void createSymlinkOrAbort(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
        }
    }
}

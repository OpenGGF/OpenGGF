package com.openggf.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestModAssetRoot {

    @TempDir
    Path temp;

    @Test
    void directoryReadsOnlyNormalizedContainedEntriesWithinLowerCap() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("project"));
        Files.write(rootDir.resolve("asset.bin"), new byte[]{1, 2, 3, 4});
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAssetBytes(4).build();
        try (ModAssetRoot root = ModAssetRoot.directory(rootDir, rootDir, limits)) {
            assertArrayEquals(new byte[]{1, 2, 3, 4}, root.readBounded("asset.bin", 4));
            assertThrows(IOException.class, () -> root.readBounded("asset.bin", 3));
            assertThrows(IllegalArgumentException.class, () -> root.readBounded("asset.bin", 5));
            assertThrows(IllegalArgumentException.class, () -> root.readBounded("../asset.bin", 4));
        }
    }

    @Test
    void jarValidatesNamesAndReadsWithoutExtraction() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("project"));
        Path jar = writeJar(rootDir.resolve("mod.jar"), Map.of("assets/a.bin", new byte[]{7, 8}));
        try (ModAssetRoot root = ModAssetRoot.jar(rootDir, jar)) {
            assertArrayEquals(new byte[]{7, 8}, root.readBounded("assets/a.bin", 2));
            assertTrue(root.describe().contains("mod.jar"));
        }
        assertFalse(Files.exists(rootDir.resolve("assets/a.bin")));
    }

    @Test
    void jarAcceptsStandardDirectoryEntriesButNeverExposesThem() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("directory-entries"));
        Path jar = rootDir.resolve("mod.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            addJarEntry(output, "META-INF/", new byte[0]);
            addJarEntry(output, "assets/", new byte[0]);
            addJarEntry(output, "assets/a.bin", new byte[]{7});
        }

        try (ModAssetRoot root = ModAssetRoot.jar(rootDir, jar)) {
            assertArrayEquals(new byte[]{7}, root.readBounded("assets/a.bin", 1));
            assertThrows(IOException.class, () -> root.readBounded("assets", 1));
        }
    }

    @Test
    void rejectsCaseFoldCollisionsAndOversizedDeclaredEntries() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("project"));
        Map<String, byte[]> colliding = new LinkedHashMap<>();
        colliding.put("A.bin", new byte[]{1});
        colliding.put("a.bin", new byte[]{2});
        Path collisionJar = writeJar(rootDir.resolve("collision.jar"), colliding);
        assertThrows(IOException.class, () -> ModAssetRoot.jar(rootDir, collisionJar));

        Path largeJar = writeJar(rootDir.resolve("large.jar"), Map.of("a.bin", new byte[5]));
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAssetBytes(4).build();
        assertThrows(IOException.class, () -> ModAssetRoot.jar(rootDir, largeJar, limits));
    }

    @Test
    void rootsRejectTargetsOutsideDeclaredRealRootAndReadsAfterClose() throws Exception {
        Path declared = Files.createDirectory(temp.resolve("declared"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        assertThrows(IOException.class, () -> ModAssetRoot.directory(declared, outside));

        Path jar = writeJar(declared.resolve("mod.jar"), Map.of("a.bin", new byte[]{1}));
        ModAssetRoot root = ModAssetRoot.jar(declared, jar);
        root.close();
        assertThrows(IOException.class, () -> root.readBounded("a.bin", 1));
    }

    @Test
    void rejectsWindowsDriveRelativeEntryAliases() {
        assertThrows(IllegalArgumentException.class,
                () -> ModAssetRoot.requireNormalizedEntry("C:asset.bin"));
    }

    @Test
    void rejectsJarSymlinkThatEscapesDeclaredRoot() throws Exception {
        Path declared = Files.createDirectory(temp.resolve("declared-link"));
        Path outside = Files.createDirectory(temp.resolve("outside-link"));
        Path jar = writeJar(outside.resolve("mod.jar"), Map.of("a.bin", new byte[]{1}));
        Path link = declared.resolve("mod.jar");
        try {
            Files.createSymbolicLink(link, jar);
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
        }
        assertThrows(IOException.class, () -> ModAssetRoot.jar(declared, link));
    }

    @Test
    void rejectsContainedDirectorySymlinkAliasesBeforeInventory() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("directory-alias"));
        Path real = Files.createDirectory(rootDir.resolve("real"));
        Files.write(real.resolve("asset.bin"), new byte[]{1});
        createSymlinkOrAbort(rootDir.resolve("alias"), real);

        assertThrows(IOException.class, () -> ModAssetRoot.directory(rootDir, rootDir));
    }

    @Test
    void rejectsDirectorySymlinkAliasesAddedAfterInventory() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("late-directory-alias"));
        Path real = Files.createDirectory(rootDir.resolve("real"));
        Files.write(real.resolve("asset.bin"), new byte[]{1});

        try (ModAssetRoot root = ModAssetRoot.directory(rootDir, rootDir)) {
            createSymlinkOrAbort(rootDir.resolve("alias"), real);
            assertThrows(IOException.class, () -> root.readBounded("alias/asset.bin", 1));
        }
    }

    @Test
    void countingStreamRejectsDishonestCentralDirectorySize() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("dishonest"));
        Path jar = writeJar(rootDir.resolve("mod.jar"), Map.of("a.bin", new byte[]{1, 2, 3, 4, 5}));
        byte[] bytes = Files.readAllBytes(jar);
        int central = findSignature(bytes, 0x02014b50);
        assertTrue(central >= 0);
        bytes[central + 24] = 1;
        bytes[central + 25] = 0;
        bytes[central + 26] = 0;
        bytes[central + 27] = 0;
        Files.write(jar, bytes, StandardOpenOption.TRUNCATE_EXISTING);

        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAssetBytes(4).build();
        try (ModAssetRoot root = ModAssetRoot.jar(rootDir, jar, limits)) {
            assertThrows(IOException.class, () -> root.readBounded("a.bin", 4));
        }
    }

    @Test
    void cumulativeActualReadBudgetChargesSuccessesAndRollsBackFailures() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("cumulative-budget"));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a.bin", new byte[]{1, 2, 3});
        entries.put("b.bin", new byte[]{4, 5});
        entries.put("c.bin", new byte[]{6});
        Path jar = writeJar(rootDir.resolve("mod.jar"), entries);
        patchAllCentralDirectorySizes(jar, 1);
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(3)
                .maxModValidationBytes(5)
                .build();

        try (ModAssetRoot root = ModAssetRoot.jar(rootDir, jar, limits)) {
            assertArrayEquals(new byte[]{1, 2, 3}, root.readBounded("a.bin", 3));
            assertThrows(IOException.class, () -> root.readBounded("b.bin", 1));
            assertArrayEquals(new byte[]{4, 5}, root.readBounded("b.bin", 2));
            assertThrows(IOException.class, () -> root.readBounded("c.bin", 1));
        }
    }

    @Test
    void cumulativeActualReadBudgetIsAtomicAcrossConcurrentReads() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("concurrent-budget"));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a.bin", new byte[]{1, 2, 3});
        entries.put("b.bin", new byte[]{4, 5, 6});
        Path jar = writeJar(rootDir.resolve("mod.jar"), entries);
        patchAllCentralDirectorySizes(jar, 1);
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(3)
                .maxModValidationBytes(5)
                .build();

        try (ModAssetRoot root = ModAssetRoot.jar(rootDir, jar, limits)) {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> first = executor.submit(() -> readAfterBarrier(root, "a.bin", ready, start));
                Future<Boolean> second = executor.submit(() -> readAfterBarrier(root, "b.bin", ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                assertNotEquals(first.get(), second.get(), "exactly one three-byte read must fit the five-byte budget");
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void jarSnapshotIgnoresSourceReplacementBeforeValidation() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("jar-snapshot"));
        Path jar = writeJar(rootDir.resolve("mod.jar"), Map.of("a.bin", new byte[]{1}));
        SnapshotHook replaceSource = (source, snapshot) ->
                writeJar(source, Map.of("a.bin", new byte[]{9}));

        try (ModAssetRoot root = new JarModAssetRoot(
                rootDir, jar, ModInputLimits.production(), replaceSource)) {
            assertArrayEquals(new byte[]{1}, root.readBounded("a.bin", 1));
        }
    }

    @Test
    void jarSnapshotEnforcesDiskLimitAgainstReplacementBeforeCopy() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("jar-snapshot-limit"));
        Path jar = writeJar(rootDir.resolve("mod.jar"), Map.of("a.bin", new byte[]{1}));
        long originalJarBytes = Files.size(jar);
        byte[] oversized = new byte[8_192];
        new Random(1).nextBytes(oversized);
        SnapshotHook replaceBeforeCopy = new SnapshotHook() {
            @Override
            public void beforeCopy(Path source) throws IOException {
                writeJar(source, Map.of("large.bin", oversized));
            }

            @Override
            public void afterCopy(Path source, Path snapshot) {
            }
        };
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxJarBytes(originalJarBytes).build();

        assertThrows(IOException.class,
                () -> new JarModAssetRoot(rootDir, jar, limits, replaceBeforeCopy));
    }

    @Test
    void directorySnapshotIgnoresSourceMutationAfterConstruction() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("directory-snapshot"));
        Path asset = rootDir.resolve("a.bin");
        Files.write(asset, new byte[]{1});

        try (ModAssetRoot root = ModAssetRoot.directory(rootDir, rootDir)) {
            Files.write(asset, new byte[]{9}, StandardOpenOption.TRUNCATE_EXISTING);
            assertArrayEquals(new byte[]{1}, root.readBounded("a.bin", 1));
        }
    }

    @Test
    void directorySnapshotHookMakesSourceReplacementBeforeValidationInvisible() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("directory-hook-snapshot"));
        Path asset = Files.write(rootDir.resolve("a.bin"), new byte[]{1});
        SnapshotHook replaceSource = (source, snapshot) ->
                Files.write(asset, new byte[]{9}, StandardOpenOption.TRUNCATE_EXISTING);

        try (ModAssetRoot root = new DirectoryModAssetRoot(
                rootDir, rootDir, ModInputLimits.production(), replaceSource)) {
            assertArrayEquals(new byte[]{1}, root.readBounded("a.bin", 1));
        }
    }

    @Test
    void closeDeletesOnlyTheOwnedSnapshot() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("snapshot-cleanup"));
        Path jar = writeJar(rootDir.resolve("mod.jar"), Map.of("a.bin", new byte[]{1}));
        Path unrelated = Files.write(temp.resolve("unrelated.bin"), new byte[]{8});
        AtomicReference<Path> snapshotContent = new AtomicReference<>();
        SnapshotHook capture = (source, snapshot) -> snapshotContent.set(snapshot);
        ModAssetRoot root = new JarModAssetRoot(rootDir, jar, ModInputLimits.production(), capture);
        Path snapshotRoot = snapshotContent.get().getParent();
        assertTrue(Files.exists(snapshotRoot));

        root.close();

        assertFalse(Files.exists(snapshotRoot));
        assertArrayEquals(new byte[]{8}, Files.readAllBytes(unrelated));
        assertTrue(Files.exists(jar));
    }

    @Test
    void snapshotCleanupCanRetryAfterTransientOwnershipFailure() throws Exception {
        Path rootDir = Files.createDirectory(temp.resolve("snapshot-cleanup-retry"));
        Path jar = writeJar(rootDir.resolve("mod.jar"), Map.of("a.bin", new byte[]{1}));
        AtomicReference<Path> snapshotContent = new AtomicReference<>();
        ModAssetRoot root = new JarModAssetRoot(rootDir, jar, ModInputLimits.production(),
                (source, snapshot) -> snapshotContent.set(snapshot));
        Path snapshotRoot = snapshotContent.get().getParent();
        Path marker = snapshotRoot.resolve(".openggf-snapshot-owner");
        String ownerToken = Files.readString(marker);
        Files.writeString(marker, "temporarily-wrong", StandardOpenOption.TRUNCATE_EXISTING);
        assertThrows(IOException.class, root::close);
        Files.writeString(marker, ownerToken, StandardOpenOption.TRUNCATE_EXISTING);

        root.close();

        assertFalse(Files.exists(snapshotRoot));
    }

    @Test
    void loweringBuilderCannotRaiseOrUseNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ModInputLimits.loweringBuilder().maxAssetBytes(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> ModInputLimits.loweringBuilder()
                        .maxAssetBytes(ModInputLimits.DEFAULT_MAX_ASSET_BYTES + 1).build());
        assertEquals(64L * 1024 * 1024, ModInputLimits.production().maxAssetBytes());
        assertEquals(16_384, ModInputLimits.production().maxJarEntries());
    }

    private static Path writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                addJarEntry(output, entry.getKey(), entry.getValue());
            }
        }
        return path;
    }

    private static void addJarEntry(JarOutputStream output, String name, byte[] bytes) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static void patchAllCentralDirectorySizes(Path jar, int declaredSize) throws IOException {
        byte[] bytes = Files.readAllBytes(jar);
        for (int offset = 0; offset <= bytes.length - 28; offset++) {
            if (matchesSignature(bytes, offset, 0x02014b50)) {
                bytes[offset + 24] = (byte) declaredSize;
                bytes[offset + 25] = (byte) (declaredSize >>> 8);
                bytes[offset + 26] = (byte) (declaredSize >>> 16);
                bytes[offset + 27] = (byte) (declaredSize >>> 24);
            }
        }
        Files.write(jar, bytes, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static boolean readAfterBarrier(ModAssetRoot root, String entry,
                                            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            root.readBounded(entry, 3);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void createSymlinkOrAbort(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
        }
    }

    private static int findSignature(byte[] bytes, int signature) {
        for (int i = 0; i <= bytes.length - 4; i++) {
            if (matchesSignature(bytes, i, signature)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesSignature(byte[] bytes, int offset, int signature) {
        return (bytes[offset] & 0xff) == (signature & 0xff)
                && (bytes[offset + 1] & 0xff) == ((signature >>> 8) & 0xff)
                && (bytes[offset + 2] & 0xff) == ((signature >>> 16) & 0xff)
                && (bytes[offset + 3] & 0xff) == ((signature >>> 24) & 0xff);
    }
}

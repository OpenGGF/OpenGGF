package com.openggf.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
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
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
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
            if ((bytes[i] & 0xff) == (signature & 0xff)
                    && (bytes[i + 1] & 0xff) == ((signature >>> 8) & 0xff)
                    && (bytes[i + 2] & 0xff) == ((signature >>> 16) & 0xff)
                    && (bytes[i + 3] & 0xff) == ((signature >>> 24) & 0xff)) {
                return i;
            }
        }
        return -1;
    }
}

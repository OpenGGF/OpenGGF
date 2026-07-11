package com.openggf.level.resources;

import com.openggf.data.Rom;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResourceLoaderModSources {

    @Test
    void modAssetOpsComposeWithOverlaysWithoutRom(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of(
                "base.bin", new byte[]{1, 1, 1, 1},
                "overlay.bin", new byte[]{9, 9}));
        ResourceLoader loader = new ResourceLoader(null);
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar)) {
            byte[] composed = loader.loadWithOverlays(List.of(
                    LoadOp.modAssetBase(root, "base.bin"),
                    LoadOp.modAssetOverlay(root, "overlay.bin", 1)), 4);
            assertArrayEquals(new byte[]{1, 9, 9, 1}, composed);
        }
    }

    @Test
    void modAssetAppendGrowsBuffer(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of(
                "a.bin", new byte[]{1, 2},
                "b.bin", new byte[]{3, 4}));
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar)) {
            byte[] composed = new ResourceLoader(null).loadWithOverlays(List.of(
                    LoadOp.modAssetBase(root, "a.bin"),
                    LoadOp.modAssetAppend(root, "b.bin")), 2);
            assertArrayEquals(new byte[]{1, 2, 3, 4}, composed);
        }
    }

    @Test
    void missingClosedAndDirectoryEntriesFailClearly(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of("assets/a.bin", new byte[]{1}));
        ResourceLoader loader = new ResourceLoader(null);
        ModAssetRoot root = ModAssetRoot.jar(tmp, jar);
        try {
            assertThrows(IOException.class,
                    () -> loader.loadSingle(LoadOp.modAssetBase(root, "nope.bin")));
            assertThrows(IOException.class,
                    () -> loader.loadSingle(LoadOp.modAssetBase(root, "assets")));
        } finally {
            root.close();
        }
        assertThrows(IOException.class,
                () -> loader.loadSingle(LoadOp.modAssetBase(root, "assets/a.bin")));
    }

    @Test
    void directoryRootAssetsLoadWithoutRom(@TempDir Path tmp) throws Exception {
        Path directory = Files.createDirectory(tmp.resolve("mod"));
        Files.write(directory.resolve("asset.bin"), new byte[]{4, 5, 6});
        try (ModAssetRoot root = ModAssetRoot.directory(
                tmp, directory, ModInputLimits.production(), DirectoryAccess.TEST)) {
            assertArrayEquals(new byte[]{4, 5, 6},
                    new ResourceLoader(null).loadSingle(LoadOp.modAssetBase(root, "asset.bin")));
        }
    }

    @Test
    void declaredAndStreamingAssetOverflowsAreRejected(@TempDir Path tmp) throws Exception {
        Path declaredDir = Files.createDirectory(tmp.resolve("declared"));
        Path declaredJar = writeJar(declaredDir.resolve("m.jar"),
                Map.of("large.bin", new byte[]{1, 2, 3, 4, 5}));
        ModInputLimits fourBytes = ModInputLimits.loweringBuilder().maxAssetBytes(4).build();
        assertThrows(IOException.class, () -> ModAssetRoot.jar(declaredDir, declaredJar, fourBytes));

        Path streamingDir = Files.createDirectory(tmp.resolve("streaming"));
        Path streamingJar = writeJar(streamingDir.resolve("m.jar"),
                Map.of("dishonest.bin", new byte[]{1, 2, 3, 4, 5}));
        patchAllCentralDirectorySizes(streamingJar, 1);
        assertThrows(IOException.class,
                () -> ModAssetRoot.jar(streamingDir, streamingJar, fourBytes));
    }

    @Test
    void aggregatePlanReadsShareRootValidationBudget(@TempDir Path tmp) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a.bin", new byte[]{1, 2});
        entries.put("b.bin", new byte[]{3, 4});
        Path jar = writeJar(tmp.resolve("m.jar"), entries);
        patchAllCentralDirectorySizes(jar, 1);
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(2)
                .maxModValidationBytes(7)
                .build();
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar, limits)) {
            assertThrows(IOException.class, () -> new ResourceLoader(null).loadWithOverlays(List.of(
                    LoadOp.modAssetBase(root, "a.bin"),
                    LoadOp.modAssetAppend(root, "b.bin")), 0));
        }
    }

    @Test
    void modPlanRejectsNegativeOrOverCapInitialBufferBeforeAllocation(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of("a.bin", new byte[]{1}));
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(1)
                .maxModValidationBytes(4)
                .build();
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar, limits)) {
            ResourceLoader loader = new ResourceLoader(null);
            assertThrows(IllegalArgumentException.class, () -> loader.loadWithOverlays(
                    List.of(LoadOp.modAssetBase(root, "a.bin")), -1));
            assertThrows(IllegalArgumentException.class, () -> loader.loadWithOverlays(
                    List.of(LoadOp.modAssetBase(root, "a.bin")), 5));
        }
    }

    @Test
    void modPlanRejectsNearIntegerMaxOverlayAndCapSizedHole(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of("a.bin", new byte[]{1}));
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(1)
                .maxModValidationBytes(4)
                .build();
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar, limits)) {
            ResourceLoader loader = new ResourceLoader(null);
            assertCheckedSizingFailure(() -> loader.loadWithOverlays(List.of(
                    LoadOp.modAssetOverlay(root, "a.bin", Integer.MAX_VALUE)), 0));
            assertCheckedSizingFailure(() -> loader.loadWithOverlays(List.of(
                    LoadOp.modAssetOverlay(root, "a.bin", 4)), 0));
        }
    }

    @Test
    void mixedModRootsUseSmallestComposedOutputCap(@TempDir Path tmp) throws Exception {
        Path firstDir = Files.createDirectory(tmp.resolve("first"));
        Path secondDir = Files.createDirectory(tmp.resolve("second"));
        Path firstJar = writeJar(firstDir.resolve("m.jar"), Map.of("a.bin", new byte[5]));
        Path secondJar = writeJar(secondDir.resolve("m.jar"), Map.of("b.bin", new byte[]{1}));
        ModInputLimits firstLimits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(5).maxModValidationBytes(8).build();
        ModInputLimits secondLimits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(1).maxModValidationBytes(4).build();
        try (ModAssetRoot first = ModAssetRoot.jar(firstDir, firstJar, firstLimits);
             ModAssetRoot second = ModAssetRoot.jar(secondDir, secondJar, secondLimits)) {
            assertThrows(IOException.class, () -> new ResourceLoader(null).loadWithOverlays(List.of(
                    LoadOp.modAssetBase(first, "a.bin"),
                    LoadOp.modAssetAppend(second, "b.bin")), 0));
        }
    }

    @Test
    void alignedModOutputCannotGrowPastComposedOutputCap(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of("a.bin", new byte[]{1, 2, 3}));
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(3)
                .maxModValidationBytes(3)
                .build();
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar, limits)) {
            assertThrows(IOException.class, () -> new ResourceLoader(null).loadWithOverlaysAligned(
                    List.of(LoadOp.modAssetBase(root, "a.bin")), 0, 4));
        }
    }

    @Test
    void mixedPlanReadsAllModAssetsBeforeAttemptingRom(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of(
                "a.bin", new byte[]{1, 2},
                "b.bin", new byte[]{3, 4}));
        patchAllCentralDirectorySizes(jar, 1);
        ModInputLimits limits = ModInputLimits.loweringBuilder()
                .maxAssetBytes(2)
                .maxModValidationBytes(7)
                .build();
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar, limits)) {
            IOException failure = assertThrows(IOException.class,
                    () -> new ResourceLoader(null).loadWithOverlays(List.of(
                            LoadOp.modAssetBase(root, "a.bin"),
                            LoadOp.kosinskiAppend(0),
                            LoadOp.modAssetAppend(root, "b.bin")), 0));
            assertTrue(failure.getMessage().contains("validation budget"));
        }
    }

    @Test
    void pureRomPlanDoesNotDecompressLaterOpAfterEarlierCompositionFailure(@TempDir Path tmp)
            throws Exception {
        byte[] compressed = {
                (byte) 0xFF, (byte) 0x5F, 0, 1, 2, 3, 4, 5, 6, 7,
                8, 9, 10, 11, 12, 0, (byte) 0xF0, 0
        };
        byte[] romBytes = new byte[64 + compressed.length];
        System.arraycopy(compressed, 0, romBytes, 0, compressed.length);
        System.arraycopy(compressed, 0, romBytes, 64, compressed.length);
        Path romPath = Files.write(tmp.resolve("fixture.bin"), romBytes);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            assertCheckedSizingFailure(() -> new ResourceLoader(rom).loadWithOverlays(List.of(
                    LoadOp.kosinskiOverlay(0, Integer.MAX_VALUE),
                    LoadOp.kosinskiAppend(64)), 0));
            long position = rom.getFileChannel().position();
            assertTrue(position > 0 && position < 64,
                    "first ROM op should decode, but the later op must remain unread; position=" + position);
        }
    }

    @Test
    void fineLoggingDescribesModSourceWithoutRomAccessor(@TempDir Path tmp) throws Exception {
        Path jar = writeJar(tmp.resolve("m.jar"), Map.of("a.bin", new byte[]{7}));
        Logger logger = Logger.getLogger(ResourceLoader.class.getName());
        Level previous = logger.getLevel();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.setLevel(Level.FINE);
        handler.setLevel(Level.FINE);
        logger.addHandler(handler);
        try (ModAssetRoot root = ModAssetRoot.jar(tmp, jar)) {
            new ResourceLoader(null).loadWithOverlays(
                    List.of(LoadOp.modAssetBase(root, "a.bin")), 1);
            assertTrue(messages.stream().anyMatch(message ->
                    message.contains(root.describe() + "!a.bin")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
    }

    @Test
    void legacyRomKosinskiFixtureIsByteIdentical(@TempDir Path tmp) throws Exception {
        byte[] compressed = {
                (byte) 0xFF, (byte) 0x5F, 0, 1, 2, 3, 4, 5, 6, 7,
                8, 9, 10, 11, 12, 0, (byte) 0xF0, 0
        };
        Path romPath = Files.write(tmp.resolve("fixture.bin"), compressed);
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12},
                    new ResourceLoader(rom).loadSingle(LoadOp.kosinskiBase(0)));
        }
    }

    private static Path writeJar(Path jar, Map<String, byte[]> entries) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    private static void patchAllCentralDirectorySizes(Path jar, int declaredSize) throws IOException {
        byte[] bytes = Files.readAllBytes(jar);
        for (int offset = 0; offset <= bytes.length - 28; offset++) {
            if ((bytes[offset] & 0xff) == 0x50
                    && (bytes[offset + 1] & 0xff) == 0x4b
                    && (bytes[offset + 2] & 0xff) == 0x01
                    && (bytes[offset + 3] & 0xff) == 0x02) {
                bytes[offset + 24] = (byte) declaredSize;
                bytes[offset + 25] = (byte) (declaredSize >>> 8);
                bytes[offset + 26] = (byte) (declaredSize >>> 16);
                bytes[offset + 27] = (byte) (declaredSize >>> 24);
            }
        }
        Files.write(jar, bytes, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void assertCheckedSizingFailure(ThrowingIoOperation operation) {
        Exception failure = assertThrows(Exception.class, operation::run);
        assertTrue(failure instanceof IOException || failure instanceof IllegalArgumentException,
                "expected a checked sizing failure, got " + failure.getClass().getName());
    }

    @FunctionalInterface
    private interface ThrowingIoOperation {
        void run() throws Exception;
    }
}

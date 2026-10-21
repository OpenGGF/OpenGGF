package com.openggf.game.recording;

import com.openggf.debug.playback.Bk2Archive;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestUserRecordingArchiveBounds {
    private static final BuildIdentity IDENTITY = new BuildIdentity("0.7.prerelease", "test", false);
    private static final String MANIFEST = "OpenGGF/manifest.json";
    private static final String INPUT = "Input Log.txt";
    private static final String SIDECAR = "OpenGGF/desync-lite.jsonl";
    private static final String INPUT_PREFIX = "[Input]\nLogKey:" + UserRecordingWriter.LOG_KEY + "\n";
    private static final String ROW = "|........|........|\n";
    private static final RecordingLaunchContext CONTEXT = new RecordingLaunchContext(
            "s3k", 0, 1, "sonic", List.of(), false, "current-act-fresh-start");

    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {MANIFEST, "Header.txt", INPUT})
    void rejectsOversizedTextBeforeItCanEnterCatalog(String entry) throws Exception {
        String content = switch (entry) {
            case MANIFEST -> manifestJson(1).replace("\"movie\"", "\"" + "x".repeat(65_536) + "\"");
            case "Header.txt" -> "Author: " + "x".repeat(65_536);
            default -> INPUT_PREFIX + ";" + "x".repeat(1_024) + "\n" + ROW + "[/Input]\n";
        };
        Path bad = archive("bad.bk2", 1, entry, content, null);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @Test
    void rejectsOversizedSidecarLineBeforeJsonMaterialization() throws Exception {
        Path bad = archive("bad.bk2", 1, SIDECAR, " ".repeat(1_025) + "{}\n", null);
        assertLaunchRejected(bad);
    }

    @Test
    void rejectsExcessOtherwiseValidInputRows() throws Exception {
        Path bad = archive("bad.bk2", 216_000, INPUT,
                INPUT_PREFIX + ROW.repeat(216_001) + "[/Input]\n", null);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
        assertEquals(216_001, new Bk2MovieLoader().load(bad).getFrameCount(),
                "research loader must remain independent of the user-import limit");
    }

    @Test
    void rejectsExcessOtherwiseValidSidecarRows() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 216_001; i++) rows.append("{\"frame\":").append(i).append("}\n");
        Path bad = archive("bad.bk2", 216_000, SIDECAR, rows.toString(), null);
        assertLaunchRejected(bad);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 216_001})
    void rejectsInvalidManifestCounts(int count) throws Exception {
        Path bad = archive("bad.bk2", 1, MANIFEST, manifestJson(count), null);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void rejectsInputCountThatDisagreesWithManifest(int count) throws Exception {
        Path bad = archive("bad.bk2", 2, INPUT, INPUT_PREFIX + ROW.repeat(count) + "[/Input]\n", null);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}\n{}\n", "{\"frame\":-1}\n", "{\"frame\":1}\n"})
    void rejectsInconsistentPresentEveryFrameSidecars(String content) throws Exception {
        assertLaunchRejected(archive("bad.bk2", 1, SIDECAR, content, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {MANIFEST, "Header.txt", INPUT, SIDECAR})
    void rejectsCaseCollisionsInRecognizedEntries(String duplicate) throws Exception {
        Path bad = archive("bad.bk2", 1, null, null, duplicate.toUpperCase(java.util.Locale.ROOT));
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @ParameterizedTest
    @ValueSource(strings = {MANIFEST, "Header.txt", INPUT, SIDECAR})
    void rejectsExactDuplicateEntries(String duplicate) throws Exception {
        String upper = duplicate.toUpperCase(java.util.Locale.ROOT);
        Path bad = archive("bad.bk2", 1, null, null, upper);
        byte[] bytes = Files.readAllBytes(bad);
        byte[] from = upper.getBytes(StandardCharsets.UTF_8);
        byte[] to = duplicate.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + from.length <= bytes.length; i++) {
            if (java.util.Arrays.equals(from, java.util.Arrays.copyOfRange(bytes, i, i + from.length))) {
                System.arraycopy(to, 0, bytes, i, to.length);
            }
        }
        Files.write(bad, bytes);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @Test
    void rejectsInflatedInputEvenWhenDeclaredSizeIsSmall() throws Exception {
        String comments = (";" + "x".repeat(1_022) + "\n").repeat(8_193);
        Path bad = archive("bad.bk2", 1, INPUT, INPUT_PREFIX + comments + ROW + "[/Input]\n", null);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
        declareSize(bad, INPUT, 1);
        assertTrue(Files.size(bad) < 65_536, "corpus must remain a small compressed archive");
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @Test
    void rejectsMisleadingManifestSizeEvenBelowTheImportLimit() throws Exception {
        Path bad = archive("bad.bk2", 1, null, null, null);
        declareSize(bad, MANIFEST, 1);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @Test
    void rejectsTruncatedDeclaredCompressedSize() throws Exception {
        Path bad = archive("bad.bk2", 1, null, null, null);
        patchCentralSize(bad, MANIFEST, 20, 1);
        assertCatalogRejectedBesideHealthy(bad);
        assertLaunchRejected(bad);
    }

    @Test
    void lineBoundStopsInflationBeforeMaterializingAnOversizedString() throws Exception {
        CountingInput input = new CountingInput('x');
        try (var reader = new Bk2Archive.LineReader(input, 8_388_608, 1_024)) {
            assertThrows(IOException.class, reader::readLine);
            assertTrue(input.count <= 1_025, "must stop at the first oversized line, not drain the entry");
        }
    }

    @Test
    void totalBoundStopsEvenAnEndlessSequenceOfEmptyLines() throws Exception {
        CountingInput input = new CountingInput('\n');
        try (var reader = new Bk2Archive.LineReader(input, 64, 1_024)) {
            assertThrows(IOException.class, () -> { while (reader.readLine() != null) { } });
            assertTrue(input.count <= 65, "must stop at the inflated byte budget plus one probe byte");
        }
    }

    @Test
    void lineBudgetCountsUtf8BytesAndPreservesCrLfBoundaries() throws Exception {
        try (var reader = new Bk2Archive.LineReader(new java.io.ByteArrayInputStream(
                "éé\r\nx\ry\n".getBytes(StandardCharsets.UTF_8)), 32, 4)) {
            assertEquals("éé", reader.readLine());
            assertEquals("x", reader.readLine());
            assertEquals("y", reader.readLine());
            assertNull(reader.readLine());
        }
        try (var reader = new Bk2Archive.LineReader(new java.io.ByteArrayInputStream(
                "ééé".getBytes(StandardCharsets.UTF_8)), 32, 4)) {
            assertThrows(IOException.class, reader::readLine);
        }
    }

    @Test
    void rejectsInflatedSidecarWithSmallDeclaredSize() throws Exception {
        Path bad = archive("bad.bk2", 1, null, null, null);
        // Stream repetitive blank lines into the archive without retaining 128 MiB in the test.
        try (var fs = java.nio.file.FileSystems.newFileSystem(bad)) {
            try (var out = Files.newOutputStream(fs.getPath("/" + SIDECAR))) {
                byte[] chunk = (" ".repeat(1_023) + "\n").repeat(1_024).getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < 128; i++) out.write(chunk);
                out.write("{}\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        assertLaunchRejected(bad);
        declareSize(bad, SIDECAR, 1);
        assertTrue(Files.size(bad) < 1_048_576);
        assertLaunchRejected(bad);
    }

    private void assertCatalogRejectedBesideHealthy(Path bad) throws Exception {
        Path healthy = archive("healthy.bk2", 1, null, null, null);
        List<UserRecordingEntry> entries = UserRecordingCatalog.scan(root, "s3k", IDENTITY);
        assertEquals(2, entries.size());
        assertFalse(entries.stream().filter(e -> e.path().equals(bad)).findFirst().orElseThrow().isLoadable());
        UserRecordingEntry neighbor = entries.stream().filter(e -> e.path().equals(healthy)).findFirst().orElseThrow();
        assertTrue(neighbor.isLoadable());
        assertEquals(UserRecordingPlaybackState.PLAYING, new PlaybackFixture().launcher().beginPlayback(
                neighbor, new UserRecordingPlaybackOptions(0, false, false)));
    }

    private void assertLaunchRejected(Path path) {
        PlaybackFixture fixture = new PlaybackFixture();
        IOException error = assertThrows(IOException.class, () -> fixture.launcher().beginPlayback(
                entry(path), new UserRecordingPlaybackOptions(0, false, false)));
        assertTrue(error.getMessage().length() < 256, "rejection must not expose attacker payloads");
        assertEquals(0, fixture.restarts);
        assertNull(fixture.started);
    }

    private Path archive(String name, int count, String replaced, String content, String duplicate) throws Exception {
        Path path = root.resolve("recordings/s3k").resolve(name);
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            write(zip, "Header.txt", "Header.txt".equals(replaced) ? content : "Author: OpenGGF\n");
            write(zip, INPUT, INPUT.equals(replaced) ? content : INPUT_PREFIX + ROW.repeat(count) + "[/Input]\n");
            write(zip, MANIFEST, MANIFEST.equals(replaced) ? content : manifestJson(count));
            write(zip, SIDECAR, SIDECAR.equals(replaced) ? content : "{}\n");
            if (duplicate != null) write(zip, duplicate, "{}");
        }
        return path;
    }

    private static String manifestJson(int count) throws IOException {
        return UserRecordingJson.writeManifest(new UserRecordingManifest(1, "movie", IDENTITY, CONTEXT,
                UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(0, null), "A",
                count, UserRecordingStopReason.USER_STOPPED, Instant.EPOCH));
    }

    private static void write(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void declareSize(Path path, String name, int size) throws IOException {
        patchCentralSize(path, name, 24, size);
    }

    private static void patchCentralSize(Path path, String name, int fieldOffset, int size) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] wanted = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + 46 + wanted.length <= bytes.length; i++) {
            if (bytes[i] == 0x50 && bytes[i + 1] == 0x4b && bytes[i + 2] == 1 && bytes[i + 3] == 2
                    && java.util.Arrays.equals(wanted, java.util.Arrays.copyOfRange(bytes, i + 46, i + 46 + wanted.length))) {
                for (int j = 0; j < 4; j++) bytes[i + fieldOffset + j] = (byte) (size >>> (8 * j));
                Files.write(path, bytes);
                return;
            }
        }
        fail("missing central directory entry");
    }

    private static final class CountingInput extends java.io.InputStream {
        private final int value;
        int count;
        CountingInput(int value) { this.value = value; }
        @Override public int read() { count++; return value; }
        @Override public int read(byte[] bytes, int offset, int length) {
            java.util.Arrays.fill(bytes, offset, offset + length, (byte) value);
            count += length;
            return length;
        }
    }

    private static UserRecordingEntry entry(Path path) {
        return new UserRecordingEntry(path, "movie", null, 1, Instant.EPOCH, RecordingVersionWarning.NONE, "");
    }

    private final class PlaybackFixture implements UserRecordingSessionLauncher.GameLoopAdapter,
            UserRecordingSessionLauncher.PlaybackAdapter {
        int restarts;
        Bk2Movie started;
        UserRecordingSessionLauncher launcher() {
            return new UserRecordingSessionLauncher(this, () -> CONTEXT, () -> false, this, root,
                    () -> Instant.EPOCH, ZoneOffset.UTC);
        }
        @Override public GameMode currentGameMode() { return GameMode.LEVEL; }
        @Override public void restartFromRecordingLaunchContext(RecordingLaunchContext context) { restarts++; }
        @Override public void startSession(Bk2Movie movie, int offset) { started = movie; }
        @Override public void setFrameObserver(PlaybackDebugManager.PlaybackFrameObserver observer) { }
        @Override public void endSession() { }
    }
}

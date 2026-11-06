package com.openggf.game.playback;

import com.openggf.game.recording.DesyncLiteFrame;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.game.recording.RecordingDeterminismMetadata;
import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.recording.UserRecordingManifest;
import com.openggf.game.recording.UserRecordingSidecarMetadata;
import com.openggf.game.recording.UserRecordingStopReason;
import com.openggf.game.recording.UserRecordingWriter;
import com.openggf.tests.trace.TraceV5RunFixture;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestPlaybackLibrary {
    private static final BuildIdentity IDENTITY = new BuildIdentity("0.7.prerelease", "abc123", false);
    @TempDir Path root;

    @Test
    void oneLibraryCombinesRecordingsBareMoviesAndDistinctTracesOverOneMovie() throws Exception {
        Path recording = recording("s3k", "my-run.bk2");
        Path bare = bareMovie(root.resolve("recordings/s2/external.bk2"), null);
        Path traces = root.resolve("traces");
        Path shared = bareMovie(traces.resolve("s3k/_movies/shared.bk2"), null);
        trace(traces.resolve("s3k/aiz-first"), "shared.bk2", 1);
        trace(traces.resolve("s3k/aiz-second"), "shared.bk2", 2);

        PlaybackLibrary library = PlaybackLibrary.scan(root, traces,
                bare.getParent().resolve(".").resolve(bare.getFileName()), IDENTITY);

        assertTrue(library.issues().isEmpty(), library.issues().toString());
        assertEquals(4, library.entries().size());
        assertEquals(1, library.entries().stream().filter(entry -> entry.path().equals(bare)).count());
        var saved = library.entries().stream().filter(PlaybackEntry.Recording.class::isInstance)
                .map(PlaybackEntry.Recording.class::cast).findFirst().orElseThrow();
        assertEquals(recording, saved.path());
        assertTrue(saved.availability().ready());
        assertInstanceOf(PlaybackEntry.Extent.MovieRows.class, saved.extent());
        var movie = library.entries().stream().filter(PlaybackEntry.Movie.class::isInstance)
                .map(PlaybackEntry.Movie.class::cast).findFirst().orElseThrow();
        assertEquals(PlaybackEntry.Availability.Status.NEEDS_SETUP, movie.availability().status());
        assertEquals("", movie.gameId(), "A recording folder must not invent a bare movie's bootstrap");
        assertEquals(2, movie.rowCount());
        var traceEntries = library.entries().stream().filter(PlaybackEntry.Trace.class::isInstance)
                .map(PlaybackEntry.Trace.class::cast).toList();
        assertEquals(2, traceEntries.size());
        assertTrue(traceEntries.stream().allMatch(entry -> entry.trace().bk2Path().equals(shared)));
        assertNotEquals(traceEntries.get(0).path(), traceEntries.get(1).path());
        assertTrue(traceEntries.stream().allMatch(entry -> entry.extent() instanceof PlaybackEntry.Extent.TraceFrames));
    }

    @Test
    void malformedManifestNeverFallsBackToAnUnconfiguredMovie() throws Exception {
        Path invalid = bareMovie(root.resolve("recordings/s3k/invalid.bk2"), "{broken");
        Path valid = recording("s3k", "valid.bk2");

        PlaybackLibrary library = PlaybackLibrary.scan(root, root.resolve("absent-traces"), invalid, IDENTITY);

        var entry = library.entries().stream().filter(item -> item.path().equals(invalid)).findFirst().orElseThrow();
        assertInstanceOf(PlaybackEntry.Recording.class, entry);
        assertEquals(PlaybackEntry.Availability.Status.INVALID, entry.availability().status());
        assertTrue(entry.availability().reason().contains("Invalid recording manifest"));
        assertTrue(library.entries().stream().anyMatch(item -> item.path().equals(valid) && item.availability().ready()));
    }

    @Test
    void aBrokenSourceCannotHideHealthyRecordings() throws Exception {
        Path valid = recording("s1", "valid.bk2");
        Path traces = Files.writeString(root.resolve("not-a-trace-directory"), "file");
        Path brokenMovie = Files.writeString(root.resolve("broken.bk2"), "not a zip");

        PlaybackLibrary library = PlaybackLibrary.scan(root, traces, brokenMovie, IDENTITY);

        assertFalse(library.issues().isEmpty());
        assertTrue(library.entries().stream().anyMatch(item -> item.path().equals(valid) && item.availability().ready()));
        PlaybackEntry invalid = library.entries().stream().filter(item -> item.path().equals(brokenMovie))
                .findFirst().orElseThrow();
        assertEquals(PlaybackEntry.Availability.Status.INVALID, invalid.availability().status());
    }

    @Test
    void failedChildRunDirectoryIsReportedWithoutHidingHealthyRecordings() throws Exception {
        Path valid = recording("s1", "valid.bk2");
        Path traces = Files.createDirectories(root.resolve("traces"));
        Path runs = Files.createDirectories(traces.resolve("s3k/runs"));
        PlaybackLibrary library;
        try (var ignored = org.mockito.Mockito.mockStatic(Files.class, invocation -> {
            if ("list".equals(invocation.getMethod().getName()) && runs.equals(invocation.getArgument(0))) {
                throw new java.io.IOException("Run directory read denied");
            }
            return invocation.callRealMethod();
        })) {
            library = PlaybackLibrary.scan(root, traces, null, IDENTITY);
        }
        assertTrue(library.issues().stream().anyMatch(issue -> issue.contains("Run directory read denied")));
        assertTrue(library.entries().stream().anyMatch(entry -> entry.path().equals(valid) && entry.availability().ready()));
    }

    @Test
    void browserImportsKeepTheirLineBudget() throws Exception {
        Path oversized = root.resolve("oversized.bk2");
        writeMovie(oversized, "[Input]\nLogKey:|P1 Up|P1 Down|P1 Left|P1 Right|P1 Start|P1 A|P1 B|P1 C|\n|"
                + ".".repeat(1_025) + "|\n[/Input]\n", null);

        PlaybackEntry entry = PlaybackLibrary.scan(root, null, oversized, IDENTITY).entries().getFirst();

        assertEquals(PlaybackEntry.Availability.Status.INVALID, entry.availability().status());
        assertTrue(entry.availability().reason().toLowerCase().contains("line"));
    }

    @Test
    void runExtentDoesNotPretendToBeABk2RowCount() throws Exception {
        Path traces = root.resolve("traces");
        TraceV5RunFixture.writeS3kBonusRun(traces.resolve("s3k/runs"));
        TraceV5RunFixture.writeMovie(traces.resolve("s3k/_movies/synthetic.bk2"));

        var run = PlaybackLibrary.scan(root, traces, null, IDENTITY).entries().stream()
                .filter(PlaybackEntry.Trace.class::isInstance).map(PlaybackEntry.Trace.class::cast)
                .filter(entry -> entry.trace().isRun()).findFirst().orElseThrow();

        assertEquals(new PlaybackEntry.Extent.RunTraceFrames(6), run.extent());
        assertTrue(run.extent().label().contains("run"));
    }

    @Test
    void absentRootsAreEmptyAndOrderIsStable() throws Exception {
        assertTrue(PlaybackLibrary.scan(root, null, null, IDENTITY).entries().isEmpty());
        recording("s3k", "z.bk2");
        recording("s1", "a.bk2");
        PlaybackLibrary first = PlaybackLibrary.scan(root, null, null, IDENTITY);
        assertEquals(first, PlaybackLibrary.scan(root, null, null, IDENTITY));
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.issues().clear());
    }

    @Test
    void recordingsKeepTheirNewestFirstOrderWithinAGame() throws Exception {
        Path older = recording("s3k", "a-older.bk2");
        Path newer = recording("s3k", "z-newer.bk2");
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.from(Instant.parse("2026-10-27T10:00:00Z")));
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.from(Instant.parse("2026-10-28T10:00:00Z")));

        var recordings = PlaybackLibrary.scan(root, null, null, IDENTITY).entries().stream()
                .filter(PlaybackEntry.Recording.class::isInstance).toList();

        assertEquals(List.of(newer, older), recordings.stream().map(PlaybackEntry::path).toList());
    }

    @Test
    void duplicatePrivateManifestRemainsAnInvalidRecording() throws Exception {
        Path duplicate = root.resolve("recordings/s3k/duplicate.bk2");
        Files.createDirectories(duplicate.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(duplicate), StandardCharsets.UTF_8)) {
            for (String name : List.of("OpenGGF/manifest.json", "openggf/MANIFEST.JSON")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("{}".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        PlaybackEntry entry = PlaybackLibrary.scan(root, null, null, IDENTITY).entries().getFirst();

        assertInstanceOf(PlaybackEntry.Recording.class, entry);
        assertEquals(PlaybackEntry.Availability.Status.INVALID, entry.availability().status());
        assertTrue(entry.availability().reason().contains("duplicate entry"));
    }

    private Path recording(String game, String name) throws Exception {
        Path path = root.resolve("recordings").resolve(game).resolve(name);
        UserRecordingManifest manifest = new UserRecordingManifest(1, name, IDENTITY,
                new RecordingLaunchContext(game, 0, 1, "sonic", List.of(), false, "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(0, null),
                "A", 1, UserRecordingStopReason.USER_STOPPED, Instant.parse("2026-10-28T10:00:00Z"));
        UserRecordingWriter.write(path, manifest, List.of(new RecordedFrameInput(0, 0, 0, false, 0, 0, false)),
                List.of(new DesyncLiteFrame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        return path;
    }

    private Path bareMovie(Path path, String manifest) throws Exception {
        writeMovie(path, "[Input]\nLogKey:|P1 Up|P1 Down|P1 Left|P1 Right|P1 Start|P1 A|P1 B|P1 C|\n"
                + "|........|\n|...R....|\n[/Input]\n", manifest);
        return path;
    }

    private void writeMovie(Path path, String input, String manifest) throws Exception {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("Input Log.txt"));
            zip.write(input.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (manifest != null) {
                zip.putNextEntry(new ZipEntry("OpenGGF/manifest.json"));
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private void trace(Path directory, String movie, int offset) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("metadata.json"), """
                {"trace_schema":5,"game":"s3k","zone":"aiz","zone_id":0,"act":1,"source_bk2":"%s","bk2_frame_offset":%d}
                """.formatted(movie, offset));
        Files.writeString(directory.resolve("physics.csv"), "frame,x\n0,1\n1,2\n");
    }
}

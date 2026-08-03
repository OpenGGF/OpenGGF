package com.openggf.trace.catalog;

import com.openggf.trace.TraceRunManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceCatalogRunDiscovery {

    @Test
    void discoversLegacyCatalogCompleteRunsWithoutHidingIndividualSegments() {
        Path tracesRoot = Path.of("src", "test", "resources", "traces");

        List<TraceEntry> entries = TraceCatalog.scan(tracesRoot);

        TraceEntry s1 = entries.stream()
                .filter(TraceEntry::isRun)
                .filter(entry -> "s1-complete-run".equals(
                        entry.runManifest().runId()))
                .findFirst()
                .orElseThrow();
        TraceEntry s3k = entries.stream()
                .filter(TraceEntry::isRun)
                .filter(entry -> "s3k-complete-sonic-tails".equals(
                        entry.runManifest().runId()))
                .findFirst()
                .orElseThrow();

        assertEquals(19, s1.runManifest().segments().size());
        assertEquals("ghz1_completerun",
                s1.runManifest().segments().getFirst().dir());
        assertEquals(5_598,
                s1.runManifest().segments().getFirst().traceFrameCount());
        assertEquals("fz_completerun",
                s1.runManifest().segments().getLast().dir());
        assertTrue(s1.runManifest().transitions().isEmpty());
        assertEquals(15, s3k.runManifest().segments().size());
        assertTrue(entries.stream().anyMatch(entry -> !entry.isRun()
                && "ghz1_completerun".equals(
                        entry.dir().getFileName().toString())));
        assertTrue(entries.stream().anyMatch(entry -> !entry.isRun()
                && "aiz_completerun".equals(
                        entry.dir().getFileName().toString())));
    }

    @Test
    void preparesCommittedKnucklesCompleteRunWithCanonicalizedTiming()
            throws Exception {
        Path tracesRoot = Path.of("src", "test", "resources", "traces");
        TraceEntry run = TraceCatalog.scan(tracesRoot).stream()
                .filter(TraceEntry::isRun)
                .filter(entry -> "s3k-knuckles-complete-superemeralds"
                        .equals(entry.runManifest().runId()))
                .findFirst()
                .orElseThrow();

        TraceCatalog.PreparedRunLaunch prepared =
                TraceCatalog.prepareRunLaunch(run);

        assertEquals(run.runManifest().segments().size(),
                prepared.segments().size());
    }

    @Test
    void discoversRunManifestAsSingleEntry(@TempDir Path root) throws Exception {
        // Copy the committed synthetic run fixture into <root>/s3k/runs/run_aiz_gumball_3seg
        Path src = Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");
        Path runDir = root.resolve("s3k").resolve("runs").resolve("run_aiz_gumball_3seg");
        Files.createDirectories(runDir.getParent());
        copyRecursively(src, runDir);
        // The manifest's source_bk2 must resolve: place a dummy bk2 at <root>/s3k/_movies/synthetic.bk2
        Path movies = root.resolve("s3k").resolve("_movies");
        Files.createDirectories(movies);
        Files.write(movies.resolve("synthetic.bk2"), new byte[] {0});

        List<TraceEntry> entries = TraceCatalog.scan(root);
        List<TraceEntry> runs = entries.stream().filter(TraceEntry::isRun).toList();
        assertEquals(1, runs.size());
        TraceEntry run = runs.get(0);
        assertEquals("s3k", run.gameId());
        assertEquals(6, run.frameCount()); // 3 segments x 2 frames
        assertEquals(500, run.bk2StartOffset());
        assertTrue(run.displayLabel().contains("run_aiz_gumball_3seg"));
        assertNotNull(run.runManifest());
    }

    @Test
    void sharedMovieWinsWhenRunAlsoContainsLocalCopy(@TempDir Path root) throws Exception {
        Path runDir = copySyntheticRun(root, "s3k");
        Path sharedMovie = root.resolve("s3k/_movies/synthetic.bk2");
        Files.createDirectories(sharedMovie.getParent());
        Files.write(sharedMovie, new byte[] {1});
        Files.write(runDir.resolve("synthetic.bk2"), new byte[] {2});

        TraceEntry run = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow();

        assertEquals(sharedMovie, run.bk2Path());
    }

    @Test
    void fallsBackToContainedMovieInsideRunDirectory(@TempDir Path root) throws Exception {
        Path runDir = copySyntheticRun(root, "s3k");
        Path localMovie = runDir.resolve("synthetic.bk2");
        Files.write(localMovie, new byte[] {1});

        TraceEntry run = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow();

        assertEquals(localMovie, run.bk2Path());
    }

    @Test
    void rejectsAbsoluteRunMoviePath(@TempDir Path root) throws Exception {
        Path runDir = copySyntheticRun(root, "s3k");
        Path outsideMovie = root.resolve("outside.bk2").toAbsolutePath();
        Files.write(outsideMovie, new byte[] {1});
        replaceSourceBk2(runDir, outsideMovie.toString());

        assertTrue(TraceCatalog.scan(root).stream().noneMatch(TraceEntry::isRun));
    }

    @Test
    void rejectsRunMovieParentTraversal(@TempDir Path root) throws Exception {
        Path runDir = copySyntheticRun(root, "s3k");
        Path outsideMovie = runDir.getParent().resolve("outside.bk2");
        Files.write(outsideMovie, new byte[] {1});
        replaceSourceBk2(runDir, "../outside.bk2");

        assertTrue(TraceCatalog.scan(root).stream().noneMatch(TraceEntry::isRun));
    }

    @Test
    void discoversCommittedS2RunWithLocalMovie() throws Exception {
        Path tracesRoot = Path.of("src", "test", "resources", "traces");
        Path runDir = tracesRoot.resolve("s2/runs/s2-ehz-halfpipe-roundtrip");
        TraceRunManifest manifest = TraceRunManifest.load(
                runDir.resolve("run_manifest.json"));
        manifest.validate(runDir);
        assertEquals(runDir.resolve("s2-ehz-halfpipe-roundtrip.bk2"),
                TraceCatalog.resolveRunBk2(runDir, manifest));

        TraceEntry run = TraceCatalog.scan(tracesRoot).stream()
                .filter(TraceEntry::isRun)
                .filter(entry -> "s2-ehz-halfpipe-roundtrip"
                        .equals(entry.runManifest().runId()))
                .findFirst()
                .orElseThrow();

        assertEquals(runDir.resolve("s2-ehz-halfpipe-roundtrip.bk2"),
                run.bk2Path());
    }

    @Test
    void syntheticRunSubtreeIsExcludedFromDiscovery(@TempDir Path root) throws Exception {
        // Place an OTHERWISE-VALID run under synthetic/runs/ — without the scanRuns
        // synthetic filter it would be discovered. Assert it is excluded, mirroring
        // the level scan's synthetic exclusion.
        Path src = Path.of("src", "test", "resources", "traces", "synthetic", "run_aiz_gumball_3seg");
        Path runDir = root.resolve("synthetic").resolve("runs").resolve("run_aiz_gumball_3seg");
        Files.createDirectories(runDir.getParent());
        copyRecursively(src, runDir);
        Path movies = root.resolve("synthetic").resolve("_movies");
        Files.createDirectories(movies);
        Files.write(movies.resolve("synthetic.bk2"), new byte[] {0});

        List<TraceEntry> entries = TraceCatalog.scan(root);
        assertTrue(entries.stream().noneMatch(TraceEntry::isRun),
                "a run under synthetic/runs/ must be excluded from discovery");
    }

    @Test
    void invalidRunIsSkippedNotFatal(@TempDir Path root) throws Exception {
        Path badRun = root.resolve("s3k").resolve("runs").resolve("broken");
        Files.createDirectories(badRun);
        Files.writeString(badRun.resolve("run_manifest.json"), "{\"run_schema\": 99}");
        List<TraceEntry> entries = TraceCatalog.scan(root);
        assertTrue(entries.stream().noneMatch(TraceEntry::isRun));
    }

    private static Path copySyntheticRun(Path root, String game) throws IOException {
        Path src = Path.of("src", "test", "resources", "traces", "synthetic",
                "run_aiz_gumball_3seg");
        Path runDir = root.resolve(game).resolve("runs")
                .resolve("run_aiz_gumball_3seg");
        Files.createDirectories(runDir.getParent());
        copyRecursively(src, runDir);
        return runDir;
    }

    private static void replaceSourceBk2(Path runDir, String sourceBk2)
            throws IOException {
        Path manifest = runDir.resolve("run_manifest.json");
        String json = Files.readString(manifest);
        Files.writeString(manifest,
                json.replace("\"source_bk2\": \"synthetic.bk2\"",
                        "\"source_bk2\": \"" + sourceBk2.replace("\\", "\\\\") + "\""));
    }

    private static void copyRecursively(Path src, Path dest) throws IOException {
        try (var stream = Files.walk(src)) {
            for (Path path : stream.toList()) {
                Path target = dest.resolve(src.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }
}

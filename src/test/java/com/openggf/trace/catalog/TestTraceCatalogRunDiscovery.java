package com.openggf.trace.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceCatalogRunDiscovery {

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
    void invalidRunIsSkippedNotFatal(@TempDir Path root) throws Exception {
        Path badRun = root.resolve("s3k").resolve("runs").resolve("broken");
        Files.createDirectories(badRun);
        Files.writeString(badRun.resolve("run_manifest.json"), "{\"run_schema\": 99}");
        List<TraceEntry> entries = TraceCatalog.scan(root);
        assertTrue(entries.stream().noneMatch(TraceEntry::isRun));
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

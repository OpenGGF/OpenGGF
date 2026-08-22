package com.openggf.trace;

import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;
import com.openggf.tests.trace.TraceV5RunFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceReaderLifecycle {

    @Test
    void balancesSuccessfulPlainAndGzipReaders(@TempDir Path root) throws Exception {
        Path runDirectory = TraceV5RunFixture.writeS3kBonusRun(root.resolve("runs"));
        Path plainSegment = runDirectory.resolve("seg00_aiz");
        Path gzipSegment = runDirectory.resolve("seg01_gumball");
        gzip(gzipSegment.resolve("physics.csv"));
        gzip(gzipSegment.resolve("aux_state.jsonl"));
        Files.delete(gzipSegment.resolve("physics.csv"));
        Files.delete(gzipSegment.resolve("aux_state.jsonl"));
        Path plainPhysics = plainSegment.resolve("physics.csv");
        Path plainAux = plainSegment.resolve("aux_state.jsonl");
        Path gzipPhysics = gzipSegment.resolve("physics.csv.gz");
        Path gzipAux = gzipSegment.resolve("aux_state.jsonl.gz");
        List<ReaderEvent> events = new ArrayList<>();
        AutoCloseable restore = TraceFiles.observeReadersForTest(
                (event, path) -> events.add(new ReaderEvent(event, path)));

        try {
            TraceData.load(plainSegment);
            TraceData.load(gzipSegment);
        } finally {
            restore.close();
        }

        assertBalanced(events);
        assertOpened(events, plainPhysics, plainAux, gzipPhysics, gzipAux);
    }

    @Test
    void balancesReadersWhenSpecialCompositeParserFails(@TempDir Path root)
            throws Exception {
        Path runDirectory = TraceV5RunFixture.writeS2SpecialStageRun(root.resolve("runs"));
        TraceRunManifest run = TraceRunManifest.load(
                runDirectory.resolve("run_manifest.json"));
        TraceRunSegmentDescriptor descriptor = TraceRunReplayWalker
                .planDescriptors(run, runDirectory).get(1);
        Files.writeString(runDirectory.resolve("ss/aux_state.jsonl"), "{not-json}\n");
        List<ReaderEvent> events = new ArrayList<>();
        AutoCloseable restore = TraceFiles.observeReadersForTest(
                (event, path) -> events.add(new ReaderEvent(event, path)));

        try {
            assertThrows(IOException.class,
                    () -> TraceRunReplayWalker.openActiveSegment(descriptor, 1));
        } finally {
            restore.close();
        }

        assertBalanced(events);
    }

    private static void gzip(Path source) throws IOException {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(
                source.resolveSibling(source.getFileName() + ".gz")))) {
            Files.copy(source, output);
        }
    }

    /**
     * Catches removing TraceFiles' observation wrapper entirely: balance alone
     * would incorrectly accept an empty event stream.
     */
    private static void assertBalanced(List<ReaderEvent> events) {
        long opened = events.stream()
                .filter(event -> event.event() == TraceFiles.ReaderLifecycleEvent.OPENED)
                .count();
        long closed = events.stream()
                .filter(event -> event.event() == TraceFiles.ReaderLifecycleEvent.CLOSED)
                .count();
        assertTrue(opened > 0,
                "reader observation must report at least one successful open: " + events);
        assertEquals(opened, closed, events::toString);
    }

    private static void assertOpened(List<ReaderEvent> events, Path... expectedPaths) {
        List<Path> openedPaths = events.stream()
                .filter(event -> event.event() == TraceFiles.ReaderLifecycleEvent.OPENED)
                .map(ReaderEvent::path)
                .toList();
        for (Path expectedPath : expectedPaths) {
            assertTrue(openedPaths.contains(expectedPath),
                    () -> "missing observed open for " + expectedPath
                            + ": " + openedPaths);
        }
    }

    private record ReaderEvent(TraceFiles.ReaderLifecycleEvent event, Path path) {
    }
}

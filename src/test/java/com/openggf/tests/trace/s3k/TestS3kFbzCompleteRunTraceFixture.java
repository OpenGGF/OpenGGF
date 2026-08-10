package com.openggf.tests.trace.s3k;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract for the FBZ segment recorded from the shared S3K complete-run movie. */
public class TestS3kFbzCompleteRunTraceFixture {
    private static final Path S3K_TRACE_ROOT =
            Path.of("src/test/resources/traces/s3k");
    private static final Path TRACE_DIR = S3K_TRACE_ROOT.resolve("fbz_completerun");
    private static final Path SHARED_MOVIE =
            S3K_TRACE_ROOT.resolve("_movies/s3k-complete-sonic-tails.bk2");

    @Test
    void metadataMatchesPinnedCompleteRunSegment() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata metadata = trace.metadata();

        assertEquals("s3k", metadata.game());
        assertEquals("fbz", metadata.zone());
        assertEquals(4, metadata.zoneId());
        assertEquals(1, metadata.act());
        assertEquals(237_913, metadata.bk2FrameOffset());
        assertEquals(44_281, metadata.traceFrameCount());
        assertEquals(44_281, trace.frameCount());
        // The complete-run recorder arms at frame 237913 and intentionally
        // returns before writing row 0. Row 0 is movie frame 237914 and the
        // final FBZ row is 282194; SOZ arms on the following frame. This is
        // the same bootstrap convention used by every existing S3K segment.
        assertEquals(282_194,
                metadata.bk2FrameOffset() + metadata.traceFrameCount(),
                "the last recorded FBZ movie frame");
        assertEquals(282_195,
                metadata.bk2FrameOffset() + metadata.traceFrameCount() + 1,
                "SOZ must arm on the frame after the final FBZ row");

        assertEquals("s3k-complete-sonic-tails.bk2", metadata.sourceBk2());
        assertEquals(List.of("sonic", "tails"), metadata.recordedCharacters());
        assertEquals("sonic", metadata.recordedMainCharacter());
        assertEquals(List.of("tails"), metadata.recordedSidekicks());

        assertEquals(5, metadata.traceSchema());
        assertEquals("complete_run", metadata.traceProfile());
        assertFalse(metadata.recorder().isBlank());
        assertFalse(metadata.recorderVersion().isBlank());
        assertEquals("2.11", metadata.bizhawkVersion());
        assertEquals("Genplus-gx", metadata.genesisCore());
        assertEquals("C5B1C655C19F462ADE0AC4E17A844D10", metadata.romChecksum());
    }

    @Test
    void fixtureUsesOnlyCompressedPayloadsAndTheSharedMovie() throws Exception {
        assertTrue(Files.isRegularFile(TRACE_DIR.resolve("metadata.json")));
        assertTrue(Files.isRegularFile(TRACE_DIR.resolve("physics.csv.gz")));
        assertTrue(Files.isRegularFile(TRACE_DIR.resolve("aux_state.jsonl.gz")));
        assertFalse(Files.exists(TRACE_DIR.resolve("physics.csv")));
        assertFalse(Files.exists(TRACE_DIR.resolve("aux_state.jsonl")));
        assertTrue(Files.isRegularFile(SHARED_MOVIE));

        try (var files = Files.list(TRACE_DIR)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".bk2")),
                    "the complete-run BK2 must remain deduplicated under s3k/_movies");
        }
    }

    @Test
    void traceCatalogDiscoversTheFbzFixture() {
        TraceEntry entry = TraceCatalog.scan(S3K_TRACE_ROOT).stream()
                .filter(candidate -> candidate.dir().equals(TRACE_DIR))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FBZ complete-run fixture was not catalogued"));

        assertEquals("s3k", entry.gameId());
        assertEquals(4, entry.zone());
        assertEquals(0, entry.act(), "catalog acts are zero-based");
        assertEquals(237_913, entry.bk2StartOffset());
        assertEquals("sonic", entry.team().mainCharacter());
        assertEquals(List.of("tails"), entry.team().sidekicks());
        assertEquals(SHARED_MOVIE, entry.bk2Path());
    }
}

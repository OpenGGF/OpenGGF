package com.openggf.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceCaptureManifestTest {
    @TempDir
    Path directory;

    @Test
    void writesPresentationAndNativeComparisonProvenance() throws Exception {
        Path output = directory.resolve("capture.mkv");
        TraceMetadata metadata = metadata();
        TraceEntry entry = entry(directory, metadata);

        Path manifest = TraceCaptureManifest.write(output, entry, metadata,
                TraceCaptureDimensions.resolve(400, 2), "aiz-fire-transition", 12);

        JsonNode root = new ObjectMapper().readTree(manifest.toFile());
        assertEquals(400, root.path("presentation").path("logical_width").asInt());
        assertEquals(800, root.path("presentation").path("physical_width").asInt());
        assertEquals(320, root.path("trace_comparison").path("logical_width").asInt());
        assertEquals("native_320x224", root.path("trace_comparison").path("authority").asText());
        assertEquals(5, root.path("trace").path("trace_schema").asInt());
        assertEquals("aiz-fire-transition", root.path("clip").path("name").asText());
    }

    @Test
    void failedPublicationPreservesPreviousManifestAndCleansTemporaryFile() throws Exception {
        Path output = directory.resolve("capture.mkv");
        Files.write(output, new byte[]{1});
        TraceMetadata metadata = metadata();
        TraceEntry entry = entry(directory, metadata);
        TraceCaptureDimensions dimensions = TraceCaptureDimensions.resolve(400, 1);
        Path manifest = TraceCaptureManifest.write(output, entry, metadata,
                dimensions, null, 150);
        String prior = Files.readString(manifest);

        TraceCaptureManifest.FileOps failure = new TraceCaptureManifest.FileOps() {
            @Override
            public void move(Path source, Path target) throws IOException {
                throw new IOException("move failure");
            }
        };
        assertThrows(IOException.class, () -> TraceCaptureManifest.write(
                output, entry, metadata, dimensions, "failed", 1, failure));
        assertEquals(prior, Files.readString(manifest));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.filter(path -> path.getFileName().toString().contains(".tmp"))
                    .count());
        }
    }

    private static TraceMetadata metadata() {
        return new TraceMetadata(
                "s3k", "AIZ", 0, 1, 12, null, 25,
                "0x100", "0x200", "date", "BizHawk", "1", 5,
                "level", "2.9", "gpgx", null, 0, "aiz1",
                "trace.bk2", "checksum", "notes", List.of("sonic"),
                "sonic", List.of(), null, null, "level", "movie",
                null, null, null, null, null, null, null, null);
    }

    private static TraceEntry entry(Path directory, TraceMetadata metadata) {
        return new TraceEntry(directory.resolve("trace"), "s3k", 0, 0,
                25, 12, 0, null, directory.resolve("trace.bk2"), metadata);
    }
}

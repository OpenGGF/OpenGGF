package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes capture-owned metadata beside an encoded video transactionally. */
final class TraceCaptureManifest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TraceCaptureManifest() { }

    interface FileOps {
        default Path createTemp(Path directory, String prefix, String suffix)
                throws IOException { return Files.createTempFile(directory, prefix, suffix); }
        default void write(Path path, Object document) throws IOException {
            JSON.writeValue(path.toFile(), document);
        }
        default void force(Path path) throws IOException {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }
        default void move(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        default void deleteIfExists(Path path) throws IOException { Files.deleteIfExists(path); }
    }

    static Path write(Path output, TraceEntry entry, TraceMetadata trace,
            TraceCaptureDimensions dimensions, String clip, int tailFrames) throws IOException {
        return write(output, entry, trace, dimensions, clip, tailFrames, new FileOps() { });
    }

    static Path write(Path output, TraceEntry entry, TraceMetadata trace,
            TraceCaptureDimensions dimensions, String clip, int tailFrames,
            FileOps fileOps) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("capture_schema", 1);
        root.put("output", output.getFileName().toString());

        Map<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("logical_width", dimensions.logicalWidth());
        presentation.put("logical_height", dimensions.logicalHeight());
        presentation.put("physical_width", dimensions.physicalWidth());
        presentation.put("physical_height", dimensions.physicalHeight());
        presentation.put("scale", dimensions.scale());
        presentation.put("width_mode", dimensions.widthMode());
        presentation.put("support_tier", dimensions.supportTier());
        root.put("presentation", presentation);

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("logical_width", 320);
        comparison.put("logical_height", TraceCaptureDimensions.LOGICAL_HEIGHT);
        comparison.put("authority", "native_320x224");
        comparison.put("width_independent", true);
        root.put("trace_comparison", comparison);

        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("directory", entry.dir().toString());
        provenance.put("source_bk2", entry.bk2Path() == null ? null : entry.bk2Path().toString());
        provenance.put("game", entry.gameId());
        provenance.put("zone", entry.zone());
        provenance.put("act", entry.act());
        provenance.put("trace_schema", trace.traceSchema());
        provenance.put("trace_profile", trace.traceProfile());
        root.put("trace", provenance);

        Map<String, Object> clipMetadata = new LinkedHashMap<>();
        clipMetadata.put("name", clip);
        clipMetadata.put("tail_frames", tailFrames);
        root.put("clip", clipMetadata);

        Path manifest = manifestPath(output);
        Path directory = manifest.toAbsolutePath().getParent();
        Path temporary = fileOps.createTemp(directory,
                "." + manifest.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            fileOps.write(temporary, root);
            fileOps.force(temporary);
            fileOps.move(temporary, manifest);
            moved = true;
            return manifest;
        } finally {
            if (!moved) {
                try { fileOps.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }

    static Path manifestPath(Path output) {
        String fileName = output.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String stem = extension > 0 ? fileName.substring(0, extension) : fileName;
        return output.resolveSibling(stem + ".json");
    }
}

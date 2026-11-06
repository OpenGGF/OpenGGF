package com.openggf.trace.timing;

import static com.openggf.trace.timing.StrictTimingFields.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.TraceFiles;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceRunManifest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict V5 companion stream for ordered initialization phase timing only. */
public final class InitializationTimingStreamLoader {
    private static final String FILE_NAME = "initialization_timing.jsonl";
    private static final Set<String> FIELDS = Set.of("event", "raw_frame", "sequence", "load_ordinal",
            "phase_ordinal", "phase", "submission_fingerprint", "operation");
    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    private InitializationTimingStreamLoader() { }

    public static InitializationTimingSchedule load(Path traceDirectory, TraceMetadata metadata) throws IOException {
        Path path = TraceFiles.resolve(traceDirectory, FILE_NAME);
        if (path == null) return InitializationTimingSchedule.empty();
        if (!Integer.valueOf(5).equals(metadata.traceSchema())) {
            throw rejected(path, "trace_schema must be integer 5");
        }
        if (metadata.traceFrameCount() < 0) throw rejected(path, "trace_frame_count must not be negative");
        return loadBounded(path, metadata.traceFrameCount());
    }

    /** Run-root namespace: absolute zero-based BK2 rows, including segment gaps. */
    public static InitializationTimingSchedule loadRun(Path runDirectory, TraceRunManifest manifest,
                                                       int movieFrameCount) throws IOException {
        java.util.Objects.requireNonNull(manifest, "validated V5 manifest");
        Path path = TraceFiles.resolve(runDirectory, FILE_NAME);
        if (path == null) return InitializationTimingSchedule.empty();
        if (movieFrameCount < 0) throw rejected(path, "movie frame count must not be negative");
        // TraceRunManifest's strict reader owns the V5 manifest contract. No
        // metadata row offset or interstitial provenance remaps this stream.
        return loadBounded(path, movieFrameCount);
    }

    private static InitializationTimingSchedule loadBounded(Path path, int frameCount) throws IOException {
        String content = decodeUtf8(path, FILE_NAME);
        if (!content.isEmpty() && (!content.endsWith("\n") || content.indexOf('\r') >= 0)) {
            throw rejected(path, FILE_NAME + " must use LF-terminated UTF-8 lines");
        }
        List<InitializationTimingEvent> events = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index];
            if (line.isEmpty() || !line.equals(line.trim())) {
                throw rejected(path, "line " + (index + 1) + " must be one compact JSON event");
            }
            InitializationTimingEvent event = parse(path, index + 1, line);
            if (event.rawFrame() >= frameCount) {
                throw rejected(path, "raw_frame is outside represented frame count");
            }
            events.add(event);
        }
        try {
            return new InitializationTimingSchedule(events);
        } catch (IllegalArgumentException invalid) {
            throw rejected(path, invalid.getMessage());
        }
    }

    private static InitializationTimingEvent parse(Path path, int lineNumber, String line) throws IOException {
        JsonNode node;
        try (JsonParser parser = MAPPER.getFactory().createParser(line)) {
            node = MAPPER.readTree(parser);
            if (parser.nextToken() != null) throw rejected(path, "line " + lineNumber + " has trailing JSON values");
        } catch (JsonProcessingException invalid) {
            throw rejected(path, "line " + lineNumber + " malformed JSON or duplicate field");
        }
        if (node == null || !node.isObject()) throw rejected(path, "line " + lineNumber + " must be a JSON object");
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        if (!FIELDS.equals(fields)) throw rejected(path, "line " + lineNumber + " has unknown or missing field");
        requireText(path, lineNumber, node, "event", "load_phase_timing");
        JsonNode load = node.get("load_ordinal");
        if (!load.isIntegralNumber() || !load.canConvertToLong() || load.longValue() <= 0) {
            throw rejected(path, "line " + lineNumber + " has invalid load_ordinal");
        }
        try {
            return new InitializationTimingEvent(
                    requireInt(path, lineNumber, node, "raw_frame"),
                    requireInt(path, lineNumber, node, "sequence"), load.longValue(),
                    requireInt(path, lineNumber, node, "phase_ordinal"),
                    InitializationTimingEvent.Phase.fromWireName(requireText(path, lineNumber, node, "phase", null)),
                    requireText(path, lineNumber, node, "submission_fingerprint", null),
                    InitializationTimingEvent.Operation.fromWireName(requireText(path, lineNumber, node, "operation", null)));
        } catch (IllegalArgumentException invalid) {
            throw rejected(path, "line " + lineNumber + " " + invalid.getMessage());
        }
    }
}

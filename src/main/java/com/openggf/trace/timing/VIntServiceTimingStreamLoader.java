package com.openggf.trace.timing;

import static com.openggf.trace.timing.StrictTimingFields.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.TraceFiles;
import com.openggf.trace.TraceMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Strict V5 companion stream for the approved lost-V-INT admission (hardware-timing contract, 2026-11-13
 * extension): {@code {"event":"vint_not_serviced","raw_frame":N}} per trace row whose VBlank produced no
 * observed interrupt entry. Absence of the file is {@link VIntServiceTimingSchedule#notObserved()}; an empty
 * file is an observed recording with no lost V-INT.
 */
public final class VIntServiceTimingStreamLoader {
    private static final String FILE_NAME = "vint_service_timing.jsonl";
    private static final String EVENT = "vint_not_serviced";
    private static final Set<String> FIELDS = Set.of("event", "raw_frame");
    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    private VIntServiceTimingStreamLoader() { }

    public static VIntServiceTimingSchedule load(Path traceDirectory, TraceMetadata metadata) throws IOException {
        Path path = TraceFiles.resolve(traceDirectory, FILE_NAME);
        if (path == null) return VIntServiceTimingSchedule.notObserved();
        if (!Integer.valueOf(5).equals(metadata.traceSchema())) {
            throw rejected(path, "trace_schema must be integer 5");
        }
        int frameCount = metadata.traceFrameCount();
        if (frameCount < 0) throw rejected(path, "trace_frame_count must not be negative");
        String content = decodeUtf8(path, FILE_NAME);
        if (!content.isEmpty() && (!content.endsWith("\n") || content.indexOf('\r') >= 0)) {
            throw rejected(path, FILE_NAME + " must use LF-terminated UTF-8 lines");
        }
        List<Integer> rows = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index];
            int lineNumber = index + 1;
            if (line.isEmpty() || !line.equals(line.trim())) {
                throw rejected(path, "line " + lineNumber + " must be one compact JSON event");
            }
            int row = parse(path, lineNumber, line);
            if (row < 0 || row >= frameCount) {
                throw rejected(path, "line " + lineNumber + " raw_frame " + row + " is outside the trace's rows");
            }
            if (!rows.isEmpty() && row <= rows.getLast()) {
                throw rejected(path, "line " + lineNumber + " raw_frame must be strictly increasing");
            }
            rows.add(row);
        }
        return VIntServiceTimingSchedule.observed(rows.stream().mapToInt(Integer::intValue).toArray());
    }

    private static int parse(Path path, int lineNumber, String line) throws IOException {
        JsonNode node;
        try (JsonParser parser = MAPPER.getFactory().createParser(line)) {
            node = MAPPER.readTree(parser);
            if (parser.nextToken() != null) throw rejected(path, "line " + lineNumber + " has trailing JSON values");
        } catch (JsonProcessingException invalid) {
            throw rejected(path, "line " + lineNumber + " malformed JSON or duplicate field");
        }
        if (node == null || !node.isObject()) throw rejected(path, "line " + lineNumber + " must be a JSON object");
        Set<String> fields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        if (!FIELDS.equals(fields)) throw rejected(path, "line " + lineNumber + " has unknown or missing field");
        requireText(path, lineNumber, node, "event", EVENT);
        return requireInt(path, lineNumber, node, "raw_frame");
    }
}

package com.openggf.trace.timing;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.trace.TraceMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict loader for the versioned, dedicated hardware-timing input stream. */
public final class HardwareTimingStreamLoader {

    private static final String FILE_NAME = "hardware_timing.jsonl";
    private static final String MEASUREMENT_FILE_NAME =
            "load_time_measurements.jsonl";
    private static final String EVENT_NAME = "hardware_work_completed";
    private static final Set<String> EVENT_FIELDS = Set.of(
            "event", "raw_frame", "boundary", "kind", "ordinal", "submission_fingerprint");
    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    private HardwareTimingStreamLoader() {
    }

    public static HardwareTimingSchedule load(Path traceDirectory, TraceMetadata metadata)
            throws IOException {
        Path measurementPath = traceDirectory.resolve(MEASUREMENT_FILE_NAME);
        if (Files.exists(measurementPath)) {
            throw rejected(
                    measurementPath,
                    "load-time measurements are tooling-only and cannot be loaded as trace data");
        }
        Path metadataPath = traceDirectory.resolve("metadata.json");
        Path timingPath = traceDirectory.resolve(FILE_NAME);
        boolean hasFile = Files.isRegularFile(timingPath);
        boolean hasKey = metadata.hasHardwareTimingStream();
        Integer traceSchema = metadata.traceSchema();

        if (traceSchema != null && traceSchema == 7 && !hasKey) {
            throw rejected(metadataPath, "hardware_timing_schema is required for trace_schema 7");
        }
        if (!hasKey) {
            if (hasFile) {
                throw rejected(timingPath, "hardware_timing_schema metadata key is required");
            }
            return HardwareTimingSchedule.empty();
        }

        final int timingSchema;
        try {
            timingSchema = metadata.requiredHardwareTimingSchema();
        } catch (IllegalArgumentException e) {
            throw rejected(metadataPath, e.getMessage());
        }
        if (traceSchema == null || traceSchema != 7) {
            throw rejected(metadataPath, "trace_schema must be 7 for hardware_timing_schema " + timingSchema);
        }
        if (!hasFile) {
            throw rejected(timingPath, "hardware_timing_schema requires " + FILE_NAME);
        }
        return loadVersion(timingPath, metadata.traceFrameCount(), timingSchema);
    }

    private static HardwareTimingSchedule loadVersion(
            Path timingPath, int traceFrameCount, int timingSchema)
            throws IOException {
        if (traceFrameCount < 0) {
            throw rejected(timingPath, "trace_frame_count must not be negative");
        }
        String content = decodeUtf8(timingPath);
        if (!content.isEmpty() && (!content.endsWith("\n") || content.indexOf('\r') >= 0)) {
            throw rejected(timingPath, "hardware_timing.jsonl must use LF-terminated UTF-8 lines");
        }

        List<HardwareCompletionEdge> edges = new ArrayList<>();
        Map<HardwareWorkKind, Long> lastOrdinalByKind = new HashMap<>();
        Set<EdgeIdentity> identities = new HashSet<>();
        String[] lines = content.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index];
            if (line.isEmpty() || !line.equals(line.trim())) {
                throw rejected(timingPath, "line " + (index + 1) + " must be one compact JSON event");
            }
            HardwareCompletionEdge edge = parseEdge(timingPath, index + 1, line);
            if (edge.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE
                    && timingSchema == 1) {
                throw rejected(timingPath, "line " + (index + 1)
                        + " kind is not authorized by hardware_timing_schema 1");
            }
            if (edge.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE
                    && edge.boundary() != HardwareServiceBoundary.PRE_MAIN_LOOP) {
                throw rejected(timingPath, "line " + (index + 1)
                        + " direct completion kind requires pre_main_loop boundary");
            }
            if (edge.rawFrame() < 0 || edge.rawFrame() >= traceFrameCount) {
                throw rejected(timingPath, "raw_frame " + edge.rawFrame()
                        + " is outside [0, " + traceFrameCount + ")");
            }
            EdgeIdentity identity = new EdgeIdentity(edge.kind(), edge.ordinal());
            if (!identities.add(identity)) {
                throw rejected(timingPath, "duplicate identity " + edge.kind() + "#" + edge.ordinal());
            }
            Long previousOrdinal = lastOrdinalByKind.put(edge.kind(), edge.ordinal());
            if (previousOrdinal != null && edge.ordinal() <= previousOrdinal) {
                throw rejected(timingPath, "ordinal must increase per kind " + edge.kind());
            }
            edges.add(edge);
        }
        edges = normalizeCanonicalOrder(timingPath, timingSchema, edges);
        return edges.isEmpty() && timingSchema == 1
                ? HardwareTimingSchedule.empty()
                : new HardwareTimingSchedule(timingSchema, edges);
    }

    private static List<HardwareCompletionEdge> normalizeCanonicalOrder(
            Path timingPath,
            int timingSchema,
            List<HardwareCompletionEdge> source) throws IOException {
        List<HardwareCompletionEdge> normalized = new ArrayList<>(source);
        for (int i = 1; i < normalized.size(); i++) {
            HardwareCompletionEdge previous = normalized.get(i - 1);
            HardwareCompletionEdge current = normalized.get(i);
            if (HardwareTimingSchedule.CANONICAL_ORDER.compare(
                    previous, current) < 0) {
                continue;
            }
            if (timingSchema == 2
                    && previous.rawFrame() == current.rawFrame()
                    && previous.boundary()
                            == HardwareServiceBoundary.PRE_MAIN_LOOP
                    && previous.kind()
                            == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE
                    && current.boundary()
                            == HardwareServiceBoundary.POST_OBJECTS
                    && current.kind() == HardwareWorkKind.KOS_MODULE_QUEUE) {
                normalized.set(i - 1, current);
                normalized.set(i, previous);
                continue;
            }
            throw rejected(timingPath, "events must use canonical ordering");
        }
        for (int i = 1; i < normalized.size(); i++) {
            if (HardwareTimingSchedule.CANONICAL_ORDER.compare(
                    normalized.get(i - 1), normalized.get(i)) >= 0) {
                throw rejected(timingPath, "events must use canonical ordering");
            }
        }
        return normalized;
    }

    private static HardwareCompletionEdge parseEdge(Path timingPath, int lineNumber, String line)
            throws IOException {
        JsonNode node;
        try (JsonParser parser = MAPPER.getFactory().createParser(line)) {
            node = MAPPER.readTree(parser);
            if (parser.nextToken() != null) {
                throw rejected(timingPath, "line " + lineNumber + " has trailing JSON values");
            }
        } catch (JsonProcessingException e) {
            String detail = e.getOriginalMessage() != null && e.getOriginalMessage().contains("Duplicate")
                    ? "has duplicate JSON field"
                    : "is malformed JSON";
            throw rejected(timingPath, "line " + lineNumber + " " + detail);
        }
        if (node == null || !node.isObject()) {
            throw rejected(timingPath, "line " + lineNumber + " must be a JSON object");
        }
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(EVENT_FIELDS)) {
            Set<String> unexpected = new HashSet<>(fields);
            unexpected.removeAll(EVENT_FIELDS);
            Set<String> missing = new HashSet<>(EVENT_FIELDS);
            missing.removeAll(fields);
            throw rejected(timingPath, "line " + lineNumber + " has unknown or missing field "
                    + (!unexpected.isEmpty() ? unexpected.iterator().next() : missing.iterator().next()));
        }
        requireText(timingPath, lineNumber, node, "event", EVENT_NAME);
        int rawFrame = requireInt(timingPath, lineNumber, node, "raw_frame");
        HardwareServiceBoundary boundary = parseBoundary(timingPath, lineNumber, requireText(timingPath, lineNumber, node, "boundary", null));
        HardwareWorkKind kind = parseKind(timingPath, lineNumber, requireText(timingPath, lineNumber, node, "kind", null));
        long ordinal = requireOrdinal(timingPath, lineNumber, node);
        String fingerprint = requireText(timingPath, lineNumber, node, "submission_fingerprint", null);
        if (!FINGERPRINT.matcher(fingerprint).matches()) {
            throw rejected(timingPath, "line " + lineNumber + " has invalid submission_fingerprint");
        }
        return new HardwareCompletionEdge(rawFrame, boundary, kind, ordinal, fingerprint);
    }

    private static String requireText(Path path, int line, JsonNode node, String field, String exact)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || (exact != null && !exact.equals(value.textValue()))) {
            throw rejected(path, "line " + line + " has invalid " + field);
        }
        return value.textValue();
    }

    private static int requireInt(Path path, int line, JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isInt()) {
            throw rejected(path, "line " + line + " has invalid " + field);
        }
        return value.intValue();
    }

    private static long requireOrdinal(Path path, int line, JsonNode node) throws IOException {
        JsonNode value = node.get("ordinal");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw rejected(path, "line " + line + " has invalid ordinal");
        }
        return value.longValue();
    }

    private static HardwareServiceBoundary parseBoundary(Path path, int line, String wireName)
            throws IOException {
        try {
            return HardwareServiceBoundary.fromWireName(wireName);
        } catch (IllegalArgumentException e) {
            throw rejected(path, "line " + line + " has invalid boundary");
        }
    }

    private static HardwareWorkKind parseKind(Path path, int line, String wireName) throws IOException {
        try {
            return HardwareWorkKind.fromWireName(wireName);
        } catch (IllegalArgumentException e) {
            throw rejected(path, "line " + line + " has invalid kind");
        }
    }

    private static String decodeUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw rejected(path, "hardware_timing.jsonl must be valid UTF-8");
        }
    }

    private static IOException rejected(Path path, String reason) {
        return new IOException(path.getFileName() + ": " + reason);
    }

    private record EdgeIdentity(HardwareWorkKind kind, long ordinal) {
    }
}

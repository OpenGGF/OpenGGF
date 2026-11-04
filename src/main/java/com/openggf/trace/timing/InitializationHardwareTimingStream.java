package com.openggf.trace.timing;

import static com.openggf.trace.timing.StrictTimingFields.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.TraceFiles;
import com.openggf.trace.TraceRunManifest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Dedicated readiness observations for real loads outside structural segments. */
public final class InitializationHardwareTimingStream {
    private static final String FILE = "initialization_hardware_timing.jsonl";
    private static final Set<String> FIELDS = Set.of("event", "raw_frame", "load_ordinal",
            "boundary", "kind", "ordinal", "submission_fingerprint");
    private static final ObjectMapper JSON = new ObjectMapper(new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private final boolean recorded;
    private final Map<Long, HardwareTimingSchedule> loads;

    public InitializationHardwareTimingStream(Map<Long, HardwareTimingSchedule> loads) {
        this(true, loads);
    }
    private InitializationHardwareTimingStream(boolean recorded, Map<Long, HardwareTimingSchedule> loads) {
        this.recorded = recorded;
        this.loads = Map.copyOf(loads);
        loads.forEach((ordinal, schedule) -> {
            if (ordinal <= 0 || !schedule.hasRecordedInput() || schedule.edges().isEmpty()) throw new IllegalArgumentException("invalid load readiness schedule");
        });
    }
    public static InitializationHardwareTimingStream empty() { return new InitializationHardwareTimingStream(false, Map.of()); }
    public boolean hasRecordedInput() { return recorded; }
    public boolean hasLoad(long ordinal) { return loads.containsKey(ordinal); }
    public HardwareTimingSchedule forLoad(long ordinal) { return loads.getOrDefault(ordinal, HardwareTimingSchedule.recordedEmpty()); }

    public void verifyObservedLoads(Set<Long> observed) {
        for (long load : loads.keySet()) {
            if (!observed.contains(load)) throw new IllegalStateException("unconsumed initialization readiness load " + load);
        }
    }

    public void requireMatchingPhases(InitializationTimingSchedule phases) {
        loads.forEach((load, schedule) -> {
            var events = phases.events().stream().filter(event -> event.loadOrdinal() == load).toList();
            if (events.isEmpty()) throw new IllegalArgumentException("load readiness has no matching initialization load " + load);
            int first = events.getFirst().rawFrame(), last = events.getLast().rawFrame();
            if (schedule.edges().stream().anyMatch(edge -> edge.rawFrame() < first || edge.rawFrame() > last))
                throw new IllegalArgumentException("load readiness lies outside its initialization load " + load);
        });
    }

    public static InitializationHardwareTimingStream loadRun(Path directory, TraceRunManifest manifest, int movieFrames) throws IOException {
        Objects.requireNonNull(manifest, "validated V5 manifest");
        Path path = TraceFiles.resolve(directory, FILE);
        if (path == null) return empty();
        if (movieFrames < 0) throw rejected(path, "negative movie frame count");
        String content = decodeUtf8(path, FILE);
        if (!content.isEmpty() && (!content.endsWith("\n") || content.indexOf('\r') >= 0)) throw rejected(path, "expected LF-terminated UTF-8");
        Map<Long, List<HardwareCompletionEdge>> loads = new LinkedHashMap<>();
        Set<String> identities = new HashSet<>();
        Map<com.openggf.game.timing.HardwareWorkKind, Long> lastOrdinals = new HashMap<>();
        long previousLoad = 0;
        HardwareCompletionEdge previous = null;
        String[] lines = content.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index];
            int number = index + 1;
            if (line.isEmpty() || !line.equals(line.trim())) throw rejected(path, "line " + number + " must be compact JSON");
            JsonNode node;
            try (JsonParser parser = JSON.getFactory().createParser(line)) {
                node = JSON.readTree(parser);
                if (parser.nextToken() != null) throw rejected(path, "trailing JSON values");
            } catch (JsonProcessingException failure) { throw rejected(path, "malformed JSON or duplicate field"); }
            if (node == null || !node.isObject()) throw rejected(path, "expected JSON object");
            Set<String> fields = new HashSet<>(); node.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(FIELDS)) throw rejected(path, "unknown or missing field");
            requireText(path, number, node, "event", "hardware_work_completed");
            JsonNode loadNode = node.get("load_ordinal");
            if (!loadNode.isIntegralNumber() || !loadNode.canConvertToLong() || loadNode.longValue() <= 0) throw rejected(path, "invalid load_ordinal");
            long load = loadNode.longValue();
            int raw = requireInt(path, number, node, "raw_frame");
            if (raw < 0 || raw >= movieFrames) throw rejected(path, "raw_frame outside movie");
            String fingerprint = requireText(path, number, node, "submission_fingerprint", null);
            if (!fingerprint.matches("sha256:[0-9a-f]{64}")) throw rejected(path, "invalid submission_fingerprint");
            var edge = new HardwareCompletionEdge(raw,
                    parseBoundary(path, number, requireText(path, number, node, "boundary", null)),
                    parseKind(path, number, requireText(path, number, node, "kind", null)),
                    requireOrdinal(path, number, node), fingerprint);
            if ((edge.kind() == com.openggf.game.timing.HardwareWorkKind.KOS_DECOMPRESSION_QUEUE
                    || edge.kind() == com.openggf.game.timing.HardwareWorkKind.NEMESIS_PLC_QUEUE)
                    && edge.boundary() != com.openggf.game.timing.HardwareServiceBoundary.PRE_MAIN_LOOP)
                throw rejected(path, "direct completion requires pre_main_loop");
            if (load < previousLoad || previous != null && HardwareTimingSchedule.CANONICAL_ORDER.compare(previous, edge) >= 0)
                throw rejected(path, "events must be in physical load and canonical completion order");
            if (!identities.add(edge.kind() + "#" + edge.ordinal())) throw rejected(path, "duplicate completion identity");
            Long lastOrdinal = lastOrdinals.put(edge.kind(), edge.ordinal());
            if (lastOrdinal != null && edge.ordinal() <= lastOrdinal) throw rejected(path, "ordinal must increase per kind");
            loads.computeIfAbsent(load, ignored -> new ArrayList<>()).add(edge);
            previousLoad = load; previous = edge;
        }
        Map<Long, HardwareTimingSchedule> schedules = new LinkedHashMap<>();
        try { loads.forEach((load, edges) -> schedules.put(load, new HardwareTimingSchedule(edges))); }
        catch (IllegalArgumentException failure) { throw rejected(path, failure.getMessage()); }
        return new InitializationHardwareTimingStream(schedules);
    }
}

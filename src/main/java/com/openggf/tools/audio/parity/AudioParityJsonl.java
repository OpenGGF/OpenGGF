package com.openggf.tools.audio.parity;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Strict, deterministic and record-streaming JSONL transport for audio parity captures. */
public final class AudioParityJsonl {
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final ObjectMapper JSON = new ObjectMapper(FACTORY);
    private static final Set<String> TICK_FIELDS = Set.of("type", "ordinal", "state", "events",
            "diagnostic", "raw_bus");
    private static final Set<String> STATE_FIELDS = Set.of("global", "tracks");
    private static final Set<String> GLOBAL_FIELDS = Set.of("fadeActive", "fadeDirection", "fadeDelay",
            "fadeSteps", "speedUp", "tempoReload", "tempoTimeout");
    private static final Set<String> TRACK_FIELDS = Set.of("active", "ams", "baseFrequency", "detune",
            "doNotAttack", "duration", "durationReload", "envelopeCursor", "fms", "hardware",
            "loopCounters", "modulationEnabled", "overridden", "pan", "returnStack", "role",
            "sequencePosition", "transpose", "voiceOrEnvelope", "volume");
    private static final Set<String> INACTIVE_TRACK_FIELDS = Set.of("active", "hardware", "role");
    private static final Set<String> YM_FIELDS = Set.of("chip", "port", "register", "value");
    private static final Set<String> PSG_FIELDS = Set.of("chip", "value");
    private static final Set<String> CORE_METADATA_FIELDS = Set.of("type", "schema", "capture", "cycle_start",
            "period", "terminal_record_count", "rom");

    private AudioParityJsonl() {
    }

    /** Reads one record at a time; raw bus and diagnostics are validated as JSON but never retained. */
    public static AudioParityMetadata read(Path path, Consumer<AudioParityTick> tickConsumer) {
        try (BufferedReader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String metadataLine = input.readLine();
            if (metadataLine == null || metadataLine.isBlank()) {
                throw invalid("missing capture metadata line");
            }
            AudioParityMetadata metadata = parseMetadata(metadataLine);
            String line;
            int expectedOrdinal = 0;
            while ((line = input.readLine()) != null) {
                if (line.isBlank()) {
                    throw invalid("blank JSONL record at ordinal " + expectedOrdinal);
                }
                AudioParityTick tick = parseTick(line);
                if (tick.ordinal() != expectedOrdinal) {
                    throw invalid("ordinal continuity failure: expected " + expectedOrdinal
                            + " but found " + tick.ordinal());
                }
                tickConsumer.accept(tick);
                expectedOrdinal++;
            }
            if (expectedOrdinal != metadata.terminalRecordCount()) {
                throw invalid("terminal_record_count is " + metadata.terminalRecordCount()
                        + " but stream contains " + expectedOrdinal + " tick records");
            }
            return metadata;
        } catch (IOException error) {
            throw invalid("cannot read audio parity JSONL: " + error.getMessage(), error);
        }
    }

    /** Writes records directly from the iterator and checks continuity/count while doing so. */
    public static void write(Path path, AudioParityMetadata metadata, Iterator<AudioParityTick> ticks) {
        try {
            Path parent = path.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter output = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                output.write(canonicalJson(metadataTree(metadata)));
                output.newLine();
                int expectedOrdinal = 0;
                while (ticks.hasNext()) {
                    AudioParityTick tick = ticks.next();
                    if (tick.ordinal() != expectedOrdinal) {
                        throw invalid("ordinal continuity failure while writing: expected " + expectedOrdinal
                                + " but found " + tick.ordinal());
                    }
                    output.write(canonicalJson(tickTree(tick)));
                    output.newLine();
                    expectedOrdinal++;
                }
                if (expectedOrdinal != metadata.terminalRecordCount()) {
                    throw invalid("terminal_record_count is " + metadata.terminalRecordCount()
                            + " but writer received " + expectedOrdinal + " tick records");
                }
            }
        } catch (IOException error) {
            throw invalid("cannot write audio parity JSONL: " + error.getMessage(), error);
        }
    }

    public static AudioParityMetadata parseMetadata(String json) {
        ObjectNode root = object(parseTree(json), "metadata");
        exactFields(root, AudioParitySchema.METADATA_FIELDS, CORE_METADATA_FIELDS, "metadata");
        requireText(root, "type", AudioParitySchema.METADATA_TYPE);
        String schema = text(root, "schema");
        String capture = text(root, "capture");
        int cycleStart = integer(root, "cycle_start");
        int period = integer(root, "period");
        int terminal = integer(root, "terminal_record_count");
        ObjectNode rom = object(required(root, "rom"), "rom");
        exactFields(rom, Set.of("sha1", "crc32"), Set.of("sha1", "crc32"), "rom");

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        root.fields().forEachRemaining(entry -> {
            if (!CORE_METADATA_FIELDS.contains(entry.getKey())) {
                details.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
        validateMetadataDetails(details);
        return new AudioParityMetadata(schema, capture, cycleStart, period, terminal,
                text(rom, "sha1"), text(rom, "crc32"), details);
    }

    public static AudioParityTick parseTick(String json) {
        ObjectNode root = parseTickGatingTree(json);
        exactFields(root, TICK_FIELDS, Set.of("type", "ordinal", "state", "events"), "tick");
        requireText(root, "type", AudioParitySchema.TICK_TYPE);
        int ordinal = integer(root, "ordinal");
        ObjectNode state = object(required(root, "state"), "state");
        exactFields(state, STATE_FIELDS, STATE_FIELDS, "state");
        AudioParityTick.GlobalState global = parseGlobal(object(required(state, "global"), "global"));
        ArrayNode trackNodes = array(required(state, "tracks"), "tracks");
        List<AudioParityTrackState> tracks = new ArrayList<>(trackNodes.size());
        trackNodes.forEach(node -> tracks.add(parseTrack(object(node, "track"))));
        ArrayNode eventNodes = array(required(root, "events"), "events");
        List<AudioParityChipWrite> events = new ArrayList<>(eventNodes.size());
        eventNodes.forEach(node -> events.add(parseWrite(object(node, "event"))));
        return new AudioParityTick(ordinal, global, tracks, events);
    }

    private static ObjectNode parseTickGatingTree(String json) {
        try (com.fasterxml.jackson.core.JsonParser parser = FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw invalid("tick must be an object");
            }
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw invalid("tick contains malformed JSON fields");
                }
                String field = parser.currentName();
                parser.nextToken();
                if (field.equals("diagnostic") || field.equals("raw_bus")) {
                    parser.skipChildren();
                    continue;
                }
                root.set(field, JSON.readTree(parser));
            }
            if (parser.nextToken() != null) {
                throw invalid("tick contains trailing JSON content");
            }
            return root;
        } catch (IOException error) {
            throw invalid("malformed or duplicate-field JSON: " + error.getMessage(), error);
        }
    }

    private static void validateMetadataDetails(ObjectNode details) {
        if (details.has("launch_update_music_invocations")) {
            int count = integer(details, "launch_update_music_invocations");
            if (count < 0) {
                throw invalid("launch_update_music_invocations must be non-negative");
            }
        }
        if (details.has("callback_contract")) {
            ObjectNode callback = object(details.get("callback_contract"), "callback_contract");
            Set<String> fields = Set.of("arguments", "proof", "source");
            exactFields(callback, fields, fields, "callback_contract");
            ArrayNode arguments = array(required(callback, "arguments"), "callback_contract.arguments");
            if (arguments.size() != 3 || !arguments.get(0).isTextual() || !arguments.get(1).isTextual()
                    || !arguments.get(2).isTextual() || !arguments.get(0).textValue().equals("address")
                    || !arguments.get(1).textValue().equals("value")
                    || !arguments.get(2).textValue().equals("flags")) {
                throw invalid("callback_contract.arguments must be [address,value,flags]");
            }
            ObjectNode proof = object(required(callback, "proof"), "callback_contract.proof");
            Set<String> proofFields = Set.of("fm_port0_pairs", "fm_port1_pairs", "psg_writes");
            exactFields(proof, proofFields, proofFields, "callback_contract.proof");
            for (String field : proofFields) {
                if (integer(proof, field) < 0) {
                    throw invalid("callback_contract.proof." + field + " must be non-negative");
                }
            }
            String source = text(callback, "source");
            if (!source.equals("memory_callback") && !source.equals("pc_manifest")) {
                throw invalid("callback_contract.source is unsupported");
            }
        }
        validateFieldInventory(details, "diagnostic_fields");
        validateFieldInventory(details, "gating_fields");
        if (details.has("movie")) {
            ObjectNode movie = object(details.get("movie"), "movie");
            Set<String> fields = Set.of("archive_sha256", "core", "emulator", "game", "input_rows",
                    "opaque_header_hash");
            exactFields(movie, fields, fields, "movie");
            String sha256 = text(movie, "archive_sha256");
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw invalid("movie.archive_sha256 must be 64 lowercase hexadecimal characters");
            }
            String opaque = text(movie, "opaque_header_hash");
            if (!opaque.matches("[0-9A-Fa-f]{32}")) {
                throw invalid("movie.opaque_header_hash must be 32 hexadecimal characters");
            }
            text(movie, "core");
            text(movie, "emulator");
            text(movie, "game");
            if (integer(movie, "input_rows") < 0) {
                throw invalid("movie.input_rows must be non-negative");
            }
        }
    }

    private static void validateFieldInventory(ObjectNode details, String field) {
        if (!details.has(field)) {
            return;
        }
        ObjectNode inventory = object(details.get(field), field);
        Set<String> fields = Set.of("global", "track");
        exactFields(inventory, fields, fields, field);
        for (String category : fields) {
            ArrayNode values = array(required(inventory, category), field + "." + category);
            values.forEach(value -> {
                if (!value.isTextual()) {
                    throw invalid(field + "." + category + " entries must be strings");
                }
            });
        }
    }

    /** Parses the shared golden's {events,state} payload without inventing an expected Java string. */
    public static AudioParityTick parseCanonicalPayload(String json, int ordinal) {
        ObjectNode payload = object(parseTree(json), "canonical payload");
        exactFields(payload, Set.of("events", "state"), Set.of("events", "state"), "canonical payload");
        payload.put("type", AudioParitySchema.TICK_TYPE);
        payload.put("ordinal", ordinal);
        return parseTick(payload.toString());
    }

    public static String canonicalGatingJson(AudioParityTick tick) {
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.set("events", eventsTree(tick.events()));
        tree.set("state", stateTree(tick));
        return canonicalJson(tree);
    }

    public static JsonNode metadataTree(AudioParityMetadata metadata) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        JsonNode details = metadata.details();
        details.fields().forEachRemaining(entry -> root.set(entry.getKey(), entry.getValue().deepCopy()));
        root.put("capture", metadata.capture());
        root.put("cycle_start", metadata.cycleStart());
        ObjectNode rom = root.putObject("rom");
        rom.put("crc32", metadata.romCrc32());
        rom.put("sha1", metadata.romSha1());
        root.put("period", metadata.period());
        root.put("schema", metadata.schema());
        root.put("terminal_record_count", metadata.terminalRecordCount());
        root.put("type", AudioParitySchema.METADATA_TYPE);
        return root;
    }

    public static JsonNode tickTree(AudioParityTick tick) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.set("events", eventsTree(tick.events()));
        root.put("ordinal", tick.ordinal());
        root.set("state", stateTree(tick));
        root.put("type", AudioParitySchema.TICK_TYPE);
        return root;
    }

    private static ObjectNode stateTree(AudioParityTick tick) {
        ObjectNode state = JsonNodeFactory.instance.objectNode();
        state.set("global", globalTree(tick.global()));
        ArrayNode tracks = state.putArray("tracks");
        tick.tracks().forEach(track -> tracks.add(trackTree(track)));
        return state;
    }

    private static ObjectNode globalTree(AudioParityTick.GlobalState global) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("fadeActive", global.fadeActive());
        if (global.fadeActive()) {
            node.put("fadeDelay", global.fadeDelay());
        }
        node.put("fadeDirection", global.fadeDirection());
        if (global.fadeActive()) {
            node.put("fadeSteps", global.fadeSteps());
        }
        node.put("speedUp", global.speedUp());
        node.put("tempoReload", global.tempoReload());
        node.put("tempoTimeout", global.tempoTimeout());
        return node;
    }

    private static ObjectNode trackTree(AudioParityTrackState track) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("active", track.active());
        node.put("hardware", track.hardware());
        node.put("role", track.role());
        if (!track.active()) {
            return node;
        }
        node.put("baseFrequency", track.baseFrequency());
        node.put("detune", track.detune());
        node.put("doNotAttack", track.doNotAttack());
        node.put("duration", track.duration());
        node.put("durationReload", track.durationReload());
        if (track.envelopeCursor() != null) {
            node.put("envelopeCursor", track.envelopeCursor());
        }
        if (track.ams() != null) {
            node.put("ams", track.ams());
            node.put("fms", track.fms());
            node.put("pan", track.pan());
        }
        ArrayNode loops = node.putArray("loopCounters");
        track.loopCounters().forEach(loops::add);
        node.put("modulationEnabled", track.modulationEnabled());
        node.put("overridden", track.overridden());
        ArrayNode stack = node.putArray("returnStack");
        track.returnStack().forEach(stack::add);
        node.put("sequencePosition", track.sequencePosition());
        node.put("transpose", track.transpose());
        node.put("voiceOrEnvelope", track.voiceOrEnvelope());
        node.put("volume", track.volume());
        return node;
    }

    private static ArrayNode eventsTree(List<AudioParityChipWrite> events) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (AudioParityChipWrite event : events) {
            ObjectNode node = array.addObject();
            node.put("chip", event.chip());
            if (event.chip().equals("ym2612")) {
                node.put("port", event.port());
                node.put("register", event.register());
            }
            node.put("value", event.value());
        }
        return array;
    }

    private static AudioParityTick.GlobalState parseGlobal(ObjectNode node) {
        boolean active = bool(node, "fadeActive");
        Set<String> required = new HashSet<>(Set.of("fadeActive", "fadeDirection", "speedUp", "tempoReload",
                "tempoTimeout"));
        if (active) {
            required.add("fadeDelay");
            required.add("fadeSteps");
        }
        exactFields(node, GLOBAL_FIELDS, required, "global");
        if (!active && (node.has("fadeDelay") || node.has("fadeSteps"))) {
            throw invalid("inactive fade cannot contain fadeDelay or fadeSteps");
        }
        return new AudioParityTick.GlobalState(active, text(node, "fadeDirection"),
                active ? integer(node, "fadeDelay") : null, active ? integer(node, "fadeSteps") : null,
                bool(node, "speedUp"), integer(node, "tempoReload"), integer(node, "tempoTimeout"));
    }

    private static AudioParityTrackState parseTrack(ObjectNode node) {
        boolean active = bool(node, "active");
        if (!active) {
            exactFields(node, INACTIVE_TRACK_FIELDS, INACTIVE_TRACK_FIELDS, "inactive track");
        } else {
            String role = text(node, "role");
            Set<String> required = new HashSet<>(Set.of("active", "baseFrequency", "detune", "doNotAttack",
                    "duration", "durationReload", "hardware", "loopCounters", "modulationEnabled",
                    "overridden", "returnStack", "role", "sequencePosition", "transpose",
                    "voiceOrEnvelope", "volume"));
            if (role.startsWith("PSG")) {
                required.add("envelopeCursor");
            } else {
                required.addAll(Set.of("pan", "ams", "fms"));
            }
            exactFields(node, TRACK_FIELDS, required, "active track");
        }
        String role = text(node, "role");
        String hardware = text(node, "hardware");
        if (!active) {
            return new AudioParityTrackState(role, hardware, false, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
        }
        return new AudioParityTrackState(role, hardware, true,
                integer(node, "baseFrequency"), integer(node, "detune"), bool(node, "doNotAttack"),
                integer(node, "duration"), integer(node, "durationReload"), nullableInteger(node, "envelopeCursor"),
                integerList(node, "loopCounters"), bool(node, "modulationEnabled"), bool(node, "overridden"),
                nullableInteger(node, "pan"), nullableInteger(node, "ams"), nullableInteger(node, "fms"),
                longList(node, "returnStack"), integer(node, "sequencePosition"), integer(node, "transpose"),
                integer(node, "voiceOrEnvelope"), integer(node, "volume"));
    }

    private static AudioParityChipWrite parseWrite(ObjectNode node) {
        String chip = text(node, "chip");
        if (chip.equals("ym2612")) {
            exactFields(node, YM_FIELDS, YM_FIELDS, "YM event");
            return AudioParityChipWrite.ym2612(integer(node, "port"), integer(node, "register"),
                    integer(node, "value"));
        }
        if (chip.equals("psg")) {
            exactFields(node, PSG_FIELDS, PSG_FIELDS, "PSG event");
            return AudioParityChipWrite.psg(integer(node, "value"));
        }
        throw invalid("unknown chip: " + chip);
    }

    private static List<Integer> integerList(ObjectNode node, String field) {
        ArrayNode array = array(required(node, field), field);
        List<Integer> result = new ArrayList<>(array.size());
        array.forEach(value -> result.add(integral(value, field)));
        return result;
    }

    private static List<Long> longList(ObjectNode node, String field) {
        ArrayNode array = array(required(node, field), field);
        List<Long> result = new ArrayList<>(array.size());
        array.forEach(value -> result.add(longIntegral(value, field)));
        return result;
    }

    private static int integer(ObjectNode node, String field) {
        return integral(required(node, field), field);
    }

    private static Integer nullableInteger(ObjectNode node, String field) {
        return node.has(field) ? integer(node, field) : null;
    }

    private static int integral(JsonNode value, String field) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(field + " must be an integer");
        }
        return value.intValue();
    }

    private static long longIntegral(JsonNode value, String field) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid(field + " must be an integer");
        }
        return value.longValue();
    }

    private static boolean bool(ObjectNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isBoolean()) {
            throw invalid(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isTextual()) {
            throw invalid(field + " must be a string");
        }
        return value.textValue();
    }

    private static void requireText(ObjectNode node, String field, String expected) {
        String actual = text(node, field);
        if (!expected.equals(actual)) {
            throw invalid(field + " must be " + expected + " but was " + actual);
        }
    }

    private static JsonNode required(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw invalid("missing required field " + field);
        }
        return value;
    }

    private static ObjectNode object(JsonNode value, String name) {
        if (!(value instanceof ObjectNode object)) {
            throw invalid(name + " must be an object");
        }
        return object;
    }

    private static ArrayNode array(JsonNode value, String name) {
        if (!(value instanceof ArrayNode array)) {
            throw invalid(name + " must be an array");
        }
        return array;
    }

    private static void exactFields(ObjectNode node, Set<String> allowed, Set<String> required, String name) {
        Set<String> actual = new TreeSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        Set<String> unknown = new TreeSet<>(actual);
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw invalid(name + " contains unknown fields " + unknown);
        }
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw invalid(name + " is missing required fields " + missing);
        }
    }

    private static JsonNode parseTree(String json) {
        try {
            return JSON.readTree(json);
        } catch (IOException error) {
            throw invalid("malformed or duplicate-field JSON: " + error.getMessage(), error);
        }
    }

    private static String canonicalJson(JsonNode node) {
        try {
            return JSON.writeValueAsString(sorted(node));
        } catch (IOException error) {
            throw invalid("cannot encode canonical JSON", error);
        }
    }

    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> sorted.set(name, sorted(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> sorted.add(sorted(value)));
            return sorted;
        }
        return node.deepCopy();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
}

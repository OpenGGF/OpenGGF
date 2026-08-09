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
import java.nio.file.StandardCopyOption;
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
    private static final Set<String> DIAGNOSTIC_FIELDS = Set.of("emulator_frame", "game_mode",
            "interrupt_mask", "invocation_open_frame", "raw_state");
    private static final Set<String> RAW_STATE_FIELDS = Set.of("global", "tracks");
    private static final Set<String> RAW_GLOBAL_FIELDS = Set.of("communication", "fade_in_flag",
            "fade_out_counter", "one_up", "pause", "priority", "push", "queues", "ring_speaker",
            "sound_id", "speed_up_reload", "updating_dac", "voice_selector");
    private static final Set<String> RAW_TRACK_FIELDS = Set.of("ams_fms_pan", "data_position",
            "duration_countdown", "duration_reload", "envelope_cursor", "envelope_or_voice",
            "modulation_delay", "modulation_delta", "modulation_enabled", "modulation_speed",
            "modulation_steps", "modulation_value", "note_fill_countdown", "note_fill_reload",
            "overridden", "resting", "role", "status", "tie_next", "voice_control");
    private static final Set<String> MEMORY_RAW_FIELDS = Set.of("address", "flags", "kind", "port",
            "source", "value");
    private static final Set<String> PC_RAW_FIELDS = Set.of("kind", "pc", "port", "source", "value");
    private static final Set<Integer> FM_MANIFEST_PCS = Set.of(0x7273A, 0x72752, 0x72770, 0x72788);
    private static final Set<Integer> PSG_MANIFEST_PCS = Set.of(0x7225E, 0x72268, 0x723B6, 0x723C0,
            0x7246A, 0x724DC, 0x72912, 0x72918, 0x72984, 0x729AE, 0x729BC, 0x729C0, 0x729C4,
            0x729C8, 0x72DFA, 0x72E16);

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
            String expectedRawBusSource = metadata.capture().equals(AudioParitySchema.REFERENCE_CAPTURE)
                    ? text(object(metadata.details().get("callback_contract"), "callback_contract"), "source")
                    : null;
            String line;
            int expectedOrdinal = 0;
            while ((line = input.readLine()) != null) {
                if (line.isBlank()) {
                    throw invalid("blank JSONL record at ordinal " + expectedOrdinal);
                }
                AudioParityTick tick = parseTick(line, expectedRawBusSource, true);
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
        write(path, metadata, ticks, (source, destination) -> Files.move(source, destination,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static void write(Path path, AudioParityMetadata metadata, Iterator<AudioParityTick> ticks,
            AtomicPublisher publisher) {
        validateMetadataDetails(metadata.capture(), object(metadata.details(), "metadata details"));
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw invalid("audio parity output must have a parent directory");
        }
        String metadataJson = canonicalJson(metadataTree(metadata));
        parseMetadata(metadataJson);
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".audio-parity-", ".jsonl.tmp");
            try (BufferedWriter output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                output.write(metadataJson);
                output.newLine();
                int expectedOrdinal = 0;
                while (ticks.hasNext()) {
                    AudioParityTick tick = ticks.next();
                    if (tick.ordinal() != expectedOrdinal) {
                        throw invalid("ordinal continuity failure while writing: expected " + expectedOrdinal
                                + " but found " + tick.ordinal());
                    }
                    String tickJson = canonicalJson(tickTree(tick));
                    parseTick(tickJson);
                    output.write(tickJson);
                    output.newLine();
                    expectedOrdinal++;
                }
                if (expectedOrdinal != metadata.terminalRecordCount()) {
                    throw invalid("terminal_record_count is " + metadata.terminalRecordCount()
                            + " but writer received " + expectedOrdinal + " tick records");
                }
            }
            publisher.publish(temporary, absolute);
            temporary = null;
        } catch (IOException error) {
            throw invalid("cannot publish audio parity JSONL atomically: " + error.getMessage(), error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Preserve the original validation/publication failure.
                }
            }
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
        validateMetadataDetails(capture, details);
        return new AudioParityMetadata(schema, capture, cycleStart, period, terminal,
                text(rom, "sha1"), text(rom, "crc32"), details);
    }

    /**
     * Parses a standalone tick and validates its structure and ranges. Without a metadata line there is no
     * trusted capture provenance, so this entry point deliberately does not constrain raw-bus source labels.
     * {@link #read(Path, Consumer)} additionally cross-validates every raw event against capture metadata.
     */
    public static AudioParityTick parseTick(String json) {
        return parseTick(json, null, false);
    }

    private static AudioParityTick parseTick(String json, String expectedRawBusSource,
            boolean enforceRawBusProvenance) {
        ObjectNode root = parseTickGatingTree(json, expectedRawBusSource, enforceRawBusProvenance);
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

    private static ObjectNode parseTickGatingTree(String json, String expectedRawBusSource,
            boolean enforceRawBusProvenance) {
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
                if (field.equals("diagnostic")) {
                    validateDiagnostic(object(JSON.readTree(parser), "diagnostic"));
                    continue;
                }
                if (field.equals("raw_bus")) {
                    validateRawBus(parser, expectedRawBusSource, enforceRawBusProvenance);
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

    private static void validateDiagnostic(ObjectNode diagnostic) {
        exactFields(diagnostic, DIAGNOSTIC_FIELDS, DIAGNOSTIC_FIELDS, "diagnostic");
        if (integer(diagnostic, "emulator_frame") < 0 || integer(diagnostic, "invocation_open_frame") < 0) {
            throw invalid("diagnostic frame counters must be non-negative");
        }
        byteValue(integer(diagnostic, "game_mode"), "diagnostic.game_mode");
        range(integer(diagnostic, "interrupt_mask"), 0, 7, "diagnostic.interrupt_mask");
        ObjectNode rawState = object(required(diagnostic, "raw_state"), "diagnostic.raw_state");
        exactFields(rawState, RAW_STATE_FIELDS, RAW_STATE_FIELDS, "diagnostic.raw_state");
        ObjectNode global = object(required(rawState, "global"), "diagnostic.raw_state.global");
        exactFields(global, RAW_GLOBAL_FIELDS, RAW_GLOBAL_FIELDS, "diagnostic.raw_state.global");
        for (String field : RAW_GLOBAL_FIELDS) {
            if (!field.equals("queues")) {
                byteValue(integer(global, field), "diagnostic.raw_state.global." + field);
            }
        }
        ArrayNode queues = array(required(global, "queues"), "diagnostic.raw_state.global.queues");
        if (queues.size() != 3) {
            throw invalid("diagnostic.raw_state.global.queues must contain three bytes");
        }
        queues.forEach(value -> byteValue(integral(value, "queues"), "diagnostic queue"));
        ArrayNode tracks = array(required(rawState, "tracks"), "diagnostic.raw_state.tracks");
        if (tracks.size() != AudioParitySchema.ROLES.size()) {
            throw invalid("diagnostic.raw_state.tracks must contain all ten fixed roles");
        }
        for (int index = 0; index < tracks.size(); index++) {
            validateRawTrack(object(tracks.get(index), "diagnostic raw track"), index);
        }
    }

    private static void validateRawTrack(ObjectNode track, int index) {
        exactFields(track, RAW_TRACK_FIELDS, RAW_TRACK_FIELDS, "diagnostic raw track");
        if (!text(track, "role").equals(AudioParitySchema.ROLES.get(index))) {
            throw invalid("diagnostic raw track role is absent or out of order at index " + index);
        }
        for (String field : List.of("ams_fms_pan", "duration_countdown", "duration_reload",
                "envelope_cursor", "envelope_or_voice", "modulation_delay", "modulation_speed",
                "modulation_steps", "note_fill_countdown", "note_fill_reload", "status", "voice_control")) {
            byteValue(integer(track, field), "diagnostic raw track " + field);
        }
        int position = integer(track, "data_position");
        if (position < -1) {
            throw invalid("diagnostic raw track data_position must be -1 or non-negative");
        }
        range(integer(track, "modulation_delta"), -128, 127, "diagnostic modulation_delta");
        range(integer(track, "modulation_value"), -32768, 32767, "diagnostic modulation_value");
        bool(track, "modulation_enabled");
        bool(track, "overridden");
        bool(track, "resting");
        bool(track, "tie_next");
    }

    private static void validateRawBus(com.fasterxml.jackson.core.JsonParser parser, String expectedSource,
            boolean enforceProvenance) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw invalid("raw_bus must be an array");
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            ObjectNode event = object(JSON.readTree(parser), "raw_bus event");
            validateRawBusEvent(event);
            if (enforceProvenance && !text(event, "source").equals(expectedSource)) {
                String expected = expectedSource == null ? "no raw_bus events" : expectedSource;
                throw invalid("raw_bus source does not match metadata callback_contract.source; expected "
                        + expected);
            }
        }
    }

    private static void validateRawBusEvent(ObjectNode event) {
        String source = text(event, "source");
        String kind = text(event, "kind");
        if (!kind.equals("address") && !kind.equals("data") && !kind.equals("psg")) {
            throw invalid("raw_bus kind is unsupported");
        }
        byteValue(integer(event, "value"), "raw_bus value");
        boolean psg = kind.equals("psg");
        if (source.equals("memory_callback")) {
            Set<String> required = psg ? Set.of("address", "flags", "kind", "source", "value")
                    : MEMORY_RAW_FIELDS;
            exactFields(event, required, required, "memory_callback raw_bus event");
            int address = integer(event, "address");
            if (integer(event, "flags") < 0) {
                throw invalid("raw_bus callback flags must be non-negative");
            }
            if (psg) {
                if (address != 0xC00011) {
                    throw invalid("PSG raw_bus callback address is invalid");
                }
            } else {
                int port = integer(event, "port");
                range(port, 0, 1, "raw_bus port");
                int expected = 0xA04000 + port * 2 + (kind.equals("data") ? 1 : 0);
                if (address != expected) {
                    throw invalid("YM raw_bus callback address does not match kind/port");
                }
            }
            return;
        }
        if (source.equals("pc_manifest")) {
            Set<String> required = psg ? Set.of("kind", "pc", "source", "value") : PC_RAW_FIELDS;
            exactFields(event, required, required, "pc_manifest raw_bus event");
            int pc = integer(event, "pc");
            if (psg) {
                if (!PSG_MANIFEST_PCS.contains(pc)) {
                    throw invalid("PSG raw_bus PC is outside the reviewed manifest");
                }
            } else {
                int port = integer(event, "port");
                range(port, 0, 1, "raw_bus port");
                int expectedPc = kind.equals("address") ? (port == 0 ? 0x7273A : 0x72770)
                        : (port == 0 ? 0x72752 : 0x72788);
                if (pc != expectedPc || !FM_MANIFEST_PCS.contains(pc)) {
                    throw invalid("YM raw_bus PC does not match the reviewed kind/port manifest");
                }
            }
            return;
        }
        throw invalid("raw_bus source is unsupported");
    }

    private static void validateMetadataDetails(String capture, ObjectNode details) {
        if (capture.equals(AudioParitySchema.OPENGGF_CAPTURE)) {
            if (!details.isEmpty()) {
                throw invalid("OpenGGF capture metadata cannot contain reference movie/callback fields");
            }
            return;
        }
        Set<String> referenceFields = Set.of("callback_contract", "diagnostic_fields", "gating_fields",
                "launch_update_music_invocations", "movie");
        exactFields(details, referenceFields, referenceFields, "reference metadata");
        if (integer(details, "launch_update_music_invocations") != 514) {
            throw invalid("reference launch_update_music_invocations must match the pinned BK2 transport");
        }
        validateCallbackContract(object(details.get("callback_contract"), "callback_contract"));
        validateFieldInventory(details, "diagnostic_fields", AudioParitySchema.DIAGNOSTIC_GLOBAL_FIELDS,
                AudioParitySchema.DIAGNOSTIC_TRACK_FIELDS);
        validateFieldInventory(details, "gating_fields", AudioParitySchema.GATING_GLOBAL_FIELDS,
                AudioParitySchema.GATING_TRACK_FIELDS);
        ObjectNode movie = object(details.get("movie"), "movie");
        Set<String> fields = Set.of("archive_sha256", "core", "emulator", "game", "input_rows",
                "opaque_header_hash");
        exactFields(movie, fields, fields, "movie");
        if (!text(movie, "archive_sha256").equals(AudioParitySchema.BK2_SHA256)
                || !text(movie, "core").equals(AudioParitySchema.BK2_CORE)
                || !text(movie, "emulator").equals(AudioParitySchema.BK2_EMULATOR)
                || !text(movie, "game").equals(AudioParitySchema.BK2_GAME)
                || integer(movie, "input_rows") != AudioParitySchema.BK2_INPUT_ROWS
                || !text(movie, "opaque_header_hash").equals(AudioParitySchema.BK2_OPAQUE_HASH)) {
            throw invalid("reference movie metadata does not match the pinned S1 GHZ BK2");
        }
    }

    private static void validateCallbackContract(ObjectNode callback) {
        String source = text(callback, "source");
        if (source.equals("memory_callback")) {
            Set<String> fields = Set.of("arguments", "proof", "source");
            exactFields(callback, fields, fields, "callback_contract");
            ArrayNode arguments = array(required(callback, "arguments"), "callback_contract.arguments");
            if (!textArray(arguments).equals(List.of("address", "value", "flags"))) {
                throw invalid("callback_contract.arguments must be [address,value,flags]");
            }
            ObjectNode proof = object(required(callback, "proof"), "callback_contract.proof");
            Set<String> proofFields = Set.of("fm_port0_pairs", "fm_port1_pairs", "psg_writes");
            exactFields(proof, proofFields, proofFields, "callback_contract.proof");
            for (String field : proofFields) {
                if (integer(proof, field) <= 0) {
                    throw invalid("memory_callback proof counts must all be positive");
                }
            }
            return;
        }
        if (source.equals("pc_manifest")) {
            Set<String> fields = Set.of("manifest_sites", "source");
            exactFields(callback, fields, fields, "callback_contract");
            if (integer(callback, "manifest_sites") != 20) {
                throw invalid("pc_manifest requires the complete 20-site reviewed manifest");
            }
            return;
        }
        throw invalid("callback_contract.source is unsupported");
    }

    private static void validateFieldInventory(ObjectNode details, String field, List<String> expectedGlobal,
            List<String> expectedTrack) {
        ObjectNode inventory = object(details.get(field), field);
        Set<String> fields = Set.of("global", "track");
        exactFields(inventory, fields, fields, field);
        if (!textArray(array(required(inventory, "global"), field + ".global")).equals(expectedGlobal)
                || !textArray(array(required(inventory, "track"), field + ".track")).equals(expectedTrack)) {
            throw invalid(field + " does not match the versioned field inventory");
        }
    }

    private static List<String> textArray(ArrayNode array) {
        List<String> result = new ArrayList<>(array.size());
        array.forEach(value -> {
            if (!value.isTextual()) {
                throw invalid("metadata inventory entries must be strings");
            }
            result.add(value.textValue());
        });
        return result;
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

    private static void byteValue(int value, String field) {
        range(value, 0, 0xff, field);
    }

    private static void range(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw invalid(field + " is out of range");
        }
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
        try (com.fasterxml.jackson.core.JsonParser parser = FACTORY.createParser(json)) {
            JsonNode root = JSON.readTree(parser);
            if (root == null) {
                throw invalid("missing root JSON value");
            }
            if (parser.nextToken() != null) {
                throw invalid("JSON contains more than one root value");
            }
            return root;
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

    @FunctionalInterface
    interface AtomicPublisher {
        void publish(Path source, Path destination) throws IOException;
    }
}

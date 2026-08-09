package com.openggf.tools.audio.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestAudioParityJsonl {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path GOLDEN = Path.of("src", "test", "resources", "audio", "parity", "s1",
            "normalization-contract-v1.json");

    @TempDir
    Path temp;

    @Test
    void javaSchemaReproducesSharedLuaCanonicalBytes() throws Exception {
        // Break caught: Java's gating schema or key ordering drifts from the shared Lua contract.
        JsonNode vector = JSON.readTree(Files.readString(GOLDEN));
        String expected = vector.required("expectedCanonicalJson").textValue();
        AudioParityTick tick = AudioParityJsonl.parseCanonicalPayload(expected, 0);

        assertEquals(expected, AudioParityJsonl.canonicalGatingJson(tick));
    }

    @Test
    void streamsTicksWithoutRetainingRawBusAndWritesDeterministically() throws Exception {
        // Break caught: the JSONL reader buffers the stream/raw bus or output depends on map insertion order.
        AudioParityTick tick = goldenTick();
        AudioParityMetadata metadata = validMetadata();
        Path first = temp.resolve("first.jsonl");
        Path second = temp.resolve("second.jsonl");
        AudioParityJsonl.write(first, metadata, List.of(tick, tick.withOrdinal(1)).iterator());
        AudioParityJsonl.write(second, metadata, List.of(tick, tick.withOrdinal(1)).iterator());
        assertEquals(Files.readString(first), Files.readString(second));

        List<AudioParityTick> observed = new ArrayList<>();
        AudioParityMetadata parsed = AudioParityJsonl.read(first, observed::add);
        assertEquals(metadata, parsed);
        assertEquals(List.of(tick, tick.withOrdinal(1)), observed);
        assertTrue(Files.readString(first).lines().allMatch(line -> !line.contains("raw_bus")));
    }

    @Test
    void rejectsUnknownSchemaAndUnknownOrMissingFields() throws Exception {
        // Break caught: incompatible or incomplete streams are silently accepted.
        ObjectNode metadata = metadataJson();
        metadata.put("schema", "openggf.s1_audio_parity_reference.v99");
        assertInvalidStream(metadata, tickJson(goldenTick()), "schema");

        ObjectNode unknown = metadataJson();
        unknown.put("surprise", true);
        assertInvalidStream(unknown, tickJson(goldenTick()), "unknown");

        ObjectNode missing = metadataJson();
        missing.remove("period");
        assertInvalidStream(missing, tickJson(goldenTick()), "period");

        ObjectNode badTick = tickJson(goldenTick());
        badTick.withObject("state").remove("global");
        assertInvalidStream(metadataJson(), badTick, "global");
    }

    @Test
    void validatesNestedLuaReferenceMetadataWithoutAcceptingUnknownFields() throws Exception {
        // Break caught: callback/movie provenance changes shape while the top-level metadata still looks valid.
        ObjectNode valid = metadataJson();
        valid.put("launch_update_music_invocations", 514);
        ObjectNode callback = valid.putObject("callback_contract");
        callback.putArray("arguments").add("address").add("value").add("flags");
        callback.putObject("proof").put("fm_port0_pairs", 2).put("fm_port1_pairs", 1).put("psg_writes", 3);
        callback.put("source", "memory_callback");
        ObjectNode diagnostic = valid.putObject("diagnostic_fields");
        diagnostic.putArray("global").add("pause");
        diagnostic.putArray("track").add("resting");
        ObjectNode gating = valid.putObject("gating_fields");
        gating.putArray("global").add("tempo timeout");
        gating.putArray("track").add("active");
        addValidMovie(valid);
        valid.put("terminal_record_count", 1);
        Path validStream = temp.resolve("valid-lua-metadata.jsonl");
        Files.writeString(validStream, valid + "\n" + tickJson(goldenTick()) + "\n");
        assertEquals(1, AudioParityJsonl.read(validStream, ignored -> { }).terminalRecordCount());

        ObjectNode invalid = valid.deepCopy();
        invalid.withObject("callback_contract").put("unreviewed", true);
        assertInvalidStream(invalid, tickJson(goldenTick()), "unknown");
    }

    @Test
    void rejectsDuplicateJsonFields() throws Exception {
        // Break caught: a later duplicate silently overrides a validated identity or tick value.
        Path stream = temp.resolve("duplicates.jsonl");
        String metadata = AudioParityJsonl.metadataTree(validMetadata()).toString();
        String duplicate = tickJson(goldenTick()).toString().replaceFirst("\\{", "{\\\"ordinal\\\":0,");
        Files.writeString(stream, metadata + "\n" + duplicate + "\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AudioParityJsonl.read(stream, ignored -> { }));
        assertTrue(error.getMessage().toLowerCase().contains("duplicate"), error::getMessage);
    }

    @Test
    void rejectsMissingDuplicateAndOutOfOrderOrdinalsWhileStreaming() throws Exception {
        // Break caught: records can be dropped, repeated, or reordered without capture-integrity failure.
        AudioParityTick tick = goldenTick();
        assertInvalidStream(metadataJson(), List.of(tickJson(tick), tickJson(tick)), "ordinal");
        assertInvalidStream(metadataJson(), List.of(tickJson(tick), tickJson(tick.withOrdinal(2))), "ordinal");
        ObjectNode startsAtOne = tickJson(tick.withOrdinal(1));
        assertInvalidStream(metadataJson(), startsAtOne, "ordinal");
    }

    @Test
    void validatesEveryChipByteAndYmShape() throws Exception {
        // Break caught: malformed decoded writes enter the parity comparison boundary.
        ObjectNode ymValue = tickJson(goldenTick());
        ((ObjectNode) ymValue.withArray("events").get(0)).put("value", 256);
        assertInvalidStream(metadataJson(), ymValue, "value");

        ObjectNode ymPort = tickJson(goldenTick());
        ((ObjectNode) ymPort.withArray("events").get(0)).put("port", 2);
        assertInvalidStream(metadataJson(), ymPort, "port");

        ObjectNode psgRegister = tickJson(goldenTick());
        ((ObjectNode) psgRegister.withArray("events").get(2)).put("register", 7);
        assertInvalidStream(metadataJson(), psgRegister, "unknown");

        ObjectNode psgValue = tickJson(goldenTick());
        ((ObjectNode) psgValue.withArray("events").get(2)).put("value", -1);
        assertInvalidStream(metadataJson(), psgValue, "value");
    }

    @Test
    void validatesFixedRolesAndAllConditionalTrackRanges() throws Exception {
        // Break caught: absent/reordered roles, inactive stale gates, or invalid active bytes pass validation.
        ObjectNode absent = tickJson(goldenTick());
        absent.withObject("state").withArray("tracks").remove(9);
        assertInvalidStream(metadataJson(), absent, "ten");

        ObjectNode wrongRole = tickJson(goldenTick());
        ((ObjectNode) wrongRole.withObject("state").withArray("tracks").get(1)).put("role", "FM2");
        assertInvalidStream(metadataJson(), wrongRole, "role");

        ObjectNode extraInactive = tickJson(goldenTick());
        ((ObjectNode) extraInactive.withObject("state").withArray("tracks").get(0)).put("duration", 0);
        assertInvalidStream(metadataJson(), extraInactive, "inactive");

        ObjectNode badSigned = tickJson(goldenTick());
        ((ObjectNode) badSigned.withObject("state").withArray("tracks").get(1)).put("detune", 128);
        assertInvalidStream(metadataJson(), badSigned, "detune");

        ObjectNode badByte = tickJson(goldenTick());
        ((ObjectNode) badByte.withObject("state").withArray("tracks").get(7)).put("envelopeCursor", 256);
        assertInvalidStream(metadataJson(), badByte, "envelopeCursor");

        ObjectNode missingPsgCursor = tickJson(goldenTick());
        ((ObjectNode) missingPsgCursor.withObject("state").withArray("tracks").get(7))
                .remove("envelopeCursor");
        assertInvalidStream(metadataJson(), missingPsgCursor, "envelopeCursor");

        ObjectNode badLoop = tickJson(goldenTick());
        ((ObjectNode) badLoop.withObject("state").withArray("tracks").get(1))
                .withArray("loopCounters").set(0, JSON.getNodeFactory().numberNode(256));
        assertInvalidStream(metadataJson(), badLoop, "loopCounters");

        ObjectNode badStack = tickJson(goldenTick());
        ((ObjectNode) badStack.withObject("state").withArray("tracks").get(1))
                .withArray("returnStack").set(0, JSON.getNodeFactory().numberNode(-1));
        assertInvalidStream(metadataJson(), badStack, "returnStack");

        ObjectNode badFrequency = tickJson(goldenTick());
        ((ObjectNode) badFrequency.withObject("state").withArray("tracks").get(1))
                .put("baseFrequency", 65536);
        assertInvalidStream(metadataJson(), badFrequency, "baseFrequency");

        ObjectNode badPan = tickJson(goldenTick());
        ((ObjectNode) badPan.withObject("state").withArray("tracks").get(1)).put("pan", 193);
        assertInvalidStream(metadataJson(), badPan, "pan");
    }

    @Test
    void activeFadeFieldsAreConditionalAndStrict() throws Exception {
        // Break caught: fade-only fields gate inactive state or disappear during an active fade.
        ObjectNode inactiveExtra = tickJson(goldenTick());
        inactiveExtra.withObject("state").withObject("global").put("fadeDelay", 0);
        assertInvalidStream(metadataJson(), inactiveExtra, "fade");

        ObjectNode activeMissing = tickJson(goldenTick());
        ObjectNode global = activeMissing.withObject("state").withObject("global");
        global.put("fadeActive", true).put("fadeDirection", "out");
        assertInvalidStream(metadataJson(), activeMissing, "fadeDelay");
    }

    @Test
    void diagnosticsDoNotParticipateInGatingEquality() throws Exception {
        // Break caught: host frame/raw state diagnostics turn a semantic match into a mismatch.
        ObjectNode firstJson = tickJson(goldenTick());
        firstJson.putObject("diagnostic").put("emulator_frame", 824);
        firstJson.putArray("raw_bus").addObject().put("kind", "psg").put("value", 159);
        ObjectNode secondJson = tickJson(goldenTick());
        secondJson.putObject("diagnostic").put("emulator_frame", 9999).put("host", "other");
        secondJson.putArray("raw_bus").addObject().put("anything", "ignored");

        AudioParityTick first = AudioParityJsonl.parseTick(firstJson.toString());
        AudioParityTick second = AudioParityJsonl.parseTick(secondJson.toString());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(AudioParityJsonl.canonicalGatingJson(first), AudioParityJsonl.canonicalGatingJson(second));
    }

    @Test
    void rejectsHostPathsAndTimestampsAnywhereInMetadata() throws Exception {
        // Break caught: normalized stream identity varies by host path or capture time.
        ObjectNode path = metadataJson();
        addValidMovie(path).put("game", "/opt/audio-fixtures/sonic.gen");
        assertInvalidStream(path, tickJson(goldenTick()), "absolute path");

        ObjectNode timestamp = metadataJson();
        addValidMovie(timestamp).put("emulator", "2026-08-09T12:34:56Z");
        assertInvalidStream(timestamp, tickJson(goldenTick()), "timestamp");

        assertThrows(IllegalArgumentException.class,
                () -> AudioParityMetadata.reference("/tmp/input.gen", 0, 1, 1,
                        "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b", "afe05eee"));
    }

    private AudioParityTick goldenTick() throws Exception {
        JsonNode vector = JSON.readTree(Files.readString(GOLDEN));
        return AudioParityJsonl.parseCanonicalPayload(vector.required("expectedCanonicalJson").textValue(), 0);
    }

    private AudioParityMetadata validMetadata() {
        return AudioParityMetadata.reference("s1_ghz_music_driver_reference", 0, 1, 2,
                "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b", "afe05eee");
    }

    private ObjectNode metadataJson() {
        return (ObjectNode) AudioParityJsonl.metadataTree(validMetadata());
    }

    private ObjectNode tickJson(AudioParityTick tick) {
        return (ObjectNode) AudioParityJsonl.tickTree(tick);
    }

    private ObjectNode addValidMovie(ObjectNode metadata) {
        ObjectNode movie = metadata.putObject("movie");
        movie.put("archive_sha256", "622ff642d0b0835a4f77bee568f2413f288ead3306a8bc2a93e8d8f77f24ca9c");
        movie.put("core", "Genplus-gx");
        movie.put("emulator", "Version 2.11");
        movie.put("game", "Sonic The Hedgehog (W) (REV01) [!]");
        movie.put("input_rows", 989);
        movie.put("opaque_header_hash", "09DADB5071EB35050067A32462E39C5F");
        return movie;
    }

    private void assertInvalidStream(ObjectNode metadata, ObjectNode tick, String message) throws Exception {
        assertInvalidStream(metadata, List.of(tick), message);
    }

    private void assertInvalidStream(ObjectNode metadata, List<ObjectNode> ticks, String message) throws Exception {
        Path stream = temp.resolve("invalid-" + System.nanoTime() + ".jsonl");
        StringBuilder content = new StringBuilder(metadata.toString()).append('\n');
        ticks.forEach(tick -> content.append(tick).append('\n'));
        Files.writeString(stream, content, StandardCharsets.UTF_8);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AudioParityJsonl.read(stream, ignored -> { }));
        assertTrue(error.getMessage().toLowerCase().contains(message.toLowerCase()), error::getMessage);
    }
}

package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHardwareTimingStreamLoader {

    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyFixtureWithoutKeyOrFileLoadsEmptySchedule() throws IOException {
        Path fixture = writeFixture(6, null, null, 2);

        HardwareTimingSchedule schedule = TraceData.load(fixture).hardwareTimingSchedule();

        assertTrue(schedule.edges().isEmpty());
    }

    @Test
    void toolingOnlyMeasurementArtifactIsRejectedFromTraceDirectory()
            throws IOException {
        Path fixture = writeFixture(6, null, null, 2);
        Files.writeString(
                fixture.resolve("load_time_measurements.jsonl"), "{}\n");

        assertRejected(
                fixture,
                "load_time_measurements.jsonl",
                "tooling-only");
    }

    @Test
    void versionOneRequiresHardwareTimingFile() throws IOException {
        Path fixture = writeFixture(7, 1, null, 2);

        assertRejected(fixture, "hardware_timing.jsonl", "hardware_timing_schema");
    }

    @Test
    void fileWithoutMetadataKeyFails() throws IOException {
        Path fixture = writeFixture(6, null, edge(0, "vint_service", 0), 2);

        assertRejected(fixture, "hardware_timing.jsonl", "hardware_timing_schema");
    }

    @Test
    void unknownSchemaFails() throws IOException {
        Path fixture = writeFixture(7, 3, "", 2);

        assertRejected(fixture, "metadata.json", "hardware_timing_schema");
    }

    @Test
    void metadataTimingSchemaMustBeJsonIntegerOneOrTwo() throws IOException {
        for (String token : List.of("\"1\"", "1.0", "true", "{}", "[]", "null")) {
            Path fixture = writeFixtureWithMetadataToken(7, token, "", 2);

            assertRejected(fixture, "metadata.json", "JSON integer 1");
        }
    }

    @Test
    void schemaOneRejectsDirectKindButSchemaTwoAcceptsBothKinds() throws IOException {
        String direct = edge(0, "pre_main_loop", "kos_decompression_queue", 0);
        Path schemaOne = writeFixture(7, 1, direct + "\n", 2);
        assertRejected(schemaOne, "hardware_timing.jsonl", "not authorized");

        Path schemaTwo = writeFixture(7, 2, direct + "\n"
                + edge(1, "post_objects", "kos_module_queue", 0) + "\n", 2);
        HardwareTimingSchedule schedule = TraceData.load(schemaTwo).hardwareTimingSchedule();

        assertEquals(2, schedule.edges().size());
        assertEquals(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,
                schedule.edges().getFirst().kind());
        assertEquals(HardwareWorkKind.KOS_MODULE_QUEUE,
                schedule.edges().get(1).kind());
    }

    @Test
    void schemaTwoDirectEdgesRequireThePreMainLoopBoundary() throws IOException {
        for (String boundary : List.of("vint_service", "post_objects")) {
            Path fixture = writeFixture(7, 2,
                    edge(0, boundary, "kos_decompression_queue", 0) + "\n", 2);

            assertRejected(fixture, "hardware_timing.jsonl", "pre_main_loop");
        }
    }

    @Test
    void loaderCanonicalOrderInstallsIntoReplayForMixedKindsAtOneBoundary()
            throws IOException {
        Path fixture = writeFixture(7, 2,
                edge(0, "pre_main_loop", "kos_module_queue", 0) + "\n"
                        + edge(0, "pre_main_loop", "kos_decompression_queue", 0) + "\n", 2);

        HardwareTimingSchedule schedule = TraceData.load(fixture).hardwareTimingSchedule();
        var authority = new com.openggf.game.timing.HardwareTimingService()
                .beginRecordedAdmission();
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(authority);

        port.install(schedule);
    }

    @Test
    void malformedOrUnknownEventFails() throws IOException {
        Path unknownEvent = writeFixture(7, 1,
                "{\"event\":\"hardware_work_pending\",\"raw_frame\":0,"
                        + "\"boundary\":\"vint_service\",\"kind\":\"kos_module_queue\","
                        + "\"ordinal\":0,\"submission_fingerprint\":\"" + fingerprint() + "\"}\n", 2);
        assertRejected(unknownEvent, "hardware_timing.jsonl", "event");

        Path unknownField = writeFixture(7, 1,
                edge(0, "vint_service", 0).replace("}", ",\"extra\":true}") + "\n", 2);
        assertRejected(unknownField, "hardware_timing.jsonl", "extra");
    }

    @Test
    void jsonlRejectsMalformedAmbiguousAndDuplicateObjects() throws IOException {
        Path malformed = writeFixture(7, 1, "{\"event\":\n", 2);
        assertRejected(malformed, "hardware_timing.jsonl", "malformed JSON");

        Path duplicate = writeFixture(7, 1,
                edge(0, "vint_service", 0).replace("\"raw_frame\":0",
                        "\"raw_frame\":0,\"raw_frame\":0") + "\n", 2);
        assertRejected(duplicate, "hardware_timing.jsonl", "duplicate");

        Path trailing = writeFixture(7, 1, edge(0, "vint_service", 0) + " {}\n", 2);
        assertRejected(trailing, "hardware_timing.jsonl", "trailing JSON");
    }

    @Test
    void jsonlRejectsInvalidWireValuesAndFingerprint() throws IOException {
        Path uppercaseBoundary = writeFixture(7, 1,
                edge(0, "VINT_SERVICE", 0) + "\n", 2);
        assertRejected(uppercaseBoundary, "hardware_timing.jsonl", "boundary");

        Path uppercaseKind = writeFixture(7, 1,
                edge(0, "vint_service", 0).replace("kos_module_queue", "KOS_MODULE_QUEUE") + "\n", 2);
        assertRejected(uppercaseKind, "hardware_timing.jsonl", "kind");

        Path badFingerprint = writeFixture(7, 1,
                edge(0, "vint_service", 0).replace(fingerprint(), "sha256:" + "A".repeat(64)) + "\n", 2);
        assertRejected(badFingerprint, "hardware_timing.jsonl", "submission_fingerprint");
    }

    @Test
    void jsonlRejectsInvalidUtf8AndNonLfFraming() throws IOException {
        Path invalidUtf8 = writeFixture(7, 1, null, 2);
        Files.write(invalidUtf8.resolve("hardware_timing.jsonl"), new byte[] {(byte) 0xC3, (byte) 0x28});
        assertRejected(invalidUtf8, "hardware_timing.jsonl", "UTF-8");

        Path carriageReturn = writeFixture(7, 1, edge(0, "vint_service", 0) + "\r\n", 2);
        assertRejected(carriageReturn, "hardware_timing.jsonl", "LF-terminated");

        Path unterminated = writeFixture(7, 1, edge(0, "vint_service", 0), 2);
        assertRejected(unterminated, "hardware_timing.jsonl", "LF-terminated");
    }

    @Test
    void eventsMustUseCanonicalOrdering() throws IOException {
        Path fixture = writeFixture(7, 1,
                edge(1, "vint_service", 0) + "\n" + edge(0, "post_objects", 1) + "\n", 2);

        assertRejected(fixture, "hardware_timing.jsonl", "canonical ordering");
    }

    @Test
    void duplicateIdentityFails() throws IOException {
        String edge = edge(0, "vint_service", 0);
        Path fixture = writeFixture(7, 1, edge + "\n" + edge + "\n", 2);

        assertRejected(fixture, "hardware_timing.jsonl", "duplicate");
    }

    @Test
    void emptyVersionOneStreamIsValid() throws IOException {
        Path fixture = writeFixture(7, 1, "", 2);

        assertTrue(TraceData.load(fixture).hardwareTimingSchedule().edges().isEmpty());
    }

    @Test
    void schemaOneRequiresTraceSchemaSeven() throws IOException {
        Path fixture = writeFixture(6, 1, "", 2);

        assertRejected(fixture, "metadata.json", "trace_schema");
    }

    @Test
    void traceSchemaSevenRequiresKeyAndFile() throws IOException {
        Path withoutKey = writeFixture(7, null, null, 2);
        assertRejected(withoutKey, "metadata.json", "hardware_timing_schema");

        Path withoutFile = writeFixture(7, 1, null, 2);
        assertRejected(withoutFile, "hardware_timing.jsonl", "hardware_timing_schema");
    }

    @Test
    void legacySchemaRejectsHardwareTimingSchema() throws IOException {
        Path fixture = writeFixture(6, 1, "", 2);

        assertRejected(fixture, "metadata.json", "trace_schema");
    }

    @Test
    void rawFrameMustBeWithinTraceFrameCount() throws IOException {
        Path fixture = writeFixture(7, 1, edge(2, "vint_service", 0) + "\n", 2);

        assertRejected(fixture, "hardware_timing.jsonl", "raw_frame");
    }

    @Test
    void negativeRawFrameFails() throws IOException {
        Path fixture = writeFixture(7, 1, edge(-1, "vint_service", 0) + "\n", 2);

        assertRejected(fixture, "hardware_timing.jsonl", "raw_frame");
    }

    @Test
    void ordinalsAreMonotonicPerKind() throws IOException {
        Path fixture = writeFixture(7, 1,
                edge(0, "vint_service", 2) + "\n" + edge(1, "vint_service", 1) + "\n", 2);

        assertRejected(fixture, "hardware_timing.jsonl", "ordinal");
    }

    @Test
    void loadsCanonicalEdgesByFrameAndBoundary() throws IOException {
        Path fixture = writeFixture(7, 1,
                edge(0, "vint_service", 0) + "\n" + edge(1, "post_objects", 1) + "\n", 2);

        HardwareTimingSchedule schedule = TraceData.load(fixture).hardwareTimingSchedule();

        assertEquals(2, schedule.edges().size());
        assertEquals(List.of(schedule.edges().get(0)),
                schedule.edgesAt(0, HardwareServiceBoundary.VINT_SERVICE));
        assertEquals(HardwareWorkKind.KOS_MODULE_QUEUE, schedule.edges().get(1).kind());
    }

    private Path writeFixture(int traceSchema, Integer hardwareTimingSchema,
                              String hardwareTiming, int frameCount) throws IOException {
        return writeFixtureWithMetadataToken(traceSchema,
                hardwareTimingSchema == null ? null : hardwareTimingSchema.toString(),
                hardwareTiming, frameCount);
    }

    private Path writeFixtureWithMetadataToken(int traceSchema, String hardwareTimingSchemaToken,
                                               String hardwareTiming, int frameCount) throws IOException {
        Path fixture = Files.createTempDirectory(temporaryDirectory, "fixture-");
        String timingProperty = hardwareTimingSchemaToken == null
                ? ""
                : ",\n  \"hardware_timing_schema\": " + hardwareTimingSchemaToken;
        Files.writeString(fixture.resolve("metadata.json"), """
                {
                  "game": "s3k",
                  "zone": "test",
                  "zone_id": 0,
                  "act": 1,
                  "bk2_frame_offset": 0,
                  "trace_frame_count": %d,
                  "start_x": "0x0000",
                  "start_y": "0x0000",
                  "trace_schema": %d,
                  "csv_version": 1%s
                }
                """.formatted(frameCount, traceSchema, timingProperty));
        Files.writeString(fixture.resolve("physics.csv"), """
                frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode
                0000,0000,0000,0000,0000,0000,0000,00,0,0,0
                0001,0000,0000,0000,0000,0000,0000,00,0,0,0
                """);
        if (hardwareTiming != null) {
            Files.writeString(fixture.resolve("hardware_timing.jsonl"), hardwareTiming);
        }
        return fixture;
    }

    private void assertRejected(Path fixture, String filename, String rejectedField) {
        Exception error = assertThrows(Exception.class, () -> TraceData.load(fixture));
        assertTrue(error.getMessage().contains(filename), error::getMessage);
        assertTrue(error.getMessage().contains(rejectedField), error::getMessage);
    }

    private static String edge(int rawFrame, String boundary, long ordinal) {
        return edge(rawFrame, boundary, "kos_module_queue", ordinal);
    }

    private static String edge(int rawFrame, String boundary, String kind, long ordinal) {
        return "{\"event\":\"hardware_work_completed\",\"raw_frame\":" + rawFrame
                + ",\"boundary\":\"" + boundary + "\",\"kind\":\"" + kind + "\","
                + "\"ordinal\":" + ordinal + ",\"submission_fingerprint\":\""
                + fingerprint() + "\"}";
    }

    private static String fingerprint() {
        return "sha256:" + "a".repeat(64);
    }
}

package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.trace.TraceReportWriter;
import com.openggf.tools.TraceTriageTool.Bootstrap;
import com.openggf.tools.TraceTriageTool.Divergence;
import com.openggf.tools.TraceTriageTool.Subsystem;
import com.openggf.tools.TraceTriageTool.TraceReport;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.BootstrapDivergence;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceVerificationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TraceTriageTool}. Exercises both published producer
 * reports and a synthetic divergence catalogue. No ROM, OpenGL, or trace replay.
 */
class TestTraceTriageTool {

    /**
     * Synthetic report matching the real schema: error_count / warning_count /
     * total_frames / summary / bootstrap[] / errors[] / warnings[]. The first
     * error group starts at frame 375 on field {@code tails_air} (a sidekick
     * field), mirroring the documented s2_mtz1 sample.
     */
    private static final String SYNTHETIC_REPORT = """
            {
              "error_count" : 2,
              "warning_count" : 1,
              "total_frames" : 10133,
              "summary" : "2 errors, 1 warning. First error: frame 375 -- tails_air mismatch (expected=1, actual=0)",
              "bootstrap" : [ {
                "field" : "player_history.pos",
                "severity" : "ERROR",
                "expected" : "0x0068",
                "actual" : "0x0019",
                "context" : "ROM and engine ring-buffer positions disagree"
              }, {
                "field" : "tails_cpu.engine",
                "severity" : "WARNING",
                "expected" : "present",
                "actual" : "missing",
                "context" : "engine has no sidekick CPU view; cannot compare against ROM"
              } ],
              "errors" : [ {
                "field" : "tails_air",
                "severity" : "ERROR",
                "start_frame" : 375,
                "end_frame" : 382,
                "frame_span" : 8,
                "expected_at_start" : "1",
                "actual_at_start" : "0",
                "cascading" : false
              }, {
                "field" : "tails_x",
                "severity" : "ERROR",
                "start_frame" : 375,
                "end_frame" : 453,
                "frame_span" : 79,
                "expected_at_start" : "0x4000",
                "actual_at_start" : "0x02BC",
                "cascading" : false
              } ],
              "warnings" : [ {
                "field" : "camera_x",
                "severity" : "WARNING",
                "start_frame" : 500,
                "end_frame" : 501,
                "frame_span" : 2,
                "expected_at_start" : "0x020C",
                "actual_at_start" : "0x0210",
                "cascading" : false
              } ]
            }
            """;

    @Test
    void parsesTopLevelCountsAndArrays() throws Exception {
        TraceReport report = TraceTriageTool.parseReport(SYNTHETIC_REPORT);
        assertEquals(2, report.errorCount());
        assertEquals(1, report.warningCount());
        assertEquals(10133, report.totalFrames());
        assertEquals(2, report.bootstrap().size());
        assertEquals(2, report.errors().size());
        assertEquals(1, report.warnings().size());
    }

    @Test
    void readsPublishedDivergenceAndItsContext(@TempDir Path tempDir) throws Exception {
        DivergenceReport produced = new DivergenceReport(List.of(new FrameComparison(7,
                Map.of("x", new FieldComparison(
                        "x", "0x0100", "0x0101", Severity.ERROR, 1)),
                "routine=02 object=0x41", "routine=04 object=0x41")));
        Path reportPath = publishReport(tempDir, produced, true);

        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(reportPath));
        TriageResult result = runTriage("--report", reportPath.toString());
        assertEquals(0, result.status(), result.errors());
        String brief = result.output();

        assertEquals(1, parsed.errorCount());
        assertEquals(1, parsed.totalFrames());
        assertEquals(7, parsed.firstDivergence().startFrame());
        assertEquals("0x0100", parsed.firstDivergence().expectedAtStart());
        assertEquals("0x0101", parsed.firstDivergence().actualAtStart());
        assertTrue(brief.contains("Replay completion: COMPLETE"));
        assertFalse(brief.contains("Comparison coverage: absent"));
        assertTrue(brief.contains("ROM: routine=02 object=0x41"));
        assertTrue(brief.contains("ENG: routine=04 object=0x41"));
    }

    @Test
    void publishedCleanPrefixIsReportedAsIncomplete(@TempDir Path tempDir) throws Exception {
        DivergenceReport cleanPrefix = new DivergenceReport(List.of(new FrameComparison(0,
                Map.of("x", new FieldComparison(
                        "x", "0x0100", "0x0100", Severity.MATCH, 0)))));
        Path reportPath = publishReport(tempDir, cleanPrefix, false);
        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(reportPath));

        String brief = TraceTriageTool.renderBrief("s2", "ehz1", parsed, "", "");

        assertEquals(0, parsed.errorCount());
        assertEquals(1, parsed.totalFrames());
        assertTrue(brief.toLowerCase(Locale.ROOT).contains("incomplete"),
                "A matching prefix whose published replay gate was not reached must be visibly incomplete.");
        TriageResult result = runTriage("--report", reportPath.toString());
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Replay completion: INCOMPLETE"));
        assertFalse(result.output().contains("Comparison coverage: absent"));
    }

    @ParameterizedTest
    @EnumSource(TraceVerificationScope.class)
    void declaredGateCompletionDoesNotDependOnComparisonCount(
            TraceVerificationScope scope, @TempDir Path tempDir) throws Exception {
        String scopeLabel = scope.name().toLowerCase(Locale.ROOT);
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true, scope);
        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(reportPath));

        assertEquals(0, parsed.totalFrames());
        assertTrue(parsed.replayGate().comparisonGateReached());
        assertEquals(10, parsed.replayGate().plannedTraceRows());
        TriageResult result = runTriage("--report", reportPath.toString());
        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
        assertTrue(result.output().contains("10 planned trace rows; " + scopeLabel + " verification"));
        assertTrue(result.output().contains("Totals: 0 errors, 0 warnings, 0 frames compared"));
        assertTrue(result.output().contains("Comparison coverage: absent (0 frames compared)"));
        assertTrue(result.output().contains("Recorded comparison fields: none retained."));
        assertTrue(result.output().contains("gameplay comparison coverage is not established"));
        assertFalse(result.output().contains("Replay completion: INCOMPLETE"),
                "No compared frames must not override the driver's completed gate.");
        assertTrue(result.output().contains("check this invocation's Surefire result and process completion"));
    }

    @Test
    void currentProducerWithoutReplayGateHasUnknownCompletion(@TempDir Path tempDir) throws Exception {
        Path reportPath = tempDir.resolve("report_without_gate.json");
        Files.writeString(reportPath, new DivergenceReport(List.of()).toJson());
        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(reportPath));

        assertNull(parsed.replayGate());
        TriageResult result = runTriage("--report", reportPath.toString());
        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("Replay completion: UNKNOWN (no replay_gate published)"));
        assertFalse(result.output().contains("legacy report"),
                "Current report producers also legitimately omit the optional gate.");
        assertTrue(result.output().contains("Comparison coverage: absent (0 frames compared)"));
        assertFalse(result.output().contains("Replay completion: INCOMPLETE"));
    }

    @ParameterizedTest
    @EnumSource(TraceVerificationScope.class)
    void recordedFieldsDescribeRetainedMapKeysAcrossEveryResultAndScope(
            TraceVerificationScope scope, @TempDir Path tempDir) throws Exception {
        DivergenceReport report = new DivergenceReport(List.of(
                new FrameComparison(0, Map.of(
                        "z_match", new FieldComparison("z_match", "1", "1", Severity.MATCH, 0),
                        "warning_field", new FieldComparison("warning_field", "1", "2", Severity.WARNING, 1),
                        "animation_field", FieldComparison.animation("animation_field", "1", "2", Severity.ERROR, 1),
                        "tolerated", new FieldComparison("tolerated", "1", "2", Severity.MATCH, 1, true))),
                new FrameComparison(1, Map.of(
                        "z_match", new FieldComparison("z_match", "1", "1", Severity.MATCH, 0),
                        "map_key", new FieldComparison("different_internal_name", "1", "1", Severity.MATCH, 0))),
                new FrameComparison(2, Map.of())), null, List.of(new BootstrapDivergence(
                        "bootstrap_only", BootstrapDivergence.Severity.ERROR, "1", "2", "prelude")));
        Path reportPath = publishReport(tempDir, report, true, scope);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(reportPath.toFile());

        assertEquals(mapper.readTree("[\"animation_field\",\"map_key\",\"tolerated\",\"warning_field\",\"z_match\"]"),
                payload.get("recorded_fields"), "Record all retained result keys, including MATCH, once in canonical order");
        assertEquals(3, payload.path("total_frames").intValue());
        assertEquals(2, payload.path("error_count").intValue());
        assertEquals(1, payload.path("warning_count").intValue());
        TriageResult result = runTriage("--report", reportPath.toString());
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Recorded comparison fields (all verification groups): "
                + "animation_field, map_key, tolerated, warning_field, z_match"));
        assertTrue(result.output().contains("Per-frame results only; listed fields may be exempt MATCHes; bootstrap is excluded. Not a census of executed comparators or full reference coverage."));
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
    }

    @ParameterizedTest
    @EnumSource(TraceVerificationScope.class)
    void aRetainedEmptyRowDoesNotInventFieldsOrClaimBootstrapCoverage(
            TraceVerificationScope scope, @TempDir Path tempDir) throws Exception {
        DivergenceReport report = new DivergenceReport(List.of(new FrameComparison(0, Map.of())),
                null, List.of(new BootstrapDivergence("bootstrap_only", BootstrapDivergence.Severity.ERROR,
                        "1", "2", "prelude")));
        Path path = publishReport(tempDir, report, true, scope);
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(path.toFile());
        assertEquals(new ObjectMapper().readTree("[]"), payload.get("recorded_fields"));
        TriageResult result = runTriage("--report", path.toString());
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Totals: 1 errors, 0 warnings, 1 frames compared"));
        assertTrue(result.output().contains("Recorded comparison fields: none retained."));
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
    }

    @Test
    void oldReportsHaveUnknownRecordedFieldsRatherThanAnEmptyCensus(@TempDir Path tempDir)
            throws Exception {
        Path path = tempDir.resolve("old-report.json");
        Files.writeString(path, SYNTHETIC_REPORT);
        TriageResult result = runTriage("--report", path.toString());
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Recorded comparison fields: not reported (unknown)."));
        assertFalse(result.output().contains("Recorded comparison fields: none retained."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "42", "false", "[null]", "[7]", "[\"\"]",
            "[\" \"]", "[\"x\",\"x\"]", "[\"x\",false]", "[\"x\"]"})
    void malformedRecordedFieldsFailBeforeAnyBrief(String literal, @TempDir Path tempDir)
            throws Exception {
        // Keep only the zero-row contradiction on an empty report. Other
        // invalid names must fail their own rule, not the zero-row rule.
        List<FrameComparison> rows = literal.equals("[\"x\"]") ? List.of()
                : List.of(new FrameComparison(0, Map.of()));
        Path path = publishReport(tempDir, new DivergenceReport(rows), true);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(path.toFile());
        payload.set("recorded_fields", mapper.readTree(literal));
        Files.writeString(path, payload.toString());
        TriageResult result = runTriage("--report", path.toString());
        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.errors().contains("recorded_fields"));
    }

    @Test
    void recordedFieldReaderAcceptsUnknownNamesAndPreservesLiteralEvidence(@TempDir Path tempDir)
            throws Exception {
        DivergenceReport report = new DivergenceReport(List.of(new FrameComparison(0, Map.of(
                "z.future", new FieldComparison("z.future", "1", "1", Severity.MATCH, 0),
                "a.future", new FieldComparison("a.future", "1", "1", Severity.MATCH, 0)))));
        Path path = publishReport(tempDir, report, false);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(path.toFile());
        payload.set("recorded_fields", mapper.readTree("[\"z.future\",\"a.future\"]"));
        Files.writeString(path, payload.toString());
        TriageResult result = runTriage("--report", path.toString());
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Recorded comparison fields (all verification groups): z.future, a.future"));
        assertTrue(result.output().contains("Replay completion: INCOMPLETE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"errors", "warnings"})
    void recordedFieldsCannotOmitPublishedDivergenceGroups(String omitted, @TempDir Path tempDir)
            throws Exception {
        DivergenceReport report = new DivergenceReport(List.of(new FrameComparison(0, Map.of(
                "error_field", new FieldComparison("error_field", "1", "2", Severity.ERROR, 1),
                "warning_field", new FieldComparison("warning_field", "1", "2", Severity.WARNING, 1)))));
        Path path = publishReport(tempDir, report, true);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(path.toFile());
        payload.set("recorded_fields", mapper.createArrayNode().add(
                omitted.equals("errors") ? "warning_field" : "error_field"));
        Files.writeString(path, payload.toString());
        TriageResult result = runTriage("--report", path.toString());
        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.errors().contains("recorded_fields omits published field"));
    }

    @Test
    void callerCannotEraseRecordedFieldsAfterModelValidation() {
        var fields = new ArrayList<>(List.of("x"));
        TraceReport report = new TraceReport(0, 0, 1, "matching row", List.of(), List.of(), List.of(),
                null, null, List.of(), fields);
        fields.clear();
        assertTrue(TraceTriageTool.renderBrief("s2", "ehz1", report, "", "")
                .contains("Recorded comparison fields (all verification groups): x"));
        assertThrows(UnsupportedOperationException.class, () -> report.recordedFields().add("late"));
    }

    @ParameterizedTest
    @CsvSource({"true, false, COMPLETE", "false, false, INCOMPLETE", "true, true, COMPLETE"})
    void advertisedAuxAvailabilityLimitsReferencesWithoutChangingGateOrMismatchTotals(
            boolean completed, boolean hasAuxEvidence, String completion, @TempDir Path tempDir)
            throws Exception {
        TraceMetadata metadata = new ObjectMapper().readValue("""
                {
                  "game": "s2", "zone": "ehz", "zone_id": 0, "act": 1,
                  "trace_schema": 5, "trace_frame_count": 10,
                  "start_x": "0x0080", "start_y": "0x03A0",
                  "aux_schema_extras": ["velocity_write_per_frame", "position_write_per_frame"]
                }
                """, TraceMetadata.class);
        Map<Integer, List<TraceEvent>> events = hasAuxEvidence
                ? Map.of(7, List.of(
                        new TraceEvent.VelocityWrite(7, "sonic", List.of(), List.of()),
                        new TraceEvent.PositionWrite(7, "sonic", List.of(), List.of())))
                : Map.of();
        DivergenceReport produced = new DivergenceReport(List.of(new FrameComparison(7, Map.of(
                "x", new FieldComparison("x", "0x0100", "0x0101", Severity.ERROR, 1),
                "camera_x", new FieldComparison("camera_x", "0x0200", "0x0201", Severity.WARNING, 1)))),
                TraceFixtures.trace(metadata, List.of(), events));
        Path reportPath = publishReport(tempDir, produced, completed);

        TriageResult result = runTriage("--report", reportPath.toString());

        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("Replay completion: " + completion + " (10 planned trace rows"));
        assertTrue(result.output().contains("Totals: 1 errors, 1 warnings, 1 frames compared"));
        assertTrue(result.output().contains("FIRST DIVERGENCE"));
        assertTrue(result.output().contains("frame    : 7"));
        assertFalse(result.output().contains("Comparison coverage: absent"));
        if (hasAuxEvidence) {
            assertFalse(result.output().contains("Reference/coverage limits:"));
        } else {
            assertTrue(result.output().contains(
                    "Reference/coverage limits: missing advertised aux schemas: "
                            + "velocity_write_per_frame, position_write_per_frame"));
            assertTrue(result.output().contains("replay gate and mismatch totals remain as published"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "\"unknown optional shape\"", "[null, 7, true]"})
    void unrecognizedOrEmptyOptionalAuxLimitsDoNotInvalidateReport(
            String optionalJson, @TempDir Path tempDir) throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(reportPath.toFile());
        payload.set("missing_advertised_aux_schemas", mapper.readTree(optionalJson));
        Files.writeString(reportPath, payload.toString());

        TriageResult result = runTriage("--report", reportPath.toString());

        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
        assertTrue(result.output().contains("Totals: 0 errors, 0 warnings, 0 frames compared"));
        assertFalse(result.output().contains("Reference/coverage limits:"));
    }

    @Test
    void rejectsProducerSerializationErrorDocument() {
        // DivergenceReport.toJson() returns this document shape on serialization failure.
        assertThrows(IOException.class,
                () -> TraceTriageTool.parseReport(
                        "{\"error\":\"Failed to serialise report: producer failure\"}"),
                "A producer error document must not become a zero-error matching trace.");
    }

    @Test
    void rejectsPublishedReportWithMissingComparisonCount(@TempDir Path tempDir) throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(reportPath.toFile());
        payload.remove("error_count");

        assertThrows(IOException.class,
                () -> TraceTriageTool.parseReport(payload.toString()),
                "A missing comparison count must not be replaced with a clean zero.");
    }

    @Test
    void rejectsMalformedGateInsteadOfTreatingItAsLegacy(@TempDir Path tempDir) throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(reportPath.toFile());
        ((ObjectNode) payload.get("replay_gate")).put("comparison_gate_reached", "true");

        assertThrows(IOException.class, () -> TraceTriageTool.parseReport(payload.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "{\"schema\":2,\"comparison_gate_reached\":true,\"planned_trace_rows\":10,\"verification\":\"all\"}",
            "{\"schema\":1,\"comparison_gate_reached\":true,\"planned_trace_rows\":0,\"verification\":\"all\"}",
            "{\"schema\":1,\"comparison_gate_reached\":true,\"planned_trace_rows\":10,\"verification\":\"both\"}",
            "{\"schema\":1,\"comparison_gate_reached\":true,\"planned_trace_rows\":10,\"verification\":\"\"}"
    })
    void malformedPresentGateFailsCliInsteadOfLookingLikeLegacy(String gateJson, @TempDir Path tempDir)
            throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(reportPath.toFile());
        payload.set("replay_gate", mapper.readTree(gateJson));
        Files.writeString(reportPath, payload.toString());

        TriageResult result = runTriage("--report", reportPath.toString());

        assertEquals(1, result.status());
        assertEquals("", result.output(), "malformed completion evidence must not produce a reassuring brief");
        assertTrue(result.errors().contains("Failed to read diagnostic evidence"));
    }

    @Test
    void extraGateEvidenceDoesNotPreventCliDiagnosis(@TempDir Path tempDir) throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(reportPath.toFile());
        ((ObjectNode) payload.get("replay_gate")).put("future_observation", "retained by another reader");
        Files.writeString(reportPath, payload.toString());

        TriageResult result = runTriage("--report", reportPath.toString());

        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "schema | \"1\"",
            "schema | 1.0",
            "schema | -1",
            "schema | 2147483648",
            "schema | null",
            "planned_trace_rows | \"10\"",
            "planned_trace_rows | 10.5",
            "planned_trace_rows | -1",
            "planned_trace_rows | 2147483648",
            "planned_trace_rows | null",
            "comparison_gate_reached | \"true\"",
            "comparison_gate_reached | 1",
            "verification | \"ALL\"",
            "verification | \" all\"",
            "verification | \"all \"",
            "verification | null"
    })
    void gateWireTypesAndCanonicalScopeCannotBeCoerced(
            String field, String jsonValue, @TempDir Path tempDir) throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(reportPath.toFile());
        ((ObjectNode) payload.get("replay_gate")).set(field, mapper.readTree(jsonValue));
        Files.writeString(reportPath, payload.toString());

        TriageResult result = runTriage("--report", reportPath.toString());

        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.errors().contains("Failed to read diagnostic evidence"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"schema", "planned_trace_rows", "comparison_gate_reached", "verification"})
    void missingGateFieldsCannotDefaultToCompletedEvidence(String field, @TempDir Path tempDir)
            throws Exception {
        Path reportPath = publishReport(tempDir, new DivergenceReport(List.of()), true);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(reportPath.toFile());
        ((ObjectNode) payload.get("replay_gate")).remove(field);
        Files.writeString(reportPath, payload.toString());

        TriageResult result = runTriage("--report", reportPath.toString());

        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.errors().contains("Failed to read diagnostic evidence"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bootstrap", "errors", "warnings"})
    void rejectsMalformedDivergenceArrays(String field) throws Exception {
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(new DivergenceReport(List.of()).toJson());
        payload.putObject(field);

        assertThrows(IOException.class, () -> TraceTriageTool.parseReport(payload.toString()));
    }

    @Test
    void rejectsStringComparisonCount() throws Exception {
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(new DivergenceReport(List.of()).toJson());
        payload.put("error_count", "0");

        assertThrows(IOException.class, () -> TraceTriageTool.parseReport(payload.toString()));
    }

    private Path publishReport(Path directory, DivergenceReport report, boolean completed)
            throws IOException {
        return publishReport(directory, report, completed, TraceVerificationScope.ALL);
    }

    private Path publishReport(Path directory, DivergenceReport report, boolean completed,
                               TraceVerificationScope scope) throws IOException {
        SessionInvocation invocation = new SessionInvocation(
                getClass().getName(), "publishedReport", 0, "0123456789abcdef", "published report");
        return TraceReportWriter.writeReport(directory, report, "trace", invocation,
                "single", "s2_ehz1", scope, 2,
                new ReplayGate(10, completed, scope))
                .physicalPath();
    }

    @Test
    void firstDivergenceIsEarliestError() throws Exception {
        TraceReport report = TraceTriageTool.parseReport(SYNTHETIC_REPORT);
        Divergence first = report.firstDivergence();
        assertNotNull(first);
        assertEquals(375, first.startFrame());
        assertEquals("tails_air", first.field());
        assertEquals("1", first.expectedAtStart());
        assertEquals("0", first.actualAtStart());
        assertEquals(8, first.frameSpan());
        assertFalse(first.cascading());
    }

    @Test
    void firstBootstrapErrorPicksErrorNotWarning() throws Exception {
        TraceReport report = TraceTriageTool.parseReport(SYNTHETIC_REPORT);
        Bootstrap boot = report.firstBootstrapError();
        assertNotNull(boot);
        assertEquals("player_history.pos", boot.field());
        assertEquals("ERROR", boot.severity());
        assertEquals("0x0068", boot.expected());
        assertEquals("0x0019", boot.actual());
    }

    @ParameterizedTest
    @ValueSource(strings = {"routine", "player_routine", "x_sub", "y_sub"})
    void canonicalPlayerStateStaysWithPlayerPhysics(String field) {
        assertEquals(Subsystem.PLAYER_PHYSICS, TraceTriageTool.classifySubsystem(field, false));
    }

    @ParameterizedTest
    @CsvSource({
            "s1, Sonic_Control, Obj01",
            "s2, Obj01, Sonic_Control",
            "s3k, Sonic_Control, Obj01"
    })
    void nativePlayerHintsUseTheReportedGame(String game, String expected, String foreign)
            throws Exception {
        String brief = TraceTriageTool.renderBrief(game, "zone", hintReport("x"), "", "");

        assertTrue(brief.contains("  - " + expected + "\n"));
        assertFalse(brief.contains("  - " + foreign + "\n"));
    }

    @Test
    void unknownGameDoesNotBorrowAnotherGamesNativeHints() throws Exception {
        String brief = TraceTriageTool.renderBrief("custom", "zone", hintReport("x"), "", "");

        assertFalse(brief.contains("  - Sonic_Move\n"));
        assertFalse(brief.contains("  - Obj01\n"));
        assertTrue(brief.contains("  - x\n"), "the recorded field remains a generic search term");
    }

    @Test
    void sonic3kAnimationHintsUseItsNativeVocabulary() throws Exception {
        String brief = TraceTriageTool.renderBrief(
                "s3k", "zone", hintReport("player_animation_id"), "", "");

        assertTrue(brief.contains("  - Animate_Sprite\n"));
        assertFalse(brief.contains("  - AnimateSprite\n"));
    }

    @Test
    void sonic3kSolidHintsUseItsNativeVocabulary() throws Exception {
        String brief = TraceTriageTool.renderBrief(
                "s3k", "zone", hintReport("object_slot[5].routine"), "", "");

        assertTrue(brief.contains("  - SolidObjectFull\n"));
        assertFalse(brief.contains("  - SolidObject\n"));
    }

    @ParameterizedTest
    @CsvSource({
            "s1, rings, HurtSonic",
            "s3k, camera_y, ScreenEvents",
            "s1, layout, v_lvllayout",
            "s3k, layout, Level_layout_main"
    })
    void nativeHintsReachTheBehaviorOrMutableStateOwner(String game, String field, String owner)
            throws Exception {
        String brief = TraceTriageTool.renderBrief(game, "zone", hintReport(field), "", "");

        assertTrue(brief.contains("  - " + owner + "\n"));
    }

    private static TraceReport hintReport(String field) throws IOException {
        FieldComparison comparison = field.equals("player_animation_id")
                ? FieldComparison.animation(field, "0x01", "0x02", Severity.ERROR, 1)
                : new FieldComparison(field, "0x01", "0x02", Severity.ERROR, 1);
        DivergenceReport report = new DivergenceReport(List.of(
                new FrameComparison(7, Map.of(field, comparison))));
        return TraceTriageTool.parseReport(report.toJson());
    }

    @Test
    void classifiesSidekickField() {
        assertEquals(Subsystem.SIDEKICK, TraceTriageTool.classifySubsystem("tails_air", false));
        assertEquals(Subsystem.SIDEKICK, TraceTriageTool.classifySubsystem("tails_x", false));
        assertEquals(Subsystem.SIDEKICK, TraceTriageTool.classifySubsystem("tails_routine", false));
    }

    @Test
    void classifiesPlayerAndSidekickAnimationBeforeSidekickPhysics() {
        assertEquals(Subsystem.PLAYABLE_ANIMATION,
                TraceTriageTool.classifySubsystem("player_animation_id", false));
        assertEquals(Subsystem.PLAYABLE_ANIMATION,
                TraceTriageTool.classifySubsystem("tails_mapping_frame", false));
    }

    @Test
    void classifiesPlayerPhysicsFields() {
        assertEquals(Subsystem.PLAYER_PHYSICS, TraceTriageTool.classifySubsystem("x", false));
        assertEquals(Subsystem.PLAYER_PHYSICS, TraceTriageTool.classifySubsystem("y_speed", false));
        assertEquals(Subsystem.PLAYER_PHYSICS, TraceTriageTool.classifySubsystem("angle", false));
        assertEquals(Subsystem.PLAYER_PHYSICS, TraceTriageTool.classifySubsystem("ground_mode", false));
    }

    @Test
    void classifiesEventAndObjectAndPaletteFields() {
        assertEquals(Subsystem.EVENT, TraceTriageTool.classifySubsystem("camera_x", false));
        assertEquals(Subsystem.OBJECT_SOLID, TraceTriageTool.classifySubsystem("object_slot[5].routine", false));
        assertEquals(Subsystem.PALETTE, TraceTriageTool.classifySubsystem("palette_line2", false));
        assertEquals(Subsystem.ART_PLC, TraceTriageTool.classifySubsystem("plc_queue", false));
        assertEquals(Subsystem.LAYOUT_MUTATION, TraceTriageTool.classifySubsystem("tilemap_chunk", false));
    }

    @Test
    void classifiesBootstrapRingBufferAsTestBootstrap() {
        assertEquals(Subsystem.TEST_BOOTSTRAP,
                TraceTriageTool.classifySubsystem("player_history.pos", true));
    }

    @Test
    void suggestsTestsAlwaysIncludeComparisonOnlyGuard() {
        var tests = TraceTriageTool.suggestFocusedTests(Subsystem.PLAYER_PHYSICS);
        assertTrue(tests.stream().anyMatch(t -> t.contains("TestTraceReplayInvariantGuard")),
                "every suggestion set must remind about the comparison-only guard");
        assertTrue(tests.contains("TestPhysicsProfile"));
    }

    @Test
    void frontierUpdateNeededWhenErrorsPresent() throws Exception {
        TraceReport report = TraceTriageTool.parseReport(SYNTHETIC_REPORT);
        assertTrue(TraceTriageTool.frontierLogLikelyNeedsUpdate(report));
    }

    @Test
    void cleanReportHasNoFirstDivergenceAndNoFrontierUpdate() throws Exception {
        String clean = """
                {
                  "error_count" : 0,
                  "warning_count" : 0,
                  "total_frames" : 5000,
                  "summary" : "All frames match trace. No divergences.",
                  "bootstrap" : [ ],
                  "errors" : [ ],
                  "warnings" : [ ]
                }
                """;
        TraceReport report = TraceTriageTool.parseReport(clean);
        assertNull(report.firstDivergence());
        assertNull(report.firstBootstrapError());
        assertFalse(TraceTriageTool.frontierLogLikelyNeedsUpdate(report));
    }

    @Test
    void renderBriefContainsFirstDivergenceOwnerAndComparisonOnlyWarning() throws Exception {
        TraceReport report = TraceTriageTool.parseReport(SYNTHETIC_REPORT);
        String brief = TraceTriageTool.renderBrief("s2", "mtz1", report, "", "");

        assertTrue(brief.contains("FIRST DIVERGENCE"), "brief should label first divergence");
        assertTrue(brief.contains("frame    : 375"), "brief should show the first divergent frame");
        assertTrue(brief.contains("tails_air"), "brief should show the divergent field");
        assertTrue(brief.contains("sidekick"), "brief should name the owning subsystem");
        assertTrue(brief.contains("COMPARISON-ONLY"), "brief must warn trace is comparison-only");
        assertTrue(brief.contains("must reproduce ROM behaviour"),
                "brief must say engine reproduces ROM behaviour natively");
        assertTrue(brief.contains("docs/status/trace-frontier-log.md"),
                "brief must mention the frontier log");
        assertTrue(brief.contains("YES"), "frontier update should read YES when errors present");
    }

    @Test
    void extractsContextLinesNearFrame() {
        String contextText = """
                === Per-frame ===
                374    | 0x02AC   | 0x02AC
                375    | 0x4000   |*0x02BC
                       ROM: sub=(B400,0300) rtn=02
                       ENG: sub=(B400,0300) rtn=02
                900    | 0x0500   | 0x0500
                """;
        String excerpt = TraceTriageTool.extractContextNearFrame(contextText, 375, 2, 12);
        assertTrue(excerpt.contains("375"), "should include the focus frame");
        assertTrue(excerpt.contains("374"), "should include nearby frame within radius");
        assertTrue(excerpt.contains("ROM:"), "should keep ROM diagnostic line");
        assertFalse(excerpt.contains("900"), "should exclude far frame");
    }

    @Test
    void extractsAuxLinesNearFrame() {
        String aux = """
                {"frame":0,"event":"state_snapshot","routine":"0x02"}
                {"frame":374,"event":"object_near","slot":32,"type":"0x25","x":"0x0144"}
                {"frame":376,"event":"object_near","slot":32,"type":"0x25","x":"0x0146"}
                {"frame":900,"event":"object_near","slot":40}
                """;
        String excerpt = TraceTriageTool.extractAuxNearFrame(aux, 375, 3, 12);
        assertTrue(excerpt.contains("\"frame\":374"));
        assertTrue(excerpt.contains("\"frame\":376"));
        assertFalse(excerpt.contains("\"frame\":900"));
        assertFalse(excerpt.contains("\"frame\":0,"));
    }

    @Test
    void crowdedPrecedingRowDoesNotHideActualAuxFocus() {
        StringBuilder aux = new StringBuilder();
        for (int slot = 0; slot < 30; slot++) {
            aux.append("{\"frame\":374,\"event\":\"object_state_snapshot\",\"slot\":")
                    .append(slot).append("}\n");
        }
        aux.append("{\"frame\":375,\"event\":\"velocity_write\",\"routine\":\"0x1234\"}\n");
        aux.append("{\"frame\":900,\"event\":\"object_removed\"}\n");

        String excerpt = TraceTriageTool.extractAuxNearFrame(aux.toString(), 375, 3, 4);

        assertEquals(4, excerpt.lines().count());
        assertTrue(excerpt.lines().findFirst().orElseThrow().contains("\"frame\":375"));
        assertTrue(excerpt.contains("0x1234"));
        assertFalse(excerpt.contains("\"frame\":900"));
    }

    @Test
    void auxWindowRetainsRecordedBootstrapRow() {
        String excerpt = TraceTriageTool.extractAuxNearFrame("""
                {"frame":-1,"event":"cpu_state_snapshot","cpu_routine":6}
                {"frame":0,"event":"zone_act_state","actual_zone_id":1}
                """, 0, 1, 4);

        assertTrue(excerpt.contains("\"frame\":-1"));
        assertTrue(excerpt.lines().findFirst().orElseThrow().contains("\"frame\":0"));
    }

    @Test
    void bootstrapBriefPrioritizesPreTraceAuxOverCrowdedFrameZero(@TempDir Path tempDir) throws Exception {
        DivergenceReport bootstrap = new DivergenceReport(List.of(), null, List.of(
                new BootstrapDivergence("object_slot[37].routine", BootstrapDivergence.Severity.ERROR,
                        "02", "04", "pre-trace object state")));
        Path report = publishReport(tempDir, bootstrap, false);
        StringBuilder aux = new StringBuilder();
        for (int slot = 0; slot < 40; slot++) {
            aux.append("{\"frame\":0,\"event\":\"object_state_snapshot\",\"slot\":")
                    .append(slot).append("}\n");
        }
        aux.append("{\"frame\":-1,\"event\":\"object_state_snapshot\",\"slot\":37}\n");
        Path auxFile = tempDir.resolve("aux_state.jsonl");
        Files.writeString(auxFile, aux);
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();

        int status = TraceTriageTool.run(new String[]{"--report", report.toString(), "--aux", auxFile.toString()},
                new PrintStream(output), new PrintStream(errors));

        assertEquals(0, status, errors.toString(StandardCharsets.UTF_8));
        String brief = output.toString(StandardCharsets.UTF_8);
        assertTrue(brief.contains("\"frame\":-1"),
                "bootstrap object evidence must survive a crowded first gameplay row");
        assertTrue(brief.indexOf("\"frame\":-1") < brief.indexOf("\"frame\":0"),
                "pre-trace evidence is the auxiliary focus for a bootstrap error");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitReportFindsPublishedContextAndFixtureAux(boolean compressed, @TempDir Path tempDir)
            throws Exception {
        Path fixture = tempDir.resolve("fixture with spaces");
        Files.createDirectories(fixture);
        Files.copy(Path.of("src/test/resources/traces/s2/ehz1_fullrun/metadata.json"),
                fixture.resolve("metadata.json"));
        String aux = "{\"frame\":7,\"event\":\"velocity_write\",\"routine\":\"0x1234\"}\n";
        if (compressed) {
            try (var stream = new GZIPOutputStream(Files.newOutputStream(fixture.resolve("aux_state.jsonl.gz")))) {
                stream.write(aux.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            Files.writeString(fixture.resolve("aux_state.jsonl"), aux);
        }
        DivergenceReport produced = new DivergenceReport(List.of(new FrameComparison(7,
                Map.of("x", new FieldComparison("x", "0x0100", "0x0101", Severity.ERROR, 1)),
                "routine=02 object=0x41", "routine=04 object=0x41")));
        Path report = publishReport(tempDir.resolve("reports"), produced, true);

        TriageResult result = runTriage("--report", report.toString(), "--fixture", fixture.toString());

        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("Game/Zone: s2 / ehz1"));
        assertTrue(result.output().contains("ROM: routine=02 object=0x41"));
        assertTrue(result.output().contains("ENG: routine=04 object=0x41"));
        assertTrue(result.output().contains("0x1234"));
    }

    @Test
    void positionalGameAndZoneStillReadLegacyReport(@TempDir Path tempDir) throws Exception {
        publishReport(tempDir, new DivergenceReport(List.of()), true);

        TriageResult result = runTriage("s2", "ehz1", "--dir", tempDir.toString());

        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Game/Zone: s2 / ehz1"));
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
    }

    @Test
    void missingReportAndMissingOptionValueReturnFailure(@TempDir Path tempDir) {
        TriageResult missingReport = runTriage("--report", tempDir.resolve("absent.json").toString());
        assertEquals(1, missingReport.status());
        assertTrue(missingReport.errors().contains("Report not found"));
        assertEquals("", missingReport.output());

        TriageResult missingValue = runTriage("--report", "--fixture", tempDir.toString());
        assertEquals(2, missingValue.status());
        assertTrue(missingValue.errors().contains("Missing value for --report"));
    }

    private record TriageResult(int status, String output, String errors) {
    }

    private static TriageResult runTriage(String... arguments) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
             var err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            int status = TraceTriageTool.run(arguments, out, err);
            return new TriageResult(status, output.toString(StandardCharsets.UTF_8),
                    errors.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void endToEndFromTempFile(@TempDir Path tempDir) throws Exception {
        // Fixture written under @TempDir, never into src/test/resources/traces.
        Path reportFile = tempDir.resolve("s2_mtz1_report.json");
        Files.writeString(reportFile, SYNTHETIC_REPORT, StandardCharsets.UTF_8);

        String json = Files.readString(reportFile, StandardCharsets.UTF_8);
        TraceReport report = TraceTriageTool.parseReport(json);
        String brief = TraceTriageTool.renderBrief("s2", "mtz1", report, "", "");

        assertTrue(brief.contains("frame    : 375"));
        assertTrue(brief.contains("sidekick"));
        assertTrue(brief.contains("BOOTSTRAP (frame 0) ERROR present"),
                "bootstrap error should surface ahead of per-frame divergence");
    }

    @Test
    void defaultAndExplicitReportPathsRemainDistinct() {
        String property = System.getProperty("openggf.trace.reports");
        try {
            System.setProperty("openggf.trace.reports", "/session/trace reports");
            assertEquals("/session/trace reports",
                    TraceTriageTool.configuredReportDirectory());
            assertEquals(Path.of("/session/trace reports/s2_mtz1_report.json"),
                    TraceTriageTool.defaultReportPath(
                            TraceTriageTool.configuredReportDirectory(), "s2", "mtz1"));
            assertEquals(Path.of("/caller/report.json"),
                    TraceTriageTool.resolveReportPath(
                            "/caller/report.json", "/ignored", "s2", "mtz1"),
                    "--report remains an explicit caller-owned path");
            assertEquals(Path.of("/session/trace reports/s2_mtz1_context.txt"),
                    TraceTriageTool.contextPathForReport(
                            Path.of("/session/trace reports/s2_mtz1_report.json")));
        } finally {
            if (property == null) {
                System.clearProperty("openggf.trace.reports");
            } else {
                System.setProperty("openggf.trace.reports", property);
            }
        }
    }

    @Test
    void defaultTriageDiscoversOneSessionReport(@TempDir Path tempDir) throws Exception {
        Path traceDirectory = tempDir.resolve("trace");
        Files.createDirectories(traceDirectory);
        Path report = traceDirectory.resolve("trace/s2_mtz1_physics-single-0123456789abcdef.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, SYNTHETIC_REPORT, StandardCharsets.UTF_8);

        assertEquals(report, TraceTriageTool.resolveDefaultReportPath(
                tempDir.toString(), "s2", "mtz1"));
    }

    @Test
    void defaultTriageDiscoversSpecialStageReportProfile(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve(
                "special-stage/s2_special_stage_0-s2-0-0123456789abcdef.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, SYNTHETIC_REPORT, StandardCharsets.UTF_8);

        assertEquals(report, TraceTriageTool.resolveDefaultReportPath(
                tempDir.toString(), "s2", "special_stage_0"));
    }

    @Test
    void sessionReportTakesPrecedenceOverStaleLegacyReport(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("s2_mtz1_report.json");
        Files.writeString(legacy, SYNTHETIC_REPORT, StandardCharsets.UTF_8);
        Path sessionReport = tempDir.resolve(
                "trace/s2_mtz1-single-0123456789abcdef.json");
        Files.createDirectories(sessionReport.getParent());
        Files.writeString(sessionReport, SYNTHETIC_REPORT, StandardCharsets.UTF_8);

        assertEquals(sessionReport, TraceTriageTool.resolveDefaultReportPath(
                tempDir.toString(), "s2", "mtz1"));
    }

    @Test
    void positionalLookupRefusesMultipleReportsEvenWithLegacyFallback(@TempDir Path tempDir)
            throws Exception {
        Files.writeString(tempDir.resolve("s2_mtz1_report.json"), SYNTHETIC_REPORT);
        Path first = tempDir.resolve("trace/s2_mtz1-first-0123456789abcdef.json");
        Path second = tempDir.resolve("trace/s2_mtz1-second-fedcba9876543210.json");
        Files.createDirectories(first.getParent());
        Files.writeString(first, SYNTHETIC_REPORT);
        Files.writeString(second, SYNTHETIC_REPORT);

        TriageResult ambiguous = runTriage("s2", "mtz1", "--dir", tempDir.toString());

        assertEquals(1, ambiguous.status());
        assertEquals("", ambiguous.output(), "Ambiguous evidence must not produce a chosen report's brief.");
        assertTrue(ambiguous.errors().contains("multiple nested reports match s2 mtz1"));
        assertTrue(ambiguous.errors().contains("--report <path>"));

        TriageResult explicit = runTriage("--report", first.toString());
        assertEquals(0, explicit.status(), explicit.errors());
        assertTrue(explicit.output().contains("Report: " + first));
        assertTrue(explicit.output().contains("FIRST DIVERGENCE"));
    }
}

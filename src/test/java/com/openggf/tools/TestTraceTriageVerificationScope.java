package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.trace.TraceReportWriter;
import com.openggf.tools.TraceTriageTool.Divergence;
import com.openggf.tools.TraceTriageTool.TraceReport;
import com.openggf.trace.BootstrapDivergence;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceVerificationScope;
import com.openggf.trace.VerificationGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real report publication and CLI diagnosis with independent verification scopes. */
class TestTraceTriageVerificationScope {
    @ParameterizedTest
    @CsvSource({"PHYSICS, x", "ANIMATION, player_animation_id"})
    void scopedBriefUsesMatchingFrontierForContextAndCompressedAux(
            TraceVerificationScope scope, String selectedField, @TempDir Path tempDir) throws Exception {
        FieldComparison physics = new FieldComparison("x", "10", "11", Severity.ERROR, 1);
        FieldComparison animation = FieldComparison.animation(
                "player_animation_id", "1", "2", Severity.ERROR, 1);
        FieldComparison earlier = scope == TraceVerificationScope.PHYSICS ? animation : physics;
        FieldComparison selected = scope == TraceVerificationScope.PHYSICS ? physics : animation;
        DivergenceReport produced = new DivergenceReport(List.of(
                new FrameComparison(2, Map.of(earlier.fieldName(), earlier),
                        "earlier-group-rom", "earlier-group-engine"),
                new FrameComparison(9, Map.of(selected.fieldName(), selected),
                        "selected-group-rom", "selected-group-engine")));
        Path reportPath = publishReport(tempDir, produced, true, scope);
        Path aux = tempDir.resolve("aux.jsonl.gz");
        try (var stream = new GZIPOutputStream(Files.newOutputStream(aux))) {
            stream.write(("{\"frame\":2,\"event\":\"earlier-group-aux\"}\n"
                    + "{\"frame\":9,\"event\":\"selected-group-aux\"}\n")
                    .getBytes(StandardCharsets.UTF_8));
        }

        TriageResult result = runTriage("--report", reportPath.toString(), "--aux", aux.toString());

        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("frame    : 9 (span"), result.output());
        assertTrue(result.output().contains("field    : " + selectedField + "  [ERROR]"));
        assertTrue(result.output().contains("ROM: selected-group-rom"));
        assertTrue(result.output().contains("ENG: selected-group-engine"));
        assertTrue(result.output().contains("selected-group-aux"));
        assertFalse(result.output().contains("earlier-group-aux"));
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(reportPath));
        assertEquals(2, parsed.errorCount(), "raw totals retain both verification groups");
        assertEquals(2, parsed.errors().size(), "raw consumers retain both error groups");
        assertEquals(2, parsed.totalFrames());
    }

    @ParameterizedTest
    @CsvSource({"ALL, true, 2", "PHYSICS, true, 2", "ANIMATION, false, 9"})
    void bootstrapFocusBelongsToPhysicsWhileRawEvidenceStaysGlobal(
            TraceVerificationScope scope, boolean bootstrapSelected, int selectedFrame,
            @TempDir Path tempDir) throws Exception {
        DivergenceReport produced = new DivergenceReport(List.of(
                frame(2, new FieldComparison("x", "10", "11", Severity.ERROR, 1)),
                frame(9, FieldComparison.animation("player_animation_id", "1", "2", Severity.ERROR, 1))),
                null, List.of(new BootstrapDivergence("camera_x", BootstrapDivergence.Severity.ERROR,
                        "100", "101", "pre-trace camera")));
        Path report = publishReport(tempDir, produced, true, scope);
        Path aux = writeAux(tempDir, """
                {"frame":-1,"event":"bootstrap-evidence"}
                {"frame":2,"event":"physics-evidence"}
                {"frame":9,"event":"animation-evidence"}
                """);

        TriageResult result = runTriage("--report", report.toString(), "--aux", aux.toString());

        assertEquals(0, result.status(), result.errors());
        assertEquals(bootstrapSelected, result.output().contains("BOOTSTRAP (frame 0) ERROR present"));
        assertTrue(result.output().contains("frame    : " + selectedFrame + " (span"));
        assertEquals(bootstrapSelected, result.output().contains("bootstrap-evidence"));
        assertEquals(!bootstrapSelected, result.output().contains("animation-evidence"));
        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(report));
        assertEquals(3, parsed.errorCount());
        assertEquals(2, parsed.errors().size());
        assertNotNull(parsed.firstBootstrapError(), "global evidence remains available to baseline readers");
    }

    @ParameterizedTest
    @CsvSource({"PHYSICS, x", "ANIMATION, player_animation_id"})
    void selectedWarningBeatsAnOutOfScopeError(
            TraceVerificationScope scope, String selectedField, @TempDir Path tempDir) throws Exception {
        FieldComparison warning = scope == TraceVerificationScope.PHYSICS
                ? new FieldComparison("x", "10", "11", Severity.WARNING, 1)
                : FieldComparison.animation("player_animation_id", "1", "2", Severity.WARNING, 1);
        FieldComparison error = scope == TraceVerificationScope.PHYSICS
                ? FieldComparison.animation("player_animation_id", "1", "2", Severity.ERROR, 1)
                : new FieldComparison("x", "10", "11", Severity.ERROR, 1);
        DivergenceReport produced = new DivergenceReport(List.of(frame(2, warning), frame(9, error)));
        Path narrow = publishReport(tempDir.resolve("narrow"), produced, false, scope);
        Path all = publishReport(tempDir.resolve("all"), produced, false, TraceVerificationScope.ALL);

        TriageResult selected = runTriage("--report", narrow.toString());
        TriageResult global = runTriage("--report", all.toString());

        assertEquals(0, selected.status(), selected.errors());
        assertTrue(selected.output().contains("frame    : 2 (span"));
        assertTrue(selected.output().contains("field    : " + selectedField + "  [WARNING]"));
        assertTrue(selected.output().contains("Frontier log update likely needed: no"));
        assertTrue(selected.output().contains("Replay completion: INCOMPLETE"));
        assertEquals(0, global.status(), global.errors());
        assertTrue(global.output().contains("frame    : 9 (span"));
        assertTrue(global.output().contains("Frontier log update likely needed: YES"));
        TraceReport parsed = TraceTriageTool.parseReport(Files.readString(narrow));
        assertEquals(1, parsed.errorCount());
        assertEquals(1, parsed.warningCount());
        assertEquals(9, parsed.firstDivergence().startFrame(), "the raw model keeps its global frontier");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHYSICS", "ANIMATION"})
    void outOfScopeOnlyEvidenceDoesNotRequestUnrelatedContextOrAux(
            TraceVerificationScope scope, @TempDir Path tempDir) throws Exception {
        DivergenceReport produced = scope == TraceVerificationScope.PHYSICS
                ? new DivergenceReport(List.of(frame(2, FieldComparison.animation(
                        "player_animation_id", "1", "2", Severity.ERROR, 1))))
                : new DivergenceReport(List.of(), null, List.of(new BootstrapDivergence(
                        "camera_x", BootstrapDivergence.Severity.ERROR, "100", "101", "pre-trace")));
        Path report = publishReport(tempDir, produced, true, scope);

        TriageResult result = runTriage("--report", report.toString(),
                "--context", tempDir.resolve("unrelated-context.txt").toString(),
                "--aux", tempDir.resolve("unrelated-aux.jsonl.gz").toString());

        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("PER-FRAME DIVERGENCE: none for"));
        assertFalse(result.output().contains("BOOTSTRAP (frame 0) ERROR present"));
        assertTrue(result.output().contains("Frontier log update likely needed: no"));
        assertTrue(result.output().contains("Replay completion: COMPLETE"));
        assertTrue(result.output().contains("totals and report summary cover all verification groups"));
        assertEquals(1, TraceTriageTool.parseReport(Files.readString(report)).errorCount());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "false | missing", "true | missing",
            "false | 17", "true | 17",
            "false | \"unknown\"", "true | \"unknown\""
    })
    void legacyUnclassifiedGroupsKeepGlobalDiagnosis(
            boolean publishGate, String groupJson, @TempDir Path tempDir) throws Exception {
        Path report = publishReport(tempDir, new DivergenceReport(List.of(frame(9,
                new FieldComparison("x", "10", "11", Severity.ERROR, 1)))), true, TraceVerificationScope.ALL);
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(Files.readString(report));
        ObjectNode group = (ObjectNode) payload.path("errors").get(0);
        if (groupJson.equals("missing")) group.remove("verification_group");
        else group.set("verification_group", new ObjectMapper().readTree(groupJson));
        if (!publishGate) payload.remove("replay_gate");
        Files.writeString(report, payload.toString());

        TriageResult result = runTriage("--report", report.toString());

        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("frame    : 9 (span"));
        assertTrue(result.output().contains("Replay completion: " + (publishGate ? "COMPLETE" : "UNKNOWN")));
    }

    @ParameterizedTest
    @MethodSource("unclassifiableGroups")
    void narrowScopeRejectsUnclassifiableGroups(
            TraceVerificationScope scope, String array, String groupJson, @TempDir Path tempDir) throws Exception {
        Severity severity = array.equals("errors") ? Severity.ERROR : Severity.WARNING;
        Path report = publishReport(tempDir, new DivergenceReport(List.of(frame(9,
                new FieldComparison("x", "10", "11", severity, 1)))), true, scope);
        var mapper = new ObjectMapper();
        ObjectNode payload = (ObjectNode) mapper.readTree(Files.readString(report));
        ObjectNode group = (ObjectNode) payload.path(array).get(0);
        if (groupJson.equals("missing")) group.remove("verification_group");
        else group.set("verification_group", mapper.readTree(groupJson));
        Files.writeString(report, payload.toString());

        assertThrows(IOException.class, () -> TraceTriageTool.parseReport(payload.toString()));

        TriageResult result = runTriage("--report", report.toString());

        assertEquals(1, result.status());
        assertEquals("", result.output(), "unknown scope membership must not produce a clean brief");
        assertTrue(result.errors().contains("verification_group"));
    }

    private static Stream<Arguments> unclassifiableGroups() {
        List<Arguments> cases = new ArrayList<>();
        for (TraceVerificationScope scope : List.of(TraceVerificationScope.PHYSICS, TraceVerificationScope.ANIMATION)) {
            for (String array : List.of("errors", "warnings")) {
                for (String value : List.of("missing", "null", "17", "\"unknown\"",
                        "\"PHYSICS\"", "\" physics\"", "\"physics \"")) {
                    cases.add(Arguments.of(scope, array, value));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @CsvSource({"PHYSICS, errors", "PHYSICS, warnings", "ANIMATION, errors", "ANIMATION, warnings"})
    void directNarrowModelsRejectUnclassifiedGroups(TraceVerificationScope scope, String array) {
        boolean error = array.equals("errors");
        Divergence unknown = new Divergence("x", error ? "ERROR" : "WARNING", 9, 9, 1,
                "10", "11", false, null);

        assertThrows(IllegalArgumentException.class, () -> new TraceReport(error ? 1 : 0, error ? 0 : 1,
                1, "unclassified evidence", List.of(), error ? List.of(unknown) : List.of(),
                error ? List.of() : List.of(unknown), new ReplayGate(10, true, scope), List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"errors", "warnings"})
    void callerMutationCannotEraseValidatedFrontier(String array) {
        boolean error = array.equals("errors");
        String severity = error ? "ERROR" : "WARNING";
        List<Divergence> callerGroups = new ArrayList<>(List.of(new Divergence(
                "x", severity, 9, 9, 1, "10", "11", false, VerificationGroup.PHYSICS)));
        TraceReport report = new TraceReport(error ? 1 : 0, error ? 0 : 1, 1, "classified evidence",
                List.of(), error ? callerGroups : List.of(), error ? List.of() : callerGroups,
                new ReplayGate(10, true, TraceVerificationScope.PHYSICS), List.of());
        callerGroups.set(0, new Divergence("x", severity, 9, 9, 1, "10", "11", false, null));

        String brief = TraceTriageTool.renderBrief("s2", "ehz1", report, "", "");

        assertTrue(brief.contains("frame    : 9 (span"), brief);
        assertEquals(error, TraceTriageTool.frontierLogLikelyNeedsUpdate(report));
    }

    @Test
    void scopeFilteringDoesNotHideMalformedOutOfScopeEvidence(@TempDir Path tempDir) throws Exception {
        Path report = publishReport(tempDir, new DivergenceReport(List.of(
                frame(2, FieldComparison.animation("player_animation_id", "1", "2", Severity.ERROR, 1)),
                frame(9, new FieldComparison("x", "10", "11", Severity.ERROR, 1)))),
                true, TraceVerificationScope.PHYSICS);
        ObjectNode payload = (ObjectNode) new ObjectMapper().readTree(Files.readString(report));
        ((ObjectNode) payload.path("errors").get(0)).put("end_frame", -1);
        Files.writeString(report, payload.toString());

        TriageResult result = runTriage("--report", report.toString());

        assertEquals(1, result.status());
        assertEquals("", result.output());
    }

    private static FrameComparison frame(int traceFrame, FieldComparison comparison) {
        return new FrameComparison(traceFrame, Map.of(comparison.fieldName(), comparison));
    }

    private static Path publishReport(Path directory, DivergenceReport report, boolean completed,
                                      TraceVerificationScope scope) throws IOException {
        SessionInvocation invocation = new SessionInvocation(
                TestTraceTriageVerificationScope.class.getName(), "publishedReport", 0,
                "0123456789abcdef", "published report");
        return TraceReportWriter.writeReport(directory, report, "trace", invocation,
                "single", "s2_ehz1", scope, 2, new ReplayGate(10, completed, scope)).physicalPath();
    }

    private static Path writeAux(Path directory, String content) throws IOException {
        Path path = directory.resolve("aux.jsonl.gz");
        try (var stream = new GZIPOutputStream(Files.newOutputStream(path))) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private record TriageResult(int status, String output, String errors) { }

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
}

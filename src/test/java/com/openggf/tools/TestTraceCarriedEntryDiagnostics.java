package com.openggf.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.tests.SessionInvocationExtension;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.TestSessionOutputPaths.ReportAllocation;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceReplayRomCatalogueExtension;
import com.openggf.tests.trace.TraceReportWriter;
import com.openggf.tests.trace.s3k.sonictails.TestS3kSonicTailsHczSegmentTraceReplay;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.DivergenceReport.CarriedEntryDiagnostics;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceVerificationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SessionInvocationExtension.class)
class TestTraceCarriedEntryDiagnostics {
    private static final String LITERAL_DIAGNOSTICS = """
            {"schema":1,"provenance":"recorded run segment 9 (entry)",
             "disagreements":["literal recorded/derived disagreement"],
             "unresolved":["literal missing entry observation"]}
            """;

    @Test
    void literalPresentDiagnosticsReachCliWithoutChangingIncompleteGate(@TempDir Path directory)
            throws Exception {
        Path path = publish(directory, new DivergenceReport(List.of()),
                new ReplayGate(10, false, TraceVerificationScope.ALL));
        ObjectNode json = (ObjectNode) new ObjectMapper().readTree(Files.readString(path));
        json.set("carried_entry_diagnostics", new ObjectMapper().readTree(LITERAL_DIAGNOSTICS));
        Files.writeString(path, json.toString());
        Cli result = run(path);
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Provenance: recorded run segment 9 (entry)"));
        assertTrue(result.output().contains("Disagreements:\n  - literal recorded/derived disagreement"));
        assertTrue(result.output().contains("Unresolved:\n  - literal missing entry observation"));
        assertTrue(result.output().contains("Replay completion: INCOMPLETE"));
        assertTrue(result.output().contains("Totals: 0 errors, 0 warnings, 0 frames compared"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "42", "\"unknown\"", "{}",
            "{\"schema\":2,\"provenance\":null,\"disagreements\":[],\"unresolved\":[]}",
            "{\"schema\":1.0,\"provenance\":null,\"disagreements\":[],\"unresolved\":[]}",
            "{\"schema\":1,\"disagreements\":[],\"unresolved\":[]}",
            "{\"schema\":1,\"provenance\":7,\"disagreements\":[],\"unresolved\":[]}",
            "{\"schema\":1,\"provenance\":null,\"unresolved\":[]}",
            "{\"schema\":1,\"provenance\":null,\"disagreements\":null,\"unresolved\":[]}",
            "{\"schema\":1,\"provenance\":null,\"disagreements\":[7],\"unresolved\":[]}",
            "{\"schema\":1,\"provenance\":null,\"disagreements\":[],\"unresolved\":{}}",
            "{\"schema\":1,\"provenance\":null,\"disagreements\":[],\"unresolved\":[null]}",
            "{\"schema\":1,\"provenance\":null,\"disagreements\":[]}"
    })
    void malformedPresentDiagnosticsRejectCliBeforeOutput(String literal, @TempDir Path directory)
            throws Exception {
        Path path = publish(directory, new DivergenceReport(List.of()), null);
        ObjectNode json = (ObjectNode) new ObjectMapper().readTree(Files.readString(path));
        json.set("carried_entry_diagnostics", new ObjectMapper().readTree(literal));
        Files.writeString(path, json.toString());
        Cli result = run(path);
        assertEquals(1, result.status());
        assertEquals("", result.output());
        assertTrue(result.errors().contains("carried_entry_diagnostics"));
    }

    @ParameterizedTest
    @CsvSource({"ALL, true, 2", "PHYSICS, false, 9", "ANIMATION, true, 2"})
    void realWriterEmitsOnlyLiteralDiagnosticsWithoutChangingEvidenceOrSelectedFrontier(
            TraceVerificationScope scope, boolean complete, int frontier, @TempDir Path directory)
            throws Exception {
        DivergenceReport report = new DivergenceReport(List.of(
                new FrameComparison(2, Map.of("player_animation_id", FieldComparison.animation(
                        "player_animation_id", "1", "2", Severity.ERROR, 1))),
                new FrameComparison(9, Map.of("x", new FieldComparison("x", "10", "11", Severity.ERROR, 1),
                        "camera_x", new FieldComparison("camera_x", "20", "21", Severity.WARNING, 1)))));
        ReplayGate gate = new ReplayGate(10, complete, scope);
        CarriedEntryDiagnostics diagnostics = new CarriedEntryDiagnostics("recorded run segment 9 (entry)",
                List.of("literal recorded/derived disagreement"), List.of("literal missing entry observation"));
        Path path = publish(directory, report, gate, diagnostics);
        ObjectNode actual = (ObjectNode) new ObjectMapper().readTree(Files.readString(path));
        assertEquals(new ObjectMapper().readTree(LITERAL_DIAGNOSTICS), actual.remove("carried_entry_diagnostics"),
                "real producer must emit exactly the diagnostic strings; disabling emission cannot pass");
        assertEquals(new ObjectMapper().readTree(report.toJson(gate)), actual,
                "counts, warnings, coverage, verification groups and gate must remain byte-for-value identical");
        Cli result = run(path);
        assertEquals(0, result.status(), result.errors());
        assertEquals("", result.errors());
        assertTrue(result.output().contains("Provenance: recorded run segment 9 (entry)"));
        assertTrue(result.output().contains("Disagreements:\n  - literal recorded/derived disagreement"));
        assertTrue(result.output().contains("Unresolved:\n  - literal missing entry observation"));
        assertTrue(result.output().contains("Replay completion: " + (complete ? "COMPLETE" : "INCOMPLETE")));
        assertTrue(result.output().contains("Totals: 2 errors, 1 warnings, 2 frames compared"));
        assertTrue(result.output().contains("frame    : " + frontier + " (span"));
        assertTrue(result.output().contains("does not establish imported gameplay state, complete provenance or run-chain parity"));
    }

    @Test
    void nullProvenanceAndEmptyNotesKeepZeroCoverageAndUnknownCompletion(@TempDir Path directory)
            throws Exception {
        Path path = publish(directory, new DivergenceReport(List.of()), null,
                new CarriedEntryDiagnostics(null, List.of(), List.of()));
        Cli result = run(path);
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Provenance: unknown"));
        assertTrue(result.output().contains("Disagreements: none reported"));
        assertTrue(result.output().contains("Unresolved: none reported"));
        assertTrue(result.output().contains("Replay completion: UNKNOWN"));
        assertTrue(result.output().contains("Comparison coverage: absent (0 frames compared)"));
        assertTrue(result.output().contains("does not establish imported gameplay state, complete provenance or run-chain parity"));
        CarriedEntryDiagnostics decoded = CarriedEntryDiagnostics.readOptionalFrom(
                new ObjectMapper().readTree(Files.readString(path)));
        assertNull(decoded.provenance());
        assertEquals(List.of(), decoded.disagreements());
        assertEquals(List.of(), decoded.unresolved());
    }

    @Test
    void absentMetadataKeepsDefaultBytesAndDoesNotClaimEntryEvaluation(@TempDir Path directory)
            throws Exception {
        DivergenceReport report = new DivergenceReport(List.of());
        String before = report.toJson();
        Path path = publish(directory, report, null);
        assertEquals(before, Files.readString(path));
        assertEquals(before, report.toJson(null, null));
        assertFalse(before.contains("carried_entry_diagnostics"));
        assertNull(CarriedEntryDiagnostics.readOptionalFrom(new ObjectMapper().readTree(before)));
        Cli result = run(path);
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Carried entry diagnostics: not reported (unknown)"));
        assertFalse(result.output().contains("Carried entry diagnostic evaluation:"));
        assertTrue(result.output().contains("Replay completion: UNKNOWN"));
    }

    @Test
    void projectionDefensivelyCopiesDiagnosticLists() {
        List<String> disagreements = new ArrayList<>(List.of("recorded difference"));
        List<String> unresolved = new ArrayList<>(List.of("missing source"));
        CarriedEntryDiagnostics diagnostics = new CarriedEntryDiagnostics(null, disagreements, unresolved);
        disagreements.clear();
        unresolved.clear();
        assertEquals(List.of("recorded difference"), diagnostics.disagreements());
        assertEquals(List.of("missing source"), diagnostics.unresolved());
        assertThrows(UnsupportedOperationException.class, () -> diagnostics.disagreements().add("mutation"));
        assertThrows(UnsupportedOperationException.class, () -> diagnostics.unresolved().clear());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void attachedDiagnosticsPreserveErrorDocumentPublicationAndMalformedFailure(
            boolean malformed, @TempDir Path directory) throws Exception {
        ReplayGate gate = new ReplayGate(10, false, TraceVerificationScope.ALL);
        Path path = publish(directory, new DivergenceReport(List.of()), gate);
        String before = Files.readString(path);
        Path owner = path.resolveSibling(path.getFileName() + ".owner.json");
        String ownerBefore = Files.readString(owner);
        DivergenceReport broken = new DivergenceReport(List.of()) {
            @Override public String toCompactSummary() {
                throw new IllegalStateException(malformed ? "broken \"summary\"" : "producer failure");
            }
        };
        CarriedEntryDiagnostics diagnostics = new CarriedEntryDiagnostics("literal provenance",
                List.of("literal disagreement"), List.of("literal unresolved"));
        if (malformed) {
            assertThrows(IOException.class, () -> publish(directory, broken, gate, diagnostics));
            assertEquals(before, Files.readString(path));
            assertEquals(ownerBefore, Files.readString(owner));
        } else {
            assertEquals(path, publish(directory, broken, gate, diagnostics));
            JsonNode error = new ObjectMapper().readTree(Files.readString(path));
            assertEquals("Failed to serialise report: producer failure", error.path("error").textValue());
            assertEquals(gate, ReplayGate.readOptionalFrom(error));
            assertFalse(error.has("carried_entry_diagnostics"), "an error document is not entry evidence");
            Cli result = run(path);
            assertEquals(1, result.status());
            assertEquals("", result.output());
            assertTrue(result.errors().contains("producer error"));
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    @ExtendWith(TraceReplayRomCatalogueExtension.class)
    void realFirstHczEntryDiagnosticsSurvivePostBinderAbort(@TempDir Path directory) throws Exception {
        Path reportDirectory = directory.resolve("carried-entry-post-binder");
        HczEntryAbortProbe probe = new HczEntryAbortProbe(reportDirectory);
        IntentionalEntryAbort failure = assertThrows(IntentionalEntryAbort.class, probe::replayMatchesTrace);
        assertSame(probe.abort, failure);
        assertEquals(1, probe.observations, "existing post-step observer must have executed after binder creation");
        assertEquals(1, probe.publications, "aborted inherited replay must publish exactly once");
        assertNotNull(probe.report);
        assertTrue(probe.report.physicalPath().toAbsolutePath().normalize()
                        .startsWith(directory.toAbsolutePath().normalize()),
                "post-binder probe must stay inside this test's TempDir");
        assertTrue(probe.report.metadataPath().toAbsolutePath().normalize()
                        .startsWith(directory.toAbsolutePath().normalize()),
                "probe owner metadata must also stay inside this test's TempDir");
        assertTrue(Files.isRegularFile(probe.report.metadataPath()), "real writer must publish owner metadata");
        JsonNode json = new ObjectMapper().readTree(Files.readString(probe.report.physicalPath()));
        JsonNode diagnostics = json.get("carried_entry_diagnostics");
        assertNotNull(diagnostics, "the computed entry evaluation must survive into the final report");
        // Independent fixture/source inspection: manifest index 9 names hcz; four
        // earlier stages finish with clear_routine=3, matching the recorded four
        // emeralds. Complete level_entry_ram removes the giant-ring unknown;
        // Act3_flag=0 admits no continuation. Empty notes do not certify wider entry state.
        assertEquals("s3k-sonic-tails-complete-emeralds segment 9 (hcz)", diagnostics.path("provenance").textValue());
        assertEquals("[]", diagnostics.path("disagreements").toString());
        assertEquals("[]", diagnostics.path("unresolved").toString());
        assertEquals(0, json.path("total_frames").intValue(), "abort precedes the first gameplay comparison");
        assertFalse(json.path("replay_gate").path("comparison_gate_reached").booleanValue());
        assertEquals(3574, json.path("replay_gate").path("planned_trace_rows").intValue());
        Cli result = run(probe.report.physicalPath());
        assertEquals(0, result.status(), result.errors());
        assertTrue(result.output().contains("Replay completion: INCOMPLETE"));
        assertTrue(result.output().contains("Provenance: s3k-sonic-tails-complete-emeralds segment 9 (hcz)"));
        assertTrue(result.output().contains("does not establish imported gameplay state, complete provenance or run-chain parity"));
    }

    private static Path publish(Path directory, DivergenceReport report, ReplayGate gate) throws Exception {
        return publish(directory, report, gate, null);
    }

    private static Path publish(Path directory, DivergenceReport report, ReplayGate gate,
                                CarriedEntryDiagnostics diagnostics) throws Exception {
        return TraceReportWriter.writeReport(directory, report, "trace",
                new SessionInvocation(TestTraceCarriedEntryDiagnostics.class.getName(), "publishedReport", 0,
                        "0123456789abcdef", "carried entry report"), "single", "s3k_hcz1",
                gate == null ? TraceVerificationScope.ALL : gate.verification(), 2, gate, diagnostics).physicalPath();
    }

    private record Cli(int status, String output, String errors) { }

    private static Cli run(Path report) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = TraceTriageTool.run(new String[]{"--report", report.toString()},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        return new Cli(status, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private static final class IntentionalEntryAbort extends RuntimeException { }

    // Probe in the binary name intentionally retains the trace profiles' Probe
    // exclusion: its inherited @Test must only execute inside the bounded witness.
    private static final class HczEntryAbortProbe extends TestS3kSonicTailsHczSegmentTraceReplay {
        private final Path directory;
        private final IntentionalEntryAbort abort = new IntentionalEntryAbort();
        private int observations;
        private int publications;
        private ReportAllocation report;

        private HczEntryAbortProbe(Path directory) { this.directory = directory; }
        @Override protected Path reportOutputDir() { return directory; }
        @Override protected TraceVerificationScope verificationScope() { return TraceVerificationScope.ALL; }
        @Override protected void onRewindReferenceClosureValidated(
                int traceIndex, TraceFrame frame, TraceExecutionPhase phase) {
            observations++;
            throw abort;
        }
        @Override protected void onReportPublished(ReportAllocation allocation) {
            publications++;
            report = allocation;
        }
    }
}

package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceVerificationScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceGateCompletionReport {
    @TempDir
    Path directory;

    @Test
    void completedGateDoesNotClaimEveryRecordedRowWasAGameplayComparison() throws Exception {
        JsonNode payload = write(new DivergenceReport(List.of()),
                new ReplayGate(3905, true, TraceVerificationScope.ALL));

        assertEquals(0, payload.path("total_frames").asInt());
        JsonNode gate = payload.path("replay_gate");
        assertEquals(1, gate.path("schema").asInt());
        assertTrue(gate.path("comparison_gate_reached").asBoolean());
        assertEquals(3905, gate.path("planned_trace_rows").asInt());
        assertEquals("all", gate.path("verification").asText());
    }

    @Test
    void cleanPrefixRemainsIncomplete() throws Exception {
        JsonNode payload = write(new DivergenceReport(List.of()),
                new ReplayGate(5852, false, TraceVerificationScope.ALL));

        assertEquals(0, payload.path("error_count").asInt());
        assertFalse(payload.path("replay_gate").path("comparison_gate_reached").asBoolean());
        assertEquals(5852, payload.path("replay_gate").path("planned_trace_rows").asInt());
    }

    @Test
    void completedMismatchRetainsItsGateCompletion() throws Exception {
        DivergenceReport mismatch = new DivergenceReport(List.of(new FrameComparison(7,
                Map.of("x", new FieldComparison("x", "0x0100", "0x0101", Severity.ERROR, 1)))));
        JsonNode payload = write(mismatch,
                new ReplayGate(20798, true, TraceVerificationScope.ALL));

        assertEquals(1, payload.path("error_count").asInt());
        assertEquals(1, payload.path("total_frames").asInt());
        assertTrue(payload.path("replay_gate").path("comparison_gate_reached").asBoolean());
        assertTrue(Files.isRegularFile(directory.resolve("completion_context.txt")));
    }

    @Test
    void legacyCallerPublishesNoGateMetadata() throws Exception {
        TestSessionOutputPaths.ReportAllocation allocation = TraceReportWriter.writeReport(
                directory, new DivergenceReport(List.of()), "trace", invocation(),
                "single", "completion", TraceVerificationScope.ALL, 2);

        JsonNode payload = new ObjectMapper().readTree(allocation.physicalPath().toFile());
        assertFalse(payload.has("replay_gate"));
        assertTrue(Files.isRegularFile(allocation.metadataPath()));
    }

    @Test
    void repeatedCompletedPublicationRetainsTheSameOwnerAndCompletion() throws Exception {
        DivergenceReport report = new DivergenceReport(List.of());
        ReplayGate gate =
                new ReplayGate(3905, true, TraceVerificationScope.ALL);
        TestSessionOutputPaths.ReportAllocation first = publish(report, gate);
        TestSessionOutputPaths.ReportAllocation second = publish(report, gate);

        assertEquals(first, second);
        JsonNode payload = new ObjectMapper().readTree(second.physicalPath().toFile());
        assertTrue(payload.path("replay_gate").path("comparison_gate_reached").asBoolean());
    }

    @Test
    void serializationFailureDoesNotReplacePublishedEvidence() throws Exception {
        ReplayGate gate = new ReplayGate(3905, true, TraceVerificationScope.ALL);
        TestSessionOutputPaths.ReportAllocation existing = publish(new DivergenceReport(List.of()), gate);
        String previousReport = Files.readString(existing.physicalPath());
        String previousOwner = "{\"prior_owner\":\"retain on failed serialization\"}\n";
        Files.writeString(existing.metadataPath(), previousOwner);
        DivergenceReport broken = new DivergenceReport(List.of()) {
            @Override
            public String toCompactSummary() {
                throw new IllegalStateException("broken \"summary\"");
            }
        };

        assertThrows(IOException.class, () -> publish(broken, gate));

        assertEquals(previousReport, Files.readString(existing.physicalPath()));
        assertEquals(previousOwner, Files.readString(existing.metadataPath()));
    }

    @Test
    void validSerializationErrorDocumentRetainsItsPublishedGate() throws Exception {
        DivergenceReport broken = new DivergenceReport(List.of()) {
            @Override
            public String toCompactSummary() {
                throw new IllegalStateException("producer failure");
            }
        };
        TestSessionOutputPaths.ReportAllocation allocation = publish(broken,
                new ReplayGate(3905, false, TraceVerificationScope.ALL));

        JsonNode payload = new ObjectMapper().readTree(allocation.physicalPath().toFile());
        assertEquals("Failed to serialise report: producer failure", payload.path("error").asText());
        assertFalse(payload.path("replay_gate").path("comparison_gate_reached").asBoolean());
        assertEquals(3905, payload.path("replay_gate").path("planned_trace_rows").asInt());
        assertTrue(Files.isRegularFile(allocation.metadataPath()));
    }

    @Test
    void invalidExtentAndConflictingVerificationAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayGate(0, false, TraceVerificationScope.ALL));
        assertThrows(IllegalArgumentException.class,
                () -> new ReplayGate(-1, true, TraceVerificationScope.ALL));
        assertThrows(NullPointerException.class,
                () -> new ReplayGate(1, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> publish(new DivergenceReport(List.of()),
                        new ReplayGate(1, true, TraceVerificationScope.PHYSICS)));
    }

    private JsonNode write(DivergenceReport report, ReplayGate gate) throws Exception {
        return new ObjectMapper().readTree(publish(report, gate).physicalPath().toFile());
    }

    private TestSessionOutputPaths.ReportAllocation publish(
            DivergenceReport report, ReplayGate gate) throws Exception {
        return TraceReportWriter.writeReport(directory, report, "trace", invocation(),
                "single", "completion", TraceVerificationScope.ALL, 2, gate);
    }

    private SessionInvocation invocation() {
        return new SessionInvocation(getClass().getName(), "reportCompletion", 0,
                "0123456789abcdef", "gate completion");
    }
}

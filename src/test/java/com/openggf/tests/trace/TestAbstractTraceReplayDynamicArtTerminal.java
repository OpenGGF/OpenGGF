package com.openggf.tests.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestSessionOutputPaths.ReportAllocation;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceVerificationScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAbstractTraceReplayDynamicArtTerminal
        extends AbstractTraceReplayTest {
    private static final Path SOURCE = Path.of(
            "src/test/resources/traces/s2/ehz1_fullrun");
    private static final int FINAL_FRAME = 5851;

    @TempDir
    Path tempDir;
    private Path activeTrace;
    private Path activeReports;
    private Path expectedReportPath;
    private TraceVerificationScope selectedScope;
    private TraceVerificationScope scopeAfterFixtureBuild;
    private String priorSystemScope;
    private String priorFrontierOnly;
    private String priorContextRadius;
    private int reportPublications;
    private int lastValidatedFrame;

    @BeforeEach
    void saveAndConfigureReplayProperties() {
        priorSystemScope = System.getProperty("trace.verification");
        priorFrontierOnly = System.getProperty("trace.frontierOnly");
        priorContextRadius = System.getProperty("trace.context.radius");
        System.setProperty("trace.verification", "all");
        System.setProperty("trace.frontierOnly", "false");
        System.setProperty("trace.context.radius", "2");
    }

    @AfterEach
    void restoreReplayProperties() {
        setOrClearProperty("trace.verification", priorSystemScope);
        setOrClearProperty("trace.frontierOnly", priorFrontierOnly);
        setOrClearProperty("trace.context.radius", priorContextRadius);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<unset>, <unset>, <unset>",
            "'', '', ''",
            "physics, true, 7"
    }, nullValues = "<unset>")
    void fixtureRestoresEveryCallerProperty(String scope, String frontier, String radius) {
        setOrClearProperty("trace.verification", scope);
        setOrClearProperty("trace.frontierOnly", frontier);
        setOrClearProperty("trace.context.radius", radius);
        var nestedFixture = new TestAbstractTraceReplayDynamicArtTerminal();
        nestedFixture.saveAndConfigureReplayProperties();
        try {
            assertAll(
                    () -> assertEquals("all", System.getProperty("trace.verification")),
                    () -> assertEquals("false", System.getProperty("trace.frontierOnly")),
                    () -> assertEquals("2", System.getProperty("trace.context.radius")));
            System.setProperty("trace.verification", "animation");
            System.setProperty("trace.frontierOnly", "false");
            System.setProperty("trace.context.radius", "0");
        } finally {
            nestedFixture.restoreReplayProperties();
        }

        assertAll(
                () -> assertEquals(scope, System.getProperty("trace.verification")),
                () -> assertEquals(frontier, System.getProperty("trace.frontierOnly")),
                () -> assertEquals(radius, System.getProperty("trace.context.radius")));
    }

    private static void setOrClearProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Override
    public void replayMatchesTrace() {
        // Replay tests below call the complete inherited replay explicitly
        // after preparing their advertised fixture and policy.
    }

    @Test
    void completedAdvertisedReplayReportsStaleTerminalSnapshot()
            throws Exception {
        prepareAdvertisedFixture();

        assertThrows(AssertionError.class, super::replayMatchesTrace);

        assertEquals(1, reportPublications, "a divergent replay publishes its final report once");
        String report = Files.readString(reportPath());
        assertEquals(new ReplayGate(FINAL_FRAME + 1, true, TraceVerificationScope.ALL),
                ReplayGate.readOptionalFrom(new ObjectMapper().readTree(report)),
                "the completed driver publishes its gate despite the terminal mismatch");
        assertTrue(report.contains("\"field\" : \"dynamic_art.frame\""));
        assertTrue(report.contains("\"end_frame\" : " + FINAL_FRAME),
                "completed replay must report the terminal expectation");
    }

    @Test
    void actualEarlyFrontierStopSuppressesUnproducedTerminalExpectation()
            throws Exception {
        prepareAdvertisedFixture();
        System.setProperty("trace.frontierOnly", "true");
        System.setProperty("trace.context.radius", "0");

        assertThrows(AssertionError.class, super::replayMatchesTrace);

        assertEquals(1, reportPublications, "an early frontier publishes its final report once");
        String report = Files.readString(reportPath());
        assertEquals(new ReplayGate(FINAL_FRAME + 1, false, TraceVerificationScope.ALL),
                ReplayGate.readOptionalFrom(new ObjectMapper().readTree(report)),
                "the actual frontier stop publishes an incomplete gate");
        var published = new ObjectMapper().readTree(report);
        var errors = published.path("errors");
        assertTrue(errors.isArray() && !errors.isEmpty(),
                "the frontier has a real per-frame error");
        var firstErrorFrame = errors.get(0).path("start_frame");
        assertTrue(firstErrorFrame.canConvertToInt());
        assertEquals(firstErrorFrame.intValue(), lastValidatedFrame,
                "zero context stops at the first erroneous row");
        assertTrue(report.contains("\"field\" : \"dynamic_art.frame\""));
        assertFalse(report.contains("\"end_frame\" : " + FINAL_FRAME),
                "an actually-taken early frontier must not invent terminal data");
    }

    @Test
    void frontierUsesTheResolvedScopeRatherThanTheSystemProperty() throws Exception {
        prepareAdvertisedFixture();
        selectedScope = TraceVerificationScope.ANIMATION;
        expectedReportPath = activeReports.resolve("s2_ehz1_animation_report.json");
        System.setProperty("trace.verification", "physics");
        System.setProperty("trace.frontierOnly", "true");
        System.setProperty("trace.context.radius", "0");

        super.replayMatchesTrace();

        assertCompleteAnimationReport();
    }

    @Test
    void cleanlinessUsesTheScopeResolvedBeforeFixtureAssembly() throws Exception {
        prepareAdvertisedFixture();
        selectedScope = TraceVerificationScope.ANIMATION;
        scopeAfterFixtureBuild = TraceVerificationScope.PHYSICS;
        expectedReportPath = activeReports.resolve("s2_ehz1_animation_report.json");
        System.setProperty("trace.verification", "animation");
        System.setProperty("trace.frontierOnly", "false");

        super.replayMatchesTrace();

        assertCompleteAnimationReport();
    }

    private void assertCompleteAnimationReport() throws IOException {
        assertEquals(1, reportPublications);
        var report = new ObjectMapper().readTree(Files.readString(reportPath()));
        assertEquals(new ReplayGate(FINAL_FRAME + 1, true, TraceVerificationScope.ANIMATION),
                ReplayGate.readOptionalFrom(report),
                "stopping, cleanliness and publication use the same resolved scope");
        assertEquals(FINAL_FRAME + 1, report.path("total_frames").asInt());
        assertEquals(FINAL_FRAME, lastValidatedFrame);
        assertTrue(report.path("error_count").asInt() > 0,
                "the intentionally mismatched physics evidence remains as an out-of-scope control");
    }

    @Override
    protected TraceVerificationScope verificationScope() {
        return selectedScope != null ? selectedScope : super.verificationScope();
    }

    @Override
    protected void afterFixtureBuild(com.openggf.trace.TraceData trace) {
        var gameplay = SessionManager.getCurrentGameplayMode();
        gameplay.endDynamicArtComparisonSegment();
        gameplay.plcFrameLifecycle()
                .setComparisonSegmentsExternallyManaged(true);
        if (scopeAfterFixtureBuild != null) {
            selectedScope = scopeAfterFixtureBuild;
        }
    }

    private void prepareAdvertisedFixture() throws IOException {
        activeTrace = tempDir.resolve("trace");
        activeReports = tempDir.resolve("reports");
        expectedReportPath = activeReports.resolve("s2_ehz1_report.json");
        selectedScope = null;
        scopeAfterFixtureBuild = null;
        reportPublications = 0;
        lastValidatedFrame = -1;
        Files.createDirectories(activeTrace);
        // The committed payload is gzipped -- TestTraceFixtureCompressionGuard
        // forbids an uncompressed physics.csv under src/test/resources/traces,
        // and the replay loader reads either form. Copy the form that exists.
        Files.copy(SOURCE.resolve("physics.csv.gz"),
                activeTrace.resolve("physics.csv.gz"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(SOURCE.resolve("s2-ehz1.bk2"),
                activeTrace.resolve("s2-ehz1.bk2"),
                StandardCopyOption.REPLACE_EXISTING);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> metadata = mapper.readValue(
                SOURCE.resolve("metadata.json").toFile(),
                new TypeReference<>() {});
        List<String> extras = new ArrayList<>(
                (List<String>) metadata.get("aux_schema_extras"));
        if (!extras.contains("dynamic_art_transfer_state_per_frame")) {
            extras.add("dynamic_art_transfer_state_per_frame");
        }
        metadata.put("aux_schema_extras", extras);
        mapper.writeValue(activeTrace.resolve("metadata.json").toFile(),
                metadata);

        // The committed fixture already advertises
        // dynamic_art_transfer_state_per_frame and carries one real heartbeat
        // per stored row. This probe substitutes its own empty per-frame
        // states, so the recorded ones must be dropped rather than appended
        // to: TraceData.validateDynamicArtTransferStates requires exactly one
        // state per stored physics frame, and two at frame 0 is a malformed
        // stream, not a validator defect.
        try (var input = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(
                            SOURCE.resolve("aux_state.jsonl.gz"))),
                    java.nio.charset.StandardCharsets.UTF_8));
             var output = Files.newBufferedWriter(
                     activeTrace.resolve("aux_state.jsonl"),
                     java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.contains("\"event\": \"dynamic_art_transfer_state\"")
                        || line.contains("\"event\":\"dynamic_art_transfer_state\"")) {
                    continue;
                }
                output.write(line);
                output.write("\n");
            }
            for (int frame = 0; frame <= FINAL_FRAME; frame++) {
                output.write(("""
                        {"frame":%d,"event":"dynamic_art_transfer_state",\
"edges":[],"outstanding_transfer_ids":[]}
                        """.formatted(frame)));
            }
        }
    }

    private Path reportPath() {
        return expectedReportPath;
    }

    @Override protected SonicGame game() { return SonicGame.SONIC_2; }
    @Override protected int zone() { return 0; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() { return activeTrace; }
    @Override
    protected Path reportOutputDir() {
        return activeReports;
    }

    @Override
    protected void onRewindReferenceClosureValidated(
            int traceIndex, TraceFrame frame, TraceExecutionPhase phase) {
        lastValidatedFrame = frame.frame();
    }

    @Override
    protected void onReportPublished(ReportAllocation allocation) {
        assertEquals(reportPath(), allocation.physicalPath());
        reportPublications++;
    }
}

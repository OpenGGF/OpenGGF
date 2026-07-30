package com.openggf.tests.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

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

    @AfterEach
    void clearFrontierProperties() {
        System.clearProperty("trace.frontierOnly");
        System.clearProperty("trace.contextRadius");
    }

    @Override
    public void replayMatchesTrace() {
        // The two tests below call the complete inherited replay explicitly
        // after preparing their advertised fixture and policy.
    }

    @Test
    void completedAdvertisedReplayReportsStaleTerminalSnapshot()
            throws Exception {
        prepareAdvertisedFixture();

        assertThrows(AssertionError.class, super::replayMatchesTrace);

        String report = Files.readString(reportPath());
        assertTrue(report.contains("\"field\" : \"dynamic_art.frame\""));
        assertTrue(report.contains("\"end_frame\" : " + FINAL_FRAME),
                "completed replay must report the terminal expectation");
    }

    @Test
    void actualEarlyFrontierStopSuppressesUnproducedTerminalExpectation()
            throws Exception {
        prepareAdvertisedFixture();
        System.setProperty("trace.frontierOnly", "true");
        System.setProperty("trace.contextRadius", "0");

        assertThrows(AssertionError.class, super::replayMatchesTrace);

        String report = Files.readString(reportPath());
        assertTrue(report.contains("\"field\" : \"dynamic_art.frame\""));
        assertFalse(report.contains("\"end_frame\" : " + FINAL_FRAME),
                "an actually-taken early frontier must not invent terminal data");
    }

    @Override
    protected void afterFixtureBuild(com.openggf.trace.TraceData trace) {
        var gameplay = SessionManager.getCurrentGameplayMode();
        gameplay.endDynamicArtComparisonSegment();
        gameplay.plcFrameLifecycle()
                .setComparisonSegmentsExternallyManaged(true);
    }

    private void prepareAdvertisedFixture() throws IOException {
        activeTrace = tempDir.resolve("trace");
        activeReports = tempDir.resolve("reports");
        Files.createDirectories(activeTrace);
        Files.copy(SOURCE.resolve("physics.csv"),
                activeTrace.resolve("physics.csv"),
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
        extras.add("dynamic_art_transfer_state_per_frame_v1");
        metadata.put("aux_schema_extras", extras);
        mapper.writeValue(activeTrace.resolve("metadata.json").toFile(),
                metadata);

        try (var input = new GZIPInputStream(Files.newInputStream(
                    SOURCE.resolve("aux_state.jsonl.gz")));
             var output = Files.newOutputStream(
                     activeTrace.resolve("aux_state.jsonl"))) {
            input.transferTo(output);
            for (int frame = 0; frame <= FINAL_FRAME; frame++) {
                output.write(("""
                        {"frame":%d,"event":"dynamic_art_transfer_state",\
"edges":[],"outstanding_transfer_ids":[]}
                        """.formatted(frame)).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private Path reportPath() {
        return activeReports.resolve("s2_ehz1_report.json");
    }

    @Override protected SonicGame game() { return SonicGame.SONIC_2; }
    @Override protected int zone() { return 0; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() { return activeTrace; }
    @Override protected Path reportOutputDir() { return activeReports; }
}

package com.openggf.tools;

import com.openggf.tools.TraceTriageTool.Bootstrap;
import com.openggf.tools.TraceTriageTool.Divergence;
import com.openggf.tools.TraceTriageTool.Subsystem;
import com.openggf.tools.TraceTriageTool.TraceReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TraceTriageTool}. Uses a synthetic report JSON string
 * matching the real {@code DivergenceReport.toJson()} schema. No ROM, no
 * OpenGL, no real trace run. JUnit 5 / Jupiter only.
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

    @Test
    void classifiesSidekickField() {
        assertEquals(Subsystem.SIDEKICK, TraceTriageTool.classifySubsystem("tails_air", false));
        assertEquals(Subsystem.SIDEKICK, TraceTriageTool.classifySubsystem("tails_x", false));
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
        assertTrue(brief.contains("docs/TRACE_FRONTIER_LOG.md"),
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
    void extractJsonIntFieldReadsHexAndQuotedAndPlainValues() {
        assertEquals(375, TraceTriageTool.extractJsonIntField("{\"frame\":375}", "frame"));
        assertEquals(7, TraceTriageTool.extractJsonIntField("{\"slot\": 7 }", "slot"));
        assertEquals(-1, TraceTriageTool.extractJsonIntField("{\"frame\":12}", "missing"));
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
}

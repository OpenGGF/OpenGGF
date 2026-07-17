package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.TraceTriageTool;
import com.openggf.tools.TraceTriageTool.TraceReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the frozen FBZ pre-implementation trace frontiers against regression.
 * The report-backed audit is opt-in with {@code -Dtrace.baseline.enabled=true}
 * because a clean test checkout has not generated {@code target/trace-reports}.
 */
public class TestTraceBaselineRegression {
    private static final String ENABLE_PROPERTY = "trace.baseline.enabled";
    private static final Path DEFAULT_BASELINE = Path.of(
            "docs/superpowers/research/2026-07-12-fbz-trace-baseline.json");
    private static final Path REPORT_DIR = Path.of("target/trace-reports");
    private static final Pattern S3K_COMPLETE_RUN_CLASS = Pattern.compile(
            "^com\\.openggf\\.tests\\.trace\\.s3k\\.TestS3k([A-Za-z0-9]+)CompleteRunTraceReplay$");

    @Test
    void knownRedTraceFrontiersDoNotRegress() throws Exception {
        assumeTrue(Boolean.getBoolean(ENABLE_PROPERTY),
                "Report-backed trace baseline audit is opt-in; run the trace sweep first, then set -D"
                        + ENABLE_PROPERTY + "=true");
        Path baselinePath = Path.of(System.getProperty(
                "trace.baseline.path", DEFAULT_BASELINE.toString()));
        assertTrue(Files.isRegularFile(baselinePath),
                "Trace baseline manifest not found: " + baselinePath);

        JsonNode root = new ObjectMapper().readTree(baselinePath.toFile());
        assertEquals(1, root.path("schema_version").asInt(-1));
        assertEquals(List.of("firstErrorFrame", "errorCount", "warningCount"),
                strings(root.path("comparison_order")));

        JsonNode knownRed = root.path("known_red");
        assertTrue(knownRed.isArray(), "known_red must be an array");
        assertFalse(knownRed.isEmpty(), "known_red test manifest is empty");

        FileTime baselineModified = Files.getLastModifiedTime(baselinePath);
        List<String> regressions = new ArrayList<>();
        for (JsonNode expected : knownRed) {
            compareReport(expected, baselinePath, baselineModified, regressions);
        }

        assertTrue(regressions.isEmpty(),
                () -> "Frozen trace baseline regressed:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), regressions));
    }

    @Test
    void aNowGreenKnownRedTraceIsMaximalImprovement() throws Exception {
        TraceReport green = TraceTriageTool.parseReport("""
                {
                  "error_count": 0,
                  "warning_count": 0,
                  "total_frames": 44282,
                  "summary": "All frames match trace. No divergences.",
                  "bootstrap": [],
                  "errors": [],
                  "warnings": []
                }
                """);

        assertNull(frontierRegression(1095, 4319, null, green));
    }

    private static void compareReport(
            JsonNode expected, Path baselinePath, FileTime baselineModified,
            List<String> regressions) {
        String testClass = requiredText(expected, "test_class", regressions);
        if (testClass == null) {
            return;
        }

        Path reportPath;
        try {
            reportPath = reportPathFor(testClass);
        } catch (IllegalArgumentException e) {
            regressions.add(e.getMessage());
            return;
        }
        if (!Files.isRegularFile(reportPath)) {
            regressions.add(testClass + ": missing report " + reportPath);
            return;
        }

        try {
            if (Files.getLastModifiedTime(reportPath).compareTo(baselineModified) < 0) {
                regressions.add(testClass + ": stale report " + reportPath
                        + " (older than " + baselinePath + ")");
                return;
            }

            TraceReport actual = TraceTriageTool.parseReport(Files.readString(reportPath));
            int expectedFirstFrame = expected.path("firstErrorFrame").asInt(-1);
            int expectedErrorCount = expected.path("errorCount").asInt(-1);
            JsonNode expectedWarnings = expected.get("warningCount");
            Integer expectedWarningCount = expectedWarnings == null || expectedWarnings.isNull()
                    ? null : expectedWarnings.asInt();
            String regression = frontierRegression(
                    expectedFirstFrame, expectedErrorCount, expectedWarningCount, actual);
            if (regression != null) {
                regressions.add(testClass + ": " + regression);
            }
        } catch (Exception e) {
            regressions.add(testClass + ": could not read " + reportPath + ": " + e.getMessage());
        }
    }

    private static String frontierRegression(
            int expectedFirstFrame, int expectedErrorCount,
            Integer expectedWarningCount, TraceReport actual) {
        if (actual.errorCount() == 0) {
            return null;
        }

        int actualFirstFrame = firstErrorFrame(actual);
        if (actualFirstFrame < 0) {
            return "report declares hard errors but has no hard-error frontier";
        }
        if (actualFirstFrame < expectedFirstFrame) {
            return "first error moved earlier from f" + expectedFirstFrame
                    + " to f" + actualFirstFrame;
        }
        if (actualFirstFrame > expectedFirstFrame) {
            return null;
        }
        if (actual.errorCount() > expectedErrorCount) {
            return "at unchanged frontier f" + actualFirstFrame
                    + ", error count grew from " + expectedErrorCount
                    + " to " + actual.errorCount();
        }
        if (expectedWarningCount != null
                && actual.warningCount() > expectedWarningCount) {
            return "at unchanged frontier f" + actualFirstFrame
                    + ", warning count grew from " + expectedWarningCount
                    + " to " + actual.warningCount();
        }
        return null;
    }

    private static int firstErrorFrame(TraceReport report) {
        if (report.firstBootstrapError() != null) {
            return 0;
        }
        return report.errors().stream()
                .mapToInt(TraceTriageTool.Divergence::startFrame)
                .min()
                .orElse(-1);
    }

    private static Path reportPathFor(String testClass) {
        Matcher matcher = S3K_COMPLETE_RUN_CLASS.matcher(testClass);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    testClass + ": unsupported known_red trace class naming convention");
        }
        String zone = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
        return REPORT_DIR.resolve("s3k_" + zone + "1_report.json");
    }

    private static String requiredText(
            JsonNode node, String field, List<String> regressions) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            regressions.add("baseline entry is missing " + field + ": " + node);
            return null;
        }
        return value.asText();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (!array.isArray()) {
            return values;
        }
        Iterator<JsonNode> iterator = array.elements();
        while (iterator.hasNext()) {
            values.add(iterator.next().asText());
        }
        return values;
    }
}

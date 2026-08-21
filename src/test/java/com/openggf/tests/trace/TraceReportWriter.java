package com.openggf.tests.trace;

import com.openggf.trace.DivergenceReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared report-writing helper for the special-stage trace replay bases
 * (S1/S2/S3K). Their {@code writeReport(report, ssIndex)} bodies were identical
 * except for the filename prefix; this hoists the body and parameterizes it by
 * output directory, prefix, and context radius so the three bases cannot drift.
 */
public final class TraceReportWriter {

    private TraceReportWriter() {
    }

    /**
     * Write a divergence report (and, when there are errors, a context window)
     * using {@code prefix} for the on-disk filenames. Mirrors the original
     * special-stage {@code writeReport} body exactly.
     */
    public static void writeSpecialStageReport(DivergenceReport report, Path outDir,
                                               String prefix, int contextRadius) throws IOException {
        assertGroupAccountingHolds(report);
        Files.createDirectories(outDir);
        Path jsonPath = outDir.resolve(prefix + "_report.json");
        Files.writeString(jsonPath, report.toJson());
        if (report.hasErrors()) {
            Path contextPath = outDir.resolve(prefix + "_context.txt");
            int firstErrorFrame = report.errors().isEmpty()
                    ? 0
                    : report.errors().get(0).startFrame();
            Files.writeString(contextPath, report.getContextWindow(firstErrorFrame, contextRadius));
        }
    }

    /**
     * Assert that the per-group error counts a standalone report publishes add up
     * to its flat {@code error_count}. The chain reports assert the same invariant
     * over the same increments; this is the standalone half, asserted while it
     * holds (56 of 56 fixtures at the time of writing) rather than after it stops
     * holding, because the reason the breakdown exists at all is that the chain
     * path had drifted somewhere nobody was checking.
     */
    public static void assertGroupAccountingHolds(DivergenceReport report) {
        assertEquals(report.publishedErrorCount(), report.errorCountByVerificationGroups(),
                "verification groups must account for the report's flat error count exactly");
    }
}

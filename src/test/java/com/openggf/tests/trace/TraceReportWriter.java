package com.openggf.tests.trace;

import com.openggf.trace.DivergenceReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}

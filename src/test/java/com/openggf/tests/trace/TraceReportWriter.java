package com.openggf.tests.trace;

import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceVerificationScope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared report publication for standalone level and special-stage replay drivers.
 * Owns the session-allocated report, context and owner-sidecar paths, including
 * verification-scoped context and retirement of verified earlier standalone attempts.
 */
public final class TraceReportWriter {

    private TraceReportWriter() {
    }

    /** The standalone driver's key convention, including its independent verification scope. */
    static String standaloneLogicalKey(TraceMetadata metadata, TraceVerificationScope scope) {
        return metadata.game() + "_" + metadata.zone() + metadata.act() + standaloneScopeSuffix(scope);
    }

    /** Retires only verified earlier evidence for a standalone method attempt that is now starting. */
    static void retireStandaloneAttemptReports(
            Path outputDirectory, SessionInvocation invocation, TraceVerificationScope scope) throws IOException {
        List<TestSessionOutputPaths.ReportAllocation> owned = usesDefaultOutputDirectory(outputDirectory)
                ? TestSessionOutputPaths.findOwnedReports("trace", invocation, "single")
                : TestSessionOutputPaths.findOwnedReports(outputDirectory, "trace", invocation, "single");
        for (TestSessionOutputPaths.ReportAllocation allocation : owned) {
            if (!hasStandaloneScope(allocation.logicalKey(), scope)) continue;
            Path context = contextPath(allocation.physicalPath());
            if (Files.exists(context, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(context, LinkOption.NOFOLLOW_LINKS)) {
                continue; // A symlink or directory is not this writer's context artifact.
            }
            for (Path artifact : List.of(allocation.physicalPath(), context, allocation.metadataPath())) {
                try {
                    Files.deleteIfExists(artifact);
                } catch (IOException failure) {
                    throw publicationFailure(allocation, artifact, "prior attempt retirement", failure);
                }
            }
        }
    }

    private static String standaloneScopeSuffix(TraceVerificationScope scope) {
        return switch (scope) {
            case ALL -> "";
            case PHYSICS -> "_physics";
            case ANIMATION -> "_animation";
        };
    }

    private static boolean hasStandaloneScope(String logicalKey, TraceVerificationScope scope) {
        String suffix = standaloneScopeSuffix(scope);
        if (!logicalKey.endsWith(suffix)) return false;
        String prefix = logicalKey.substring(0, logicalKey.length() - suffix.length());
        // The unsuffixed key ends in metadata's decimal act number. Requiring
        // that boundary also preserves unknown/noncanonical scope conventions.
        return !prefix.isEmpty() && prefix.charAt(prefix.length() - 1) >= '0'
                && prefix.charAt(prefix.length() - 1) <= '9';
    }

    private static boolean usesDefaultOutputDirectory(Path outputDirectory) {
        return outputDirectory == null || outputDirectory.toAbsolutePath().normalize().equals(
                TestSessionOutputPaths.traceReports().toAbsolutePath().normalize());
    }

    public static TestSessionOutputPaths.ReportAllocation writeSpecialStageReport(
            DivergenceReport report,
            String profile,
            SessionInvocation invocation,
            String laneId,
            String prefix,
            int contextRadius) throws IOException {
        assertGroupAccountingHolds(report);
        return writeReport(report, profile, invocation, laneId, prefix,
                TraceVerificationScope.ALL, contextRadius);
    }

    public static TestSessionOutputPaths.ReportAllocation writeReport(
            DivergenceReport report,
            String profile,
            SessionInvocation invocation,
            String laneId,
            String logicalKey,
            TraceVerificationScope scope,
            int contextRadius) throws IOException {
        return writeReport(null, report, profile, invocation, laneId, logicalKey,
                scope, contextRadius);
    }

    /** Writes to a caller-selected report directory while retaining its legacy basename. */
    public static TestSessionOutputPaths.ReportAllocation writeReport(
            Path outputDirectory,
            DivergenceReport report,
            String profile,
            SessionInvocation invocation,
            String laneId,
            String logicalKey,
            TraceVerificationScope scope,
            int contextRadius) throws IOException {
        return writeReport(outputDirectory, report, profile, invocation, laneId, logicalKey,
                scope, contextRadius, null);
    }

    public static TestSessionOutputPaths.ReportAllocation writeReport(
            Path outputDirectory,
            DivergenceReport report,
            String profile,
            SessionInvocation invocation,
            String laneId,
            String logicalKey,
            TraceVerificationScope scope,
            int contextRadius,
            ReplayGate replayGate) throws IOException {
        if (replayGate != null && replayGate.verification() != scope) {
            throw new IllegalArgumentException("Replay gate verification must match report scope");
        }
        assertGroupAccountingHolds(report);
        TestSessionOutputPaths.ReportAllocation allocation =
                usesDefaultOutputDirectory(outputDirectory)
                        ? TestSessionOutputPaths.allocateReport(profile,
                                invocation.className(), invocation.methodName(),
                                invocation.parameterIndex(), invocation.invocationId(),
                                laneId, logicalKey, ".json")
                        : TestSessionOutputPaths.allocateReport(outputDirectory,
                                profile, invocation.className(), invocation.methodName(),
                                invocation.parameterIndex(), invocation.invocationId(),
                                laneId, logicalKey, ".json");
        publish(allocation, allocation.physicalPath(), report.toJson(replayGate), "report");
        if (report.hasErrors(scope)) {
            Path contextPath = contextPath(allocation.physicalPath());
            publish(allocation, contextPath,
                    report.getContextWindow(report.firstErrorFrame(scope), contextRadius),
                    "context");
        } else {
            Path contextPath = contextPath(allocation.physicalPath());
            try {
                Files.deleteIfExists(contextPath);
            } catch (IOException failure) {
                throw publicationFailure(allocation, contextPath,
                        "stale context retirement", failure);
            }
        }
        try {
            TestSessionOutputPaths.publishOwnerMetadata(allocation);
        } catch (IOException failure) {
            throw publicationFailure(allocation, allocation.metadataPath(), "owner metadata",
                    failure);
        }
        return allocation;
    }

    private static void publish(
            TestSessionOutputPaths.ReportAllocation allocation,
            Path path,
            String content,
            String artifactKind) throws IOException {
        try {
            TestSessionOutputPaths.publish(path, content);
        } catch (IOException failure) {
            throw publicationFailure(allocation, path, artifactKind, failure);
        }
    }

    private static IOException publicationFailure(
            TestSessionOutputPaths.ReportAllocation allocation,
            Path path,
            String artifactKind,
            IOException failure) {
        return new IOException(
                "failed to publish trace " + artifactKind
                        + " for logical key '" + allocation.logicalKey()
                        + "' at " + path,
                failure);
    }

    private static Path contextPath(Path reportPath) {
        String filename = reportPath.getFileName().toString();
        String stem = filename.endsWith(".json")
                ? filename.substring(0, filename.length() - ".json".length())
                : filename;
        String contextName = stem.endsWith("_report")
                ? stem.substring(0, stem.length() - "_report".length()) + "_context.txt"
                : stem + "_context.txt";
        return reportPath.resolveSibling(contextName);
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

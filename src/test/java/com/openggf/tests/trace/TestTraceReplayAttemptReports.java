package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.tests.TestSessionOutputPaths.ReportAllocation;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.s3k.sonictails.TestS3kSonicTailsHczSegmentTraceReplay;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceVerificationScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/** Exercises stale evidence through the real inherited replay entry, including its JUnit lifecycle. */
class TestTraceReplayAttemptReports {
    private static final String SENTINEL = "intentional pre-binder replay failure";
    private static Scenario active;

    @TempDir
    Path temporary;

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void fixtureBuildFailureRetiresPreviousCompletedEvidence() throws Exception {
        exercise(Boundary.AFTER_FIXTURE, TraceVerificationScope.ALL, false);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void bootstrapEntryFailureRetiresPreviousCompletedEvidence() throws Exception {
        exercise(Boundary.BOOTSTRAP_ENTRY, TraceVerificationScope.ALL, false);
    }

    @ParameterizedTest
    @EnumSource(TraceVerificationScope.class)
    @RequiresRom(SonicGame.SONIC_3K)
    void missingFixtureRetiresOnlyThisInvocationAndScope(TraceVerificationScope scope) throws Exception {
        exercise(Boundary.MISSING_FIXTURE, scope, true);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void malformedMetadataRetiresPreviousEvidenceBeforeLoading() throws Exception {
        exercise(Boundary.MALFORMED_METADATA, TraceVerificationScope.ALL, false);
    }

    private void exercise(Boundary boundary, TraceVerificationScope scope, boolean includeUntrusted)
            throws Exception {
        Map<String, String> properties = new HashMap<>();
        for (String key : List.of("openggf.trace.reports", "openggf.trace.fixtureRoot", "trace.verification")) {
            properties.put(key, System.getProperty(key));
        }
        Scenario scenario = new Scenario(boundary, scope, temporary.resolve("reports"),
                temporary.resolve("fixture"), includeUntrusted);
        if (boundary == Boundary.MALFORMED_METADATA) {
            Files.createDirectories(scenario.fixture);
            Files.writeString(scenario.fixture.resolve("metadata.json"), "{broken metadata");
            Files.writeString(scenario.fixture.resolve("physics.csv"), "frame,x,y\n");
        }
        active = scenario;
        try {
            System.setProperty("openggf.trace.reports", scenario.reports.toString());
            System.clearProperty("openggf.trace.fixtureRoot");
            System.setProperty("trace.verification", scope.name().toLowerCase(java.util.Locale.ROOT));
            var listener = new SummaryGeneratingListener();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectMethod(ReplayProbe.class, "replayMatchesTrace")).build(), listener);
            var summary = listener.getSummary();

            assertTrue(scenario.seeded, "the child's real invocation must seed its own previous allocation");
            assertEquals(1, summary.getTestsStartedCount());
            assertEquals(0, summary.getTestsSkippedCount(), "a ROM condition skip is not this entry witness");
            if (boundary == Boundary.MISSING_FIXTURE) {
                assertEquals(1, summary.getTestsAbortedCount());
                assertEquals(0, summary.getTestsFailedCount());
            } else {
                assertEquals(1, summary.getTestsFailedCount(), () -> summary.getFailures().toString());
                if (boundary == Boundary.MALFORMED_METADATA) {
                    assertTrue(summary.getFailures().getFirst().getException() instanceof IOException);
                } else {
                    assertTrue(scenario.boundaryReached, "the real driver must reach the requested pre-binder hook");
                    assertEquals(SENTINEL, summary.getFailures().getFirst().getException().getMessage());
                }
            }
            assertEquals(3, scenario.previous.size(), "seeded report, context and owner must all exist first");
            for (Path path : scenario.previous) {
                assertFalse(Files.exists(path, LinkOption.NOFOLLOW_LINKS), "stale current-attempt artifact: " + path);
            }
            for (var entry : scenario.preserved.entrySet()) {
                assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()),
                        "independent or untrusted evidence changed: " + entry.getKey());
            }
            for (Path path : scenario.symbolicLinks) {
                assertTrue(Files.isSymbolicLink(path), "untrusted link must be preserved: " + path);
            }
        } finally {
            active = null;
            properties.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    private enum Boundary { MISSING_FIXTURE, MALFORMED_METADATA, AFTER_FIXTURE, BOOTSTRAP_ENTRY }

    private static final class Scenario {
        final Boundary boundary;
        final TraceVerificationScope scope;
        final Path reports;
        final Path fixture;
        final boolean includeUntrusted;
        final Set<Path> previous = new HashSet<>();
        final Map<Path, byte[]> preserved = new HashMap<>();
        final List<Path> symbolicLinks = new ArrayList<>();
        boolean seeded;
        boolean boundaryReached;

        Scenario(Boundary boundary, TraceVerificationScope scope, Path reports, Path fixture,
                 boolean includeUntrusted) {
            this.boundary = boundary;
            this.scope = scope;
            this.reports = reports;
            this.fixture = fixture;
            this.includeUntrusted = includeUntrusted;
        }

        void seed() throws IOException {
            SessionInvocation invocation = SessionInvocation.current();
            publish(invocation, "trace", "single", key("s3k_hcz1", scope), scope);
            previous.addAll(files(reports));
            publish(new SessionInvocation(invocation.className() + "Other", invocation.methodName(),
                    invocation.parameterIndex(), invocation.invocationId(), "other class"),
                    "trace", "single", key("s3k_hcz1", scope), scope);
            publish(new SessionInvocation(invocation.className(), invocation.methodName() + "Other",
                    invocation.parameterIndex(), invocation.invocationId(), "other method"),
                    "trace", "single", key("s3k_hcz1", scope), scope);
            publish(new SessionInvocation(invocation.className(), invocation.methodName(),
                    invocation.parameterIndex() + 1, invocation.invocationId(), "other parameter"),
                    "trace", "single", key("s3k_hcz1", scope), scope);
            publish(new SessionInvocation(invocation.className(), invocation.methodName(),
                    invocation.parameterIndex(), "different-invocation", "other invocation"),
                    "trace", "single", key("s3k_hcz1", scope), scope);
            publish(invocation, "trace", "other-lane", key("s3k_hcz1", scope), scope);
            publish(invocation, "special-stage", "single", key("s3k_hcz1", scope), scope);
            for (TraceVerificationScope other : TraceVerificationScope.values()) {
                if (other != scope) publish(invocation, "trace", "single", key("s3k_hcz1", other), other);
            }
            if (includeUntrusted) seedUntrusted(invocation);
            for (Path path : files(reports)) {
                if (!previous.contains(path)) preserved.put(path, Files.readAllBytes(path));
            }
            seeded = true;
        }

        private void seedUntrusted(SessionInvocation invocation) throws IOException {
            ReportAllocation malformed = publish(invocation, "trace", "single", key("s3k_bad1", scope), scope);
            Files.writeString(malformed.metadataPath(), "{broken owner");
            ReportAllocation orphan = publish(invocation, "trace", "single", key("s3k_orphan1", scope), scope);
            Files.delete(orphan.metadataPath());
            Path victim = reports.resolve("unrelated.json");
            Files.writeString(victim, "must survive tampered physical_path and symlinks");
            ReportAllocation tampered = publish(invocation, "trace", "single", key("s3k_tampered1", scope), scope);
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode owner = (ObjectNode) mapper.readTree(tampered.metadataPath().toFile());
            owner.put("physical_path", victim.toString());
            Files.writeString(tampered.metadataPath(), owner.toString());
            ReportAllocation linked = publish(invocation, "trace", "single", key("s3k_linked1", scope), scope);
            Files.delete(linked.physicalPath());
            Files.createSymbolicLink(linked.physicalPath(), victim.toAbsolutePath());
            symbolicLinks.add(linked.physicalPath());
            // A nonregular context also preserves the whole verified triple.
            ReportAllocation linkedContext = publish(invocation, "trace", "single",
                    key("s3k_contextLink1", scope), scope);
            Path contextLink = linkedContext.physicalPath().resolveSibling(
                    linkedContext.physicalPath().getFileName().toString().replace(".json", "_context.txt"));
            Files.delete(contextLink);
            Files.createSymbolicLink(contextLink, victim.toAbsolutePath());
            symbolicLinks.add(contextLink);
            ReportAllocation directoryContext = publish(invocation, "trace", "single",
                    key("s3k_contextDirectory1", scope), scope);
            Path contextDirectory = directoryContext.physicalPath().resolveSibling(
                    directoryContext.physicalPath().getFileName().toString().replace(".json", "_context.txt"));
            Files.delete(contextDirectory);
            Files.createDirectory(contextDirectory);
        }

        private ReportAllocation publish(SessionInvocation invocation, String profile, String lane,
                                         String key, TraceVerificationScope selected) throws IOException {
            DivergenceReport report = new DivergenceReport(List.of(new FrameComparison(7, Map.of(
                    "x", new FieldComparison("x", "0x0100", "0x0101", Severity.ERROR, 1),
                    "player_mapping_frame", FieldComparison.animation(
                            "player_mapping_frame", "0x01", "0x02", Severity.ERROR, 1)))));
            return TraceReportWriter.writeReport(TestSessionOutputPaths.traceReports(), report,
                    profile, invocation, lane, key, selected, 2,
                    new ReplayGate(3574, true, selected));
        }
    }

    private static String key(String prefix, TraceVerificationScope scope) {
        return prefix + switch (scope) {
            case ALL -> "";
            case PHYSICS -> "_physics";
            case ANIMATION -> "_animation";
        };
    }

    private static Set<Path> files(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toSet());
        }
    }

    // The trace profiles exclude *Probe*: this fixture is driven only by the
    // Launcher above, after active is installed. Preserve that naming contract.
    static class ReplayProbe extends TestS3kSonicTailsHczSegmentTraceReplay {
        @BeforeEach
        void seedPreviousEvidence() throws IOException {
            active.seed();
        }

        @Override
        protected Path traceDirectory() {
            return active.boundary == Boundary.MISSING_FIXTURE || active.boundary == Boundary.MALFORMED_METADATA
                    ? active.fixture : super.traceDirectory();
        }

        @Override
        protected void afterFixtureBuild(TraceData trace) {
            if (active.boundary == Boundary.AFTER_FIXTURE) {
                active.boundaryReached = true;
                throw new IllegalStateException(SENTINEL);
            }
        }

        @Override
        protected int overridePreTraceOscFrames() {
            if (active.boundary == Boundary.BOOTSTRAP_ENTRY) {
                active.boundaryReached = true;
                throw new IllegalStateException(SENTINEL);
            }
            return super.overridePreTraceOscFrames();
        }
    }
}

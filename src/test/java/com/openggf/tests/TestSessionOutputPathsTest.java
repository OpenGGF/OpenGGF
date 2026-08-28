package com.openggf.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSessionOutputPathsTest {

    private static final String TRACE_REPORTS = "openggf.trace.reports";
    private static final String DIAGNOSTICS = "openggf.test.diagnostics";
    private static final String ARTIFACT_ROOT = "openggf.artifact.root";

    private final Map<String, String> originalProperties = new HashMap<>();

    @TempDir
    Path tempDir;

    @BeforeEach
    void rememberSessionProperties() {
        for (String name : new String[]{TRACE_REPORTS, DIAGNOSTICS, ARTIFACT_ROOT}) {
            originalProperties.put(name, System.getProperty(name));
            System.clearProperty(name);
        }
    }

    @AfterEach
    void restoreSessionProperties() {
        originalProperties.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    @Test
    void noSessionKeepsLegacyOutputDefaults() {
        assertEquals(Path.of("target", "trace-reports"), TestSessionOutputPaths.traceReports());
        assertEquals(Path.of("target", "diagnostics", "physics"),
                TestSessionOutputPaths.diagnostics("physics"));
        assertEquals(Path.of("target"), TestSessionOutputPaths.artifactRoot());
        assertEquals(Path.of("target", "classes"), TestSessionOutputPaths.compiledClasses());
        assertEquals(Path.of("target", "test-classes"),
                TestSessionOutputPaths.compiledTestClasses());
    }

    @Test
    void outputPathsResolveBeneathSuppliedBuildDirectory() throws IOException {
        Path buildDirectory = Files.createTempDirectory(tempDir, "openggf build output ");
        Path traceReports = buildDirectory.resolve("trace reports");
        Path diagnostics = buildDirectory.resolve("diagnostics");
        Path artifacts = buildDirectory.resolve("artifacts");
        System.setProperty(TRACE_REPORTS, traceReports.toString());
        System.setProperty(DIAGNOSTICS, diagnostics.toString());
        System.setProperty(ARTIFACT_ROOT, artifacts.toString());

        assertEquals(traceReports.normalize(), TestSessionOutputPaths.traceReports(buildDirectory));
        assertEquals(diagnostics.resolve("physics"),
                TestSessionOutputPaths.diagnostics(buildDirectory, "physics"));
        assertEquals(artifacts.normalize(), TestSessionOutputPaths.artifactRoot(buildDirectory));
        assertTrue(TestSessionOutputPaths.traceReports(buildDirectory).startsWith(buildDirectory.toAbsolutePath()));
        assertTrue(TestSessionOutputPaths.diagnostics(buildDirectory, "physics")
                .startsWith(buildDirectory.toAbsolutePath()));
        assertTrue(TestSessionOutputPaths.artifactRoot(buildDirectory)
                .startsWith(buildDirectory.toAbsolutePath()));
    }

    @Test
    void configuredRootsCannotEscapeSuppliedBuildDirectory() throws IOException {
        Path buildDirectory = Files.createTempDirectory(tempDir, "openggf build containment ");
        String outside = "../outside";

        System.setProperty(TRACE_REPORTS, outside);
        assertThrows(IllegalArgumentException.class,
                () -> TestSessionOutputPaths.traceReports(buildDirectory));

        System.setProperty(DIAGNOSTICS, buildDirectory.getParent().resolve("outside").toString());
        assertThrows(IllegalArgumentException.class,
                () -> TestSessionOutputPaths.diagnostics(buildDirectory, "physics"));

        System.setProperty(ARTIFACT_ROOT, outside);
        assertThrows(IllegalArgumentException.class,
                () -> TestSessionOutputPaths.artifactRoot(buildDirectory));
    }

    @Test
    void repeatedAllocationReplacesStaleReportAndPreservesOwnerMetadata() throws IOException {
        Path sessionRoot = Files.createTempDirectory(tempDir, "openggf report owner ");
        System.setProperty(TRACE_REPORTS, sessionRoot.toString());

        TestSessionOutputPaths.ReportAllocation first = TestSessionOutputPaths.allocateReport(
                "trace", "com.openggf.tests.ExampleTest", "replay", 2,
                "0123456789abcdef", "lane-a", "s2_mtz1", ".json");

        assertEquals("s2_mtz1", first.logicalKey());
        assertNotEquals(first.physicalPath(), first.metadataPath());
        assertTrue(first.physicalPath().startsWith(sessionRoot));
        TestSessionOutputPaths.publish(first.physicalPath(), "report");
        TestSessionOutputPaths.publishOwnerMetadata(first);
        assertTrue(Files.isRegularFile(first.metadataPath()),
                "allocation must publish the ownership sidecar atomically");

        TestSessionOutputPaths.ReportAllocation duplicate =
                TestSessionOutputPaths.allocateReport(
                        "trace", "com.openggf.tests.ExampleTest", "replay", 2,
                        "0123456789abcdef", "lane-a", "s2_mtz1", ".json");
        TestSessionOutputPaths.publish(duplicate.physicalPath(), "replacement");
        TestSessionOutputPaths.publishOwnerMetadata(duplicate);

        assertEquals("replacement", Files.readString(duplicate.physicalPath()));
        assertTrue(Files.readString(duplicate.metadataPath()).contains(
                "\"owner_key\": \"" + duplicate.ownerKey() + "\""));
    }

    @Test
    void equivalentPublicationIsIdempotentAndPreservesOwnerMetadata() throws IOException {
        Path sessionRoot = Files.createTempDirectory(tempDir, "openggf report repeat ");
        System.setProperty(TRACE_REPORTS, sessionRoot.toString());

        TestSessionOutputPaths.ReportAllocation allocation =
                TestSessionOutputPaths.allocateReport(
                        "trace", "com.openggf.tests.ExampleTest", "replay", 2,
                        "0123456789abcdef", "lane-a", "s2_mtz1", ".json");

        TestSessionOutputPaths.publish(allocation.physicalPath(), "report");
        TestSessionOutputPaths.publish(allocation.physicalPath(), "report");
        TestSessionOutputPaths.publishOwnerMetadata(allocation);
        TestSessionOutputPaths.publishOwnerMetadata(allocation);

        assertEquals("report", Files.readString(allocation.physicalPath()));
        assertTrue(Files.readString(allocation.metadataPath()).contains(
                "\"owner_key\": \"" + allocation.ownerKey() + "\""));
    }

    @Test
    void pathComponentsCannotEscapeTheirParent() throws IOException {
        Path sessionRoot = Files.createTempDirectory(tempDir, "openggf component safety ");
        System.setProperty(DIAGNOSTICS, sessionRoot.toString());
        System.setProperty(TRACE_REPORTS, sessionRoot.toString());

        assertEquals(sessionRoot.resolve("_.."), TestSessionOutputPaths.diagnostics(".."));
        assertTrue(TestSessionOutputPaths.diagnostics("..").normalize().startsWith(sessionRoot));
        TestSessionOutputPaths.ReportAllocation allocation =
                TestSessionOutputPaths.allocateReport(
                        "..", "com.openggf.tests.ExampleTest", "replay", 0,
                        "0123456789abcdef", "lane", "key", ".json");
        assertTrue(allocation.physicalPath().startsWith(sessionRoot));
    }

    @Test
    void explicitOutputDirectoryRetainsLegacyReportBasename() throws IOException {
        Path outputDirectory = Files.createTempDirectory(tempDir, "openggf explicit report ");
        TestSessionOutputPaths.ReportAllocation allocation =
                TestSessionOutputPaths.allocateReport(outputDirectory,
                        "trace", "com.openggf.tests.ExampleTest", "replay", 2,
                        "0123456789abcdef", "lane-a", "s2_mtz1", ".json");

        assertEquals(outputDirectory.resolve("s2_mtz1_report.json"),
                allocation.physicalPath());
        assertTrue(allocation.metadataPath().startsWith(outputDirectory));
    }
}

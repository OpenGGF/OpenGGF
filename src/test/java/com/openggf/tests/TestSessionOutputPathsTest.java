package com.openggf.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        TestSessionOutputPaths.publish(allocation.physicalPath(), "explicit evidence");
        TestSessionOutputPaths.publishOwnerMetadata(allocation);
        SessionInvocation invocation = new SessionInvocation("com.openggf.tests.ExampleTest", "replay", 2,
                "0123456789abcdef", "explicit owner");
        assertEquals(List.of(allocation), TestSessionOutputPaths.findOwnedReports(
                outputDirectory, "trace", invocation, "lane-a"));
    }

    @Test
    void explicitDirectoryPreservesAnotherOwnerAndLane() throws IOException {
        Path outputDirectory = tempDir.resolve("explicit reports");
        SessionInvocation owner = new SessionInvocation("Example", "replay", 0,
                "invocation-a", "owner A");
        TestSessionOutputPaths.ReportAllocation allocation = TestSessionOutputPaths.allocateReport(
                outputDirectory, "trace", owner.className(), owner.methodName(), owner.parameterIndex(),
                owner.invocationId(), "single", "s3k_hcz1", ".json");
        TestSessionOutputPaths.publish(allocation.physicalPath(), "owner A evidence");
        TestSessionOutputPaths.publishOwnerMetadata(allocation);
        byte[] metadata = Files.readAllBytes(allocation.metadataPath());
        SessionInvocation other = new SessionInvocation(owner.className(), owner.methodName(),
                owner.parameterIndex(), "invocation-b", "owner B");

        assertTrue(TestSessionOutputPaths.findOwnedReports(
                outputDirectory, "trace", other, "single").isEmpty());
        assertTrue(TestSessionOutputPaths.findOwnedReports(
                outputDirectory, "trace", owner, "other-lane").isEmpty());
        assertEquals("owner A evidence", Files.readString(allocation.physicalPath()));
        assertArrayEquals(metadata, Files.readAllBytes(allocation.metadataPath()));
    }

    @Test
    void ownerDiscoveryDoesNotTraverseASymbolicProfileDirectory() throws IOException {
        Path reports = tempDir.resolve("reports");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(reports);
        Files.createDirectories(outside);
        SessionInvocation invocation = new SessionInvocation("Example", "replay", 0,
                "0123456789abcdef", "owner");
        System.setProperty(TRACE_REPORTS, reports.toString());
        Files.createSymbolicLink(reports.resolve("trace"), outside);
        TestSessionOutputPaths.ReportAllocation allocation = TestSessionOutputPaths.allocateReport(
                "trace", invocation.className(), invocation.methodName(), invocation.parameterIndex(),
                invocation.invocationId(), "single", "s3k_hcz1", ".json");
        TestSessionOutputPaths.publish(allocation.physicalPath(), "outside evidence");
        TestSessionOutputPaths.publishOwnerMetadata(allocation);

        assertTrue(TestSessionOutputPaths.findOwnedReports("trace", invocation, "single").isEmpty());
        assertEquals("outside evidence", Files.readString(allocation.physicalPath()));
    }

    @Test
    void ownerDiscoveryRejectsDuplicateMetadataFields() throws IOException {
        Path reports = tempDir.resolve("reports");
        System.setProperty(TRACE_REPORTS, reports.toString());
        SessionInvocation invocation = new SessionInvocation("Example", "replay", 0,
                "0123456789abcdef", "owner");
        TestSessionOutputPaths.ReportAllocation allocation = TestSessionOutputPaths.allocateReport(
                "trace", invocation.className(), invocation.methodName(), invocation.parameterIndex(),
                invocation.invocationId(), "single", "s3k_hcz1", ".json");
        TestSessionOutputPaths.publish(allocation.physicalPath(), "untrusted evidence");
        TestSessionOutputPaths.publishOwnerMetadata(allocation);
        String owner = Files.readString(allocation.metadataPath());
        Files.writeString(allocation.metadataPath(), owner.replaceFirst("\\{", "{\"logical_key\":\"other\","));

        assertTrue(TestSessionOutputPaths.findOwnedReports("trace", invocation, "single").isEmpty());
        assertEquals("untrusted evidence", Files.readString(allocation.physicalPath()));
    }

    @Test
    void ownerDiscoveryDoesNotSelectASidecarsTraversalTarget() throws IOException {
        Path reports = tempDir.resolve("reports");
        System.setProperty(TRACE_REPORTS, reports.toString());
        SessionInvocation invocation = new SessionInvocation("Example", "replay", 0,
                "0123456789abcdef", "owner");
        TestSessionOutputPaths.ReportAllocation allocation = TestSessionOutputPaths.allocateReport(
                "trace", invocation.className(), invocation.methodName(), invocation.parameterIndex(),
                invocation.invocationId(), "single", "s3k_hcz1", ".json");
        TestSessionOutputPaths.publish(allocation.physicalPath(), "owned evidence");
        TestSessionOutputPaths.publishOwnerMetadata(allocation);
        Path victim = reports.resolve("unrelated.json");
        Files.writeString(victim, "foreign evidence");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode owner = (ObjectNode) mapper.readTree(allocation.metadataPath().toFile());
        owner.put("physical_path", reports.resolve("trace/../unrelated.json").toString());
        Files.writeString(allocation.metadataPath(), owner.toString());

        assertTrue(TestSessionOutputPaths.findOwnedReports("trace", invocation, "single").isEmpty());
        assertEquals("foreign evidence", Files.readString(victim));
        assertEquals("owned evidence", Files.readString(allocation.physicalPath()));
    }
}

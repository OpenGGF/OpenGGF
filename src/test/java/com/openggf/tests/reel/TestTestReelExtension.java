package com.openggf.tests.reel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Executes a failing parameterized child, checking recording identity without changing its outcome. */
class TestTestReelExtension {
    @TempDir Path output;

    @Test void ordinaryExecutionDoesNotRequireARecorder() {
        assertNull(System.getProperty("openggf.testReel.dir"));
        var summary = execute();
        assertEquals(2,summary.getTestsStartedCount());
        assertEquals(1,summary.getTestsFailedCount());
        assertFalse(Files.exists(output.resolve("cases")));
    }

    @Test void recordingRetainsBothParameterIdentitiesAndTheRealFailure() throws Exception {
        System.setProperty("openggf.testReel.dir",output.toString());
        try {
            var summary=execute();
            assertEquals(2,summary.getTestsStartedCount()); assertEquals(1,summary.getTestsFailedCount());
            try(var dirs=Files.list(output.resolve("cases"))) {
                var manifests=dirs.map(p->p.resolve("case.json")).toList();
                assertEquals(2,manifests.size());
                var json=new ObjectMapper();
                var first=json.readTree(manifests.get(0).toFile());var second=json.readTree(manifests.get(1).toFile());
                assertNotEquals(first.path("junit_unique_id"),second.path("junit_unique_id"));
                assertNotEquals(first.path("arguments"),second.path("arguments"));
                assertNotEquals(first.path("outcome"),second.path("outcome"));
                for(var row:java.util.List.of(first,second)) {
                    assertEquals(0,row.path("frames").asInt());
                    assertEquals("no-adapter",row.path("capture_status").asText());
                }
            }
        } finally { System.clearProperty("openggf.testReel.dir"); }
    }

    @Test void passingBodyWithFailingTeardownIsNotReportedAsPassed() throws Exception {
        System.setProperty("openggf.testReel.dir",output.toString());
        try {
            var listener=new SummaryGeneratingListener();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(TeardownFailureProbe.class)).build(),listener);
            assertEquals(1,listener.getSummary().getTestsFailedCount());
            try(var dirs=Files.list(output.resolve("cases"))) {
                var path=dirs.findFirst().orElseThrow().resolve("case.json");
                var row=new ObjectMapper().readTree(path.toFile());
                assertEquals("passed",row.path("body_outcome").asText());
                assertEquals("failed",row.path("outcome").asText());
                assertTrue(row.path("detail").asText().contains("intentional teardown failure"));
            }
        } finally { System.clearProperty("openggf.testReel.dir"); }
    }
    static class TeardownFailureProbe {
        @Test void bodyPasses() { }
        @AfterEach void teardownFails() { fail("intentional teardown failure"); }
    }

    @Test void menuObservationDoesNotRequireAPlayerOrObjectManager() {
        com.openggf.tests.TestEnvironment.resetAll();
        var row=TestReelCapture.observeState(0,"TITLE_SCREEN",null);
        assertEquals(false,row.get("player_present"));
        assertEquals(false,row.get("objects_present"));
        assertNull(row.get("x")); assertNull(row.get("vint"));
        assertEquals("TITLE_SCREEN",row.get("mode"));
    }

    private org.junit.platform.launcher.listeners.TestExecutionSummary execute() {
        var listener=new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Probe.class)).build(),listener);
        return listener.getSummary();
    }
    static class Probe {
        @ParameterizedTest @ValueSource(booleans={false,true})
        void actualControl(boolean failure) { assertFalse(failure,"intentional child failure"); }
    }
}

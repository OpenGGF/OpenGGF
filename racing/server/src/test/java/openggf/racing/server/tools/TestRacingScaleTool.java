package openggf.racing.server.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Bounded two-room process/lifecycle coverage; capacity measurements stay explicit CLI work. */
class TestRacingScaleTool {
    @Test
    void rejectsShapesThatCouldAccidentallyLaunchAnUnboundedMatrix() {
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--rooms", "3"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--players", "257"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--duration", "601"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--warmup", "0"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--browser-ms", "1999"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--players", "2", "--players", "4"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--rooms"}));
        assertThrows(IllegalArgumentException.class, () -> RacingScaleTool.parseArgs(new String[]{"--transport", "jdk"}));
        assertEquals(1, RacingScaleTool.parseArgs(new String[0]).rooms());
    }

    @Test
    void latencySummaryRetainsTailAndDoesNotPretendAnEmptyProbeHasSamples() {
        RacingScaleMetrics.Latencies samples = new RacingScaleMetrics.Latencies();
        assertEquals(0, samples.report().samples());
        for (int i = 1; i <= 100; i++) samples.add(i * 1_000_000L);
        var report = samples.report();
        assertEquals(100, report.samples());
        assertEquals(50, report.p50Millis());
        assertEquals(95, report.p95Millis());
        assertEquals(99, report.p99Millis());
        assertEquals(100, report.maxMillis());
    }

    @Test
    @Timeout(100)
    void isolatedRoomWorkersFinishRecoverAndReleaseTheServer(@TempDir Path dir) throws Exception {
        var config = new RacingScaleTool.Config(2, 2, 1, 1, 20, 2_000, 1);
        var report = RacingScaleTool.run(config, dir);
        assertTrue(report.success(), report::toString);
        assertTrue(report.childrenTerminated());
        assertEquals(2, report.loads().size());
        assertNotEquals(ProcessHandle.current().pid(), report.server().process().pid());
        assertEquals(0, report.server().roomsAfterDeparture());
        assertTrue(report.server().brokerDispatchLateness().samples() > 0);
        assertEquals(2, report.server().roomTicks().size());
        for (var load : report.loads()) {
            assertNotEquals(report.server().process().pid(), load.process().pid());
            assertEquals(2, load.finishedPlayers());
            assertTrue(load.overflowRejected());
            assertTrue(load.reconnected());
            assertTrue(load.removedFromBrowser());
            assertTrue(load.proxyTerminated());
            assertTrue(load.measuredUpstreamBytes() > 0);
            assertTrue(load.measuredDownstreamBytes() > 0);
            assertTrue(load.receivedGhostFrames() > 0);
            assertTrue(load.browserRequests() > 0);
            assertTrue(load.admissionConnections() > 0);
        }
        try (var children = Files.list(dir)) {
            assertEquals(0, children.count(), "owned identities, child output and recordings must be removed");
        }
    }
}

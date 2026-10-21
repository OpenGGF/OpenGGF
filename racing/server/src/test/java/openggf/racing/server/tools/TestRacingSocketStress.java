package openggf.racing.server.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small real-socket lifecycle smoke; maximum capacity and long soaks are explicit CLI runs. */
@Timeout(90)
class TestRacingSocketStress {
    @Test
    void rejectsUnknownDuplicateMissingAndUnsupportedOptions() {
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--wat", "1"}));
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--players"}));
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--players", "2", "--players", "3"}));
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--players", "257"}));
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--players", "9", "--transport", "jdk"}));
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--duration", "601"}));
        assertThrows(IllegalArgumentException.class, () -> RacingSocketStressTool.parseArgs(new String[]{"--cycles", "0"}));
        assertEquals(RacingSocketStressTool.Config.smoke().players(), RacingSocketStressTool.parseArgs(new String[0]).players());
    }

    @Test
    void idleMembersKeepOneBoundedPingAliveBeforeTheirAttemptStarts() {
        long second = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        // The schedule has no attempt-state gate: a member idling for minutes still sends.
        assertTrue(RacingSocketStressTool.keepaliveDue(second, 0, false));
        assertFalse(RacingSocketStressTool.keepaliveDue(second + second / 2, second, false));
        assertTrue(RacingSocketStressTool.keepaliveDue(2 * second, second, false));
        assertTrue(RacingSocketStressTool.keepaliveDue(120 * second, second, false));
        assertFalse(RacingSocketStressTool.keepaliveDue(120 * second, second, true));
    }

    @Test
    void interruptedProxyCloseStillReleasesItsSocketsAndThreads() throws Exception {
        try (java.net.ServerSocket target = new java.net.ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"));
             LoopbackSourceProxy proxy = new LoopbackSourceProxy(target.getLocalPort(), 1);
             java.net.Socket client = new java.net.Socket("127.0.0.1", proxy.port())) {
            target.setSoTimeout(3000);
            try (java.net.Socket upstream = target.accept()) {
                upstream.setSoTimeout(3000);
                client.getOutputStream().write(7);
                assertEquals(7, upstream.getInputStream().read(), "actual proxy pump must be running");
                Thread.currentThread().interrupt();
                try {
                    assertThrows(InterruptedException.class, proxy::close);
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
                assertTrue(proxy.isTerminated());
                assertEquals(0, proxy.openSockets());
                upstream.setSoTimeout(3000);
                assertEquals(-1, upstream.getInputStream().read());
            }
        }
    }

    @Test
    void laterFailurePreservesFirstObservedFinishesWithoutCountingBroadcastDuplicates() {
        RacingSocketStressTool.Totals totals = new RacingSocketStressTool.Totals(RacingSocketStressTool.Config.smoke());
        totals.admitted = 4;
        totals.recordFinish("room-session-a");
        totals.recordFinish("room-session-a");
        totals.recordFinish("room-session-b");
        totals.fail(new IllegalStateException("remaining finish timed out"));
        RacingSocketStressTool.Report report = totals.report();
        assertFalse(report.success());
        assertEquals(4, report.admittedPlayers());
        assertEquals(2, report.finishedPlayers());
        assertEquals(4, report.expectedFinishes());
        assertEquals(1, report.unexpectedFailures());
    }

    @Test
    void infrastructureFailureIsCountedAndCannotPass(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("not-a-directory"), "owned test input");
        RacingSocketStressTool.Report report = RacingSocketStressTool.run(RacingSocketStressTool.Config.smoke(), file);
        assertFalse(report.success());
        assertEquals(1, report.unexpectedFailures());
        assertEquals(0, report.admittedPlayers());
        assertEquals(0, report.finishedPlayers());
        assertFalse(report.failures().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> report.failures().add("mutation"));
    }

    @ParameterizedTest
    @EnumSource(RacingSocketStressTool.Transport.class)
    void smallProductionClientsCompleteAndRecoverCapacity(RacingSocketStressTool.Transport transport,
                                                         @TempDir Path dir) throws Exception {
        RacingSocketStressTool.Config config = new RacingSocketStressTool.Config(
                4, 1, 1, 4675398, RacingSocketStressTool.Mix.MIXED, transport, 15);
        RacingSocketStressTool.Report report = RacingSocketStressTool.run(config, dir);
        assertTrue(report.success(), report::toString);
        assertEquals(0, report.unexpectedFailures());
        assertEquals(4, report.requestedPlayers());
        assertEquals(4, report.admittedPlayers());
        assertEquals(4, report.peakAdmittedConcurrency());
        assertEquals(4, report.expectedFinishes());
        assertEquals(4, report.finishedPlayers());
        assertEquals(1, report.expectedRejections());
        assertEquals(1, report.expectedSanctions());
        assertEquals(2, report.reconnects());
        assertEquals(1, report.roundResets());
        assertEquals(1, report.verifiedShutdowns());
        assertEquals(transport == RacingSocketStressTool.Transport.RELAY ? 1 : 0, report.removedRooms());
        assertTrue(report.sentGhostFrames() >= 120);
        assertTrue(report.sentBinaryMessages() > 0);
        assertTrue(report.receivedGhostFrames() > 0);
        assertTrue(report.receivedGhostEntries() > 0);
        assertTrue(report.sentControlMessages() > 0);
        assertTrue(report.receivedControlMessages() > 0);
        assertTrue(report.measuredTcpUpstreamBytes() > 0);
        assertTrue(report.measuredTcpDownstreamBytes() > 0);
        assertTrue(report.pingSamples() >= 4);
        // No absolute throughput/latency/heap threshold is a correctness condition.
        assertTrue(report.measuredElapsedSeconds() > 0);
        try (var children = Files.list(dir)) {
            assertEquals(0, children.count(), "owned identities/databases must be cleaned after the run");
        }
    }
}

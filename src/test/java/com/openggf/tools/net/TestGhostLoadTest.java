package com.openggf.tools.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
class TestGhostLoadTest {
    @Test
    void thirtyTwoBotsKeepHubWithinTickBudget(@TempDir Path dir) throws Exception {
        GhostLoadTestTool.LoadReport report = GhostLoadTestTool.run(32,
                GhostLoadTestTool.Behavior.NORMAL, Duration.ofSeconds(5), dir);
        assertTrue(report.meanTickMillis() < 50.0,
                "mean hub tick " + report.meanTickMillis() + "ms");
        assertTrue(report.p99TickMillis() < 50.0,
                "p99 hub tick " + report.p99TickMillis() + "ms");
        assertTrue(report.healthyClientsFinished() >= 30);
    }

    @Test
    void adversariesAreCaughtAndHealthyClientsUnaffected(@TempDir Path dir) throws Exception {
        GhostLoadTestTool.LoadReport report = GhostLoadTestTool.run(16,
                GhostLoadTestTool.Behavior.ADVERSARIAL_MIX, Duration.ofSeconds(5), dir);
        assertTrue(report.adversariesSanctioned() > 0);
        assertTrue(report.healthyClientsFinished() > 0);
        assertTrue(report.meanTickMillis() < 50.0);
    }
}

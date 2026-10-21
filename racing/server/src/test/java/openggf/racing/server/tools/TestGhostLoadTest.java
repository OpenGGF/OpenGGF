package openggf.racing.server.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
class TestGhostLoadTest {
    @Test
    void thirtyTwoBotsCompleteTheirAttempts(@TempDir Path dir) throws Exception {
        GhostLoadTestTool.LoadReport report = GhostLoadTestTool.run(32,
                GhostLoadTestTool.Behavior.NORMAL, Duration.ofSeconds(5), dir);
        assertEquals(32, report.healthyClientsFinished());
        assertEquals(0, report.adversariesSanctioned());
        assertTrue(report.maxEmittedPacketBytes() > 0);
    }

    @Test
    void adversariesAreCaughtAndHealthyClientsUnaffected(@TempDir Path dir) throws Exception {
        GhostLoadTestTool.LoadReport report = GhostLoadTestTool.run(16,
                GhostLoadTestTool.Behavior.ADVERSARIAL_MIX, Duration.ofSeconds(5), dir);
        assertEquals(5, report.adversariesSanctioned());
        assertEquals(11, report.healthyClientsFinished());
    }
}

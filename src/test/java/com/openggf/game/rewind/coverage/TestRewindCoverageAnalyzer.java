package com.openggf.game.rewind.coverage;

import com.openggf.game.GameId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestRewindCoverageAnalyzer {
    @Test
    void enumeratesSpawnableObjectsForS3k() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K);
        assertFalse(report.objects().isEmpty(), "must enumerate S3K spawnable objects");
        assertTrue(report.objects().stream()
                .anyMatch(o -> o.className().endsWith("AizLrzRockObjectInstance")),
                "AizLrzRock is a known S3K spawnable object");
    }
}

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

    @Test
    void flagsUncapturedFinalScalarField() {
        RewindCoverageReport report = RewindCoverageAnalyzer.analyze(GameId.S3K);
        // AutomaticTunnelObjectInstance has `private final int subtype` (no @RewindTransient /
        // @RewindDeferred). It is a concrete AbstractObjectInstance subclass in the LBZ/S3K route.
        // GenericFieldCapturer skips it solely because it is final — this is the gap we detect.
        ObjectCoverage cov = report.objects().stream()
                .filter(o -> o.className().endsWith("AutomaticTunnelObjectInstance"))
                .findFirst().orElseThrow();
        assertTrue(cov.uncapturedFinalScalarFields().contains("subtype"),
                "final non-transient scalar must be reported as uncaptured");
    }
}

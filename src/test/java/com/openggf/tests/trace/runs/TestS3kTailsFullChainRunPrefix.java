package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/** Holds the verified opening of the Tails route independently of later failures. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("trace-scope-r7")
class TestS3kTailsFullChainRunPrefix extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s3k", "runs",
            "s3k-tails-full-chain-all-emeralds");

    /** Includes all AIZ rows, production load camera state and the giant-ring handoff. */
    @Test
    void aiz1ThroughGiantRingIntoFirstSpecialStageRow() throws Exception {
        assertChainReplayThroughSegmentRow(RUN_DIR, 1, 1);
    }

    /** Defends the complete first return before entering the second special stage. */
    @Test
    void firstSpecialStageReturnThroughSecondEntry() throws Exception {
        assertChainReplayThroughSegmentRow(RUN_DIR, 3, 1);
    }
}

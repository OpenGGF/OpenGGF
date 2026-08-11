package com.openggf.tests.trace.runs;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Chain integration test for the committed
 * {@code s3k-sonic-tails-complete-emeralds} run: 63 segments carrying
 * Sonic and Tails from AIZ1 to DDZ, through fourteen special stages and six
 * bonus stages (gumball, slots and pachinko), collecting all seven chaos
 * emeralds. Drives ONE continuous {@code GameLoop} through the segments via
 * the shared {@link AbstractRunChainTest} base.
 *
 * <p>This is the third committed S3K route, and the first Sonic+Tails one to
 * run end to end: {@link TestS3kKnucklesSuperEmeraldRunChain} takes the
 * Knuckles super-emerald route and {@code TestS3kMegaRunChain} a shorter one.
 * A defect that only shows on a two-character party, or only on the emerald
 * (rather than super-emerald) special-stage returns, has had no chain test
 * until now.
 *
 * <p><b>This is a new end-to-end frontier harness and is expected RED.</b> S3K
 * parity work is incomplete, so the honest expectation is that it stops early;
 * it was added deliberately, to say WHERE, rather than as a regression.
 * Nothing here is weakened, tolerance-fitted or trimmed to reach a green. See
 * the entry in {@code docs/status/trace-frontier-log.md} for the measured stop
 * point and first error.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSonicTailsCompleteEmeraldRunChain extends AbstractRunChainTest {

    private static final Path RUN_DIR = Path.of(
            "src", "test", "resources", "traces", "s3k", "runs",
            "s3k-sonic-tails-complete-emeralds");

    /**
     * The whole route, end to end. This is the frontier the run exists to
     * measure; raise nothing and skip nothing to keep it quiet.
     */
    @Test
    void aiz1ToDoomsdayAcrossEverySpecialAndBonusStage() throws Exception {
        assertChainReplay(RUN_DIR);
    }
}

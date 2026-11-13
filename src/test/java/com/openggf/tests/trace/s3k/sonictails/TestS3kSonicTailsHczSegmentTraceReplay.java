package com.openggf.tests.trace.s3k.sonictails;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Compared replay of segment {@code hcz} of the committed
 * {@code s3k-sonic-tails-complete-emeralds} run: recorded act 1 maps to engine act
 * index 0. It begins at the start-table boundary and ends at the giant-ring
 * transition into {@code ss_5}; the later {@code hcz_2} segment spans the act change.
 *
 * <p>The current selection lane is {@code trace-segments} in {@code pom.xml};
 * {@code docs/status/trace-scope-release-6.md} records historical scope.
 * Dated measurements and first
 * errors belong in {@code docs/status/trace-frontier-log.md}; this fixture
 * declaration does not duplicate mutable pass/fail status. For the focused
 * command and result checks, see the trace diagnosis entry in
 * {@code docs/guide/contributing/testing.md}.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSonicTailsHczSegmentTraceReplay extends AbstractTraceReplayTest {
    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_3K;
    }

    @Override
    protected int zone() {
        return 1;
    }

    @Override
    protected int act() {
        return 0;
    }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/hcz");
    }
}

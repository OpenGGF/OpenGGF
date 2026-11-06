package com.openggf.tests.trace.s3k;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Strict replay of the committed {@code hpz_completerun} zone slice.
 * The shared driver compares the recorded route without trimming divergent rows;
 * this is a standalone slice, not certification of the full campaign.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kHpzCompleteRunTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_3K; }
    @Override protected int zone() { return 10; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/hpz_completerun");
    }
}

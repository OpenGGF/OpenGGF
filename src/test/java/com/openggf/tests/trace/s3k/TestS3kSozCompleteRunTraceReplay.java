package com.openggf.tests.trace.s3k;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import java.nio.file.Path;

/**
 * Independent ordinary-input SOZ recording, without a transformation that
 * requires progression from earlier campaign segments. Both acts and the exit
 * remain fully compared; this frontier harness does not trim failing rows.
 * See docs/status/trace-frontier-log.md for measured status and coverage limits.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSozCompleteRunTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_3K; }
    @Override protected int zone() { return 8; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/soz_completerun");
    }
}

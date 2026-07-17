package com.openggf.tests.trace.s3k;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import com.openggf.trace.TraceEvent;

import java.nio.file.Path;

/**
 * S3K FBZ from the Sonic+Tails complete-run TAS. The per-zone segment covers
 * act 1, the seamless act transition, act 2, and the SOZ exit handoff.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kFbzCompleteRunTraceReplay extends AbstractTraceReplayTest {
    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_3K;
    }

    @Override
    protected int zone() {
        return 4;
    }

    @Override
    protected int act() {
        return 0;
    }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/fbz_completerun");
    }

    @Override
    protected boolean compareObjectNearEvents() {
        return true;
    }

    @Override
    protected boolean shouldCompareObjectNearEvent(TraceEvent.ObjectNear near) {
        return near.objectType() != null && near.objectType().toUpperCase().endsWith("3CF90");
    }

}

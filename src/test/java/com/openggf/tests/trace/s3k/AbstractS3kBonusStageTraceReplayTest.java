package com.openggf.tests.trace.s3k;

import com.openggf.trace.TraceData;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.trace.AbstractTraceReplayTest;

/**
 * Shared base for S3K bonus-stage trace replay (gumball/pachinko).
 * Bonus zones run on the LEVEL pipeline, so the entire level replay stack
 * applies; the only addition is the bonus-entry bootstrap after load.
 */
public abstract class AbstractS3kBonusStageTraceReplayTest extends AbstractTraceReplayTest {

    @Override
    protected void afterFixtureBuild(TraceData trace) {
        TraceReplaySessionBootstrap.applyBonusStageEntry(trace);
    }
}

package com.openggf.tests.trace.s2;

import java.nio.file.Path;

/**
 * Concrete S2 special-stage trace replay against the committed MVP trace at
 * {@code src/test/resources/traces/s2/special_stage} (Special Stage 1,
 * Sonic + Tails). Red-allowed: divergences are recorded to the report, not
 * asserted. See {@link AbstractS2SpecialStageTraceReplayTest}.
 */
class TestS2SpecialStageTraceReplay extends AbstractS2SpecialStageTraceReplayTest {

    @Override
    protected Path traceDirectory() {
        return TRACE_DIRECTORY;
    }
}

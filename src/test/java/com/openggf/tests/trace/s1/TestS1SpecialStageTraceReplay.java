package com.openggf.tests.trace.s1;

import java.nio.file.Path;

/**
 * Replays the committed S1 special-stage (maze) trace in
 * {@code traces/s1/runs/s1-ghz-maze-roundtrip/ss}. See tools/tracechaser/docs/capture-s1.md for the recording
 * procedure.
 */
class TestS1SpecialStageTraceReplay extends AbstractS1SpecialStageTraceReplayTest {
    @Override
    protected Path traceDirectory() {
        return Path.of(System.getProperty(
                "openggf.trace.candidate.dir",
                TRACE_DIRECTORY.toString()));
    }
}

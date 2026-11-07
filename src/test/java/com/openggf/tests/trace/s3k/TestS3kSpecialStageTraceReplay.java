package com.openggf.tests.trace.s3k;

import com.openggf.trace.DivergenceReport;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Concrete S3K special-stage replay of the canonical Mega run SS segment.
 * The standalone payload was an identical copy of
 * {@code traces/s3k/runs/s3-knux-multibonus-ss/ss}.
 */
class TestS3kSpecialStageTraceReplay extends AbstractS3kSpecialStageTraceReplayTest {

    @Override
    protected Path traceDirectory() {
        return TRACE_DIRECTORY;
    }

    @Test
    void releaseRatchetRejectsTierOneError() {
        DivergenceReport report = new DivergenceReport(List.of(new FrameComparison(0, Map.of(
                "started", new FieldComparison("started", "true", "false",
                Severity.ERROR, 1)))));

        assertThrows(AssertionFailedError.class,
                () -> assertNoReleaseBlockingDivergences(report));
    }
}

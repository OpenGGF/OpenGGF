package com.openggf.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the held-rewind memory leak: comparing the same frame
 * multiple times (as happens when {@code SegmentCache} rebuilds a segment
 * during held rewind in test mode) must not grow {@link TraceBinder}'s
 * internal comparison store. Memory is bounded by the trace length.
 */
class TestTraceBinderComparisonDedup {

    private static final ToleranceConfig TOLERANCES =
            new ToleranceConfig(1, 4, 1, 4, false, 1, 4);

    @Test
    void repeatedComparisonsOfSameFrameDoNotAccumulate() throws Exception {
        TraceBinder binder = new TraceBinder(TOLERANCES);
        TraceFrame expected = TraceFrame.of(
                42, 0, (short) 0x1000, (short) 0x0200,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);

        // Simulate held-rewind segment-cache rebuilds: same frame compared
        // many times. Each call would have appended to allComparisons before
        // the fix; with dedup by frame key, the entry is replaced in place.
        for (int i = 0; i < 100; i++) {
            binder.compareFrame(expected,
                    expected.x(), expected.y(),
                    expected.xSpeed(), expected.ySpeed(), expected.gSpeed(),
                    expected.angle(), expected.air(), expected.rolling(),
                    expected.groundMode());
        }

        DivergenceReport report = binder.buildReport();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(report.toJson());
        assertEquals(1, root.get("total_frames").asInt(),
                "100 comparisons of the same frame must produce a single retained entry");
    }

    @Test
    void distinctFramesEachRetainOneEntry() throws Exception {
        TraceBinder binder = new TraceBinder(TOLERANCES);
        for (int frame = 0; frame < 50; frame++) {
            TraceFrame expected = TraceFrame.of(
                    frame, 0, (short) (0x1000 + frame), (short) 0x0200,
                    (short) 0, (short) 0, (short) 0,
                    (byte) 0, false, false, 0);
            binder.compareFrame(expected,
                    expected.x(), expected.y(),
                    expected.xSpeed(), expected.ySpeed(), expected.gSpeed(),
                    expected.angle(), expected.air(), expected.rolling(),
                    expected.groundMode());
        }

        DivergenceReport report = binder.buildReport();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(report.toJson());
        assertEquals(50, root.get("total_frames").asInt(),
                "50 distinct frames must each retain a single entry");
    }

    @Test
    void laterComparisonOfSameFrameOverwritesEarlier() throws Exception {
        TraceBinder binder = new TraceBinder(TOLERANCES);
        TraceFrame expected = TraceFrame.of(
                7, 0, (short) 0x1000, (short) 0x0200,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);

        // First call: matching values (no divergence).
        binder.compareFrame(expected,
                expected.x(), expected.y(),
                expected.xSpeed(), expected.ySpeed(), expected.gSpeed(),
                expected.angle(), expected.air(), expected.rolling(),
                expected.groundMode());

        // Second call: x differs by enough to trigger an ERROR-severity divergence.
        binder.compareFrame(expected,
                (short) (expected.x() + 0x100), expected.y(),
                expected.xSpeed(), expected.ySpeed(), expected.gSpeed(),
                expected.angle(), expected.air(), expected.rolling(),
                expected.groundMode());

        DivergenceReport report = binder.buildReport();
        assertEquals(true, report.hasErrors(),
                "Second comparison's ERROR must override the first comparison's MATCH");
    }
}

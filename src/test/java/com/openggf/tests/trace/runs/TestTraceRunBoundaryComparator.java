package com.openggf.tests.trace.runs;

import com.openggf.trace.FrameComparison;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunBoundaryComparator;
import com.openggf.trace.replay.runs.TraceRunBoundaryComparator.ActualBoundary;
import com.openggf.trace.replay.runs.TraceRunBoundaryComparator.ExpectedBoundary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunBoundaryComparator {

    @Test
    void positionalReturnComparesFrameZeroCentreRingsAndRecordedEmeraldProgression() {
        ExpectedBoundary expected = expected(
                transition("starpost_special", 320, 170, null, 42, 0, null),
                stageExit(0, 1),
                level(0, 1), level(0, 1), frame(320, 173), 0);
        ActualBoundary actual = new ActualBoundary(
                320, 173, 0, 0, 0, 0, 99, false);

        FrameComparison comparison = TraceRunBoundaryComparator.compare(
                9701, expected, actual);

        assertFalse(comparison.hasError(), comparison.divergentFields()::toString);
        assertTrue(comparison.fields().containsKey("run_boundary.position.x"));
        assertTrue(comparison.fields().containsKey("run_boundary.position.y"));
        assertTrue(comparison.fields().containsKey("run_boundary.emeralds.recorded_progression"));
        assertFalse(comparison.fields().containsKey("run_boundary.emeralds.live"));
    }

    @Test
    void checkpointReturnRequiresCheckpointAndFrameZeroPosition() {
        ExpectedBoundary expected = expected(
                transition("starpost_bonus", 400, 220, 3, 12, null, 4),
                stageExit(12, 4),
                level(5, 1), level(5, 1), frame(404, 224), 5);
        ActualBoundary actual = new ActualBoundary(
                404, 224, 2, 5, 0, 12, 4, true);

        FrameComparison comparison = TraceRunBoundaryComparator.compare(
                200, expected, actual);

        assertTrue(comparison.hasErrorInField("run_boundary.checkpoint"));
        assertFalse(comparison.hasErrorInField("run_boundary.position.x"));
        assertFalse(comparison.hasErrorInField("run_boundary.position.y"));
    }

    @Test
    void nextActReturnComparesAdvanceIdentityAndLiveEmeralds() {
        ExpectedBoundary expected = expected(
                transition("giant_ring", null, null, null, 50, 2, null),
                stageExit(0, 3),
                level(1, 1), level(1, 2), frame(999, 999), 7);
        ActualBoundary actual = new ActualBoundary(
                1, 2, 0, 7, 1, 0, 3, true);

        FrameComparison comparison = TraceRunBoundaryComparator.compare(
                350, expected, actual);

        assertFalse(comparison.hasError(), comparison.divergentFields()::toString);
        assertTrue(comparison.fields().containsKey("run_boundary.next_act.manifest_advance"));
        assertTrue(comparison.fields().containsKey("run_boundary.next_act.zone"));
        assertTrue(comparison.fields().containsKey("run_boundary.next_act.act"));
        assertTrue(comparison.fields().containsKey("run_boundary.emeralds.live"));
        assertFalse(comparison.fields().containsKey("run_boundary.position.x"));
    }

    @Test
    void giantRingWithSavedPositionUsesS3kPositionPolicyNotNextActPolicy() {
        ExpectedBoundary expected = expected(
                transition("giant_ring", 800, 300, null, 20, 6, null),
                stageExit(0, 7),
                level(2, 1), level(2, 1), frame(801, 302), 2);
        ActualBoundary actual = new ActualBoundary(
                801, 302, 0, 99, 99, 0, 7, true);

        FrameComparison comparison = TraceRunBoundaryComparator.compare(
                500, expected, actual);

        assertFalse(comparison.hasError(), comparison.divergentFields()::toString);
        assertTrue(comparison.fields().containsKey("run_boundary.position.x"));
        assertFalse(comparison.fields().containsKey("run_boundary.next_act.zone"));
    }

    @Test
    void uncomparedInteriorReportsImpossibleRecordedEmeraldDelta() {
        ExpectedBoundary expected = expected(
                transition("starpost_special", 320, 170, null, 42, 2, null),
                stageExit(0, 5),
                level(0, 1), level(0, 1), frame(320, 173), 0);
        ActualBoundary actual = new ActualBoundary(
                320, 173, 0, 0, 0, 0, 5, false);

        FrameComparison comparison = TraceRunBoundaryComparator.compare(
                9701, expected, actual);

        assertTrue(comparison.hasErrorInField(
                "run_boundary.emeralds.recorded_progression"));
    }

    private static ExpectedBoundary expected(
            TraceRunManifest.Transition entry,
            TraceRunManifest.Transition exit,
            TraceRunManifest.Segment preEntry,
            TraceRunManifest.Segment returned,
            TraceFrame returnFrameZero,
            int resolvedReturnZone) {
        return new ExpectedBoundary(entry, exit, preEntry, returned,
                returnFrameZero, resolvedReturnZone);
    }

    private static TraceRunManifest.Transition transition(
            String kind, Integer savedX, Integer savedY,
            Integer checkpoint, Integer ringsBefore,
            Integer emeraldsBefore, Integer emeraldsAfter) {
        return new TraceRunManifest.Transition(
                0, 1, kind, 100, 1, savedX, savedY, checkpoint,
                ringsBefore, null, emeraldsBefore, emeraldsAfter);
    }

    private static TraceRunManifest.Transition stageExit(
            Integer ringsAfter, Integer emeraldsAfter) {
        return new TraceRunManifest.Transition(
                1, 2, "stage_exit", 200, null, null, null, null,
                null, ringsAfter, null, emeraldsAfter);
    }

    private static TraceRunManifest.Segment level(int zone, int act) {
        return new TraceRunManifest.Segment(
                "level-" + zone + "-" + act, "level", "gameplay_unlock",
                0, 1, zone, act, null, null);
    }

    private static TraceFrame frame(int x, int y) {
        return TraceFrame.of(0, 0, (short) x, (short) y,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);
    }
}

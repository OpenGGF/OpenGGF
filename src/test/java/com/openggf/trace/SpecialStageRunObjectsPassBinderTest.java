package com.openggf.trace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialStageRunObjectsPassBinderTest {

    @Test
    void duplicateObservationFramePassesBindToLatestCompletedAtomicState() {
        SpecialStageRunObjectsPassBinder binder = new SpecialStageRunObjectsPassBinder(List.of(
                pass(434, 0, 434),
                pass(434, 1, 434)));

        assertTrue(binder.nextForExecutedFrame(433).isEmpty());
        assertEquals(1, sequence(binder.nextForExecutedFrame(434).orElseThrow()));
        assertTrue(binder.nextForExecutedFrame(436).isEmpty());
    }

    @Test
    void laterPassDoesNotBindBeforeItsRecordedEligibilityCursor() {
        SpecialStageRunObjectsPassBinder binder = new SpecialStageRunObjectsPassBinder(
                List.of(pass(429, 0, 428)));

        assertTrue(binder.nextForExecutedFrame(427).isEmpty());
        assertEquals(0, sequence(binder.nextForExecutedFrame(429).orElseThrow()));
        assertFalse(binder.hasRemaining());
    }

    @Test
    void sequenceGapIsRejectedBeforeComparison() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SpecialStageRunObjectsPassBinder(List.of(
                        pass(434, 0, 434), pass(436, 2, 436))));

        assertTrue(error.getMessage().contains("sequence discontinuity"));
    }

    @Test
    void bindingBeforeFirstEligibleFrameIsRejectedBeforeComparison() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SpecialStageRunObjectsPassBinder(
                        List.of(pass(434, 0, 435))));

        assertTrue(error.getMessage().contains("precedes first eligible"));
    }

    private static TraceEvent.StateSnapshot pass(int frame, int sequence, int firstEligible) {
        return new TraceEvent.StateSnapshot(frame, Map.of(
                "type", "run_objects_end",
                "pass_sequence", sequence,
                "first_eligible_frame", firstEligible));
    }

    private static int sequence(TraceEvent.StateSnapshot snapshot) {
        return ((Number) snapshot.fields().get("pass_sequence")).intValue();
    }
}

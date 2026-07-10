package com.openggf.tests.trace.s2;

import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState.PlayerState;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.SpecialStageExpectedState;
import com.openggf.trace.SpecialStageTraceFrame;
import com.openggf.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S2SpecialStageExpectedComparisonTest {

    @Test
    void f799EngineThreeRingsMatchesAtomicRunObjectsEndInsteadOfMidPassCsv() {
        SpecialStageTraceFrame csv = frame(799, 1);
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(csv,
                List.of(snapshot(799, 3)));
        Sonic2SpecialStageComparisonState engine = engine(3);

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(expected, engine);

        assertEquals(Severity.MATCH, compared.get("combined_rings").severity());
    }

    @Test
    void f791EngineOneRingStillMatchesExactPassEnd() {
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(frame(791, 1),
                List.of(snapshot(791, 1)));

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(expected, engine(1));

        assertEquals(Severity.MATCH, compared.get("combined_rings").severity());
    }

    private static Sonic2SpecialStageComparisonState engine(int rings) {
        PlayerState player = new PlayerState(128, 90, 300, 64, "NORMAL", 0, 1, 2);
        return new Sonic2SpecialStageComparisonState(12, 5, 7, 4, 58,
                rings, 9, false, player, player);
    }

    private static TraceEvent.StateSnapshot snapshot(int frame, int sonicRings) {
        return SpecialStageExpectedStateTestFixtures.runObjectsEnd(frame, sonicRings);
    }

    private static SpecialStageTraceFrame frame(int frame, int sonicRings) {
        return SpecialStageExpectedStateTestFixtures.frame(frame, sonicRings);
    }
}

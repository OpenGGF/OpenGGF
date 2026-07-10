package com.openggf.tests.trace.s2;

import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageComparisonState.PlayerState;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.SpecialStageExpectedState;
import com.openggf.trace.SpecialStageTraceFrame;
import com.openggf.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void signedRomTrackCoordinatesMatchEngineSignedIntegers() {
        SpecialStageTraceFrame.CharacterState sonic = character(0xFFFA, 0xFFF2, 0x006E);
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(frame(sonic), List.of());

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(
                        expected, engine(new PlayerState(-6, -14, 0x006E, 64,
                                "NORMAL", 0, 1, 2, 0, 0, 0, 0)));

        assertEquals(Severity.MATCH, compared.get("sonic_ss_x").severity());
        assertEquals(Severity.MATCH, compared.get("sonic_ss_y").severity());
    }

    @Test
    void specialStageDepthRemainsAnUnsignedRawWord() {
        SpecialStageTraceFrame.CharacterState sonic = character(0, 0, 0xFFFA);
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(frame(sonic), List.of());

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(
                        expected, engine(new PlayerState(0, 0, -6, 64,
                                "NORMAL", 0, 1, 2, 0, 0, 0, 0)));

        assertEquals(Severity.ERROR, compared.get("sonic_ss_z").severity());
        assertEquals("65530", compared.get("sonic_ss_z").expected());
    }

    @Test
    void perPlayerRingMismatchIsAnError() {
        SpecialStageTraceFrame.CharacterState sonic = character(0, 0, 0x006E, 5);
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(frame(sonic), List.of());

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(
                        expected, engine(new PlayerState(0, 0, 0x006E, 64,
                                "NORMAL", 0, 1, 2, 4, 0, 0, 0)));

        FieldComparison rings = compared.get("sonic_rings");
        assertNotNull(rings);
        assertEquals(Severity.ERROR, rings.severity());
        assertEquals("5", rings.expected());
        assertEquals("4", rings.actual());
    }

    @Test
    void rawVblankPlayerTimersAreNotComparedAsAnAtomicObjectPass() {
        SpecialStageTraceFrame.CharacterState sonic = new SpecialStageTraceFrame.CharacterState(
                true, 0, 0, 0, 0, 0x6E, 64, 2, 0, 0, 1, 2,
                0, 8, 7, 6);
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(
                new SpecialStageTraceFrame(1, 0, 0, false,
                        12, 0, 7, 4, 0, 2, 5, 7, 0, 0, 4, 0xFF,
                        sonic, character(128, 90, 300)),
                List.of());
        PlayerState engineSonic = new PlayerState(0, 0, 0x6E, 64,
                "NORMAL", 0, 1, 2, 0, 0, 0, 0);

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(
                        expected, engine(engineSonic));

        assertEquals(Severity.ERROR, compared.get("swap_positions_flag").severity());
        assertFalse(compared.containsKey("sonic_hurt_timer"));
        assertFalse(compared.containsKey("sonic_slide_timer"));
        assertFalse(compared.containsKey("sonic_flip_timer"));
    }

    @Test
    void atomicPassEndTimersUseTheirRatchetedSeverities() {
        TraceEvent.StateSnapshot base = snapshot(799, 0);
        Map<String, Object> fields = new HashMap<>(base.fields());
        fields.put("sonic_hurt_timer", 8);
        fields.put("sonic_slide_timer", 7);
        fields.put("sonic_flip_timer", 6);
        SpecialStageExpectedState expected = SpecialStageExpectedState.from(frame(799, 0),
                List.of(new TraceEvent.StateSnapshot(799, fields)));

        Map<String, FieldComparison> compared =
                AbstractS2SpecialStageTraceReplayTest.compareExpectedFrame(
                        expected, engine(new PlayerState(0, 0, 0x6E, 64,
                                "NORMAL", 0, 1, 2, 0, 0, 0, 0)));

        assertEquals(Severity.ERROR, compared.get("sonic_hurt_timer").severity());
        assertEquals(Severity.ERROR, compared.get("sonic_slide_timer").severity());
        assertEquals(Severity.WARNING, compared.get("sonic_flip_timer").severity());
    }

    private static Sonic2SpecialStageComparisonState engine(int rings) {
        PlayerState player = new PlayerState(128, 90, 300, 64, "NORMAL", 0, 1, 2,
                rings, 0, 0, 0);
        PlayerState tails = new PlayerState(128, 90, 300, 64, "NORMAL", 0, 1, 2,
                0, 0, 0, 0);
        return new Sonic2SpecialStageComparisonState(12, 5, 7, 4, 58,
                rings, 9, 0, false, player, tails);
    }

    private static Sonic2SpecialStageComparisonState engine(PlayerState sonic) {
        PlayerState tails = new PlayerState(128, 90, 300, 64, "NORMAL", 0, 1, 2,
                0, 0, 0, 0);
        return new Sonic2SpecialStageComparisonState(12, 5, 7, 4, 10,
                0, 4, 0, false, sonic, tails);
    }

    private static TraceEvent.StateSnapshot snapshot(int frame, int sonicRings) {
        return SpecialStageExpectedStateTestFixtures.runObjectsEnd(frame, sonicRings);
    }

    private static SpecialStageTraceFrame frame(int frame, int sonicRings) {
        return SpecialStageExpectedStateTestFixtures.frame(frame, sonicRings);
    }

    private static SpecialStageTraceFrame frame(SpecialStageTraceFrame.CharacterState sonic) {
        SpecialStageTraceFrame.CharacterState tails = character(128, 90, 300);
        return new SpecialStageTraceFrame(1, 0, 0, false,
                12, 0, 7, 4, 0, 2, 5, 7, 0, 0, 4, 0, sonic, tails);
    }

    private static SpecialStageTraceFrame.CharacterState character(int ssX, int ssY, int ssZ) {
        return character(ssX, ssY, ssZ, 0);
    }

    private static SpecialStageTraceFrame.CharacterState character(int ssX, int ssY, int ssZ,
                                                                    int rings) {
        return new SpecialStageTraceFrame.CharacterState(true,
                ssX, 0, ssY, 0, ssZ, 64, 2, 0, 0, 1, 2,
                rings, 0, 0, 0);
    }
}

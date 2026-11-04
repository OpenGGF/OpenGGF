package com.openggf.trace.timing;

import com.openggf.data.RomByteReader;
import com.openggf.game.internal.NativeLevelLoadInputs;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Decision;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Phase;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission.PreparedPhase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.openggf.trace.timing.InitializationTimingEvent.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

class TestInitializationTimingReplayPort {
    private static PreparedPhase phase(int ordinal, Phase phase) {
        return new PreparedPhase(1, ordinal, phase, NativeLevelLoadInputs.fromRom(
                new RomByteReader(new byte[] {1, 2}), List.of(), List.of(2)));
    }

    private static InitializationTimingEvent event(int row, int sequence,
            PreparedPhase phase, InitializationTimingEvent.Operation operation) {
        return new InitializationTimingEvent(row, sequence, phase.loadOrdinal(), phase.phaseOrdinal(),
                InitializationTimingEvent.Phase.valueOf(phase.phase().name()), phase.fingerprint(), operation);
    }

    @Test
    void servicesAreConsumedOnceAndSameRowRelatchCannotEraseHold() {
        var phase = phase(1, Phase.DISPLAY_CLEAR);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(
                event(0, 0, phase, VINT_0), event(0, 1, phase, HOLD), event(1, 0, phase, RESUME))));
        port.beginRawFrame(0);
        assertEquals(Decision.VINT_0, port.next(phase));
        assertEquals(Decision.HOLD, port.next(phase));
        port.beginRawFrame(0);
        assertEquals(Decision.HOLD, port.next(phase));
        port.beginRawFrame(1);
        assertEquals(Decision.RESUME, port.next(phase));
        port.verifyRunComplete();
        assertThrows(IllegalStateException.class, () -> port.next(phase));
    }

    @Test
    void onePhysicalRowCanResumeOneRealPhaseAndServiceTheNext() {
        var first = phase(1, Phase.LEVEL_DATA_DECODE);
        var second = phase(2, Phase.LEVEL_SETUP);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(
                event(4, 0, first, RESUME), event(4, 1, second, VINT_0), event(4, 2, second, RESUME))));
        port.beginRawFrame(4);
        assertEquals(Decision.RESUME, port.next(first));
        assertEquals(Decision.VINT_0, port.next(second));
        assertEquals(Decision.RESUME, port.next(second));
        port.verifyRunComplete();
    }

    @Test
    void wrongPhaseOrdinalFingerprintOrLoadCannotSpendTheObservation() {
        var actual = phase(1, Phase.LEVEL_DATA_DECODE);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(
                event(0, 0, actual, VINT_0), event(0, 1, actual, HOLD))));
        port.beginRawFrame(0);
        assertThrows(IllegalStateException.class, () -> port.next(phase(2, Phase.LEVEL_DATA_DECODE)));
        assertThrows(IllegalStateException.class, () -> port.next(phase(1, Phase.LEVEL_SETUP)));
        assertThrows(IllegalStateException.class, () -> port.next(new PreparedPhase(
                2, 1, actual.phase(), actual.inputs())));
        assertThrows(IllegalStateException.class, () -> port.next(new PreparedPhase(
                1, 1, actual.phase(), NativeLevelLoadInputs.fromRom(
                        new RomByteReader(new byte[] {9}), List.of(), List.of(2)))));
        assertEquals(Decision.VINT_0, port.next(actual));
        assertEquals(Decision.HOLD, port.next(actual));
        assertThrows(IllegalStateException.class, () -> port.next(phase(2, Phase.LEVEL_SETUP)));
    }

    @Test
    void representedPendingPhaseCannotFallBackAfterExplicitGap() {
        var phase = phase(1, Phase.LEVEL_SETUP);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(
                event(0, 0, phase, HOLD), event(1, 0, phase, RESUME))));
        port.beginRawFrame(0);
        assertEquals(Decision.HOLD, port.next(phase));
        assertThrows(IllegalStateException.class, port::enterUnrepresentedGap);
        assertEquals(Decision.HOLD, port.next(phase));
        port.beginRawFrame(1);
        assertEquals(Decision.RESUME, port.next(phase));
        port.verifyRunComplete();
        var live = new InitializationTimingReplayPort(InitializationTimingSchedule.empty());
        assertEquals(Decision.RESUME, live.next(phase));
        var unrepresented = new InitializationTimingReplayPort(InitializationTimingSchedule.recordedEmpty());
        assertThrows(IllegalStateException.class, () -> unrepresented.next(phase));
        var represented = new InitializationTimingReplayPort(InitializationTimingSchedule.recordedEmpty());
        represented.beginRawFrame(0);
        assertThrows(IllegalStateException.class, () -> represented.next(phase));
    }

    @Test
    void heldPhaseCannotJumpOverAnUnservicedPhysicalRow() {
        var phase = phase(1, Phase.LEVEL_DATA_DECODE);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(
                event(0, 0, phase, HOLD), event(2, 0, phase, RESUME))));
        port.beginRawFrame(0);
        assertEquals(Decision.HOLD, port.next(phase));
        assertThrows(IllegalStateException.class, () -> port.beginRawFrame(2));
        port.beginRawFrame(1);
        assertThrows(IllegalStateException.class, () -> port.next(phase));
    }

    @Test
    void pendingPhaseCanCrossAnExplicitScheduleHandoffWithMatchingIdentity() {
        var phase = phase(1, Phase.INITIAL_OBJECTS);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(event(9, 0, phase, HOLD))));
        port.beginRawFrame(9);
        assertEquals(Decision.HOLD, port.next(phase));
        assertThrows(IllegalStateException.class, () -> port.handoffTo(InitializationTimingSchedule.empty()));
        port.handoffTo(new InitializationTimingSchedule(List.of(
                event(0, 0, phase, VINT_0), event(0, 1, phase, RESUME))));
        port.beginRawFrame(0);
        assertEquals(Decision.VINT_0, port.next(phase));
        assertEquals(Decision.RESUME, port.next(phase));
        port.verifyRunComplete();
    }

    @Test
    void missedObservationsAndUnfinishedPhasesCannotBeReportedComplete() {
        var phase = phase(1, Phase.LEVEL_SETUP);
        var port = new InitializationTimingReplayPort(new InitializationTimingSchedule(List.of(event(3, 0, phase, HOLD))));
        assertThrows(IllegalStateException.class, () -> port.beginRawFrame(4));
        assertThrows(IllegalStateException.class, port::verifyRunComplete);
        port.beginRawFrame(3);
        assertEquals(Decision.HOLD, port.next(phase));
        assertThrows(IllegalStateException.class, port::verifyRunComplete);
        port.abort();
        assertThrows(IllegalStateException.class, () -> port.next(phase));
        assertThrows(IllegalStateException.class, () -> port.beginRawFrame(4));
        assertThrows(IllegalStateException.class, port::verifyRunComplete);
    }

    @Test
    void malformedPhysicalRowClosureAndUnboundedSequencesAreRejected() {
        var phase = phase(1, Phase.LEVEL_SETUP);
        assertThrows(IllegalArgumentException.class, () -> new InitializationTimingSchedule(
                List.of(event(0, 0, phase, HOLD), event(0, 1, phase, RESUME))));
        assertThrows(IllegalArgumentException.class, () -> new InitializationTimingSchedule(
                List.of(event(0, 0, phase, VINT_0))));
        var overflowing = new ArrayList<InitializationTimingEvent>();
        for (int sequence = 0; sequence < 64; sequence++) overflowing.add(event(0, sequence, phase, VINT_0));
        overflowing.add(event(0, 64, phase, RESUME));
        assertThrows(IllegalArgumentException.class, () -> new InitializationTimingSchedule(overflowing));
    }
}

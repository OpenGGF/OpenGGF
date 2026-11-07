package com.openggf.level;

import com.openggf.data.RomByteReader;
import com.openggf.game.InitStep;
import com.openggf.game.internal.NativeLevelLoadInputs;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;
import com.openggf.game.internal.NativeLevelLoadPlan;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeScreenInitialization {
    private static final NativeLevelLoadInputs INPUTS = NativeLevelLoadInputs.fromRom(
            RomByteReader.fromBytes(new byte[]{1}), List.of(new NativeLevelLoadInputs.RegionSelection(0,1)), List.of());
    @Test void theControllerRetainsActualPreparedWorkAndConsumesOnlySelectedInterrupts() {
        var manager = mock(LevelManager.class); manager.nativeLoads = new NativeLevelLoadController();
        var decisions = new ArrayDeque<>(List.of(NativeLevelLoadPhaseAdmission.Decision.HOLD,
                NativeLevelLoadPhaseAdmission.Decision.VINT_0, NativeLevelLoadPhaseAdmission.Decision.RESUME));
        var events = new ArrayList<String>();
        NativeLevelLoadAccess.beginRepresentedTimingRun(manager, phase -> {
            assertEquals(1, phase.loadOrdinal()); assertEquals(1, phase.phaseOrdinal()); return decisions.removeFirst();
        });
        var plan = new NativeLevelLoadPlan(List.of(new NativeLevelLoadPlan.Section(
                NativeLevelLoadPhaseAdmission.Phase.LEVEL_SETUP, () -> INPUTS, () -> true,
                List.of(new InitStep("prepare", "ROM-backed font", () -> events.add("prepare"))),
                List.of(new InitStep("commit", "ready font", () -> events.add("commit"))))));
        NativeLevelLoadAccess.beginScreenInitialization(manager, plan, () -> events.add("vint0"),
                () -> events.add("complete"), () -> events.add("cleanup"));
        assertEquals(List.of("prepare"), events);
        assertTrue(NativeLevelLoadAccess.isPending(manager));
        assertThrows(IllegalStateException.class, () -> NativeLevelLoadAccess.beginRepresentedTimingRun(
                manager, NativeLevelLoadPhaseAdmission.IMMEDIATE));
        assertTrue(NativeLevelLoadAccess.resumeScreenInitialization(manager));
        assertEquals(List.of("prepare","vint0","commit","complete","cleanup"), events);
        assertFalse(NativeLevelLoadAccess.isPending(manager));
        assertFalse(NativeLevelLoadAccess.resumeScreenInitialization(manager));
    }
    @Test void failedAdmissionRetiresItsContinuationAndCleansUpExactlyOnce() {
        var manager = mock(LevelManager.class); manager.nativeLoads = new NativeLevelLoadController();
        NativeLevelLoadAccess.setAdmission(manager, phase -> { throw new IllegalStateException("mismatch"); });
        int[] cleanup = {0};
        var plan = new NativeLevelLoadPlan(List.of(new NativeLevelLoadPlan.Section(
                NativeLevelLoadPhaseAdmission.Phase.LEVEL_SETUP, () -> INPUTS, () -> true, List.of(), List.of())));
        assertThrows(IllegalStateException.class, () -> NativeLevelLoadAccess.beginScreenInitialization(
                manager, plan, () -> fail("no interrupt selected"), () -> fail("not complete"), () -> cleanup[0]++));
        assertFalse(NativeLevelLoadAccess.isPending(manager));
        assertEquals(1, cleanup[0]);
    }
}

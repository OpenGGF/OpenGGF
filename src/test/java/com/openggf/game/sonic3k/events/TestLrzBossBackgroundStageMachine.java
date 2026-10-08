package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLrzBossBackgroundStageMachine {
    private LrzZoneRuntimeState state() {
        return new LrzZoneRuntimeState(22, 0, PlayerCharacter.SONIC_ALONE);
    }

    @Test void thresholdFallsThroughAndArenaAllocationIsAttemptedOnce() {
        var state = state();
        int[] allocations = {0};
        Runnable allocate = () -> allocations[0]++;
        LrzBossBackgroundStageMachine.advance(state, 0xA00, 0x4FF, 0x560, allocate);
        assertEquals(0, state.backgroundRoutine());
        LrzBossBackgroundStageMachine.advance(state, 0xA00, 0x500, 0x560, allocate);
        assertEquals(4, state.backgroundRoutine()); assertEquals(0, allocations[0]);
        LrzBossBackgroundStageMachine.advance(state, 0xA01, 0x560, 0x560, allocate);
        assertEquals(4, state.backgroundRoutine());
        LrzBossBackgroundStageMachine.advance(state, 0xA00, 0x560, 0x560, allocate);
        assertEquals(12, state.backgroundRoutine()); assertEquals(1, allocations[0]);
        // A failed allocator does not reset the event state or manufacture a later retry.
        for (int i=0; i<100; i++) LrzBossBackgroundStageMachine.advance(state,0xA00,0x560,0x560,allocate);
        assertEquals(1, allocations[0]);
    }

    @Test void risingCameraPinsThePriorBackgroundWhileSixteenRowsDrain() {
        var state = state(); state.setBackgroundRoutine(4);
        state.publishDeformationWords(0x340, 0x40, 0, 0);
        LrzBossBackgroundStageMachine.advance(state,0xA00,0x4FF,0x560,()->fail("no arena"));
        assertEquals(8,state.backgroundRoutine()); assertEquals(13,state.delayedRowcount());
        assertEquals(0x340,state.savedBackgroundCameraX()); assertEquals(0x40,state.savedBackgroundCameraY());
        for(int i=0;i<6;i++) LrzBossBackgroundStageMachine.advance(state,0xA00,0x500,0x560,()->fail("no arena"));
        assertEquals(8,state.backgroundRoutine()); assertEquals(1,state.delayedRowcount());
        LrzBossBackgroundStageMachine.advance(state,0xA00,0x500,0x560,()->fail("no arena"));
        assertEquals(0,state.backgroundRoutine()); assertEquals(-1,state.delayedRowcount());
        assertEquals(0,state.savedBackgroundCameraX()); assertEquals(0x40,state.savedBackgroundCameraY());
    }

    @Test void defeatWaitsForTheLavaOwnerToPublishZeroAmplitude() {
        var state=state(); state.setBackgroundRoutine(12);
        state.bossAct().setLavaDirection(1); state.bossAct().advanceLava(true);
        state.bossAct().requestBackgroundExit();
        LrzBossBackgroundStageMachine.advance(state,0xA00,0x560,0x560,()->fail("no allocation"));
        assertEquals(12,state.backgroundRoutine()); assertTrue(state.bossAct().backgroundExitRequested());
        state.bossAct().setLavaDirection(0); state.bossAct().advanceLava(true);
        LrzBossBackgroundStageMachine.advance(state,0xA00,0x560,0x560,()->fail("no allocation"));
        assertEquals(16,state.backgroundRoutine()); assertFalse(state.bossAct().backgroundExitRequested());
    }
}

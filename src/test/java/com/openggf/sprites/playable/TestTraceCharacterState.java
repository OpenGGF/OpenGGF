package com.openggf.sprites.playable;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceCharacterState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestTraceCharacterState {

    @BeforeEach
    void resetRuntime() {
        TestEnvironment.resetAll();
    }

    @Test
    void cpuSidekickDeadFallingCapturesObjectRoutineSix() {
        Sonic sonic = new Sonic("sonic", (short) 0x04AF, (short) 0x0665);
        Tails tails = new Tails("tails_p2", (short) 0x0464, (short) 0x07FF);
        tails.setPhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);
        tails.setCpuControlled(true);
        tails.setAir(true);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) -0x06C8);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.DEAD_FALLING, 0);

        TraceCharacterState state = TraceCharacterState.fromSprite(tails);

        assertEquals(0x06, state.routine(),
                "ROM Obj02 switches Tails to routine 6 on the level-boundary death frame");
        assertEquals(0x02, state.statusByte(),
                "Kill_Character leaves only the in-air status bit set for this trace state");
    }

    @Test
    void hurtRoutineStillTakesPrecedenceForPlayableCapture() {
        Sonic sonic = new Sonic("sonic", (short) 0x0100, (short) 0x0200);
        sonic.setHurt(true);

        TraceCharacterState state = TraceCharacterState.fromSprite(sonic);

        assertEquals(0x04, state.routine());
    }
}

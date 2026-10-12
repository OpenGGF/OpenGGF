package com.openggf.game.mutators;

import com.openggf.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TestGameplayMutatorPacing {
    private static GameplayMutatorPolicy speed(int percent) {
        return new GameplayMutatorPolicy(100, 0, 100, 0xC00, percent, false);
    }

    @ParameterizedTest
    @CsvSource({"25,50", "50,50", "75,50", "125,50", "150,50", "400,50",
            "25,60", "50,60", "75,60", "125,60", "150,60", "400,60"})
    void admitsExactWholeNativeTicksAtBothPresentationRates(int percent, int hz) {
        var runtime = new GameplayMutatorPacing(() -> speed(percent));
        int ticks = 0;
        for (int frame = 1; frame <= hz * 10; frame++) {
            int step = runtime.stepsForPresentation();
            assertTrue(step >= 0 && step <= 4);
            ticks += step;
            assertEquals(frame * percent / 100, ticks, "prefix budget at " + frame);
        }
    }

    @Test void restoredFractionAndInputReplayAcrossIndependentWorldsAndLiveEdits() {
        var policy = new AtomicReference<>(speed(25));
        var left = new GameplayMutatorPacing(policy::get);
        var right = new GameplayMutatorPacing(() -> speed(400));
        assertEquals(0, left.stepsForPresentation());
        var pressed = PlayerInputState.of(0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, true, true);
        left.retain(LogicalInputSnapshot.ofPlayers(pressed, pressed));
        left.retain(LogicalInputSnapshot.neutral());
        var saved = left.capture();
        assertEquals(0, saved.player1().actionHeldMask());
        assertEquals(InputActionMasks.ACTION_A, saved.player1().actionPressedMask());
        assertFalse(saved.player1().startPressed(), "P1 Start belongs to host pause");
        assertTrue(saved.player2().startPressed(), "P2 Start remains native rescue input");
        policy.set(speed(150));
        assertEquals(1, left.stepsForPresentation());
        assertEquals(75, left.capture().remainder());
        left.clearPendingInput();
        assertEquals(4, right.stepsForPresentation());
        left.restore(saved);
        assertEquals(1, left.stepsForPresentation());
        assertEquals(75, left.capture().remainder());
        assertTrue(left.hasPendingInput());
        assertFalse(right.hasPendingInput());
        left.reset();
        assertEquals(0, left.capture().remainder());
        assertFalse(left.hasPendingInput());
    }
}

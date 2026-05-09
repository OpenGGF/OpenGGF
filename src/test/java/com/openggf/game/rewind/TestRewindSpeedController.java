package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRewindSpeedController {
    @Test
    void disabledPolicyPreservesOneStepWhileHeldAndNoCoastOnRelease() {
        RewindSpeedController controller = RewindSpeedController.disabled();

        assertEquals(1, controller.stepsWhileHeld());
        assertEquals(1, controller.stepsWhileHeld());
        assertEquals(0, controller.stepsAfterRelease());
    }

    @Test
    void enabledPolicyAcceleratesWhileHeldAndDeceleratesAfterRelease() {
        RewindSpeedController controller = new RewindSpeedController(true, 1.0, 1.0, 3.0);

        assertEquals(1, controller.stepsWhileHeld());
        assertEquals(2, controller.stepsWhileHeld());
        assertEquals(3, controller.stepsWhileHeld());
        assertEquals(2, controller.stepsAfterRelease());
        assertEquals(1, controller.stepsAfterRelease());
        assertEquals(0, controller.stepsAfterRelease());
    }

    @Test
    void newHoldCancelsCoastAndUsesCurrentHeldSpeed() {
        RewindSpeedController controller = new RewindSpeedController(true, 1.0, 0.5, 3.0);

        assertEquals(1, controller.stepsWhileHeld());
        assertEquals(2, controller.stepsWhileHeld());
        assertEquals(2, controller.stepsAfterRelease());

        assertEquals(2, controller.stepsWhileHeld());
    }

    @Test
    void maxStepSettingCanLimitOutputToOneStepWhileStillAllowingCoast() {
        RewindSpeedController controller = new RewindSpeedController(true, 0.5, 0.5, 1.99);

        assertEquals(1, controller.stepsWhileHeld());
        assertEquals(1, controller.stepsWhileHeld());
        assertEquals(1, controller.stepsWhileHeld());

        assertEquals(1, controller.stepsAfterRelease());
        assertEquals(0, controller.stepsAfterRelease());
    }

    @Test
    void enabledPolicyTreatsZeroDecelerationAsImmediateReleaseStop() {
        RewindSpeedController controller = new RewindSpeedController(true, 1.0, 0.0, 3.0);

        assertEquals(1, controller.stepsWhileHeld());

        assertEquals(0, controller.stepsAfterRelease());
    }
}

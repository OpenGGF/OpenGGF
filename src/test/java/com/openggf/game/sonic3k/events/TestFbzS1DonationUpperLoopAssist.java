package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.S1DonationUpperLoopAssistState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Contract for the independent FBZ2 upper-loop assist used only by S1 donation. */
class TestFbzS1DonationUpperLoopAssist {

    private static final int APPROACH_X = 0x0A4D;
    private static final int APPROACH_Y = 0x0769;
    private static final int RUNNING_LEFT = -0x0396;

    @Test
    void s1DonationWithoutSpindashConvertsOrdinaryLeftRunToNativeReleaseSpeed() {
        assertEquals(-0x0800, FbzS1DonationUpperLoopAssist.resolveGroundSpeed(
                true, false, APPROACH_X, APPROACH_Y,
                false, true, RUNNING_LEFT));
    }

    @Test
    void nativeAndS2DonationNeverReceiveTheS1OnlyAssist() {
        assertUnchanged(false, false, APPROACH_X, APPROACH_Y,
                false, true, RUNNING_LEFT);
        assertUnchanged(true, true, APPROACH_X, APPROACH_Y,
                false, true, RUNNING_LEFT);
    }

    @Test
    void assistRequiresGroundedLeftwardRunningInsideTheAuthoredApproach() {
        assertUnchanged(true, false, 0x0A3F, APPROACH_Y,
                false, true, RUNNING_LEFT);
        assertUnchanged(true, false, 0x0A71, APPROACH_Y,
                false, true, RUNNING_LEFT);
        assertUnchanged(true, false, APPROACH_X, 0x073F,
                false, true, RUNNING_LEFT);
        assertUnchanged(true, false, APPROACH_X, 0x0781,
                false, true, RUNNING_LEFT);
        assertUnchanged(true, false, APPROACH_X, APPROACH_Y,
                true, true, RUNNING_LEFT);
        assertUnchanged(true, false, APPROACH_X, APPROACH_Y,
                false, false, RUNNING_LEFT);
        assertUnchanged(true, false, APPROACH_X, APPROACH_Y,
                false, true, 0);
        assertUnchanged(true, false, APPROACH_X, APPROACH_Y,
                false, true, 0x0001);
    }

    @Test
    void independentStateConsumesExactlyOnceAndRearmsOutsideTheWiderEnvelope() {
        FbzS1DonationUpperLoopAssist.Decision first =
                FbzS1DonationUpperLoopAssist.resolve(
                        S1DonationUpperLoopAssistState.ARMED,
                        true, false, APPROACH_X, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(-0x0800, first.groundSpeed());
        assertEquals(S1DonationUpperLoopAssistState.CONSUMED, first.nextState());

        FbzS1DonationUpperLoopAssist.Decision held =
                FbzS1DonationUpperLoopAssist.resolve(
                        first.nextState(), true, false, APPROACH_X, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(RUNNING_LEFT, held.groundSpeed());
        assertEquals(S1DonationUpperLoopAssistState.CONSUMED, held.nextState());

        FbzS1DonationUpperLoopAssist.Decision rearmed =
                FbzS1DonationUpperLoopAssist.resolve(
                        held.nextState(), true, false, 0x0A1F, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(S1DonationUpperLoopAssistState.ARMED, rearmed.nextState());
    }

    @Test
    void existingFasterLeftwardSpeedIsPreservedButConsumesTheAttempt() {
        FbzS1DonationUpperLoopAssist.Decision decision =
                FbzS1DonationUpperLoopAssist.resolve(
                        S1DonationUpperLoopAssistState.ARMED,
                        true, false, APPROACH_X, APPROACH_Y,
                        false, true, -0x0900);
        assertEquals(-0x0900, decision.groundSpeed());
        assertEquals(S1DonationUpperLoopAssistState.CONSUMED, decision.nextState());
    }

    private static void assertUnchanged(boolean donationActive, boolean spindashEnabled,
                                        int x, int y, boolean airborne,
                                        boolean leftPressed, int groundSpeed) {
        assertEquals(groundSpeed, FbzS1DonationUpperLoopAssist.resolveGroundSpeed(
                donationActive, spindashEnabled, x, y,
                airborne, leftPressed, groundSpeed));
    }
}

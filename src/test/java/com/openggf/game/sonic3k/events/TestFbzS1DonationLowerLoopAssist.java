package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.S1DonationLowerLoopAssistState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Contract for the FBZ2 lower-loop assist used only by S1 donation. */
class TestFbzS1DonationLowerLoopAssist {

    private static final int APPROACH_X = 0x095A;
    private static final int APPROACH_Y = 0x0A6B;
    private static final int RUNNING_LEFT = -0x600;

    @Test
    void s1DonationWithoutSpindashConvertsAuthoredLeftRunToNativeReleaseSpeed() {
        assertEquals(-0x0B00, FbzS1DonationLowerLoopAssist.resolveGroundSpeed(
                true, false, APPROACH_X, APPROACH_Y,
                false, true, RUNNING_LEFT));
    }

    @Test
    void nativeAndS2DonationNeverReceiveTheS1OnlyAssist() {
        assertEquals(RUNNING_LEFT, FbzS1DonationLowerLoopAssist.resolveGroundSpeed(
                false, false, APPROACH_X, APPROACH_Y,
                false, true, RUNNING_LEFT));
        assertEquals(RUNNING_LEFT, FbzS1DonationLowerLoopAssist.resolveGroundSpeed(
                true, true, APPROACH_X, APPROACH_Y,
                false, true, RUNNING_LEFT));
    }

    @Test
    void s1AssistRequiresGroundedLeftwardRunningInsideTheAuthoredApproach() {
        assertUnchanged(0x093F, APPROACH_Y, false, true, RUNNING_LEFT);
        assertUnchanged(0x0981, APPROACH_Y, false, true, RUNNING_LEFT);
        assertUnchanged(APPROACH_X, 0x0A4F, false, true, RUNNING_LEFT);
        assertUnchanged(APPROACH_X, 0x0A81, false, true, RUNNING_LEFT);
        assertUnchanged(APPROACH_X, APPROACH_Y, true, true, RUNNING_LEFT);
        assertUnchanged(APPROACH_X, APPROACH_Y, false, false, RUNNING_LEFT);
        assertUnchanged(APPROACH_X, APPROACH_Y, false, true, 0);
        assertUnchanged(APPROACH_X, APPROACH_Y, false, true, 0x600);
        assertEquals(-0x0B00, FbzS1DonationLowerLoopAssist.resolveGroundSpeed(
                true, false, APPROACH_X, APPROACH_Y,
                false, true, -1),
                "any genuine leftward ground motion activates inside the exact envelope");
    }

    @Test
    void anExistingFasterLeftwardSpeedIsNotReduced() {
        assertEquals(-0x0C00, FbzS1DonationLowerLoopAssist.resolveGroundSpeed(
                true, false, APPROACH_X, APPROACH_Y,
                false, true, -0x0C00));
        FbzS1DonationLowerLoopAssist.Decision decision =
                FbzS1DonationLowerLoopAssist.resolve(
                        S1DonationLowerLoopAssistState.ARMED,
                        true, false, APPROACH_X, APPROACH_Y,
                        false, true, -0x0C00);
        assertEquals(-0x0C00, decision.groundSpeed());
        assertEquals(S1DonationLowerLoopAssistState.CONSUMED, decision.nextState(),
                "crossing the trigger at native-or-faster speed still consumes this attempt");
    }

    @Test
    void assistConsumesExactlyOnceWhileThePlayerRemainsInTheApproachEnvelope() {
        FbzS1DonationLowerLoopAssist.Decision first =
                FbzS1DonationLowerLoopAssist.resolve(
                        S1DonationLowerLoopAssistState.ARMED,
                        true, false, APPROACH_X, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(-0x0B00, first.groundSpeed());
        assertEquals(S1DonationLowerLoopAssistState.CONSUMED, first.nextState());

        FbzS1DonationLowerLoopAssist.Decision held =
                FbzS1DonationLowerLoopAssist.resolve(
                        first.nextState(), true, false, APPROACH_X, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(RUNNING_LEFT, held.groundSpeed(),
                "the compatibility impulse must not be written again on the next frame");
        assertEquals(S1DonationLowerLoopAssistState.CONSUMED, held.nextState());
    }

    @Test
    void leavingTheWiderApproachEnvelopeRearmsALaterAttempt() {
        FbzS1DonationLowerLoopAssist.Decision rearmed =
                FbzS1DonationLowerLoopAssist.resolve(
                        S1DonationLowerLoopAssistState.CONSUMED,
                        true, false, 0x091F, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(RUNNING_LEFT, rearmed.groundSpeed());
        assertEquals(S1DonationLowerLoopAssistState.ARMED, rearmed.nextState());

        FbzS1DonationLowerLoopAssist.Decision retry =
                FbzS1DonationLowerLoopAssist.resolve(
                        rearmed.nextState(), true, false, APPROACH_X, APPROACH_Y,
                        false, true, RUNNING_LEFT);
        assertEquals(-0x0B00, retry.groundSpeed());
        assertEquals(S1DonationLowerLoopAssistState.CONSUMED, retry.nextState());
    }

    private static void assertUnchanged(int x, int y, boolean airborne,
                                        boolean leftPressed, int groundSpeed) {
        assertEquals(groundSpeed, FbzS1DonationLowerLoopAssist.resolveGroundSpeed(
                true, false, x, y, airborne, leftPressed, groundSpeed));
    }
}

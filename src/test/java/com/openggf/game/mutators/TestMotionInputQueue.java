package com.openggf.game.mutators;

import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.mods.mutators.InputControlPolicy;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestMotionInputQueue {
    private static final PlayerInputState TAP = PlayerInputState.of(AbstractPlayableSprite.INPUT_LEFT,
            AbstractPlayableSprite.INPUT_LEFT, InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, true, true);
    private static final PlayerInputState NONE = PlayerInputState.neutral();

    @Test void delayRetainsReleasedPressAndTheEntireQueueTailAcrossRewind() {
        var queue = new MotionInputQueue();
        var policy = new InputControlPolicy(InputControlPolicy.Mode.DELAY, 6);
        var first = queue.admit(TAP, policy, 41, true, true);
        assertTrue(first.startPressed(), "menu Start is immediate");
        assertEquals(0, first.actionPressedMask());
        var checkpoint = queue.capture();
        for (int i = 0; i < 5; i++) assertEquals(NONE, queue.admit(NONE, policy, 41, true, true));
        var expected = queue.admit(NONE, policy, 41, true, true);
        assertEquals(InputActionMasks.ACTION_C, expected.actionPressedMask());
        assertEquals(AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_JUMP, expected.heldMask());
        queue.restore(checkpoint);
        for (int i = 0; i < 5; i++) queue.admit(NONE, policy, 41, true, true);
        assertEquals(expected, queue.admit(NONE, policy, 41, true, true));
    }

    @Test void cancellationOrAnotherActivationDiscardsPendingButtons() {
        var queue = new MotionInputQueue();
        var delay = new InputControlPolicy(InputControlPolicy.Mode.DELAY);
        queue.admit(TAP, delay, 1, true, false);
        assertEquals(NONE, queue.admit(NONE, null, 0, true, false));
        for (int i = 0; i < 7; i++) assertEquals(NONE, queue.admit(NONE, delay, 2, true, false));
        queue.admit(TAP, delay, 2, true, false);
        queue.admit(NONE, delay, 2, false, false);
        assertTrue(queue.capture().pending().isEmpty());
    }

    @Test void reversalSwapsBothControllerBytesAndPreservesActionAndMenuEdges() {
        var queue = new MotionInputQueue();
        var result = queue.admit(TAP, new InputControlPolicy(InputControlPolicy.Mode.REVERSE_HORIZONTAL), 2, true, false);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP, result.heldMask());
        assertEquals(result.heldMask(), result.pressedMask());
        assertEquals(TAP.actionPressedMask(), result.actionPressedMask());
        assertEquals(TAP.startPressed(), result.startPressed());
    }

    @Test void landingMakesExactlyOneFreshNativeJumpAndReplayRestoresTheEdge() {
        var queue = new MotionInputQueue();
        var hop = new InputControlPolicy(InputControlPolicy.Mode.LANDING_JUMP);
        queue.admit(NONE, hop, 3, true, true);
        var checkpoint = queue.capture();
        var landed = queue.admit(NONE, hop, 3, true, false);
        assertEquals(InputActionMasks.ACTION_A, landed.actionPressedMask());
        assertEquals(NONE, queue.admit(NONE, hop, 3, true, false));
        queue.restore(checkpoint);
        assertEquals(landed, queue.admit(NONE, hop, 3, true, false));
    }

    @Test void nativeControlSuppressionCannotLeaveAFakeLandingJumpOnRestoration() {
        var queue = new MotionInputQueue();
        var hop = new InputControlPolicy(InputControlPolicy.Mode.LANDING_JUMP);
        queue.admit(NONE, hop, 3, false, true);
        var suppressed = queue.capture();
        assertEquals(NONE, queue.admit(NONE, hop, 3, true, false));
        queue.restore(suppressed);
        assertEquals(NONE, queue.admit(NONE, hop, 3, true, false));
        queue.admit(NONE, hop, 3, true, true);
        queue.admit(NONE, hop, 3, false, false);
        assertEquals(NONE, queue.admit(NONE, hop, 3, true, false));
    }
}

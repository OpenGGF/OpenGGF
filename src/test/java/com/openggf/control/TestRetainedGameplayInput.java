package com.openggf.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestRetainedGameplayInput {
    @Test void retainedPlayerTapDoesNotBecomeHeldOrRepeatHostMenuInput() {
        var input = new InputHandler();
        var tap = PlayerInputState.of(0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false);
        InputHandlerInternalAccess.retainGameplayInput(input, tap, tap);
        input.refreshLogicalSnapshot();
        assertFalse(input.hasLogicalOverride());
        assertEquals(0, input.logical().player1().actionHeldMask());
        assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
        assertFalse(input.logical().menuAccept());
        input.update(); input.refreshLogicalSnapshot();
        assertEquals(0, input.logical().player1().actionPressedMask());
        assertEquals(0, input.logical().player2().actionPressedMask());
    }

    @Test void externalOverrideOwnsInputAndDiscardsInternalRetainedEdges() {
        var input = new InputHandler();
        var tap = PlayerInputState.of(0, 0, 1, 1, false, false);
        InputHandlerInternalAccess.retainGameplayInput(input, tap, tap);
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        input.refreshLogicalSnapshot();
        assertEquals(LogicalInputSnapshot.neutral(), input.logical());
        input.clearLogicalOverride(); input.refreshLogicalSnapshot();
        assertEquals(0, input.logical().player1().actionPressedMask());
    }
}

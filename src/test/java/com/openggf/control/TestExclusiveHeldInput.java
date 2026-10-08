package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestExclusiveHeldInput {
    @Test
    void identicalSamplesDeriveEdgesAtEachWorldsActualPollBoundary() {
        InputHandler fast = new InputHandler();
        InputHandler lagged = new InputHandler();
        try (var a = ExclusiveHeldInput.acquire(fast); var b = ExclusiveHeldInput.acquire(lagged)) {
            a.beginIteration(0x28); // Right + C
            b.beginIteration(0x28);
            assertEquals(InputActionMasks.ACTION_C, fast.logical().player1().actionPressedMask());
            a.finishIteration(true);
            b.retainUnpolledState();
            assertEquals(0, lagged.logical().player1().heldMask());
            b.finishIteration(false);

            a.beginIteration(0x28);
            b.beginIteration(0x28);
            assertEquals(0, fast.logical().player1().actionPressedMask());
            assertEquals(InputActionMasks.ACTION_C, lagged.logical().player1().actionPressedMask());
            a.finishIteration(true);
            b.finishIteration(true);
        }
    }

    @Test
    void pauseLoopPollsReleaseAndPreservesDistinctActions() {
        InputHandler input = new InputHandler();
        try (var lease = ExclusiveHeldInput.acquire(input)) {
            lease.beginIteration(0x80);
            assertTrue(input.logical().player1().startPressed());
            lease.finishIteration(true);
            lease.beginIteration(0x80);
            assertFalse(input.logical().player1().startPressed());
            lease.finishIteration(true);
            lease.beginIteration(0);
            lease.finishIteration(true); // Pause_Loop polls the release, too.
            lease.beginIteration(0xf0);
            var player = input.logical().player1();
            assertTrue(player.startPressed());
            assertEquals(InputActionMasks.ACTION_ALL, player.actionHeldMask());
            assertEquals(InputActionMasks.ACTION_ALL, player.actionPressedMask());
            lease.finishIteration(true);
            lease.beginIteration(0xa0); // Keep C + Start, release A/B.
            assertEquals(InputActionMasks.ACTION_C, input.logical().player1().actionHeldMask());
            assertEquals(0, input.logical().player1().actionPressedMask());
            lease.finishIteration(true);
        }
    }

    @Test
    void excludedPhysicalInputNeverPollsDevicesOrLeaksShortcuts() {
        AtomicInteger polls = new AtomicInteger();
        GamepadStateSource source = () -> { polls.incrementAndGet(); return List.of(); };
        InputHandler input = new InputHandler(InputBindingFactory.supplier(
                SonicConfigurationService.createStandalone()), source);
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        try (var lease = ExclusiveHeldInput.acquire(input)) {
            input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
            input.handleMouseButton(0, GLFW_PRESS);
            input.pollPhysicalGamepads();
            lease.beginIteration(0x41);
            input.refreshLogicalSnapshot();
            input.update();
            assertEquals(0, polls.get());
            assertFalse(input.isKeyPressed(GLFW_KEY_F1));
            assertFalse(input.isKeyDown(GLFW_KEY_RIGHT));
            assertFalse(input.isPhysicalShiftDown());
            assertFalse(input.isRewindHeld());
            assertFalse(input.isAnyKeyJustPressed());
            assertFalse(input.isMouseButtonDown(0));
            assertEquals(List.of(), input.capturePhysicalInput().events());
            assertEquals(List.of(), input.capturePhysicalInput().keysDown());
            assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
            lease.finishIteration(true);
        }
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
        assertTrue(input.isKeyPressed(GLFW_KEY_F1), "stock input is restored on close");
    }

    @Test
    void competingOverridesClaimsThreadsAndInvalidBytesAreRejected() throws Exception {
        InputHandler input = new InputHandler();
        var lease = ExclusiveHeldInput.acquire(input);
        try {
            assertThrows(IllegalStateException.class, () -> ExclusiveHeldInput.acquire(input));
            assertThrows(IllegalStateException.class, () -> input.setLogicalOverride(LogicalInputSnapshot.neutral()));
            assertThrows(IllegalStateException.class, input::clearLogicalOverride);
            assertThrows(IllegalArgumentException.class, () -> lease.beginIteration(256));
            assertThrows(IllegalArgumentException.class, () -> lease.beginIteration(-1));
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                assertInstanceOf(IllegalStateException.class,
                        executor.submit(() -> assertThrows(IllegalStateException.class,
                                () -> lease.beginIteration(0))).get());
            }
            lease.beginIteration(0);
            assertThrows(IllegalStateException.class, () -> lease.beginIteration(0));
            assertThrows(IllegalStateException.class, lease::capturePollState);
            assertThrows(IllegalStateException.class,
                    () -> lease.restorePollState(new ExclusiveHeldInput.PollState(0)));
            lease.finishIteration(false);
        } finally { lease.close(); }
        lease.close();
        assertThrows(IllegalStateException.class, () -> lease.beginIteration(0));
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        assertThrows(IllegalStateException.class, () -> ExclusiveHeldInput.acquire(input));
    }
}

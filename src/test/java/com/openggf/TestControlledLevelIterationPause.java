package com.openggf;

import com.openggf.control.InputHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestControlledLevelIterationPause {
    @Test void modalOwnershipSuppressesBothHostToggleAndPlaybackTakeover() {
        var input = mock(InputHandler.class);
        when(input.isKeyPressed(10)).thenReturn(true);
        var toggles = new AtomicInteger();
        var takeovers = new AtomicInteger();
        var pause = new ControlledLevelIteration.HostPause(10, 20, () -> false,
                () -> { takeovers.incrementAndGet(); return false; }, toggles::incrementAndGet);
        assertFalse(pause.frameStep(input, true));
        assertEquals(0, toggles.get());
        assertEquals(0, takeovers.get());
        assertFalse(pause.frameStep(input, false));
        assertEquals(1, toggles.get());
        assertEquals(1, takeovers.get());
    }

    @Test void modalOwnershipKeepsExplicitFrameStepAvailableToAnAlreadyPausedHost() {
        var input = mock(InputHandler.class);
        when(input.isKeyPressed(20)).thenReturn(true);
        var pause = new ControlledLevelIteration.HostPause(10, 20, () -> true,
                () -> fail("a modal input row cannot request playback takeover"), () -> fail("unexpected pause toggle"));
        assertTrue(pause.frameStep(input, true));
    }
}

package com.openggf.game.sonic3k.runtime;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class TestSszLaunchState {
    @Test void delayExpiryPrecedesOldVelocityIntegrationAndClamping() {
        var state = new SszLaunchState();
        state.initialize();
        state.setDelay(0, 1);
        assertFalse(state.advance(0x600));
        assertEquals(0, state.delay(0));
        assertEquals(0, state.velocity(0));
        state.advance(0x600);
        assertEquals(0x800, state.velocity(0));
        assertEquals(0, state.offset(0));
        state.advance(0x600);
        assertEquals(-0x800, state.offset(0));
        assertEquals(0x5FF, state.scroll(0));
        assertEquals(0x600, state.scroll(1), "negative delay stays dormant");
        for (int i = 0; i < 10; i++) state.setDelay(i, 0);
        boolean all = false;
        for (int frame = 0; frame < 200 && !all; frame++) all = state.advance(0x600);
        assertTrue(all);
        for (int i = 0; i < 10; i++) {
            assertEquals(0x580, state.scroll(i));
            assertEquals(0, state.velocity(i));
        }
    }

    @Test void columnsRestoreAndReplayTheirFractionalMotion() {
        var state = new SszLaunchState();
        state.initialize();
        for (int i = 0; i < 10; i++) state.setDelay(i, i);
        for (int frame = 0; frame < 24; frame++) state.advance(0x620);
        var before = ByteBuffer.allocate(SszLaunchState.CAPTURE_BYTES);
        state.capture(before);
        state.advance(0x621);
        var after = ByteBuffer.allocate(SszLaunchState.CAPTURE_BYTES);
        state.capture(after);
        var restored = new SszLaunchState();
        restored.restore(ByteBuffer.wrap(before.array()));
        restored.advance(0x621);
        var replay = ByteBuffer.allocate(SszLaunchState.CAPTURE_BYTES);
        restored.capture(replay);
        assertArrayEquals(after.array(), replay.array());
    }
}

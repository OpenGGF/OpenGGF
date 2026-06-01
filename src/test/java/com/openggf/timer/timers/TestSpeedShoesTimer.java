package com.openggf.timer.timers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-alignment tests for the speed-shoes decrement cadence.
 *
 * <p>S1/S2 (decimation 1) decrement every frame. S3K (decimation 8) decrements
 * only on frames where {@code (frame + LEVEL_FRAME_PHASE_OFFSET) & 7 == 0}. The
 * offset is 7 because the engine level frame counter, read at the pre-physics
 * timer tick, leads ROM {@code Level_frame_counter} by 2 (mod 8); ROM decrements
 * on {@code Level_frame_counter & 7 == 7}, which maps to engine
 * {@code frameCounter & 7 == 1}. Validated against the S3K CNZ/AIZ/MGZ trace
 * frontiers (all held at baseline with this offset).
 */
class TestSpeedShoesTimer {

    @Test
    void decimationOneDecrementsEveryFrame() {
        for (int frame = 0; frame < 16; frame++) {
            assertTrue(SpeedShoesTimer.isDecrementFrame(frame, 1),
                    "decimation 1 must decrement on frame " + frame);
        }
    }

    @Test
    void decimationEightDecrementsExactlyOncePerEightFrameWindow() {
        for (int base = 0; base < 24; base += 8) {
            int hits = 0;
            int alignedFrame = -1;
            for (int i = 0; i < 8; i++) {
                int frame = base + i;
                if (SpeedShoesTimer.isDecrementFrame(frame, 8)) {
                    hits++;
                    alignedFrame = frame;
                }
            }
            assertEquals(1, hits, "exactly one decrement per 8-frame window starting at " + base);
            assertEquals(0, (alignedFrame + SpeedShoesTimer.LEVEL_FRAME_PHASE_OFFSET) & 7,
                    "aligned frame must satisfy (frame + offset) & 7 == 0");
        }
    }

    @Test
    void decimationEightAlignsToEngineFrameCounterModEightEqualsOne() {
        // engine frameCounter & 7 == 1 are the decrement frames (ROM
        // Level_frame_counter & 7 == 7, +2 engine seed-phase offset).
        for (int frame = 0; frame < 16; frame++) {
            boolean expected = (frame & 7) == 1;
            assertEquals(expected, SpeedShoesTimer.isDecrementFrame(frame, 8),
                    "frame " + frame + " decrement gate");
        }
    }
}

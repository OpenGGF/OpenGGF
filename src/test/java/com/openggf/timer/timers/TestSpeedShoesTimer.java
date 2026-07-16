package com.openggf.timer.timers;

import com.openggf.game.rules.GameRules;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase-alignment tests for the speed-shoes decrement cadence.
 *
 * <p>S1/S2 (decimation 1) decrement every frame. S3K (decimation 8) decrements
 * only on frames where {@code (frame + LEVEL_FRAME_PHASE_OFFSET) & 7 == 0}. The
 * offset is zero because {@code (Level_frame_counter+1).w} reads the low byte
 * at the word label's second address; it does not arithmetically add one to the
 * counter. The timer therefore decrements when {@code frameCounter & 7 == 0}.
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
    void decimationEightAlignsToEngineFrameCounterModEightEqualsZero() {
        for (int frame = 0; frame < 16; frame++) {
            boolean expected = (frame & 7) == 0;
            assertEquals(expected, SpeedShoesTimer.isDecrementFrame(frame, 8),
                    "frame " + frame + " decrement gate");
        }
    }

    @Test
    void constructorUsesS3kDecimationRules() {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getGameRules()).thenReturn(GameRules.SONIC_3K);

        SpeedShoesTimer timer = new SpeedShoesTimer("speed-shoes", sprite);

        assertEquals(SpeedShoesTimer.ROM_DURATION_FRAMES
                        / GameRules.SONIC_3K.powerUp().speedShoesTimerDecimation(),
                timer.getTicks());
    }

    @Test
    void hurtRoutineFreezesCountdownUntilNormalControlResumes() {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getGameRules()).thenReturn(GameRules.SONIC_3K);
        when(sprite.isHurt()).thenReturn(true, false);
        SpeedShoesTimer timer = new SpeedShoesTimer("speed-shoes", sprite);

        int initialTicks = timer.getTicks();
        timer.decrementTick();
        assertEquals(initialTicks, timer.getTicks(),
                "native hurt routine bypasses Sonic_ChkShoes");

        timer.decrementTick();
        assertEquals(initialTicks - 1, timer.getTicks(),
                "countdown resumes with the normal control routine");
    }
}

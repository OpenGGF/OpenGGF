package com.openggf.sprites.managers;

import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTailsTailPose {
    @Test void romScriptRelationshipsCoverIdleRunAirSpindashAndFlight() {
        assertEquals(9, TailsTailPose.sample(false, 5, 0, false, 0, 0).mappingFrame());
        assertEquals(0xA, TailsTailPose.sample(false, 5, 8, false, 0, 0).mappingFrame());
        assertEquals(0x22, TailsTailPose.sample(true, 5, 0, false, 0, 0).mappingFrame());
        for (boolean separate : new boolean[]{false, true}) {
            assertNull(TailsTailPose.sample(separate, 0, 0, false, 0, 0), "walk includes tails");
            assertNull(TailsTailPose.sample(separate, 1, 0, false, 0, 0), "run includes tails");
            assertNull(TailsTailPose.sample(separate, 0x10, 0, false, 0, 0), "spring includes tails");
        }
        assertEquals(0x81, TailsTailPose.sample(false, 9, 0, false, 0, 0).mappingFrame());
        assertEquals(2, TailsTailPose.sample(true, 9, 3, false, 0, 0).mappingFrame());
        assertEquals(new TailsTailPose.Pose(0x51, true, true), TailsTailPose.sample(false, 2, 0, false, 0, 0));
        assertEquals(0x27, TailsTailPose.sample(true, 0x20, 0, false, 0, 0).mappingFrame());
        assertEquals(0x28, TailsTailPose.sample(true, 0x21, 1, false, 0, 0).mappingFrame());
        assertNull(TailsTailPose.sample(false, 0x20, 0, false, 0, 0), "S2 flying body includes tails");
        assertNull(TailsTailPose.sample(true, 0x25, 0, false, 0, 0), "swimming body includes tails");
    }

    @Test void randomAccessSamplingMatchesNativeControllerWithoutMutatingIt() {
        for (boolean separate : new boolean[]{false, true}) for (boolean left : new boolean[]{false, true}) {
            for (int animation : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 9, 0xD, 0x10, 0x14, 0x20, 0x21, 0x25}) {
                var sprite = new TestablePlayableSprite("tails", (short) 100, (short) 120);
                sprite.setAnimationId(animation); sprite.setDirection(left ? Direction.LEFT : Direction.RIGHT);
                var controller = new TailsTailsController(sprite, null, separate);
                int latchedX = 0, latchedY = 0;
                for (int tick = 0; tick < 48; tick++) {
                    int x = (tick % 3 - 1) * 2, y = (tick % 5 - 2) * 2;
                    sprite.setXSpeed((short) (x * 256)); sprite.setYSpeed((short) (y * 256));
                    if (TailsTailPose.sampleTick(separate, animation, tick) == tick) { latchedX = x; latchedY = y; }
                    controller.update();
                    var before = controller.captureRewindState();
                    var sampled = TailsTailPose.sample(separate, animation, tick, left, latchedX, latchedY);
                    TailsTailPose.sample(separate, animation, 999, !left, -5, 4);
                    assertEquals(sampled, TailsTailPose.sample(separate, animation, tick, left, latchedX, latchedY));
                    assertEquals(before, controller.captureRewindState());
                    if (sampled == null) assertEquals(0, before.mappingFrame());
                    else {
                        assertEquals(before.mappingFrame(), sampled.mappingFrame(), "animation " + animation + " tick " + tick);
                        if (animation == 2 || animation == 3) {
                            assertEquals(before.dirHFlip(), sampled.flipX()); assertEquals(before.dirVFlip(), sampled.flipY());
                        } else { assertEquals(left, sampled.flipX()); assertFalse(sampled.flipY()); }
                    }
                }
            }
        }
    }
}

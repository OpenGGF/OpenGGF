package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SingletonResetExtension.class)
class TestHczTransitionBubbleInstance {

    @BeforeEach
    void setUp() {
        TestEnvironment.activeGameplayMode();
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void initDispatchDrawsWithoutMovingThenUsesRomCarrierAcceleration() {
        HczTransitionBubbleInstance bubble = bubble(0x80, 0x60, 0xC0, false);

        assertEquals(0x800, bubble.getXVelocityForTest(),
                "loc_6A710 overwrites sub_6A940's signed helper result with abs(delta*2)<<4");
        bubble.update(0, null);
        assertEquals(0x80, bubble.getX());
        assertEquals(0x60, bubble.getY());
        assertFalse(bubble.isHiddenForTest());

        bubble.update(1, null);
        assertEquals(0x900, bubble.getXVelocityForTest());
        assertEquals(0x89, bubble.getX());
        assertEquals(0x62, bubble.getY());
        assertTrue(bubble.isHiddenForTest());

        bubble.update(2, null);
        assertEquals(0xA00, bubble.getXVelocityForTest());
        assertEquals(0x93, bubble.getX());
        assertEquals(0x64, bubble.getY());
        assertFalse(bubble.isHiddenForTest());
    }

    @Test
    void randomAnimationPhaseCanKeepFirstActiveDispatchVisible() {
        HczTransitionBubbleInstance bubble = bubble(0x80, 0x60, 0xC0, true);

        bubble.update(0, null);
        bubble.update(1, null);

        assertFalse(bubble.isHiddenForTest());
    }

    @Test
    void precedingRenderFlagGateDeletesOffscreenBubbleBeforeMovement() {
        HczTransitionBubbleInstance bubble = bubble(0x500, 0x60, 0x540, false);

        bubble.update(0, null);
        bubble.update(1, null);

        assertTrue(bubble.isDestroyed());
        assertEquals(0x500, bubble.getX());
        assertEquals(0x60, bubble.getY());
    }

    private static HczTransitionBubbleInstance bubble(
            int x, int y, int targetX, boolean secondAnimationStep) {
        HczTransitionBubbleInstance bubble = new HczTransitionBubbleInstance(
                x, y, targetX, 2, secondAnimationStep);
        bubble.setServices(TestEnvironment.objectServices());
        return bubble;
    }
}

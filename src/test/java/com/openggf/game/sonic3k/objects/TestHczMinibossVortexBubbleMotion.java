package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestHczMinibossVortexBubbleMotion {

    @Test
    void farEdgeBubblesAccumulateFractionalPullTowardVortex() throws Exception {
        int vortexX = 0x3600;
        AbstractObjectInstance leftBubble = newBubble(vortexX - 0x80, 0x0780, vortexX, 0x0780);
        AbstractObjectInstance rightBubble = newBubble(vortexX + 0x7F, 0x0780, vortexX, 0x0780);

        for (int frame = 0; frame < 4; frame++) {
            leftBubble.update(frame, null);
            rightBubble.update(frame, null);
        }

        assertAll(
                () -> assertEquals(vortexX - 0x7F, leftBubble.getSpawn().x(),
                        "four +$40 pull steps must accumulate into one pixel toward the vortex"),
                () -> assertEquals(vortexX + 0x7E, rightBubble.getSpawn().x(),
                        "four -$40 pull steps must accumulate into one pixel toward the vortex")
        );
    }

    @Test
    void verticalPullUsesRomHalfPixelSteps() throws Exception {
        int vortexY = 0x0780;
        AbstractObjectInstance upperBubble = newBubble(0x3600, vortexY - 0x11, 0x3600, vortexY);
        AbstractObjectInstance lowerBubble = newBubble(0x3600, vortexY + 0x11, 0x3600, vortexY);

        upperBubble.update(0, null);
        lowerBubble.update(0, null);

        assertEquals(vortexY - 0x11, upperBubble.getSpawn().y(),
                "the first +$8000 step must remain in the starting integer pixel");

        upperBubble.update(1, null);
        lowerBubble.update(1, null);

        assertAll(
                () -> assertEquals(vortexY - 0x10, upperBubble.getSpawn().y(),
                        "two +$8000 steps must move the bubble down by one pixel"),
                () -> assertEquals(vortexY + 0x10, lowerBubble.getSpawn().y(),
                        "two -$8000 steps must move the bubble up by one pixel")
        );
    }

    @Test
    void bubbleAnimatesChangesDepthAndStopsMovingOnReleaseInstall() throws Exception {
        AbstractObjectInstance bubble = newBubble(0x3580, 0x0780, 0x3600, 0x0780);
        assertEquals(5, bubble.getPriorityBucket());
        bubble.update(0, null);
        assertEquals(0x16, TestHczMinibossVisualParity.field(bubble, "frame"));
        assertEquals(2, bubble.getPriorityBucket());
        bubble.update(1, null);
        assertEquals(0, TestHczMinibossVisualParity.field(bubble, "frame"));
        for (int i = 2; i < 32; i++) bubble.update(i, null);
        assertEquals(1, TestHczMinibossVisualParity.field(bubble, "phase"));
        int beforeX = bubble.getSpawn().x();
        int beforeY = bubble.getSpawn().y();
        var end = bubble.getClass().getDeclaredMethod("signalVortexEnd");
        end.setAccessible(true);
        end.invoke(bubble);
        bubble.update(32, null);
        assertEquals(beforeX, bubble.getSpawn().x());
        assertEquals(beforeY, bubble.getSpawn().y());
        for (int i = 0; i < 31; i++) bubble.update(33 + i, null);
        org.junit.jupiter.api.Assertions.assertFalse(bubble.isDestroyed());
        bubble.update(64, null);
        org.junit.jupiter.api.Assertions.assertTrue(bubble.isDestroyed());

        AbstractObjectInstance right = newBubble(0x367F, 0x0780, 0x3600, 0x0780);
        right.update(0, null);
        assertEquals(6, right.getPriorityBucket());
        org.junit.jupiter.api.Assertions.assertTrue(right.isHighPriority());
    }

    private static AbstractObjectInstance newBubble(
            int x, int y, int vortexX, int vortexY) throws Exception {
        Class<?> type = Class.forName(HczMinibossInstance.class.getName() + "$VortexBubbleChild");
        Constructor<?> constructor = type.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        return (AbstractObjectInstance) constructor.newInstance(x, y, 0, vortexX, vortexY);
    }
}

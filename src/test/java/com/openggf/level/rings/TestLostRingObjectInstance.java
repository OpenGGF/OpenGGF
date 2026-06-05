package com.openggf.level.rings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TestLostRingObjectInstance {

    @Test
    void spillAnimationDeceleratesLikeRom() {
        // ROM ChangeRingFrame: accum += counter each frame; frame = (accum >> 9) & 3;
        // counter decrements; counter starts at 0xFF.
        SpillAnimationState anim = new SpillAnimationState();
        anim.reset();                 // counter=0xFF, accum=0, frame=0
        assertEquals(0xFF, anim.counter());
        anim.tick();                  // accum = 0xFF; frame = (0xFF>>9)&3 = 0; counter=0xFE
        assertEquals(0, anim.frame());
        assertEquals(0xFE, anim.counter());
        // advance enough to roll bits 10:9
        for (int i = 0; i < 3; i++) anim.tick();
        // accum after 4 ticks = 0xFF+0xFE+0xFD+0xFC = 0x03FA; (0x03FA>>9)&3 = 1
        assertEquals(1, anim.frame());
    }

    @Test
    void ringBouncePhysicsMatchesLegacyPool() {
        // Fixed-point contract (identical to LostRing.reset, RingManager LostRing.java:24):
        //   xSubpixel = x << 8 (pixel coordinate stored in the high byte; low byte = sub-pixel).
        // forTest(x, y, ...) constructs with xSubpixel = x << 8, ySubpixel = y << 8.
        LostRingObjectInstance ring = LostRingObjectInstance.forTest(
                /*xPixel*/0x100, /*yPixel*/0x100, /*xVel*/0x0200, /*yVel*/-0x0400, /*phase*/0, /*lifetime*/0xFF);
        assertEquals(0x100 << 8, ring.getXSubpixelForTest());      // 0x10000 at start
        ring.stepPhysicsForTest(/*gravity*/0x18, /*floorCheck*/false);
        // ROM step (LostRingPool.updatePhysics, RingManager.java:1245-1247):
        //   xSubpixel += xVel;  ySubpixel += yVel;  yVel += gravity.
        assertEquals((0x100 << 8) + 0x0200, ring.getXSubpixelForTest()); // 0x10200
        assertEquals((0x100 << 8) + (-0x0400), ring.getYSubpixelForTest()); // 0x0FC00
        assertEquals(-0x0400 + 0x18, ring.getYVelForTest());
    }
}

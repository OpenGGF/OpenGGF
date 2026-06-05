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
}

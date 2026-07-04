package com.openggf.game.timeattack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackTimeFormat {
    @Test
    void formatsFramesAsMinutesSecondsCentis() {
        assertEquals("0:00.00", TimeAttackTimeFormat.frames(0));
        assertEquals("0:01.00", TimeAttackTimeFormat.frames(60));
        assertEquals("1:00.50", TimeAttackTimeFormat.frames(3630)); // 60s + 30f = 1:00.50
        assertEquals("0:59.98", TimeAttackTimeFormat.frames(3599));
    }

    @Test
    void formatsDeltasSignedInSeconds() {
        assertEquals("+1.00", TimeAttackTimeFormat.delta(60));
        assertEquals("-0.50", TimeAttackTimeFormat.delta(-30));
        assertEquals("", TimeAttackTimeFormat.delta(Integer.MIN_VALUE));
    }
}

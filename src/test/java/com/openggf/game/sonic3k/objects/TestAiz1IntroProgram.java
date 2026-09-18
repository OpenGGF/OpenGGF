package com.openggf.game.sonic3k.objects;

import com.openggf.tests.route.InputProgram;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestAiz1IntroProgram {
    @Test
    void earlyHandoffPreservesRecordedNeutralRows() {
        var input = new Aiz1IntroProgram(InputProgram.parse("3:0,2:8,1:10"), 289);
        for (int i = 0; i < 3; i++) assertEquals(0, input.next(true));
        assertEquals(292, input.row());
        assertEquals(8, input.next(true));
        assertEquals(0, input.heldFrames());
    }

    @Test
    void lateHandoffHoldsOnlyTheFirstNonNeutralRow() {
        var input = new Aiz1IntroProgram(InputProgram.parse("2:0,2:8,1:10"), 289);
        assertEquals(0, input.next(false));
        assertEquals(0, input.next(false));
        for (int i = 0; i < 7; i++) assertEquals(0, input.next(false));
        assertEquals(291, input.row());
        assertEquals(7, input.heldFrames());
        assertEquals(8, input.next(true));
        // Later control ownership does not reapply the one-time intro gate.
        assertEquals(8, input.next(false));
        assertEquals(16, input.next(false));
        assertTrue(input.exhausted());
    }
}

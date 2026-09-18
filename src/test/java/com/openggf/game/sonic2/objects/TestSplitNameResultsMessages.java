package com.openggf.game.sonic2.objects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSplitNameResultsMessages {
    @Test void failedAttemptWithAllEmeraldsKeepsTheUnshiftedTitle() {
        // Obj6F_Knuckles deletes on Got_Emerald=0 before applying all-seven offsets.
        var messages = new SplitNameResultsMessages(false, true);
        assertEquals(-48, messages.capture().titleX());
        for (int i = 0; i < 18; i++) messages.tick();
        assertEquals(240, messages.capture().titleX());
        assertEquals(2, messages.lines().size());
        assertEquals(22, messages.lines().get(1).frame());
        messages.startSuper();
        assertEquals(SplitNameResultsMessages.Phase.ARRIVAL, messages.capture().phase());
    }

    @Test void headingsApproachFromOppositeSidesAndNameIsIndependent() {
        var messages = new SplitNameResultsMessages(true, false);
        messages.tick();
        assertEquals(new SplitNameResultsMessages.Line(4, 432, 42), messages.lines().get(0));
        assertEquals(new SplitNameResultsMessages.Line(1, -32, 24), messages.lines().get(1));
        assertEquals(2, messages.lines().size(), "name is clipped before hardware x=80");
        for (int i = 1; i < 18; i++) messages.tick();
        assertEquals(java.util.List.of(new SplitNameResultsMessages.Line(4,160,42),
                new SplitNameResultsMessages.Line(1,240,24),
                new SplitNameResultsMessages.Line(29,168,24)), messages.lines());
        messages.startSuper();
        assertEquals(SplitNameResultsMessages.Phase.ARRIVAL, messages.capture().phase());
    }

    @Test void sevenEmeraldsUseSeparateSlideOutSlideInAndExactly180HoldPasses() {
        var messages = new SplitNameResultsMessages(true, true);
        for (int i = 0; i < 18; i++) messages.tick();
        assertEquals(228, messages.capture().titleX());
        assertEquals(124, messages.capture().nameX());
        messages.startSuper();
        assertEquals(92, messages.capture().nameX(), "later name object sees tally routine change immediately");
        for (int i = 0; i < 9; i++) messages.tick();
        assertEquals(SplitNameResultsMessages.Phase.LEAVE, messages.capture().phase());
        assertEquals(448, messages.capture().mainX());
        assertEquals(-60, messages.capture().titleX());
        messages.tick();
        assertEquals(SplitNameResultsMessages.Phase.ENTER_SUPER, messages.capture().phase());
        assertEquals(-48, messages.capture().titleX());
        assertEquals(-192, messages.capture().superX());
        assertEquals(448, messages.capture().mainX());
        for (int i = 0; i < 18; i++) messages.tick();
        assertEquals(160, messages.capture().mainX());
        assertEquals(SplitNameResultsMessages.Phase.ENTER_SUPER, messages.capture().phase());
        messages.tick();
        assertEquals(180, messages.capture().timer());
        for (int i = 0; i < 180; i++) {
            assertFalse(messages.complete());
            messages.tick();
        }
        assertFalse(messages.complete(), "TimedDisplay advances routine before DisplayOnly ends level");
        messages.tick();
        assertTrue(messages.complete());
    }

    @Test void everyTransitionReplaysIdenticallyFromItsCapturedPresentationState() {
        var messages = new SplitNameResultsMessages(true, true);
        for (int i = 0; i < 18; i++) messages.tick();
        messages.startSuper();
        for (int i = 0; i < 230; i++) {
            var before = messages.capture();
            messages.tick();
            var after = messages.capture();
            var lines = messages.lines();
            messages.restore(before);
            messages.tick();
            assertEquals(after, messages.capture());
            assertEquals(lines, messages.lines());
        }
    }
}

package com.openggf.game.sonic3k.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzTransitionRewind {
    @Test
    void backgroundEventConsumesResultsFlagBeforeOrdinaryTail() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        events.setEventsFg5(true);

        events.updateAct1BackgroundEvent(0x2E20, 0x540, false);

        assertFalse(events.isEventsFg5(),
                "FBZ1BGE_Normal clears Events_fg_5 in the ScreenEvents call that observes it");
    }

    @Test
    void activeRedrawRoutineDoesNotConsumeResultsFlag() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        events.setBackgroundRedraw(4, Sonic3kFBZEvents.RedrawDirection.TOP_DOWN);
        events.restoreAct1EventState(0, 0, 1, 0, 0,
                Sonic3kFBZEvents.DeformMode.INDOOR,
                Sonic3kFBZEvents.PaletteVariant.INDOOR,
                Sonic3kFBZEvents.PaletteTarget.NORMAL,
                true, true, false, false);
        events.setEventsFg5(true);

        events.updateAct1BackgroundEvent(0x2E20, 0x540, false);

        assertTrue(events.isEventsFg5(),
                "only FBZ1BGE_Normal reads Events_fg_5; redraw routines leave it pending");
        assertEquals(1, events.getBackgroundRedrawProgress(),
                "the active redraw routine still executes its own frame");
    }

    @Test
    void normalRoutineConsumesTransitionEvenWhenPlayerIsDying() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        events.setEventsFg5(true);

        events.updateAct1BackgroundEvent(0x2E20, 0x540, true);

        assertFalse(events.isEventsFg5(),
                "the transition branch precedes FBZ1BGE_Normal's death-only early return");
    }
}

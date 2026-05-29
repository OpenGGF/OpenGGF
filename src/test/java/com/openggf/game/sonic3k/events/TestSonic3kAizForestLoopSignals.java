package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kAizForestLoopSignals {

    @Test
    void forestLoopActiveOnlyDuringPostBombingAutoScroll() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);

        events.setBattleshipAutoScrollActiveRaw(false);
        events.setBattleshipWrapX(0x46C0);
        assertFalse(events.isBattleshipForestLoopActive(),
                "No loop while auto-scroll is inactive");

        events.setBattleshipAutoScrollActiveRaw(true);
        events.setBattleshipWrapX(0x4440);
        assertFalse(events.isBattleshipForestLoopActive(),
                "No forest loop during the bombing phase");

        events.setBattleshipWrapX(0x46C0);
        assertTrue(events.isBattleshipForestLoopActive(),
                "Forest loop active post-bombing with auto-scroll running");
    }

    @Test
    void forestLoopPeriodIsThe200Window() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        assertEquals(0x200, events.getForestLoopBgWrapPeriod(),
                "Forest loop BG period is the ROM $200 (the forest occupies BG cols 0-3)");
    }
}

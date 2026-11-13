package com.openggf.game.sonic2.competition.view;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionScrollSourceAdapter {
    @Test void readAndRebuildDoNotAdvanceDeformation() throws Exception {
        var rom = GameServices.rom().getRom();
        var source = new Sonic2CompetitionScrollSourceAdapter(rom,
                new Sonic2GameModule().getScrollHandlerProvider().getZoneConstants());
        // Cold state can be read before the host has initialized its cameras.
        var before = source.owner().capture();
        int[] page = new int[224];
        var handler = source.getHandler(0);
        handler.update(page, 320, 640, 8, 0);
        ((ScrollOutputRebuilder) handler).rebuildOutput(page, 320, 640, 8, 0);
        assertArrayEquals(before.scratch(), source.owner().capture().scratch());
        assertArrayEquals(before.horizontal(), source.owner().capture().horizontal());
        assertEquals(before.p1(), source.owner().capture().p1());
        Object saved = handler.captureRewindState();
        source.owner().update(com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.Zone.EHZ,
                0, new com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.Camera(320, 640),
                new com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.Camera(640, 720), 8);
        handler.restoreRewindState(saved);
        handler.update(page, 320, 640, 9, 0);
        assertArrayEquals(before.horizontal(), page);
    }
}

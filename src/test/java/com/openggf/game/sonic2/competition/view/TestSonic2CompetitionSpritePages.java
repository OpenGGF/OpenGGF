package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.resources.PlcLifecyclePhase;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

class TestSonic2CompetitionSpritePages {
    private static Build input(int marker) {
        return new Build(new Sonic2CompetitionDisplayInputs.Camera(0, 0), new Sonic2CompetitionDisplayInputs.Camera(0, 0),
                new Hud(false, 0, new PlayerHud(0, 0, 0, 0), new PlayerHud(0, 0, 0, 0), 0),
                new RingWindow(List.of()), new RingWindow(List.of()), 0,
                List.of(new Admission(16, 0, 0, new ObjectSlot(1, 32, 200, 140, 8, 8,
                        new Mapping(0, 0), marker, null))), false);
    }
    @Test void prepareNeverChangesLatchedPagesAndLagRepublishesLastCompletedPair() {
        var pages = new Sonic2CompetitionSpritePages(new Sonic2CompetitionSatBuilder(new RomByteReader(new byte[8])));
        var camera = new Camera(); var before = camera.capture();
        var registers = new Sonic2CompetitionViewRegisters(before, before);
        pages.prepare(input(1)); var first = pages.completed();
        registers.publish(PlcLifecyclePhase.ORDINARY_LEVEL, 0, 0, new int[224], before, before,
                pages.completed(), java.util.Collections.nCopies(64, 0), 0, false);
        pages.prepare(input(2));
        assertSame(first, registers.capture().pages());
        assertEquals(1, registers.capture().pages().first().entries().get(2).word());
        assertEquals(1, registers.capture().pages().second().entries().getFirst().word());
        var second = pages.completed();
        registers.publish(PlcLifecyclePhase.LAG, 0, 0, new int[224], before, before,
                pages.completed(), java.util.Collections.nCopies(64, 0), 0, false);
        assertSame(second, registers.capture().pages());
        assertEquals(2, registers.capture().pages().second().entries().getFirst().word());
        pages.prepare(input(3)); assertSame(second, registers.capture().pages());
        var saved = pages.capture(); pages.prepare(input(4)); pages.restore(saved);
        assertEquals(saved, pages.capture()); assertEquals(before, camera.capture());
    }
}

package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.competition.Sonic2CompetitionResults;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeSessionEvents {
    @Test void explicitArchivesAndNonfinalTiesKeepNativeBanksSeparateFromTerminalPublication() {
        var session = session();
        var port = new CapturingPort();
        var adapter = new Sonic2CompetitiveSpecialStageAdapter(session, port);
        adapter.load();
        session.levelState(0).setRings(8); session.levelState(1).setRings(8);
        port.events.tiedSector(0);
        assertEquals(List.of(0, 0, 0, 0, 0, 0), session.specialRings());
        assertEquals(8, session.levelState(0).getRings());
        assertEquals(8, session.levelState(1).getRings());
        port.events.collectedRing(1);
        port.events.archivedSector(1, 8, 9);
        assertEquals(0, session.levelState(0).getRings());
        assertEquals(0, session.levelState(1).getRings());
        var saved = session.capture();
        for (int replay = 0; replay < 2; replay++) {
            port.events.archivedSector(2, 12, 3);
            assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_STAGE, session.phase());
            assertNull(session.actResult(3, 0));
            port.events.stageComplete();
            assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ACT_RESULTS, session.phase());
            assertEquals(new Sonic2CompetitionResults.Wins(1, 1), session.actResult(3, 0));
            assertEquals(List.of(0, 0, 8, 9, 12, 3), session.specialRings());
            if (replay == 0) session.restore(saved);
        }
    }

    @Test void compatibilityCheckpointStillPublishesItsThirdSectorAndInvalidOrderingIsRejected() {
        var session = session();
        var port = new CapturingPort();
        new Sonic2CompetitiveSpecialStageAdapter(session, port).load();
        assertThrows(IllegalStateException.class, port.events::stageComplete);
        assertThrows(IllegalStateException.class, () -> port.events.archivedSector(1, 0, 0));
        port.events.checkpoint(); port.events.checkpoint();
        assertThrows(IllegalArgumentException.class, () -> port.events.tiedSector(2));
        port.events.checkpoint();
        assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ACT_RESULTS, session.phase());
    }

    @Test void actualThirdArchiveStaysInTheHalfpipeUntilItsNativeTerminalClockAndRestoresForward() throws Exception {
        var session = session();
        var graphics = new GraphicsManager(); graphics.initHeadless();
        var clock = new AtomicInteger();
        try (var halfpipe = new Sonic2CompetitiveHalfpipe(
                new CompetitiveHalfpipeRomData(TestEnvironment.currentRom()), graphics,
                SpecialStageViewport.nativeViewport(), AudioManager.createSilent(), clock::get)) {
            var adapter = new Sonic2CompetitiveSpecialStageAdapter(session, halfpipe);
            var input = new InputHandler();
            adapter.load();
            while (session.specialSector() != 3 && halfpipe.ticks() < 15_000) {
                clock.incrementAndGet(); adapter.step(input);
            }
            assertEquals(3, session.specialSector());
            assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_STAGE, session.phase());
            assertNotEquals(Sonic2CompetitiveHalfpipe.Phase.COMPLETE, halfpipe.phase());
            var savedSession = session.capture();
            var savedHalfpipe = halfpipe.capture();
            int savedClock = clock.get();
            int expected = savedHalfpipe.checkpoint().phase() == CompetitiveHalfpipeCheckpoint.Phase.FINAL_FADE ? 48 : 70;
            for (int replay = 0; replay < 2; replay++) {
                for (int tick = 1; tick < expected; tick++) {
                    clock.incrementAndGet(); adapter.step(input);
                    assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_STAGE, session.phase());
                }
                clock.incrementAndGet(); adapter.step(input);
                assertEquals(Sonic2CompetitiveHalfpipe.Phase.COMPLETE, halfpipe.phase());
                assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ACT_RESULTS, session.phase());
                assertTrue(halfpipe.objects().isEmpty(), "SSClearObjs precedes publication completion");
                if (replay == 0) { session.restore(savedSession); halfpipe.restore(savedHalfpipe); clock.set(savedClock); }
            }
        }
    }

    private static Sonic2CompetitionSession session() {
        var session = new Sonic2CompetitionSession();
        var input = PlayerInputState.of(10, 10, 0, 0, true, true);
        session.updateMenu(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), input), true);
        assertEquals(4, session.specialStageIndex());
        return session;
    }
    private static final class CapturingPort implements Sonic2CompetitiveSpecialStageAdapter.Halfpipe {
        Sonic2CompetitiveSpecialStageAdapter.Events events;
        @Override public boolean supportsCompetitiveStage(int stage) { return true; }
        @Override public void loadCompetitiveStage(int stage, Sonic2CompetitiveSpecialStageAdapter.Events events) { this.events = events; }
        @Override public void step(InputHandler input) { }
        @Override public void draw() { }
        @Override public void reset() { }
    }
}

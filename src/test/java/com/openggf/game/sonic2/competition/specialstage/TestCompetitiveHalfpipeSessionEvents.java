package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.competition.Sonic2CompetitionResults;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HexFormat;
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
        assertThrows(IllegalStateException.class, () -> port.events.archivedSector(-1, 0, 0));
        assertThrows(IllegalStateException.class, () -> port.events.archivedSector(3, 0, 0));
        port.events.checkpoint(); port.events.checkpoint();
        assertThrows(IllegalArgumentException.class, () -> port.events.tiedSector(2));
        port.events.checkpoint();
        assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ACT_RESULTS, session.phase());
    }

    @Test void forwardGapsKeepSkippedWordsAndTerminalDoesNotInventMissingFinalArchiveOrClearBanks() {
        var session = session();
        var port = new CapturingPort();
        new Sonic2CompetitiveSpecialStageAdapter(session, port).load();
        port.events.archivedSector(1, 7, 3);
        assertEquals(2, session.specialSector());
        assertEquals(List.of(0, 0, 7, 3, 0, 0), session.specialRings());
        assertThrows(IllegalStateException.class, () -> port.events.archivedSector(0, 1, 2));
        assertThrows(IllegalStateException.class, () -> port.events.archivedSector(1, 1, 2));
        assertThrows(IllegalStateException.class, () -> port.events.tiedSector(1));
        session.levelState(0).setRings(19); session.levelState(1).setRings(21);
        var saved = session.capture();
        for (int replay = 0; replay < 2; replay++) {
            port.events.stageComplete();
            assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_ACT_RESULTS, session.phase());
            assertEquals(new Sonic2CompetitionResults.Wins(1, 0), session.actResult(3, 0));
            assertEquals(List.of(0, 0, 7, 3, 0, 0), session.specialRings());
            assertEquals(19, session.levelState(0).getRings());
            assertEquals(21, session.levelState(1).getRings());
            assertThrows(IllegalStateException.class, port.events::stageComplete);
            assertThrows(IllegalStateException.class, () -> port.events.archivedSector(2, 4, 5));
            if (replay == 0) session.restore(saved);
        }
        var anotherSession = session();
        var anotherPort = new CapturingPort();
        new Sonic2CompetitiveSpecialStageAdapter(anotherSession, anotherPort).load();
        anotherPort.events.tiedSector(1);
        assertEquals(2, anotherSession.specialSector());
        assertEquals(List.of(0, 0, 0, 0, 0, 0), anotherSession.specialRings());
        anotherPort.events.archivedSector(2, 2, 9);
        anotherPort.events.stageComplete();
        assertEquals(new Sonic2CompetitionResults.Wins(0, 1), anotherSession.actResult(3, 0));
    }

    @Test void actualHandAllocationFailuresAdvanceNativeActAndLaterTerminalArchivesOnlyItsRealRow() throws Exception {
        var session = session();
        var port = new CapturingPort();
        new Sonic2CompetitiveSpecialStageAdapter(session, port).load();
        var data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
        // The helper's first allocation branches directly to RTS on failure;
        // its caller has already incremented Current_Special_Act.
        assertEquals("383c00484a38fe006704383c001c61000b266600008e12bc005a137c00060024",
                HexFormat.of().formatHex(data.rom().slice(0x35AB6, 32)));
        assertEquals("06380001fe17303c000c3438", HexFormat.of().formatHex(data.rom().slice(0x358F6, 12)),
                "ADDI.B #1,Current_Special_Act precedes the native winner/hand path");
        var players = CompetitiveSpecialStagePlayerAccess.create();
        for (int i = 0; i < 8; i++) { players.collectRing(0); port.events.collectedRing(0); }
        for (int i = 0; i < 2; i++) { players.collectRing(1); port.events.collectedRing(1); }
        var machine = new CompetitiveHalfpipeObjectMachine(data, players, AudioManager.createSilent(), port.events);
        machine.load(4); machine.ram().clear();
        for (int slot = 8; slot <= 126; slot++) machine.ram().replace(slot,
                new CompetitiveHalfpipeObjectMachine.Node(CompetitiveHalfpipeObjectMachine.Kind.HIDDEN));
        var track = new CompetitiveHalfpipeTrack(data); track.load(4);
        while (track.drawingIndex() != 4) track.tick();
        for (int sector = 0; sector < 3; sector++) {
            if (sector == 2) { machine.ram().clear(8); machine.ram().clear(9); }
            // This is the final native rainbow routine state, immediately
            // before it reads FF and compares at its previous x=$E8.
            var rainbow = new CompetitiveHalfpipeObjectMachine.Node(CompetitiveHalfpipeObjectMachine.Kind.RAINBOW);
            rainbow.base = 6; rainbow.frame = 9; rainbow.x = 0xE8;
            machine.ram().replace(60, rainbow);
            machine.runPass(track, 0x14, ignored -> { });
            assertEquals(sector + 1, machine.checkpoint().sector());
            assertEquals(0, session.specialSector(), "failed hands emit no archive or synthetic tie");
            assertEquals(List.of(0, 0, 0, 0, 0, 0), session.specialRings());
            if (sector < 2) {
                assertNull(machine.ram().at(60));
                machine.ram().replace(60, new CompetitiveHalfpipeObjectMachine.Node(
                        CompetitiveHalfpipeObjectMachine.Kind.HIDDEN));
            }
        }
        assertEquals(CompetitiveHalfpipeObjectMachine.Kind.WINGS, machine.ram().at(8).kind());
        assertEquals(CompetitiveHalfpipeObjectMachine.Kind.HAND, machine.ram().at(9).kind());
        assertEquals(70, machine.ram().at(9).timer, "lower allocated cells wait until the next object pass");
        var savedMachine = machine.capture(); var savedPlayers = players.capture();
        var savedSession = session.capture(); var savedTrack = track.capture();
        for (int replay = 0; replay < 2; replay++) {
            for (int tick = 0; tick < 69; tick++) { track.tick(); machine.runPass(track, 0x14, ignored -> { }); }
            assertEquals(0, session.specialSector());
            track.tick(); machine.runPass(track, 0x14, ignored -> { });
            assertEquals(3, session.specialSector());
            assertEquals(List.of(0, 0, 0, 0, 8, 2), session.specialRings());
            assertEquals(Sonic2CompetitionSession.Phase.SPECIAL_STAGE, session.phase());
            for (int tick = 0; tick < 48; tick++) { track.tick(); machine.runPass(track, 0x14, ignored -> { }); }
            assertTrue(machine.complete());
            assertTrue(machine.capture().display().isEmpty());
            assertTrue(machine.capture().ram().slots().stream().allMatch(slot -> slot.state() == null));
            port.events.stageComplete();
            assertEquals(new Sonic2CompetitionResults.Wins(1, 0), session.actResult(3, 0));
            if (replay == 0) {
                session.restore(savedSession); players.restore(savedPlayers); machine.restore(savedMachine); track.restore(savedTrack);
            }
        }
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

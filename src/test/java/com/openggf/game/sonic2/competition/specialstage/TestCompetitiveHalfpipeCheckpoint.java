package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter.Events;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeCheckpoint {
    @Test
    void nativeBytesOwnRainbowOrderAndPreviousMappingGate() throws Exception {
        var data = data();
        var checkpoint = new CompetitiveHalfpipeCheckpoint(data);
        var events = new ObservedEvents();
        assertEquals("000101010204060809ff", HexFormat.of().formatHex(data.rom().slice(0x357F4, 10)));
        assertEquals("162628fcececececececd6fce826",
                HexFormat.of().formatHex(data.rom().slice(0x35846 + 8 * 14, 14)));
        assertEquals(0x61000112, data.rom().readU32BE(0x35966), "final tie calls VSReset");
        assertEquals(0x117C000A, data.rom().readU32BE(0x3595E), "nonfinal tie selects MostRingsWin");
        checkpoint.request();
        assertThrows(IllegalStateException.class, checkpoint::request);
        for (int previousFrame : new int[]{0x13, 0x15, 0x0D}) {
            checkpoint.tick(previousFrame, true, 5, 6, events);
            assertEquals(CompetitiveHalfpipeCheckpoint.Phase.WAITING, checkpoint.capture().phase());
        }
        checkpoint.tick(0x14, false, 5, 6, events);
        assertEquals(0, checkpoint.capture().rainbowStep());
        for (int step = 0; step < 9; step++) {
            checkpoint.tick(0x14, true, 5, 6, events);
            var rainbow = checkpoint.overlay().rainbow();
            assertEquals(7, rainbow.size());
            for (int i = 0; i < 7; i++) {
                int raw = 0x35846 + step * 14 + (6 - i) * 2;
                assertEquals(data.rom().readU8(raw), rainbow.get(i).x());
                assertEquals(data.rom().readU8(raw + 1), rainbow.get(i).y());
                assertEquals(data.rom().readU8(0x357F4 + step), rainbow.get(i).mappingFrame());
            }
            assertTrue(events.log.isEmpty());
        }
        assertEquals(0xE8, checkpoint.overlay().rainbow().getFirst().x());
        checkpoint.tick(0x14, false, 5, 6, events);
        assertEquals(0, checkpoint.sector(), "negative rainbow exit still waits for drawing four");
        checkpoint.tick(0x14, true, 5, 6, events);
        assertEquals(1, checkpoint.sector());
        assertEquals(2, checkpoint.overlay().winner());
        assertTrue(checkpoint.overlay().rainbow().isEmpty());
    }

    @Test
    void nonfinalTieCarriesRingsAndPublishesMostRingsAfterSeventyTicks() throws Exception {
        var checkpoint = new CompetitiveHalfpipeCheckpoint(data());
        var events = new ObservedEvents();
        var resolution = resolve(checkpoint, 17, 17, events);
        assertFalse(resolution.archived());
        assertEquals(List.of("tie:0"), events.log);
        assertEquals(0x10, checkpoint.overlay().phraseId());
        for (int tick = 0; tick < 69; tick++) {
            assertFalse(checkpoint.tick(0, false, 17, 17, events).archived());
        }
        assertEquals(0x10, checkpoint.overlay().phraseId());
        checkpoint.tick(0, false, 17, 17, events);
        assertEquals(0x0A, checkpoint.overlay().phraseId());
        assertEquals(CompetitiveHalfpipeCheckpoint.Phase.IDLE, checkpoint.capture().phase());
        assertEquals(List.of("tie:0"), events.log, "a tied sector never archives or clears carried rings");
        resolve(checkpoint, 19, 20, events);
        for (int tick = 0; tick < 70; tick++) checkpoint.tick(0, false, 21, 28, events);
        assertEquals(List.of("tie:0", "archive:1:21:28"), events.log);
    }

    @Test
    void winnerHandOscillatesAndArchivesOnlyAfterInterveningTouches() throws Exception {
        var checkpoint = new CompetitiveHalfpipeCheckpoint(data());
        var events = new ObservedEvents();
        assertFalse(resolve(checkpoint, 30, 12, events).archived());
        assertEquals(0x0C, checkpoint.overlay().phraseId());
        assertEquals(1, checkpoint.overlay().winner());
        int[] firstHandRows = {27, 26, 25, 25, 26, 27, 28, 29, 30, 31, 31};
        for (int row : firstHandRows) {
            assertFalse(checkpoint.tick(0, false, 30, 12, events).archived());
            assertEquals(row, checkpoint.overlay().handY());
        }
        for (int tick = firstHandRows.length; tick < 69; tick++) checkpoint.tick(0, false, 30, 12, events);
        assertTrue(events.log.isEmpty());
        var archive = checkpoint.tick(0, false, 31, 32, events);
        assertTrue(archive.archived(), "caller resets both actor ring counters on precisely this pass");
        assertFalse(archive.fadeMusic());
        assertEquals(List.of("archive:0:31:32"), events.log,
                "VSReset sees current banks, even if later touches changed the displayed winner");
        assertEquals(0x0A, checkpoint.overlay().phraseId());
        assertFalse(checkpoint.tick(0, false, 0, 0, events).archived());
    }

    @Test
    void thirdTieArchivesImmediatelyThenCompletesAfterSeventyWithoutMusicFade() throws Exception {
        var checkpoint = new CompetitiveHalfpipeCheckpoint(data());
        var events = new ObservedEvents();
        finishFirstTwoTies(checkpoint, events);
        var archive = resolve(checkpoint, 44, 44, events);
        assertTrue(archive.archived());
        assertFalse(archive.fadeMusic());
        assertEquals(3, checkpoint.sector());
        assertEquals(List.of("tie:0", "tie:1", "archive:2:44:44"), events.log);
        for (int tick = 0; tick < 69; tick++) {
            var action = checkpoint.tick(0, false, 3, 7, events);
            assertFalse(action.archived());
            assertFalse(action.fadeMusic());
            assertFalse(checkpoint.complete());
        }
        checkpoint.tick(0, false, 3, 7, events);
        assertTrue(checkpoint.complete());
        assertEquals("complete", events.log.getLast());
        int callbacks = events.log.size();
        checkpoint.tick(0x14, true, 9, 9, events);
        assertEquals(callbacks, events.log.size());
        assertThrows(IllegalStateException.class, checkpoint::request);
    }

    @Test
    void thirdWinnerArchivesAtHandExpiryThenFadesForFortyEightTicks() throws Exception {
        var checkpoint = new CompetitiveHalfpipeCheckpoint(data());
        var events = new ObservedEvents();
        finishFirstTwoTies(checkpoint, events);
        resolve(checkpoint, 3, 7, events);
        for (int tick = 0; tick < 69; tick++) checkpoint.tick(0, false, 5, 11, events);
        assertEquals(List.of("tie:0", "tie:1"), events.log);
        var action = checkpoint.tick(0, false, 5, 12, events);
        assertTrue(action.archived());
        assertTrue(action.fadeMusic());
        assertEquals("archive:2:5:12", events.log.getLast());
        assertFalse(checkpoint.complete());
        for (int tick = 0; tick < 47; tick++) {
            var next = checkpoint.tick(0, false, 1, 2, events);
            assertFalse(next.fadeMusic(), "music request is an edge, not a persistent command");
            assertFalse(checkpoint.complete());
        }
        checkpoint.tick(0, false, 1, 2, events);
        assertTrue(checkpoint.complete());
        assertEquals(List.of("tie:0", "tie:1", "archive:2:5:12", "complete"), events.log);
    }

    @Test
    void liveWinnerIconsPreserveTheComparisonEvenIfLaterTouchesReverseTheArchive() throws Exception {
        var checkpoint = new CompetitiveHalfpipeCheckpoint(data());
        var events = new ObservedEvents();
        resolve(checkpoint, 8, 7, events);
        assertEquals(1, checkpoint.wins(0));
        assertEquals(0, checkpoint.wins(1));
        var compared = checkpoint.capture();
        for (int tick = 0; tick < 70; tick++) checkpoint.tick(0, false, 8, 12, events);
        assertEquals(List.of("archive:0:8:12"), events.log);
        assertEquals(1, checkpoint.wins(0));
        assertEquals(0, checkpoint.wins(1));
        checkpoint.restore(compared);
        assertEquals(1, checkpoint.wins(0));
        assertEquals(0, checkpoint.wins(1));
    }

    @Test
    void restoreRecreatesRainbowAndRepeatsItsForwardEventsWithoutAliasing() throws Exception {
        var data = data();
        var original = new CompetitiveHalfpipeCheckpoint(data);
        var events = new ObservedEvents();
        original.request();
        for (int i = 0; i < 4; i++) original.tick(0x14, true, 4, 2, events);
        var snapshot = original.capture();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rainbow().clear());
        var recreated = new CompetitiveHalfpipeCheckpoint(data);
        recreated.restore(snapshot);
        var replayEvents = new ObservedEvents();
        for (int tick = 0; tick < 90; tick++) {
            boolean drawing4 = tick < 6;
            var expected = original.tick(0x14, drawing4, 8, 3, events);
            assertEquals(expected, recreated.tick(0x14, drawing4, 8, 3, replayEvents));
            assertEquals(original.capture(), recreated.capture());
            assertEquals(original.overlay(), recreated.overlay());
        }
        assertEquals(events.log, replayEvents.log);
        assertEquals(List.of("archive:0:8:3"), events.log);
        assertEquals(4, snapshot.rainbowStep());
    }

    private static CompetitiveHalfpipeCheckpoint.Tick resolve(CompetitiveHalfpipeCheckpoint checkpoint,
                                                               int one, int two, Events events) {
        checkpoint.request();
        CompetitiveHalfpipeCheckpoint.Tick tick = null;
        for (int i = 0; i < 10; i++) tick = checkpoint.tick(0x14, true, one, two, events);
        return tick;
    }

    private static void finishFirstTwoTies(CompetitiveHalfpipeCheckpoint checkpoint, Events events) {
        for (int sector = 0; sector < 2; sector++) {
            resolve(checkpoint, 10, 10, events);
            for (int i = 0; i < 70; i++) checkpoint.tick(0, false, 10, 10, events);
        }
    }

    private static CompetitiveHalfpipeRomData data() throws Exception {
        return new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
    }

    private static final class ObservedEvents implements Events {
        private final List<String> log = new ArrayList<>();
        @Override public void collectedRing(int slot) { fail("checkpoint cannot forge ring touches"); }
        @Override public void hitBomb(int slot) { fail("checkpoint cannot forge bomb touches"); }
        @Override public void checkpoint() { fail("native phases cannot use the ordinary immediate checkpoint ingress"); }
        @Override public void archivedSector(int sector, int one, int two) {
            log.add("archive:" + sector + ":" + one + ":" + two);
        }
        @Override public void tiedSector(int sector) { log.add("tie:" + sector); }
        @Override public void stageComplete() { log.add("complete"); }
    }
}

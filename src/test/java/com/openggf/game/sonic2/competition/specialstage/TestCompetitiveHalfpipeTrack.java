package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native byte tables, publication order and deterministic stage-local rewind. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeTrack {
    @Test
    void retailAnimationAndOffsetTablesOwnTheClock() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        CompetitiveHalfpipeTrack track = new CompetitiveHalfpipeTrack(data);
        assertEquals("18180c100b00", HexFormat.of().formatHex(data.rom().slice(0x55FE, 6)));
        assertEquals("3c1e0f0a08060500", HexFormat.of().formatHex(data.rom().slice(0xB46, 8)));
        assertEquals("000a0022003a00460056", HexFormat.of().formatHex(data.rom().slice(0x63DC, 10)));
        assertEquals("262728292a2b26000102030405060708090a0b0c0d0e0f10",
                HexFormat.of().formatHex(data.rom().slice(0x63E6, 24)));
        assertEquals("11121314111213141112131411121314",
                HexFormat.of().formatHex(data.rom().slice(0x6422, 16)));
        assertEquals(0x0010, data.rom().readU16BE(0x768A + 0x0D * 2));
        assertEquals(0x0012, data.rom().readU16BE(0x768A + 0x0E * 2));
        int[] lengths = {24, 24, 12, 16, 11};
        for (int geometry = 0; geometry < lengths.length; geometry++) {
            assertEquals(lengths[geometry], track.segmentLength(geometry));
        }
        track.load(0);
        List<Integer> frames = new ArrayList<>();
        for (int tick = 0; tick < 16 * 5; tick++) {
            track.tick();
            if (track.drawingIndex() == 0) frames.add(track.frame());
        }
        assertEquals(List.of(0x11, 0x12, 0x13, 0x14, 0x11, 0x12, 0x13, 0x14,
                0x11, 0x12, 0x13, 0x14, 0x11, 0x12, 0x13, 0x14), frames);
        assertEquals(1, track.segment(), "native segment advances while its last mapping remains displayed");
        assertEquals(0x14, track.frame());
        assertEquals(0x13, track.previousFrame());
        for (int i = 0; i < 5; i++) track.tick();
        assertEquals(0x11, track.frame());
        assertEquals(0x14, track.previousFrame(), "Obj5A_Init sees the previous displayed mapping");
    }

    @Test
    void vintReloadAndGeometryPublicationKeepTheirNativeBoundaries() throws Exception {
        CompetitiveHalfpipeTrack track = new CompetitiveHalfpipeTrack(data());
        assertEquals(0, track.drawingIndex());
        assertEquals(0, track.frame());
        assertEquals(0, track.animationTimer());
        for (int tick = 1; tick <= 4; tick++) {
            track.tick();
            assertEquals(tick, track.drawingIndex());
            assertEquals(6 - tick, track.capture().durationTimer());
            assertEquals(4, track.animationTimer(), "d1 is five, stored player byte is four");
            assertEquals(0, track.frame());
        }
        assertEquals(19, track.offsetX(), "slice four publishes frame zero's ROM curve offset");
        track.tick();
        assertEquals(0, track.drawingIndex());
        assertEquals(0x11, track.frame());
        assertEquals(1, track.capture().animationFrame());
        assertEquals(19, track.offsetX(), "a new mapping does not immediately publish its geometry offset");
        assertEquals(-8, track.bgScroll(), "four initial native turn-scroll slices");
        for (int i = 0; i < 4; i++) track.tick();
        assertEquals(4, track.drawingIndex());
        assertEquals(0, track.offsetX());
        assertEquals(-1, track.vScroll());
        for (int i = 0; i < 8; i++) track.tick();
        assertEquals(-257, track.vScroll(), "native row FF is zero-extended before word subtraction");
    }

    @Test
    void prerollVintsDrawWithoutGeometryOrScrollUntilItsSingleExplicitScroll() throws Exception {
        var track = new CompetitiveHalfpipeTrack(data());
        for (int i = 0; i < 4; i++) track.tickPreRoll();
        assertEquals(4, track.drawingIndex());
        assertEquals(0, track.offsetX());
        assertEquals(0, track.offsetY());
        assertEquals(0, track.bgScroll());
        assertEquals(0, track.vScroll());
        track.scrollAfterPreRoll();
        assertEquals(-2, track.bgScroll(), "one SS_ScrollBG call, rather than four bootstrap scroll updates");
        assertEquals(0, track.offsetX());
        var snapshot = track.capture();
        track.tickPreRoll();
        assertEquals(0x11, track.frame());
        assertEquals(-2, track.bgScroll());
        assertEquals(0, track.offsetX());
        track.restore(snapshot);
        track.tick();
        assertEquals(0x11, track.frame());
        assertEquals(-2, track.bgScroll());
    }

    @Test
    void allNativeOrientationsUseRiseFourteenAndPublishBuffersAtSliceFour() throws Exception {
        CompetitiveHalfpipeRomData data = data();
        CompetitiveHalfpipeTrack track = new CompetitiveHalfpipeTrack(data);
        boolean sawRiseFourteen = false;
        boolean sawDropSix = false;
        boolean sawStraightTwo = false;
        boolean sawFlipped = false;
        for (int stage = 0; stage < 7; stage++) {
            track.load(stage);
            int ticks = 0;
            while (!track.capture().ended()) {
                assertTrue(ticks++ < 20_000, "finite retail layout");
                var before = track.capture();
                track.tick();
                var after = track.capture();
                if (after.ended()) break;
                int mapping = track.frame();
                boolean trigger = mapping == 0x0D || mapping == 0x12 || mapping == 0x1A;
                if (trigger && track.segment() < data.segments(stage).size()) {
                    assertEquals(data.segments(stage).get(track.segment()).flipped(), track.flipped());
                } else {
                    assertEquals(before.flipped(), after.flipped(), "orientation persists between native triggers");
                }
                if (after.alternateScroll() != before.alternateScroll()) {
                    assertTrue(trigger);
                    assertEquals(4, track.drawingIndex());
                }
                if (track.drawingIndex() == 4) {
                    int rawX = (byte) data.rom().readU8(0x768A + mapping * 2);
                    assertEquals(track.flipped() ? (byte) -rawX : rawX, track.offsetX());
                    assertEquals((byte) data.rom().readU8(0x768A + mapping * 2 + 1), track.offsetY());
                }
                sawRiseFourteen |= mapping == 0x0D;
                sawDropSix |= mapping == 0x1A;
                sawStraightTwo |= mapping == 0x12;
                sawFlipped |= track.flipped();
            }
        }
        assertTrue(sawRiseFourteen);
        assertTrue(sawDropSix);
        assertTrue(sawStraightTwo);
        assertTrue(sawFlipped);
    }

    @Test
    void captureRestoreAndNewLoadsIsolateCounterGeometryAndBothScrollBuffers() throws Exception {
        CompetitiveHalfpipeTrack track = new CompetitiveHalfpipeTrack(data());
        track.load(2);
        for (int i = 0; i < 937; i++) track.tick();
        var snapshot = track.capture();
        List<CompetitiveHalfpipeTrack.State> forward = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            track.tick();
            forward.add(track.capture());
        }
        track.load(6);
        assertEquals(0, track.segment());
        assertEquals(0, track.frame());
        assertEquals(0, track.bgScroll());
        assertEquals(0, track.vScroll());
        track.restore(snapshot);
        assertEquals(snapshot, track.capture());
        for (var expected : forward) {
            track.tick();
            assertEquals(expected, track.capture());
        }
        track.load(2);
        for (int i = 0; i < 937; i++) track.tick();
        assertEquals(snapshot, track.capture(), "fresh load repeats its own ROM clock without prior-stage history");
    }

    private static CompetitiveHalfpipeRomData data() throws Exception {
        return new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
    }
}

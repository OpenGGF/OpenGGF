package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.openggf.game.soniccd.objects.CdLostRing.DeleteEntry.*;
import static com.openggf.game.soniccd.objects.CdLostRing.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdLostRing.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNCOMPILED and UNRUN by the external author.
 * ObjLostRing ($11, Level/_Objects/Ring/Main.asm:317-460) at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 *
 * <p>Velocities are hand-derived from ObjLostRing_Init (:376-398) over the cited SineTable words
 * (Level/Functions (Misc).asm:81-101). Floor probes use the TestCdMosqui/TestCdFlower terrain through the actual
 * CdR11aTerrain/CdPlayerTerrain decoders: a solid floor whose top is world Y $780 across X $200..$2FF.
 * CollectRing is a recording stand-in for Root's CdRing::collectRing binding; no reward arithmetic is restated.
 * Native hurt-poll positions, velocities and bounce polls are comparison-only corroboration, never inputs.</p>
 */
class TestCdLostRing {
    private static final int RING_MAP = 0x20A0FA;
    private static final int RING_TILE = 0xA7AE;
    /** bottomBound word for most cases: the deletion limit is $07E0. */
    private static final int BOTTOM = 0x0700;
    /** SineTable (Functions (Misc).asm:81-84,89-96) words read at the eight lost-ring angles and their cosines. */
    private static final int[][] SOURCE_SINE_WORDS = {
            {0x08, 0x31}, {0x18, 0x8E}, {0x28, 0xD4}, {0x38, 0xFB},
            {0x88, -0x31}, {0x98, -0x8E}, {0xA8, -0xD4}, {0xB8, -0xFB},
            {0xC8, -0xFB}, {0xD8, -0xD4}, {0xE8, -0x8E}, {0xF8, -0x31}};
    /**
     * (oXVel, oYVel) of ring k. Shift 2 for rings 0..15, 1 for 16..31 and 0 for 32..47 (LSR.W #8 of d4);
     * odd rings reuse the negated d2 and unchanged d3. Ring 46's carry then borrow reloads $288, so the
     * zero-ring quirk repeats this list every 48 rings. Never fitted to native frames.
     */
    private static final int[][] VELOCITIES = {
            {-0xC4, -0x3EC}, {0xC4, -0x3EC}, {-0x238, -0x350}, {0x238, -0x350},
            {-0x350, -0x238}, {0x350, -0x238}, {-0x3EC, -0xC4}, {0x3EC, -0xC4},
            {-0x3EC, 0xC4}, {0x3EC, 0xC4}, {-0x350, 0x238}, {0x350, 0x238},
            {-0x238, 0x350}, {0x238, 0x350}, {-0xC4, 0x3EC}, {0xC4, 0x3EC},
            {-0x62, -0x1F6}, {0x62, -0x1F6}, {-0x11C, -0x1A8}, {0x11C, -0x1A8},
            {-0x1A8, -0x11C}, {0x1A8, -0x11C}, {-0x1F6, -0x62}, {0x1F6, -0x62},
            {-0x1F6, 0x62}, {0x1F6, 0x62}, {-0x1A8, 0x11C}, {0x1A8, 0x11C},
            {-0x11C, 0x1A8}, {0x11C, 0x1A8}, {-0x62, 0x1F6}, {0x62, 0x1F6},
            {-0x31, -0xFB}, {0x31, -0xFB}, {-0x8E, -0xD4}, {0x8E, -0xD4},
            {-0xD4, -0x8E}, {0xD4, -0x8E}, {-0xFB, -0x31}, {0xFB, -0x31},
            {-0xFB, 0x31}, {0xFB, 0x31}, {-0xD4, 0x8E}, {0xD4, 0x8E},
            {-0x8E, 0xD4}, {0x8E, 0xD4}, {-0x31, 0xFB}, {0x31, 0xFB}};
    private static final CdLostRing.Effect RING_LOSS = new CdLostRing.SoundRequest(CdLostRing.Sound.FM_RINGLOSS);

    @Test
    void hurtSpawnerIsRingZeroThenFirstFreeSlotsAndOnlyLaterChildrenMoveInTheSamePass() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(3, 3, 0x06, 0x01, 2, 0, 7, 3);
        h.spawner(32, 0x022B, 0x076B);   // HurtPlayer: oID $11 and the player's integer oX/oY only
        h.occupy(34, 35);

        var spawn = h.run(32, 661);

        assertEquals(effects(created(0, 32, -0xC4, -0x3EC), created(1, 33, 0xC4, -0x3EC),
                created(2, 36, -0x238, -0x350), RING_LOSS, call(INIT), call(MAIN), call(DRAW_OBJECT)), spawn.effects());
        assertEquals(List.of("ring.read", "spill.read", "spill.write", "spill.read", "spill.write", "spill.read",
                "spill.write", "ring.read", "ring.write", "spill.read", "spill.read", "draw:32"), h.log,
                "MOVE.W rings, a timer byte per ring, the .DidInit writes, then Main's frame and timer reads");
        assertEquals(new CdRingGlobals(0, 3, 0, 0x80, 2, 0, 7, 3), h.ring.value,
                "MOVE.W #0 / MOVE.B #$80 / MOVE.B #0; lives, HUD lives, time stop and ring animation untouched");
        assertEquals(new CdRingSpillGlobals(0xFF, 0, 0), h.spill.value, "only the timer byte is written");
        assertArrayEquals(lostRing(0x022A, 0x3C00, 0x0767, 0x1400, 0xFF3C, 0xFC2C, 0), h.slot(32).copyBytes(),
                "Init falls into Main: old velocity move, then ADDI.W #$18");
        assertArrayEquals(lostRing(0x022B, 0, 0x076B, 0, 0x00C4, 0xFC14, 0), h.slot(33).copyBytes(),
                "a child is only initialized by the spawner; it moves at its own physical visit");
        assertEquals(0x3D, h.slot(34).id());
        assertEquals(0x3D, h.slot(35).id());

        var later = h.passFrom(33, 661);

        assertEquals(List.of(33, 36), List.copyOf(later.keySet()));
        for (var result : later.values()) { assertEquals(effects(call(MAIN), call(DRAW_OBJECT)), result.effects()); }
        assertArrayEquals(lostRing(0x022B, 0xC400, 0x0767, 0x1400, 0x00C4, 0xFC2C, 0), h.slot(33).copyBytes());
        assertArrayEquals(lostRing(0x0228, 0xC800, 0x0767, 0xB000, 0xFDC8, 0xFCC8, 0), h.slot(36).copyBytes());
        assertEquals(List.of(32, 33, 36), h.draw.wholes());
        h.tail();
        assertEquals(new CdRingSpillGlobals(0xFE, 0x00FF, 0), h.spill.value);
        // Comparison only: native hurt poll 3002 saw slots 32/33/36 at X 554/555/552, Y 1895, these velocities
        // and timer $FE / accumulator $00FF / frame 0 (player-contact-reference-opus.md).
    }

    @ParameterizedTest(name = "rings {0} -> {1} lost rings")
    @CsvSource({"1, 1", "2, 2", "0x1F, 31", "0x20, 32", "0x21, 32", "0x25, 32", "0x7FFF, 32", "0x8000, 32", "0xFFFF, 32"})
    void ringCountCapsAtThirtyTwoWithAnUnsignedWordCompare(String rings, int count) {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(Integer.decode(rings), 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0700);
        var result = h.run(32, 0);
        assertEquals(count, result.effects().stream().filter(CdLostRing.RingInitialized.class::isInstance).count());
        assertTrue(result.effects().stream().noneMatch(CdLostRing.SlotUnavailable.class::isInstance),
                "DBF exhausts before FindObjSlot fails");
        for (int whole = 32; whole < 128; whole++) {
            assertEquals(whole < 32 + count ? 0x11 : 0, h.slot(whole).id(), "slot " + whole);
        }
        assertEquals(0, h.ring.value.rings());
    }

    @Test
    void everyCappedRingGetsTheAlternatingSineVelocityWithItsShiftAndCarryRules() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(0x25, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0700);
        var result = h.run(32, 0);
        var expected = new ArrayList<CdLostRing.Effect>();
        for (int k = 0; k < 32; k++) { expected.add(created(k, 32 + k, VELOCITIES[k][0], VELOCITIES[k][1])); }
        expected.addAll(effects(RING_LOSS, call(INIT), call(MAIN), call(DRAW_OBJECT)));
        assertEquals(expected, result.effects());
        for (int k = 1; k < 32; k++) {
            var s = h.slot(32 + k);
            assertEquals(VELOCITIES[k][0] & 0xFFFF, s.word(X_VELOCITY), "ring " + k);
            assertEquals(VELOCITIES[k][1] & 0xFFFF, s.word(Y_VELOCITY), "ring " + k + " waits for its own Main");
            assertEquals(0x0260, s.centreX());
            assertEquals(0x0700, s.centreY());
        }
        assertEquals((VELOCITIES[0][1] + 0x18) & 0xFFFF, h.slot(32).word(Y_VELOCITY), "only the spawner ran Main");
        assertEquals(0, h.slot(64).id());
    }

    @Test
    void zeroRingsUnderflowTheDbfCounterAndAllocateUntilFindObjSlotFails() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(0, 3, 0x02, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0700);
        var result = h.run(32, 0);
        var expected = new ArrayList<CdLostRing.Effect>();
        for (int k = 0; k < 96; k++) {
            expected.add(created(k, 32 + k, VELOCITIES[k % 48][0], VELOCITIES[k % 48][1]));
        }
        expected.add(new CdLostRing.SlotUnavailable(96));
        expected.addAll(effects(RING_LOSS, call(INIT), call(MAIN), call(DRAW_OBJECT)));
        assertEquals(expected, result.effects(), "SUBQ.W #1 of 0 gives $FFFF: the source has no zero-ring guard");
        assertEquals(new CdRingGlobals(0, 3, 0, 0x80, 0, 0, 7, 3), h.ring.value);
        assertEquals(96, h.log.stream().filter("spill.write"::equals).count(), "MOVE.B #$FF on every ring");
    }

    @Test
    void allocationFailureStopsSpawningButStillClearsTheRingGlobalsAndRequestsOneRingLoss() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(5, 3, 0x06, 0, 0, 0, 7, 3);
        h.occupyAllExcept(40, 60, 90);
        h.spawner(60, 0x0260, 0x0700);
        var result = h.run(60, 0);
        assertEquals(effects(created(0, 60, -0xC4, -0x3EC), created(1, 40, 0xC4, -0x3EC),
                created(2, 90, -0x238, -0x350), new CdLostRing.SlotUnavailable(3), RING_LOSS, call(INIT),
                call(MAIN), call(DRAW_OBJECT)), result.effects());
        assertEquals(new CdRingGlobals(0, 3, 0, 0x80, 0, 0, 7, 3), h.ring.value);
        // FindObjSlot always restarts at whole 32: slot 40 lies below the cursor and waits for the next pass.
        var later = h.passFrom(61, 0);
        assertEquals(List.of(90), List.copyOf(later.keySet()));
        assertEquals(0xFC14, h.slot(40).word(Y_VELOCITY), "the lower child has not run Main in this pass");
    }

    @Test
    void aFullPoolLeavesOnlyTheSpawnerRingWithNoReservationClearOrRetry() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(3, 3, 0, 0, 0, 0, 7, 3);
        h.occupyAllExcept(127);
        h.spawner(127, 0x0260, 0x0700);
        var before = h.pool().capture().slots();
        var result = h.run(127, 0);
        assertEquals(effects(created(0, 127, -0xC4, -0x3EC), new CdLostRing.SlotUnavailable(1), RING_LOSS,
                call(INIT), call(MAIN), call(DRAW_OBJECT)), result.effects());
        for (int whole = 32; whole < 127; whole++) { assertEquals(before.get(whole - 32), h.slot(whole), "slot " + whole); }
        assertEquals(0, h.ring.value.rings(), "rings are cleared even though only the spawner became a ring");
    }

    @Test
    void findObjSlotAnswersRetainEveryUnwrittenDirtyByteAndAddTwoToTheirRoutine() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(2, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0700);
        h.write(h.slot(32).moveByte(SUBTYPE2, 0x5C));   // the spawner's own byte; Init copies it from a0
        var dirty = h.dirtyFree(33, 4);
        h.run(32, 0);
        var expected = dirty.moveByte(ID, 0x11).addByte(ROUTINE, 2).moveByte(Y_RADIUS, 8).moveByte(X_RADIUS, 8)
                .moveWord(X, 0x0260).moveWord(Y, 0x0700).moveLong(MAPPINGS, RING_MAP).moveByte(SUBTYPE2, 0x5C)
                .moveWord(TILE, RING_TILE).moveByte(PRIORITY, 3).moveByte(SPRITE_FLAGS, 4)
                .moveByte(COLLISION_TYPE, 0x47).moveByte(WIDTH, 8).moveWord(X_VELOCITY, 0x00C4)
                .moveWord(Y_VELOCITY, 0xFC14);
        assertArrayEquals(expected.copyBytes(), h.slot(33).copyBytes(),
                "fractions, animator, flags, status, saved ID and object variables keep the free slot's bytes");
        assertEquals(6, h.slot(33).routine());
    }

    @Test
    void aDirtyChildRoutineSelectsSparkleInThisPassAndItsDirtyAnimationStopsAtTheBoundedScriptDirectory() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(2, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0700);
        h.dirtyFree(33, 4);
        h.run(32, 0);
        var child = h.slot(33);
        assertEquals(6, child.routine(), "ADDQ.B #2 of the dirty routine 4 selects ObjLostRing_Sparkle");
        var failure = assertThrows(UnsupportedOperationException.class, () -> h.run(33, 0));
        assertFalse(failure instanceof CdLostRing.UnsupportedLostRing, "the existing bounded animator owns this stop");
        assertEquals(child, h.slot(33), "AnimateObject publishes nothing on a failed read and DrawObject is not reached");
        assertEquals(List.of(32), h.draw.wholes());
    }

    @Test
    void aDirtyFeRoutineWrapsToInitSoTheChildSpawnsAgainWithTheClearedZeroRingCount() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(2, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0700);
        h.dirtyFree(33, 0xFE);
        h.run(32, 0);
        assertEquals(0, h.slot(33).routine(), "ADDQ.B #2 wraps $FE to ObjLostRing_Init");
        var again = h.run(33, 0);   // its own later visit in the same RunObjects pass
        assertEquals(created(0, 33, -0xC4, -0x3EC), again.effects().getFirst());
        assertTrue(again.effects().contains(new CdLostRing.SlotUnavailable(95)), "rings is now 0: allocate until full");
        assertEquals(1, again.effects().stream().filter(RING_LOSS::equals).count());
        for (int whole = 32; whole < 128; whole++) { assertEquals(0x11, h.slot(whole).id(), "slot " + whole); }
    }

    @Test
    void aRingDeletedEarlierInThisPassIsTheFindObjSlotAnswerAndItsLostRingWaitsForTheNextPass() {
        var h = new Harness();
        h.write(h.slot(32).moveByte(ID, 0x10).moveByte(ROUTINE, 8));   // an ObjRing at ObjRing_Destroy
        h.ring.value = new CdRingGlobals(2, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(33, 0x0260, 0x0700);
        var ring = new CdRing(h.runtime, TestCdRing.data());
        var ringPorts = new TestCdRing.Ports(h.runtime);
        // Root's live walk reads each ID at its own visit; no list of active IDs is captured before the pass.
        ring.executeSelected(32, TestCdRing.input(), ringPorts, ringPorts);
        assertArrayEquals(new byte[64], h.slot(32).copyBytes());
        var spawn = h.run(33, 0);
        assertEquals(created(1, 32, 0xC4, -0x3EC), spawn.effects().get(1), "$10 -> $11 at slot 32 within one pass");
        assertEquals(0xFC14, h.slot(32).word(Y_VELOCITY), "below the cursor: no Main until the next pass");
        assertTrue(h.passFrom(34, 0).isEmpty());
        var next = h.pass(1);
        assertEquals(List.of(32, 33), List.copyOf(next.keySet()));
        assertEquals(0xFC2C, h.slot(32).word(Y_VELOCITY));
    }

    @Test
    void mainMovesWithTheOldVelocityWordsThenAddsSignedGravityAndCommitsBeforeDrawObject() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0x1234, 2);
        h.write(h.ringAt(40, 0x0300, 0x0700, 0x0123, 0xFF00).moveWord(X_FRACTION, 0x8000).moveWord(Y_FRACTION, 0x8000));
        var result = h.run(40, 0);   // d7 $57 + V 0 is not a multiple of four; oYVel stays negative anyway
        assertEquals(effects(call(MAIN), call(DRAW_OBJECT)), result.effects());
        var s = result.state();
        assertEquals(0x0301, s.centreX());
        assertEquals(0xA300, s.word(X_FRACTION), "$03008000 + EXT.L/ASL.L #8 of $0123");
        assertEquals(0x06FF, s.centreY());
        assertEquals(0x8000, s.word(Y_FRACTION), "the OLD oYVel $FF00 moves Y before ADDI.W #$18");
        assertEquals(0xFF18, s.word(Y_VELOCITY));
        assertEquals(2, s.unsignedByte(MAP_FRAME));
        var seen = h.draw.observed.getFirst();
        assertEquals(0x0301, seen.centreX(), "DrawObject sees the committed image");
        assertEquals(0xFF18, seen.word(Y_VELOCITY));
        assertEquals(new CdRingSpillGlobals(0x80, 0x1234, 2), h.spill.value, "Main never writes the spill store");
    }

    @ParameterizedTest(name = "whole {0}, levelVIntCounter ${1} -> floor probe {2}")
    @CsvSource({"32, 00000000, false", "32, 00000001, true", "32, 00000002, false", "32, 00000003, false",
            "32, 000000FD, true", "32, 123456FD, true", "32, FFFFFF01, true", "33, 00000002, true",
            "33, 00000001, false", "34, 00000003, true", "34, 000000FF, true", "35, 00000000, true",
            "35, 00000100, true", "35, 00000001, false", "126, 00000003, true", "126, 00000000, false",
            "127, 00000000, true", "127, 80000000, true", "127, 00000002, false",
            // Comparison only: the five observed native bounce polls (V 717/741/761/750/737) and one miss.
            "32, 000002CD, true", "32, 000002E5, true", "32, 000002F9, true", "33, 000002EE, true",
            "36, 000002E1, true", "36, 000002E2, false"})
    void floorProbeRunsOnlyWhenTheCounterLowBytePlusD7IsAMultipleOfFour(int whole, String counter, boolean probes) {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 1);
        h.ringAt(whole, 0x0270, 0x0778, 0, 0x00E8);
        var result = h.run(whole, Integer.parseUnsignedInt(counter, 16));
        if (probes) {
            assertEquals(effects(angle(0x22), call(MAIN), call(DRAW_OBJECT)), result.effects());
            assertEquals(0x0777, result.state().centreY());
            assertEquals(0xFF40, result.state().word(Y_VELOCITY));
        } else {
            assertEquals(effects(call(MAIN), call(DRAW_OBJECT)), result.effects());
            assertEquals(0x0778, result.state().centreY());
            assertEquals(0x0100, result.state().word(Y_VELOCITY));
        }
        assertEquals(0xE800, result.state().word(Y_FRACTION), "ADD.W d1,oY(a0) keeps the fraction");
    }

    @Test
    void floorBounceAddsTheNegativeDistanceThenSubtractsAQuarterAndNegates() {
        // whole 35 with V 0: d7 $5C makes the probe run. Expected: {startY, startYFraction, oldYVel, endY, endYFraction, endYVel}.
        int[][] cases = {
                {0x0778, 0x0000, 0x00E8, 0x0777, 0xE800, 0xFF40},   // probe $780: d1 -1; $100 - $40, NEG
                {0x077D, 0x0000, 0x00E8, 0x0777, 0xE800, 0xFF40},   // probe $785: d1 -6
                {0x0775, 0x0000, 0x03E8, 0x0777, 0xE800, 0xFD00},   // $400 - $100 = $300, NEG
                {0x0779, 0x2000, 0xFFE8, 0x0777, 0x0800, 0x0000}};  // gravity reaches 0: not BMI, probe $781: d1 -2
        for (int[] c : cases) {
            var h = new Harness();
            h.spill.value = new CdRingSpillGlobals(0x80, 0, 1);
            h.write(h.ringAt(35, 0x0270, c[0], 0, c[2]).moveWord(Y_FRACTION, c[1]));
            var result = h.run(35, 0);
            assertEquals(effects(angle(0x22), call(MAIN), call(DRAW_OBJECT)), result.effects(), "start $" + Integer.toHexString(c[0]));
            assertEquals(c[3], result.state().centreY());
            assertEquals(c[4], result.state().word(Y_FRACTION));
            assertEquals(c[5], result.state().word(Y_VELOCITY));
            assertEquals(0x0270, result.state().centreX());
        }
    }

    @Test
    void zeroOrPositiveFloorDistanceKeepsTheFallButStillReportsTheAngleByte() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 1);
        h.ringAt(35, 0x0270, 0x0777, 0, 0x00E8);   // probe $77F: d1 0, BPL
        var touching = h.run(35, 0);
        assertEquals(effects(angle(0x22), call(MAIN), call(DRAW_OBJECT)), touching.effects());
        assertEquals(0x0777, touching.state().centreY());
        assertEquals(0x0100, touching.state().word(Y_VELOCITY));

        var high = new Harness();
        high.spill.value = new CdRingSpillGlobals(0x80, 0, 1);
        high.ringAt(35, 0x0270, 0x0760, 0, 0x00E8);   // probe $768: no block within reach
        var falling = high.run(35, 0);
        assertEquals(effects(angle(0), call(MAIN), call(DRAW_OBJECT)), falling.effects(),
                "ObjGetFloorDist zeroes primaryAngle before FindLevelFloor");
        assertEquals(0x0100, falling.state().word(Y_VELOCITY));
    }

    @Test
    void anOddFloorAngleIsReportedAsTheGlobalByteNotTheClearedD3() {
        var h = new Harness(0x23);
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 1);
        h.ringAt(35, 0x0270, 0x0778, 0, 0x00E8);
        var result = h.run(35, 0);
        assertEquals(effects(angle(0x23), call(MAIN), call(DRAW_OBJECT)), result.effects(),
                "primaryAngle keeps the odd byte; only d3 is cleared, and ObjLostRing never reads d3");
        assertEquals(0x0777, result.state().centreY());
        assertEquals(0xFF40, result.state().word(Y_VELOCITY));
    }

    @Test
    void upwardOrOverflowedVelocitySkipsTheFloorTestEntirely() {
        int[][] cases = {   // {startY, oldYVel, endY, endYFraction, endYVel, probes}
                {0x0778, 0xFF00, 0x0777, 0x0000, 0xFF18, 0},
                {0x0700, 0x7FF0, 0x077F, 0xF000, 0x8008, 0},   // ADDI.W overflows into the sign bit: BMI
                {0x0700, 0x7FE7, 0x0777, 0xE700, 0xA000, 1}};  // $7FFF probes $787: d1 -8, $7FFF - $1FFF, NEG
        for (int[] c : cases) {
            var h = new Harness();
            h.spill.value = new CdRingSpillGlobals(0x80, 0, 1);
            h.ringAt(35, 0x0270, c[0], 0, c[1]);
            var result = h.run(35, 0);
            assertEquals(c[5] == 1 ? effects(angle(0x22), call(MAIN), call(DRAW_OBJECT)) : effects(call(MAIN), call(DRAW_OBJECT)),
                    result.effects(), "old oYVel $" + Integer.toHexString(c[1]));
            assertEquals(c[2], result.state().centreY());
            assertEquals(c[3], result.state().word(Y_FRACTION));
            assertEquals(c[4], result.state().word(Y_VELOCITY));
        }
    }

    @Test
    void timerZeroDeletesAfterTheFloorProbeAndBeforeTheBottomTest() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0, 0x1234, 1);
        h.ringAt(35, 0x0270, 0x0778, 0, 0x00E8);
        var result = h.run(35, 0);
        assertEquals(effects(angle(0x22), call(MAIN), deleted(TIMER_EXPIRED)), result.effects());
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], result.state().copyBytes(), "DeleteObject clears all 16 longwords");
        assertTrue(h.draw.observed.isEmpty());
        assertEquals(List.of("spill.read", "spill.read"), h.log);

        var low = new Harness();
        low.spill.value = new CdRingSpillGlobals(0, 0, 0);
        low.ringAt(40, 0x0300, 0x07F1, 0, 0xFF00);
        assertEquals(effects(call(MAIN), deleted(TIMER_EXPIRED)), low.run(40, 0).effects(), "TST.B precedes the bottom test");
    }

    @ParameterizedTest(name = "bottomBound ${0}, final oY ${1} -> deleted {2}")
    @CsvSource({"0700, 07E0, false", "0700, 07E1, true", "0700, FFF0, true", "FF30, 0010, false", "FF30, 0011, true",
            "FFFF, 00DF, false", "FFFF, 00E0, true", "0000, 00E0, false", "0000, 00E1, true"})
    void bottomDeletionIsAnUnsignedWordCompareAgainstBottomBoundPlusE0(String bottom, String finalY, boolean expectDeleted) {
        var h = new Harness();
        h.bottom = Integer.parseInt(bottom, 16);
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 0);
        int y = Integer.parseInt(finalY, 16);
        h.ringAt(40, 0x0300, (y + 1) & 0xFFFF, 0, 0xFF00);   // moves up one pixel and never probes
        var result = h.run(40, 0);
        if (expectDeleted) {
            assertEquals(effects(call(MAIN), deleted(BELOW_BOTTOM_BOUND)), result.effects());
            assertArrayEquals(new byte[64], result.state().copyBytes());
            assertTrue(h.draw.observed.isEmpty());
        } else {
            assertEquals(effects(call(MAIN), call(DRAW_OBJECT)), result.effects());
            assertEquals(y, result.state().centreY());
        }
    }

    @Test
    void mainCopiesTheSpillFrameUnconditionallyAndNeverReadsTimeStopOrTheRingGlobals() {
        byte[] first = null;
        for (int timeStop : new int[]{0, 0x0123}) {
            var h = new Harness();
            h.ring.value = new CdRingGlobals(7, 3, 0, 0, 0, timeStop, 7, 1);
            h.spill.value = new CdRingSpillGlobals(0x40, 0x0400, 2);
            h.ringAt(40, 0x0300, 0x0700, 0, 0xFF00);
            var result = h.run(40, 0);
            assertEquals(2, result.state().unsignedByte(MAP_FRAME), "MOVE.B ringLossAnimFrame,oMapFrame(a0): no time-stop gate");
            assertEquals(List.of("spill.read", "spill.read", "draw:40"), h.log);
            if (first == null) { first = result.state().copyBytes(); }
            else { assertArrayEquals(first, result.state().copyBytes()); }
        }
    }

    @Test
    void drawObjectSeesThePriorBuildsBit7AndTheResultReloadsItsWrite() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 3);
        h.write(h.ringAt(40, 0x0300, 0x0700, 0, 0xFF00).moveByte(SPRITE_FLAGS, 0x84));
        var result = h.run(40, 0);
        var seen = h.draw.observed.getFirst();
        assertEquals(0x84, seen.spriteFlags(), "DrawObject clears bit 7 itself; Main does not clear it first");
        assertEquals(3, seen.unsignedByte(MAP_FRAME));
        assertEquals(0x06FF, seen.centreY());
        assertEquals(0x04, result.state().spriteFlags(), "the Result is the live slot after the port");
        assertSame(h.pool(), h.draw.pools.getFirst());
        assertEquals(40, h.draw.slots.getFirst().wholeIndex());

        var failing = new Harness();
        failing.spill.value = new CdRingSpillGlobals(0x80, 0, 3);
        failing.ringAt(40, 0x0300, 0x0700, 0, 0xFF00);
        var failure = new IllegalStateException("queue failure");
        failing.draw.onDraw = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> failing.run(40, 0)));
        assertEquals(0x06FF, failing.slot(40).centreY(), "completed writes stay; nothing is rolled back or retried");
    }

    @Test
    void collectCommitsItsThreeWritesBeforeTheSharedCollectRingThenFallsIntoSparkleAndDraws() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(4, 3, 0, 0, 0, 0, 7, 3);
        h.write(h.ringAt(40, 0x0300, 0x0700, 0x0040, 0x0100).moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 0x84)
                .moveByte(MAP_FRAME, 2));
        var bank = h.runtime.capture().savedFlags().copy();
        var result = h.run(40, 0);
        assertEquals(effects(new CdLostRing.RewardFmRequest(CdRing.FmSound.FM_RING), call(COLLECT), call(SPARKLE),
                call(DRAW_OBJECT)), result.effects());
        var atCall = h.collect.atCall.getFirst().slots().get(40 - 32);
        assertEquals(6, atCall.routine(), "ADDQ.B #2,oRoutine(a0)");
        assertEquals(0, atCall.unsignedByte(COLLISION_TYPE));
        assertEquals(1, atCall.unsignedByte(PRIORITY));
        assertEquals(2, atCall.unsignedByte(MAP_FRAME), "Collect resets no animation or map byte");
        assertEquals(0x0300, atCall.centreX(), "routine 4 has no ObjMove");
        var s = result.state();
        assertEquals(6, s.routine());
        assertEquals(4, s.unsignedByte(MAP_FRAME), "Ani_Ring delay 5, first frame 4");
        assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(5, s.unsignedByte(ANIMATION_TIME));
        assertEquals(0x04, s.spriteFlags());
        assertEquals(0x84, h.draw.observed.getFirst().spriteFlags());
        assertEquals(0x5A, h.ring.value.lives(), "the binding's write reached Root's one ring-globals store");
        assertEquals(List.of("collect", "ring.read", "ring.write", "draw:40"), h.log);
        assertArrayEquals(bank, h.runtime.capture().savedFlags().copy(), "ObjLostRing never touches the saved bank");
    }

    @Test
    void sparklePublishesFramesFourToSevenThenFcAdvancesToDestroyWhichDeletesOnTheNextDispatch() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(4, 3, 0, 0, 0, 0, 7, 3);
        h.write(h.ringAt(40, 0x0300, 0x0700, 0, 0).moveByte(ROUTINE, 4));
        for (int n = 0; n <= 24; n++) {
            var result = h.run(40, n);
            int frame = n < 6 ? 4 : n < 12 ? 5 : n < 18 ? 6 : 7;
            assertEquals(frame, result.state().unsignedByte(MAP_FRAME), "call " + n);
            assertEquals(n == 24 ? 8 : 6, result.state().routine(), "call " + n);
            List<CdLostRing.Effect> expected = n == 0
                    ? effects(new CdLostRing.RewardFmRequest(CdRing.FmSound.FM_RING), call(COLLECT), call(SPARKLE), call(DRAW_OBJECT))
                    : effects(call(SPARKLE), call(DRAW_OBJECT));
            assertEquals(expected, result.effects(), "call " + n);
        }
        var last = h.run(40, 25);
        assertEquals(effects(deleted(DESTROY_ROUTINE)), last.effects());
        assertArrayEquals(new byte[64], last.state().copyBytes());
        assertEquals(25, h.draw.observed.size(), "the $FC call is still drawn; only the next dispatch deletes");
        assertEquals(1, h.collect.calls);
    }

    @Test
    void collectRingBindingMustWriteTheGlobalsOnceAndRequestExactlyOneSound() {
        List<BiConsumer<CdRing.GlobalsPort, CdRing.SourceCalls>> wrong = List.of(
                (g, c) -> g.write(g.read()),
                (g, c) -> { g.write(g.read()); c.playFmSound(CdRing.FmSound.FM_RING); c.subCpuCommand(CdRing.SubCpuCommand.SCMD_YESSFX); },
                (g, c) -> c.playFmSound(CdRing.FmSound.FM_RING),
                (g, c) -> { g.write(g.read()); g.write(g.read()); c.playFmSound(CdRing.FmSound.FM_RING); },
                (g, c) -> { g.write(g.read()); c.drawObject(null, 40); });
        for (var behaviour : wrong) {
            var h = new Harness();
            h.write(h.ringAt(40, 0x0300, 0x0700, 0, 0).moveByte(ROUTINE, 4).moveByte(MAP_FRAME, 2));
            h.collect.behaviour = behaviour;
            assertThrows(IllegalStateException.class, () -> h.run(40, 0));
            var s = h.slot(40);
            assertEquals(6, s.routine());
            assertEquals(0, s.unsignedByte(COLLISION_TYPE));
            assertEquals(1, s.unsignedByte(PRIORITY));
            assertEquals(2, s.unsignedByte(MAP_FRAME), "Sparkle never ran");
            assertTrue(h.draw.observed.isEmpty());
        }
        var h = new Harness();
        h.write(h.ringAt(40, 0x0300, 0x0700, 0, 0).moveByte(ROUTINE, 4));
        var failure = new IllegalStateException("helper failure");
        h.collect.behaviour = (g, c) -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> h.run(40, 0)), "propagated unwrapped");
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"0x01", "0x03", "0x05", "0x07", "0x0A", "0x0C", "0xFE"})
    void routineOutsideTheFiveEntryIndexStopsBeforeAnyWriteOrPortCall(String routine) {
        var h = new Harness();
        h.write(h.ringAt(40, 0x0300, 0x0700, 0, 0).moveByte(ROUTINE, Integer.decode(routine)));
        var before = h.slot(40);
        var failure = assertThrows(CdLostRing.UnsupportedLostRing.class, () -> h.run(40, 0));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        assertEquals(before, h.slot(40));
        assertTrue(h.log.isEmpty());
    }

    @Test
    void wrongIdD7ZoneAndWidthGatesFailBeforeAnyWriteOrPortCall() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(3, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(40, 0x0260, 0x0700);
        h.write(h.slot(41).moveByte(ID, 0x10));
        h.ringAt(42, 0x0300, 0x0700, 0, 0xFF00);
        var before = h.pool().capture().slots();
        var ports = new CdLostRing.Ports(h.ring, h.spill);
        assertThrows(IllegalArgumentException.class,
                () -> h.body.run(40, new CdLostRing.Inputs(0, 127 - 41, 0, BOTTOM), ports), "d7 is the physical counter");
        for (int zone : new int[]{1, 6, 0xFF}) {
            for (int whole : new int[]{40, 42}) {
                var failure = assertThrows(CdLostRing.UnsupportedLostRing.class,
                        () -> h.body.run(whole, new CdLostRing.Inputs(0, 127 - whole, zone, BOTTOM), ports));
                assertEquals(ZONE_DOMAIN, failure.boundary());
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> h.body.run(41, new CdLostRing.Inputs(0, 127 - 41, 0, BOTTOM), ports), "RunObjects dispatched $11 here");
        assertThrows(NullPointerException.class, () -> h.body.run(40, null, ports));
        assertThrows(NullPointerException.class, () -> h.body.run(40, new CdLostRing.Inputs(0, 87, 0, BOTTOM), null));
        assertEquals(before, h.pool().capture().slots());
        assertTrue(h.log.isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Inputs(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Inputs(0, 0x10000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Inputs(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Inputs(0, 0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Inputs(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Inputs(0, 0, 0, 0x10000));
        assertDoesNotThrow(() -> new CdLostRing.Inputs(Integer.MIN_VALUE, 0, 0, 0xFFFF), "the counter is raw long bits");
        assertThrows(NullPointerException.class, () -> new CdLostRing.Ports(null, h.spill));
        assertThrows(NullPointerException.class, () -> new CdLostRing.Ports(h.ring, null));
    }

    @Test
    void aDirtyStandingObjectFlagStopsAtObjMoveAfterCommittingTheMapFrameWrite() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 3);
        var before = h.ringAt(40, 0x0300, 0x0700, 0x0100, 0x0100).moveByte(FLAGS, 0x08);
        h.write(before);
        var failure = assertThrows(CdLostRing.UnsupportedLostRing.class, () -> h.run(40, 0));
        assertEquals(OBJ_MOVE_STANDING_OBJECT, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        assertArrayEquals(before.moveByte(MAP_FRAME, 3).copyBytes(), h.slot(40).copyBytes(),
                "only MOVE.B ringLossAnimFrame,oMapFrame(a0) precedes BSR.W ObjMove");
        assertEquals(List.of("spill.read"), h.log);
        assertTrue(h.draw.observed.isEmpty());

        var plain = new Harness();
        plain.spill.value = new CdRingSpillGlobals(0x80, 0, 3);
        plain.write(plain.ringAt(40, 0x0300, 0x0700, 0x0100, 0x0100).moveByte(FLAGS, 0xF7));
        assertEquals(0x0301, plain.run(40, 0).state().centreX(), "every other oFlags bit keeps the plain ObjMove path");
    }

    @Test
    void objMoveAddsSignExtendedVelocityLongsWithNativeWrapAndFractionCarry() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 0);
        h.write(h.ringAt(40, 0x0000, 0x0010, -0x100 & 0xFFFF, 0x0018).moveWord(Y_FRACTION, 0xF000));
        var result = h.run(40, 0);   // d7 $57 + V 0: no probe
        var s = result.state();
        assertEquals(0xFFFF, s.centreX(), "$00000000 - $00010000 wraps the 32-bit X long");
        assertEquals(0x0000, s.word(X_FRACTION));
        assertEquals(0x0011, s.centreY(), "fraction $F000 + $1800 carries into the integer word");
        assertEquals(0x0800, s.word(Y_FRACTION));
        assertEquals(0x0030, s.word(Y_VELOCITY));
        assertEquals(effects(call(MAIN), call(DRAW_OBJECT)), result.effects());
    }

    @Test
    void closedCutCaptureRestoresAndReplaysWithoutCallingAnyPort() {
        var h = new Harness();
        h.ring.value = new CdRingGlobals(9, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x0260, 0x0770);
        h.pass(0x100);
        h.tail();
        var capture = h.runtime.capture();
        var ring = h.ring.value;
        var spill = h.spill.value;
        var forward = h.replay(30);
        var finalSlots = h.pool().capture().slots();
        var finalRing = h.ring.value;
        var finalSpill = h.spill.value;
        int logSize = h.log.size();

        h.runtime.validateRestore(capture);
        h.runtime.restore(capture);
        h.ring.value = ring;
        h.spill.value = spill;
        assertEquals(logSize, h.log.size(), "restore invokes no globals, draw or CollectRing port");
        assertEquals(capture.pool().slots(), h.pool().capture().slots());

        assertEquals(forward, h.replay(30));
        assertEquals(finalSlots, h.pool().capture().slots());
        assertEquals(finalRing, h.ring.value);
        assertEquals(finalSpill, h.spill.value);
        var foreign = TestCdRing.runtime();
        assertThrows(IllegalArgumentException.class, () -> foreign.restore(capture));
    }

    @Test
    void resultsAreImmutableAndConstructionRejectsMissingOrUnboundedInputs() {
        var h = new Harness();
        h.spill.value = new CdRingSpillGlobals(0x80, 0, 0);
        h.ringAt(40, 0x0300, 0x0700, 0, 0xFF00);
        var result = h.run(40, 0);
        assertThrows(UnsupportedOperationException.class, () -> result.effects().add(call(MAIN)));
        assertFalse(result.deleted());
        assertThrows(NullPointerException.class, () -> new CdLostRing.Result(null, List.of()));

        var rings = TestCdRing.data().visuals();
        assertThrows(NullPointerException.class, () -> new CdLostRing.Assets(null, RING_MAP, RING_TILE));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Assets(rings, RING_MAP + 1, RING_TILE));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Assets(rings, 0x1FFFFE, RING_TILE));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Assets(rings, 0x240000, RING_TILE));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Assets(rings, RING_MAP, 0x10000));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.Assets(
                CdObjectVisualAssets.decodeStatic(rings.mappings().copy(), 9, 14), RING_MAP, RING_TILE),
                "Ani_Ring must be bound");
        assertThrows(NullPointerException.class, () -> CdLostRing.Assets.fromObjectAssets(null));

        var a = assets();
        var math = sourceSineMath();
        var terrain = new Terrain(0x22).probes();
        CdLostRing.DrawObjectCall draw = (pool, slot) -> { };
        CdLostRing.CollectRingCall collect = (globals, calls) -> { };
        assertThrows(NullPointerException.class, () -> new CdLostRing(null, a, math, terrain, draw, collect));
        assertThrows(NullPointerException.class, () -> new CdLostRing(h.runtime, null, math, terrain, draw, collect));
        assertThrows(NullPointerException.class, () -> new CdLostRing(h.runtime, a, null, terrain, draw, collect));
        assertThrows(NullPointerException.class, () -> new CdLostRing(h.runtime, a, math, null, draw, collect));
        assertThrows(NullPointerException.class, () -> new CdLostRing(h.runtime, a, math, terrain, null, collect));
        assertThrows(NullPointerException.class, () -> new CdLostRing(h.runtime, a, math, terrain, draw, null));
        assertSame(a, new CdLostRing(h.runtime, a, math, terrain, draw, collect).assets());

        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.RingInitialized(-1, 32, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.RingInitialized(96, 32, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.RingInitialized(0, 31, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.RingInitialized(0, 128, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.RingInitialized(0, 32, 0x8000, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.RingInitialized(0, 32, 0, -0x8001));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.SlotUnavailable(0));
        assertThrows(IllegalArgumentException.class, () -> new CdLostRing.SlotUnavailable(97));
        assertThrows(IllegalArgumentException.class,
                () -> new CdLostRing.GlobalWrite(CdLostRing.GlobalField.PRIMARY_ANGLE, 0x100));
        assertThrows(NullPointerException.class, () -> new CdLostRing.GlobalWrite(null, 0));
        assertThrows(NullPointerException.class, () -> new CdLostRing.CallReturned(null));
        assertThrows(NullPointerException.class, () -> new CdLostRing.SoundRequest(null));
        assertThrows(NullPointerException.class, () -> new CdLostRing.RewardFmRequest(null));
        assertThrows(NullPointerException.class, () -> new CdLostRing.RewardSubCpuRequest(null));
        assertThrows(NullPointerException.class, () -> new CdLostRing.Deleted(null));
        assertEquals(0x11, CdLostRing.OBJECT_ID);
    }

    /** Opt-in supplied-media check through the production loaders; a missing property is an explicit skip. */
    @Test
    void authenticatedUsaAssetsBindTheRingTablesAndTheActualSineTableGivesTheSourceVelocities() throws Exception {
        var assets = loadUsa();
        var objects = assets.openingObjects();
        var bound = CdLostRing.Assets.fromObjectAssets(objects);
        assertSame(objects.rings(), bound.rings());
        assertEquals(RING_MAP, bound.mappingsAddress());
        assertEquals(RING_TILE, bound.baseTileWord());
        assertEquals(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS).baseWord(), bound.baseTileWord());
        assertArrayEquals(TestCdRing.data().visuals().mappings().copy(), bound.rings().mappings().copy());
        assertArrayEquals(TestCdRing.data().visuals().animationTable().copy(), bound.rings().animationTable().copy());
        for (int[] word : SOURCE_SINE_WORDS) {
            assertEquals(word[1], assets.player().sineWord(word[0]), "fixture word $" + Integer.toHexString(word[0]));
        }
        var h = new Harness(CdObjectPlacementRuntime.fromAssets(assets), bound, new CdPlayerMath(assets.player()),
                new CdPlayerTerrain(assets.terrain()));
        h.ring.value = new CdRingGlobals(0, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x022B, 0x076B);
        var effects = h.run(32, 0).effects();
        for (int k = 0; k < 96; k++) {
            assertEquals(created(k, 32 + k, VELOCITIES[k % 48][0], VELOCITIES[k % 48][1]), effects.get(k), "ring " + k);
        }
    }

    /** Opt-in; the native hurt poll is a comparison only and supplies no runtime value. */
    @Test
    void actualAssetsReproduceTheNativeHurtPollRingsAsAComparisonOnly() throws Exception {
        var assets = loadUsa();
        var h = new Harness(CdObjectPlacementRuntime.fromAssets(assets),
                CdLostRing.Assets.fromObjectAssets(assets.openingObjects()), new CdPlayerMath(assets.player()),
                new CdPlayerTerrain(assets.terrain()));
        h.ring.value = new CdRingGlobals(3, 3, 0, 0, 0, 0, 7, 3);
        h.spawner(32, 0x022B, 0x076B);   // the player's (555, 1899) at the touch
        h.occupy(34, 35);
        h.pass(661);                     // every ring moves up in this pass, so the counter is not read
        h.tail();
        // NATIVE poll 3002 (player-contact-reference-opus.md): {slot, x, y, xVel, yVel}.
        int[][] observed = {{32, 554, 1895, -0xC4, -0x3D4}, {33, 555, 1895, 0xC4, -0x3D4}, {36, 552, 1895, -0x238, -0x338}};
        for (int[] o : observed) {
            var s = h.slot(o[0]);
            assertEquals(0x11, s.id(), "slot " + o[0]);
            assertEquals(o[1], s.centreX(), "slot " + o[0]);
            assertEquals(o[2], s.centreY(), "slot " + o[0]);
            assertEquals(o[3] & 0xFFFF, s.word(X_VELOCITY), "slot " + o[0]);
            assertEquals(o[4] & 0xFFFF, s.word(Y_VELOCITY), "slot " + o[0]);
        }
        assertEquals(new CdRingGlobals(0, 3, 0, 0x80, 0, 0, 7, 3), h.ring.value);
        assertEquals(new CdRingSpillGlobals(0xFE, 0x00FF, 0), h.spill.value);
    }

    @ParameterizedTest @CsvSource({"4,5,3,0,0", "99,100,4,2,1"})
    void lostRingCollectionBindsTheExistingProductionRewardOwner(
            int initialRings, int finalRings, int finalLives, int lifeFlags, int hudLives) {
        var log = new ArrayList<String>(); var runtime = TestCdRing.runtime();
        var ring = new RingStore(log); var spill = new SpillStore(log);
        var body = new CdLostRing(runtime, assets(), sourceSineMath(), new Terrain(0x22).probes(),
                new RecordingDraw(log), CdRing::collectRing);
        ring.value = new CdRingGlobals(initialRings, 3, 0, 0, 0, 0, 7, 3);
        runtime.pool().write(runtime.pool().state(40).moveByte(ID, 0x11).moveByte(ROUTINE, 4));
        var savedFlags = runtime.capture().savedFlags().copy();
        var result = body.run(40, new CdLostRing.Inputs(0, 87, 0, BOTTOM), new CdLostRing.Ports(ring, spill));
        CdLostRing.Effect reward = initialRings == 99
                ? new CdLostRing.RewardSubCpuRequest(CdRing.SubCpuCommand.SCMD_YESSFX)
                : new CdLostRing.RewardFmRequest(CdRing.FmSound.FM_RING);
        assertEquals(effects(reward, call(COLLECT), call(SPARKLE), call(DRAW_OBJECT)), result.effects());
        assertEquals(new CdRingGlobals(finalRings, finalLives, lifeFlags, 1, hudLives, 0, 7, 3), ring.value);
        assertArrayEquals(savedFlags, runtime.capture().savedFlags().copy());
    }

    private static CdLostRing.Effect call(CdLostRing.NativeCall nativeCall) { return new CdLostRing.CallReturned(nativeCall); }
    private static CdLostRing.Effect created(int ring, int whole, int xVelocity, int yVelocity) {
        return new CdLostRing.RingInitialized(ring, whole, xVelocity, yVelocity);
    }
    private static CdLostRing.Effect angle(int value) {
        return new CdLostRing.GlobalWrite(CdLostRing.GlobalField.PRIMARY_ANGLE, value);
    }
    private static CdLostRing.Effect deleted(CdLostRing.DeleteEntry entry) { return new CdLostRing.Deleted(entry); }
    private static List<CdLostRing.Effect> effects(CdLostRing.Effect... all) { return List.of(all); }

    /** Expected image of a ring that Init wrote over a clean slot, after its own Main and DrawObject. */
    private static byte[] lostRing(int x, int xFraction, int y, int yFraction, int xVelocity, int yVelocity, int mapFrame) {
        byte[] b = new byte[64];
        b[0x00] = 0x11;
        b[0x01] = 0x04;
        word(b, 0x02, RING_TILE);
        word(b, 0x04, RING_MAP >>> 16);
        word(b, 0x06, RING_MAP);
        word(b, 0x08, x);
        word(b, 0x0A, xFraction);
        word(b, 0x0C, y);
        word(b, 0x0E, yFraction);
        word(b, 0x10, xVelocity);
        word(b, 0x12, yVelocity);
        b[0x16] = 8;
        b[0x17] = 8;
        b[0x18] = 3;
        b[0x19] = 8;
        b[0x1A] = (byte) mapFrame;
        b[0x20] = 0x47;
        b[0x24] = 2;
        return b;
    }

    private static CdLostRing.Assets assets() {
        return new CdLostRing.Assets(TestCdRing.data().visuals(), RING_MAP, RING_TILE);
    }

    /** Sparse admitted SineTable: only the cited source words; every other word stays zero so a wrong index shows. */
    static CdPlayerMath sourceSineMath() {
        byte[] sine = new byte[640];
        for (int[] entry : SOURCE_SINE_WORDS) {
            word(sine, entry[0] * 2, entry[1]);
            if (entry[0] < 64) { word(sine, (entry[0] + 256) * 2, entry[1]); }   // cosine extension (:98-101)
        }
        byte[] atan = new byte[257];
        for (int i = 0; i <= 256; i++) { atan[i] = (byte) (i / 8); }   // admitted monotonic table; CalcAngle is unused
        byte[] animations = new byte[578];
        for (int id = 0; id < 60; id++) { word(animations, id * 2, 120); }
        animations[122] = (byte) 0xFF;
        byte[] mappings = new byte[398];
        byte[] dplcs = new byte[398];
        for (int frame = 0; frame < 198; frame++) {
            word(mappings, frame * 2, 396);
            word(dplcs, frame * 2, 396);
        }
        CdSonicVisualAssets visuals = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
        return new CdPlayerMath(CdR11aPlayerAssets.decode(animations, new byte[60],
                new byte[]{1, 2, 3, 4, 5, (byte) 0xFF}, new byte[4], sine, atan, visuals));
    }

    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static CdR11aAssets loadUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "USA needs exactly one actual CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }

    /** Root's one existing ring-globals store, as a test-owned value; not another production owner. */
    private static final class RingStore implements CdRing.GlobalsPort {
        private final List<String> log;
        CdRingGlobals value = new CdRingGlobals(0, 3, 0, 0, 0, 0, 7, 3);
        RingStore(List<String> log) { this.log = log; }
        @Override public CdRingGlobals read() { log.add("ring.read"); return value; }
        @Override public void write(CdRingGlobals next) { log.add("ring.write"); value = Objects.requireNonNull(next); }
    }

    /** The new spill store. Default: a fresh level, whose .ClearFlowers span zeroes these bytes (Level/Main.asm:178-184). */
    private static final class SpillStore implements CdLostRing.SpillGlobalsPort {
        private final List<String> log;
        CdRingSpillGlobals value = new CdRingSpillGlobals(0, 0, 0);
        SpillStore(List<String> log) { this.log = log; }
        @Override public CdRingSpillGlobals read() { log.add("spill.read"); return value; }
        @Override public void write(CdRingSpillGlobals next) { log.add("spill.write"); value = Objects.requireNonNull(next); }
    }

    /** DrawObject entry stand-in over the live pool (BCLR #7 only); the shared queue is a separate owner. */
    private static final class RecordingDraw implements CdLostRing.DrawObjectCall {
        private final List<String> log;
        final List<CdObjectState> observed = new ArrayList<>();
        final List<CdObjectPool> pools = new ArrayList<>();
        final List<CdObjectState.Identity> slots = new ArrayList<>();
        Runnable onDraw = () -> { };
        RecordingDraw(List<String> log) { this.log = log; }

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            pools.add(pool);
            slots.add(slot);
            log.add("draw:" + slot.wholeIndex());
            pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
            onDraw.run();
        }
        List<Integer> wholes() { return slots.stream().map(CdObjectState.Identity::wholeIndex).toList(); }
    }

    /** Stand-in for Root's CdRing::collectRing binding: records its cut and makes one marker write and one request. */
    private static final class RecordingCollect implements CdLostRing.CollectRingCall {
        private final List<String> log;
        CdObjectPool pool;
        final List<CdObjectPool.Snapshot> atCall = new ArrayList<>();
        int calls;
        BiConsumer<CdRing.GlobalsPort, CdRing.SourceCalls> behaviour = (g, c) -> {
            var now = g.read();
            g.write(new CdRingGlobals(now.rings(), 0x5A, now.livesFlags(), now.updateHudRings(), now.updateHudLives(),
                    now.timeStopTimer(), now.ringAnimationTimer(), now.ringAnimationFrame()));
            c.playFmSound(CdRing.FmSound.FM_RING);
        };
        RecordingCollect(List<String> log) { this.log = log; }

        @Override public void collectRing(CdRing.GlobalsPort globals, CdRing.SourceCalls sourceCalls) {
            calls++;
            log.add("collect");
            atCall.add(pool.capture());
            behaviour.accept(globals, sourceCalls);
        }
    }

    /**
     * The TestCdMosqui terrain: layout cell (2,7) covers world X $200..$2FF, Y $700..$7FF with chunk 2, whose block
     * row 8 is a full-height solid floor (top at world Y $780) across every column, with the given angle byte.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Terrain(int floorAngle) {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2 + 7 * 42 + 2] = 2;
            for (int column = 0; column < 16; column++) {
                int at = 512 + (8 * 16 + column) * 2;
                chunks[at] = 0x20; chunks[at + 1] = 0x01;
            }
            ids[1] = 7; angles[7] = (byte) floorAngle;
            Arrays.fill(heights, 7 * 16, 7 * 16 + 16, (byte) 16);
        }
        CdPlayerTerrain probes() {
            return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths));
        }
    }

    private record Visit(int wholeIndex, List<CdLostRing.Effect> effects) { }

    private static final class Harness {
        final List<String> log = new ArrayList<>();
        final RingStore ring = new RingStore(log);
        final SpillStore spill = new SpillStore(log);
        final RecordingDraw draw = new RecordingDraw(log);
        final RecordingCollect collect = new RecordingCollect(log);
        final CdObjectPlacementRuntime runtime;
        final CdLostRing body;
        int zone = 0;
        int bottom = BOTTOM;

        Harness() { this(0x22); }
        Harness(int floorAngle) {
            this(TestCdRing.runtime(), assets(), sourceSineMath(), new Terrain(floorAngle).probes());
        }
        Harness(CdObjectPlacementRuntime runtime, CdLostRing.Assets assets, CdPlayerMath math, CdPlayerTerrain terrain) {
            this.runtime = runtime;
            collect.pool = runtime.pool();
            body = new CdLostRing(runtime, assets, math, terrain, draw, collect);
        }

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }

        CdLostRing.Result run(int whole, int levelVIntCounter) {
            return body.run(whole, new CdLostRing.Inputs(levelVIntCounter, 127 - whole, zone, bottom),
                    new CdLostRing.Ports(ring, spill));
        }
        LinkedHashMap<Integer, CdLostRing.Result> pass(int levelVIntCounter) { return passFrom(32, levelVIntCounter); }
        /** Root's live walk stand-in: each ID is read at its own visit; only $11 is dispatched here. */
        LinkedHashMap<Integer, CdLostRing.Result> passFrom(int first, int levelVIntCounter) {
            var results = new LinkedHashMap<Integer, CdLostRing.Result>();
            for (int whole = first; whole < 128; whole++) {
                if (slot(whole).id() == CdLostRing.OBJECT_ID) { results.put(whole, run(whole, levelVIntCounter)); }
            }
            return results;
        }
        /** Root's single unpaused loop tail: .RingSpill after DrawObjects (Level/Main.asm:430,536-549). */
        void tail() { spill.value = spill.value.advanceTail(); }
        List<Visit> replay(int passes) {
            var visits = new ArrayList<Visit>();
            for (int i = 1; i <= passes; i++) {
                for (var entry : pass(0x100 + i).entrySet()) { visits.add(new Visit(entry.getKey(), entry.getValue().effects())); }
                tail();
            }
            return visits;
        }

        void spawner(int whole, int x, int y) { write(slot(whole).moveByte(ID, CdLostRing.OBJECT_ID).moveWord(X, x).moveWord(Y, y)); }
        void occupy(int... wholes) { for (int whole : wholes) { write(slot(whole).moveByte(ID, 0x3D)); } }
        void occupyAllExcept(int... free) {
            for (int whole = 32; whole < 128; whole++) {
                final int w = whole;
                if (Arrays.stream(free).noneMatch(f -> f == w)) { write(slot(whole).moveByte(ID, 0x3D)); }
            }
        }
        /** A ring as Init leaves it at routine 2, over this slot's other bytes. */
        CdObjectState ringAt(int whole, int x, int y, int xVelocity, int yVelocity) {
            var state = slot(whole).moveByte(ID, CdLostRing.OBJECT_ID).moveByte(ROUTINE, 2).moveByte(Y_RADIUS, 8)
                    .moveByte(X_RADIUS, 8).moveWord(X, x).moveWord(Y, y).moveLong(MAPPINGS, RING_MAP)
                    .moveWord(TILE, RING_TILE).moveByte(PRIORITY, 3).moveByte(SPRITE_FLAGS, 4)
                    .moveByte(COLLISION_TYPE, 0x47).moveByte(WIDTH, 8).moveWord(X_VELOCITY, xVelocity)
                    .moveWord(Y_VELOCITY, yVelocity);
            write(state);
            return state;
        }
        /** An ObjNull-style free slot: ID 0, the given routine and nonzero leftovers in every named field. */
        CdObjectState dirtyFree(int whole, int routine) {
            var state = slot(whole).moveByte(SPRITE_FLAGS, 0xFF).moveWord(TILE, 0x1234).moveLong(MAPPINGS, 0x00ABCDEF)
                    .moveWord(X, 0x1111).moveWord(X_FRACTION, 0xFFFF).moveWord(Y, 0x2222).moveWord(Y_FRACTION, 0x1357)
                    .moveWord(X_VELOCITY, 0x3333).moveWord(Y_VELOCITY, 0x4444).moveByte(Y_RADIUS, 0x55)
                    .moveByte(X_RADIUS, 0x66).moveByte(PRIORITY, 0x07).moveByte(WIDTH, 0x77).moveByte(MAP_FRAME, 0xE4)
                    .moveByte(ANIMATION_FRAME, 0xB2).moveByte(ANIMATION, 0x91).moveByte(PREVIOUS_ANIMATION, 0xA2)
                    .moveByte(ANIMATION_TIME, 0xD3).moveByte(COLLISION_TYPE, 0x88).moveByte(COLLISION_STATUS, 0xC7)
                    .moveByte(FLAGS, 0xF5).moveByte(SAVED_FLAGS_ID, 0x0B).moveByte(ROUTINE, routine)
                    .moveByte(ROUTINE_2, 0x81).moveByte(ANGLE, 0x5A).moveByte(SUBTYPE, 0x66).moveByte(SUBTYPE2, 0x76)
                    .moveLong(MOSQUI_X_DELTA, 0xDEADBEEF).moveLong(MOSQUI_ANIMATION_SCRIPT, 0x0BADF00D)
                    .moveWord(EXPLOSION_POINTS_FRAME, 0xBEEF).moveByte(ID, 0);
            write(state);
            return state;
        }
    }
}

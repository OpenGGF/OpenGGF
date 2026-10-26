package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdExplosion.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdExplosion.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * ObjExplosion (Level/_Objects/Explosion/Main.asm:7-106) at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * The literal fixtures are the reviewed USA Ani_Explosion/MapSpr_Explosion includes (Explosion/Data/Animations.asm,
 * Explosion/Data/Mappings.asm), hand-assembled here as bounded constructed bytes; no runtime disassembly read,
 * native comparison row, fixture coordinate or frame index drives the body.
 */
class TestCdExplosion {
    private static final int EXPLOSION_MAP = 0x23F088;
    /** Ani_Explosion: ordinary 3,0,5,6,3,4,FC and badnik 3,0,1,2,3,4,FC, each with its real trailing zero. */
    private static final byte[] EXPLOSION_SCRIPTS = bytes(
            0x00, 0x04, 0x00, 0x0C,
            0x03, 0x00, 0x05, 0x06, 0x03, 0x04, 0xFC, 0x00,
            0x03, 0x00, 0x01, 0x02, 0x03, 0x04, 0xFC, 0x00);
    /** MapSpr_Explosion: seven counted frames (2,3,4,4,4,3,4 pieces), alignment zeros after frames 0,2,3,4,6. */
    private static final byte[] EXPLOSION_MAPPINGS = bytes(
            0x00, 0x0E, 0x00, 0x1A, 0x00, 0x2A, 0x00, 0x40, 0x00, 0x56, 0x00, 0x6C, 0x00, 0x7C,
            0x02, 0xF8, 0x05, 0x00, 0x00, 0xF0, 0xF8, 0x05, 0x08, 0x00, 0x00, 0x00,
            0x03, 0xF0, 0x0D, 0x00, 0x04, 0xF0, 0x00, 0x05, 0x00, 0x0C, 0xF0, 0x00, 0x05, 0x08, 0x0C, 0x00,
            0x04, 0xF0, 0x05, 0x00, 0x10, 0xF0, 0xF0, 0x05, 0x00, 0x14, 0x00, 0x00, 0x05, 0x00, 0x18, 0xF0, 0x00, 0x05, 0x18, 0x10, 0x00, 0x00,
            0x04, 0xE8, 0x0A, 0x00, 0x1C, 0xE8, 0xE8, 0x0A, 0x08, 0x1C, 0x00, 0x00, 0x0A, 0x10, 0x1C, 0xE8, 0x00, 0x0A, 0x18, 0x1C, 0x00, 0x00,
            0x04, 0xE8, 0x0A, 0x00, 0x25, 0xE8, 0xE8, 0x0A, 0x08, 0x25, 0x00, 0x00, 0x0A, 0x10, 0x25, 0xE8, 0x00, 0x0A, 0x18, 0x25, 0x00, 0x00,
            0x03, 0xF0, 0x0D, 0x00, 0x2E, 0xF0, 0x00, 0x05, 0x00, 0x36, 0xF0, 0x00, 0x05, 0x08, 0x36, 0x00,
            0x04, 0xF0, 0x05, 0x00, 0x3A, 0xF0, 0xF0, 0x05, 0x00, 0x3E, 0x00, 0x00, 0x05, 0x00, 0x42, 0xF0, 0x00, 0x05, 0x18, 0x3A, 0x00, 0x00);

    @Test
    void badnikInitCreatesPointsInTheFirstFreeSlotRetainsEveryUnwrittenByteAndFallsIntoMain() {
        var h = new Harness();
        h.occupy(32, 33);
        // A free (ID 0) slot keeps leftover bytes, e.g. after ObjNull clears only oID.
        var freeButDirty = h.slot(33).moveWord(X_FRACTION, 0xABCD).moveWord(Y_FRACTION, 0x1234)
                .moveByte(PRIORITY, 6).moveByte(ROUTINE, 0x0E).moveByte(MAP_FRAME, 9);
        h.write(freeButDirty);
        var original = h.badnik(40, 1, 0x0004);

        var result = h.run(40);

        assertEquals(List.of(new CdExplosion.PointsCreated(33, 0x82), call(INIT), call(MAIN), call(DRAW_OBJECT)),
                result.effects());
        byte[] expected = original.copyBytes();
        expected[0x24] = 2;                  // ADDQ.B #2,oRoutine(a0)
        expected[0x01] = 0x05;               // ORI.B #4 kept bit 0; frame 0 copies oFlags bit 0; the port cleared bit 7
        expected[0x18] = 1;                  // MOVE.B #1,oPriority(a0)
        expected[0x02] = (byte) 0x86;        // MOVE.W #$8680,oTile(a0); oLayer 0 keeps the priority bit
        expected[0x03] = (byte) 0x80;
        expected[0x04] = 0x00;               // MOVE.L #MapSpr_Explosion,oMap(a0)
        expected[0x05] = 0x23;
        expected[0x06] = (byte) 0xF0;
        expected[0x07] = (byte) 0x88;
        expected[0x20] = 0;                  // MOVE.B #0,oColType(a0)
        expected[0x1C] = 1;                  // MOVE.W #$100,oAnim(a0); AnimateObject's reset then stores oPrevAnim 1
        expected[0x1D] = 1;
        expected[0x1B] = 1;                  // frame 0 published, script index 1
        expected[0x1E] = 3;                  // delay 3 reloaded
        expected[0x1A] = 0;
        assertArrayEquals(expected, result.state().copyBytes(), "oVar3E, flags, radii, velocities and Mosqui bytes stay");

        byte[] child = freeButDirty.copyBytes();
        child[0x00] = 0x1C;                  // MOVE.B #$1C,oID(a1)
        child[0x08] = 0x02;                  // MOVE.W oX(a0),oX(a1): the child's fraction word stays
        child[0x09] = 0x70;
        child[0x0C] = 0x07;                  // MOVE.W oY(a0),oY(a1)
        child[0x0D] = 0x60;
        child[0x28] = (byte) 0x82;           // MOVE.B d1,oSubtype(a1)
        assertArrayEquals(child, h.slot(33).copyBytes(), "no other child byte is written");

        assertEquals(1, h.draw.observed.size());
        var drawn = h.draw.observed.getFirst();
        assertEquals(40, drawn.identity().wholeIndex());
        assertEquals(0x85, drawn.spriteFlags(), "DrawObject saw the committed post-Main image before its bit-7 clear");
        assertEquals(0x8680, drawn.word(TILE));
    }

    @Test
    void firstFreeSearchTestsOnlyIdsAndMayChooseALaterSlot() {
        var h = new Harness();
        h.occupy(32, 40);
        h.badnik(40, 1, 0x0006);
        var result = h.run(40);
        assertEquals(new CdExplosion.PointsCreated(41, 0x83), result.effects().getFirst(),
                "the explosion's own nonzero ID is skipped like every other occupant");
        assertEquals(0x1C, h.slot(41).id());
        assertEquals(0x83, h.slot(41).subtype());
        for (int whole = 32; whole < 40; whole++) {
            assertEquals(0x10, h.slot(whole).id(), "occupant " + whole);
        }
    }

    @ParameterizedTest(name = "oVar3E {0} -> points subtype {1}")
    @CsvSource({"0x0000, 0x80", "0x0002, 0x81", "0x0004, 0x82", "0x0006, 0x83", "0x000A, 0x85",
            "0x0104, 0x82", "0x00FF, 0xFF", "0x0001, 0x80", "0xFF0A, 0x85"})
    void pointsSubtypeIsTheLowByteShiftedRightWithBitSevenSet(String word, String subtype) {
        var h = new Harness();
        h.badnik(40, 1, Integer.decode(word));
        var result = h.run(40);
        assertEquals(new CdExplosion.PointsCreated(32, Integer.decode(subtype)), result.effects().getFirst(),
                "MOVE.W oVar3E / LSR.B #1 / ORI.B #$80: the high byte never reaches the subtype");
        assertEquals((int) Integer.decode(subtype), h.slot(32).subtype());
        assertEquals((int) Integer.decode(word), h.slot(40).word(EXPLOSION_POINTS_FRAME), "the word is read, never written");
    }

    @Test
    void ordinaryExplosionOnLayerTwoClearsTheTilePriorityAndTheWordWriteClearsADirtyPreviousId() {
        var h = new Harness();
        h.write(h.badnik(40, 0, 0).moveByte(LAYER, 1).moveByte(PREVIOUS_ANIMATION, 0x33));
        var result = h.run(40);
        var s = result.state();
        assertEquals(0x0680, s.word(TILE), "ANDI.B #$7F,oTile(a0) clears only the high byte's priority bit");
        assertEquals(0, s.unsignedByte(ANIMATION));
        assertEquals(0, s.unsignedByte(PREVIOUS_ANIMATION), "MOVE.W #0,oAnim(a0) overwrote the dirty previous ID");
        assertEquals(0, s.unsignedByte(MAP_FRAME));
        assertEquals(1, s.unsignedByte(ANIMATION_FRAME));
        assertEquals(3, s.unsignedByte(ANIMATION_TIME));
        assertEquals(1, s.unsignedByte(LAYER), "oLayer is only read");
        assertEquals(new CdExplosion.PointsCreated(32, 0x80), result.effects().getFirst(),
                "a zero oRoutine2 makes points even for an ordinary explosion");
    }

    @Test
    void badnikScriptHoldsEachFrameFourCallsAdvancesOnCallTwentyOneAndBecomesAHiddenFlowerOnCallTwentyTwo() {
        var h = new Harness();
        h.badnik(40, 1, 4);
        int[] frames = {0, 1, 2, 3, 4};
        for (int call = 1; call <= 20; call++) {
            var s = h.run(40).state();
            assertEquals(2, s.routine(), "call " + call);
            assertEquals(frames[(call - 1) / 4], s.unsignedByte(MAP_FRAME), "call " + call);
            assertEquals(3 - (call - 1) % 4, s.unsignedByte(ANIMATION_TIME), "call " + call);
        }
        var advanced = h.run(40);                                              // call 21: $FC
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT)), advanced.effects());
        assertEquals(4, advanced.state().routine());
        assertEquals(4, advanced.state().unsignedByte(MAP_FRAME), "the command publishes no frame");
        assertEquals(3, advanced.state().unsignedByte(ANIMATION_TIME), "the delay reloads before the command byte");
        assertEquals(5, advanced.state().unsignedByte(ANIMATION_FRAME), "$FC does not advance the script index");
        assertEquals(21, h.draw.observed.size(), "Main drew on calls 1..21");

        var before = h.slot(40);
        var flower = h.run(40);                                                // call 22: Done
        assertEquals(List.of(new CdExplosion.BecameFlower(), call(DONE)), flower.effects());
        assertFalse(flower.deleted());
        assertEquals(21, h.draw.observed.size(), "Done returns without DrawObject");
        byte[] expected = before.copyBytes();
        expected[0x00] = 0x1F;                                                 // MOVE.B #$1F,oID(a0)
        expected[0x24] = 0;                                                    // MOVE.B #0,oRoutine(a0)
        assertArrayEquals(expected, flower.state().copyBytes(), "only oID and oRoutine change");
        assertThrows(IllegalArgumentException.class, () -> h.run(40), "ID $1F belongs to the flower body");
    }

    @Test
    void ordinaryScriptShowsFramesZeroFiveSixThreeFourBeforeItsRoutineAdvance() {
        var h = new Harness();
        h.badnik(40, 0, 0);
        int[] frames = {0, 5, 6, 3, 4};
        for (int call = 1; call <= 20; call++) {
            assertEquals(frames[(call - 1) / 4], h.run(40).state().unsignedByte(MAP_FRAME), "call " + call);
        }
        assertEquals(4, h.run(40).state().routine(), "call 21 processes $FC");
    }

    @Test
    void aNonzeroBadnikFlagSkipsPointsAndMakesDoneDeleteTheSlot() {
        var h = new Harness();
        h.write(h.badnik(40, 1, 4).moveByte(ROUTINE_2, 2));
        var first = h.run(40);
        assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT)), first.effects(), "no FindObjSlot call");
        assertEquals(0, h.slot(32).id());
        assertEquals(2, first.state().unsignedByte(ROUTINE_2), "AnimateObject's FC path never clears oRoutine2");
        for (int call = 2; call <= 21; call++) {
            h.run(40);
        }
        assertEquals(4, h.slot(40).routine());
        var gone = h.run(40);
        assertEquals(List.of(new CdExplosion.Deleted(), call(DONE)), gone.effects());
        assertTrue(gone.deleted());
        assertArrayEquals(new byte[64], gone.state().copyBytes(), "JMP DeleteObject clears all 64 bytes");
        assertEquals(21, h.draw.observed.size());
    }

    @Test
    void aFullPoolCreatesNoPointsAndWritesNoOtherSlot() {
        var h = new Harness();
        h.occupy(32, 128);
        h.badnik(40, 1, 4);
        var before = h.pool().capture().slots();
        var result = h.run(40);
        assertEquals(List.of(new CdExplosion.PointsSlotUnavailable(), call(INIT), call(MAIN), call(DRAW_OBJECT)),
                result.effects());
        var after = h.pool().capture().slots();
        for (int i = 0; i < CdObjectPool.DYNAMIC_COUNT; i++) {
            if (i != 40 - CdObjectPool.FIRST_WHOLE_INDEX) {
                assertEquals(before.get(i), after.get(i), "slot " + (i + CdObjectPool.FIRST_WHOLE_INDEX));
            }
        }
        assertEquals(2, result.state().routine(), "the explosion itself continues");
        assertEquals(0x8680, result.state().word(TILE));
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"1", "3", "6", "0xFE"})
    void routineOutsideTheThreeEntryIndexStopsBeforeAnyWrite(String routineHex) {
        var h = new Harness();
        h.write(h.badnik(40, 1, 4).moveByte(ROUTINE, Integer.decode(routineHex)));
        var before = h.slot(40);
        var failure = assertThrows(CdExplosion.UnsupportedExplosion.class, () -> h.run(40));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        assertEquals(before, h.slot(40));
        assertEquals(0, h.slot(32).id(), "no child allocation");
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void theResultReloadsThePortsBitSevenWriteAndTheBodyNeverClearsIt() {
        var h = new Harness();
        h.badnik(40, 1, 4);
        h.draw.mode = RecordingDraw.Mode.SET_BIT7;
        assertEquals(0x85, h.run(40).state().spriteFlags());
        h.draw.mode = RecordingDraw.Mode.UNTOUCHED;
        assertEquals(0x85, h.run(40).state().spriteFlags(), "only DrawObject owns bit 7");
    }

    @Test
    void wrongSlotIdAndInvalidInputsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.write(h.slot(40).moveByte(ID, 0x1C));
        var before = h.slot(40);
        assertThrows(IllegalArgumentException.class, () -> h.run(40));
        assertEquals(before, h.slot(40));
        assertThrows(IllegalArgumentException.class, () -> new CdExplosion.PointsCreated(31, 0x80));
        assertThrows(IllegalArgumentException.class, () -> new CdExplosion.PointsCreated(128, 0x80));
        assertThrows(IllegalArgumentException.class, () -> new CdExplosion.PointsCreated(32, 0x100));
        assertThrows(IllegalArgumentException.class, () -> new CdExplosion.PointsCreated(32, 0x7F),
                "ORI.B #$80 always sets bit 7");
        assertThrows(NullPointerException.class, () -> new CdExplosion.CallReturned(null));
        assertThrows(NullPointerException.class, () -> new CdExplosion.Assets(null, EXPLOSION_MAP));
        var oneScript = CdObjectVisualAssets.decode(EXPLOSION_MAPPINGS, 7, bytes(0x00, 0x02, 0x03, 0x00, 0xFC, 0x00), 1, 70);
        assertThrows(IllegalArgumentException.class, () -> new CdExplosion.Assets(oneScript, EXPLOSION_MAP));
        var unscripted = CdObjectVisualAssets.decodeStatic(EXPLOSION_MAPPINGS, 7, 70);
        assertThrows(IllegalArgumentException.class, () -> new CdExplosion.Assets(unscripted, EXPLOSION_MAP));
        assertThrows(NullPointerException.class, () -> new CdExplosion(null, assets(), new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdExplosion(h.runtime, null, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdExplosion(h.runtime, assets(), null));
        var a = assets();
        assertSame(a, new CdExplosion(h.runtime, a, new RecordingDraw()).assets());
    }

    @Test
    void storageAliasesMatchTheNativeEquates() {
        assertEquals(0x1C, ANIMATION_WORD.byteOffset(), "MOVE.W writes to oAnim");
        assertEquals(ANIMATION.byteOffset(), ANIMATION_WORD.byteOffset());
        assertEquals(PREVIOUS_ANIMATION.byteOffset(), ANIMATION_WORD.byteOffset() + 1, "the word's low byte is oPrevAnim");
        assertEquals(0x3E, EXPLOSION_POINTS_FRAME.byteOffset(), "oExplodePoints EQU oVar3E");
        assertEquals(0x25, ROUTINE_2.byteOffset(), "oExplodeBadnik EQU oRoutine2");
        assertEquals(SOLID_TYPE.byteOffset(), ROUTINE_2.byteOffset(), "oSolidType rs.b 0 aliases oRoutine2");
        assertEquals(0x29, LAYER.byteOffset(), "oLayer rs.b 0 aliases oSubtype2");
        assertEquals(SUBTYPE2.byteOffset(), LAYER.byteOffset());
        assertEquals(0x18, CdExplosion.OBJECT_ID);
        assertEquals(0x8680, CdExplosion.NATIVE_TILE_WORD);
        assertEquals(0x1C, CdExplosion.POINTS_OBJECT_ID);
        assertEquals(0x1F, CdExplosion.FLOWER_OBJECT_ID);
    }

    /** Opt-in supplied-media check through the production loaders; a missing property is an explicit skip. */
    @Test
    void authenticatedUsaAssetsBindTheExplosionAtItsNativeMappingAddress() throws Exception {
        var assets = loadUsa();
        var bound = CdExplosion.Assets.fromObjectAssets(assets.openingObjects());
        assertEquals(EXPLOSION_MAP, bound.mappingsAddress());
        assertArrayEquals(EXPLOSION_MAPPINGS, bound.explosion().mappings().copy(), "the fixture is the authenticated include");
        assertArrayEquals(EXPLOSION_SCRIPTS, bound.explosion().animationTable().copy());

        var runtime = CdObjectPlacementRuntime.fromAssets(assets);
        var draw = new RecordingDraw();
        var explosion = new CdExplosion(runtime, bound, draw);
        runtime.pool().write(runtime.pool().state(40).moveByte(ID, CdExplosion.OBJECT_ID).moveByte(SUBTYPE, 1)
                .moveWord(X, 0x270).moveWord(Y, 0x760).moveWord(EXPLOSION_POINTS_FRAME, 4));
        var result = explosion.run(40);
        assertEquals(EXPLOSION_MAP, result.state().longBits(MAPPINGS));
        assertEquals(0x8680, result.state().word(TILE));
        assertEquals(0, result.state().unsignedByte(MAP_FRAME));
        assertEquals(new CdExplosion.PointsCreated(32, 0x82), result.effects().getFirst());
        assertEquals(0x1C, runtime.pool().state(32).id());
    }

    private static CdExplosion.Effect call(CdExplosion.NativeCall nativeCall) { return new CdExplosion.CallReturned(nativeCall); }

    private static CdExplosion.Assets assets() {
        return new CdExplosion.Assets(CdObjectVisualAssets.decode(EXPLOSION_MAPPINGS, 7, EXPLOSION_SCRIPTS, 2, 70),
                EXPLOSION_MAP);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] r : records) {
            bytes.putShort((short) r[0]).putShort((short) r[1]).put((byte) r[2]).put((byte) r[3])
                    .put((byte) r[4]).put((byte) r[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
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

    /** Native DrawObject stand-in over the live pool; the shared queue is a separate owner. */
    private static final class RecordingDraw implements CdExplosion.DrawObjectCall {
        enum Mode { CLEAR_BIT7, SET_BIT7, UNTOUCHED }
        Mode mode = Mode.CLEAR_BIT7;
        final List<CdObjectState> observed = new ArrayList<>();

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            switch (mode) {
                case CLEAR_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
                case SET_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() | 0x80));
                case UNTOUCHED -> { }
            }
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(0x270, 0x730, 0xBD, 0x00, 0x40, 0)));
        final RecordingDraw draw = new RecordingDraw();
        final CdExplosion explosion = new CdExplosion(runtime, assets(), draw);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        CdExplosion.Result run(int whole) { return explosion.run(whole); }

        /** Live occupants for whole slots [from, to): a nonzero oID is all FindObjSlot tests. */
        void occupy(int from, int to) {
            for (int whole = from; whole < to; whole++) {
                write(slot(whole).moveByte(ID, 0x10).moveByte(ROUTINE, 2));
            }
        }

        /**
         * A Mosqui slot after Player_TouchEnemy .KillEnemy (Player Object Collision.asm:155-178): the patrol
         * body's own bytes plus oFlags bit 7, the oVar3E word, oID $18, oRoutine 0 and oSubtype. KillEnemy leaves
         * oColType $2B and every other byte; the explosion's Init is the next writer.
         */
        CdObjectState badnik(int whole, int subtype, int pointsWord) {
            var state = slot(whole).moveByte(ID, CdExplosion.OBJECT_ID).moveByte(SPRITE_FLAGS, 0x85)
                    .moveWord(TILE, 0x23A0).moveLong(MAPPINGS, 0x20B6E6)
                    .moveLong(X_POSITION, 0x02708000).moveLong(Y_POSITION, 0x07604321)
                    .moveWord(X_VELOCITY, 0x0123).moveWord(Y_VELOCITY, 0xFF00)
                    .moveByte(Y_RADIUS, 0x10).moveByte(X_RADIUS, 0x10).moveByte(PRIORITY, 3).moveByte(WIDTH, 0x10)
                    .moveByte(MAP_FRAME, 4).moveByte(ANIMATION_FRAME, 2).moveByte(ANIMATION, 1)
                    .moveByte(PREVIOUS_ANIMATION, 1).moveByte(ANIMATION_TIME, 5).moveByte(COLLISION_TYPE, 0x2B)
                    .moveByte(FLAGS, 0x81).moveByte(SAVED_FLAGS_ID, 10).moveByte(ROUTINE, 0).moveByte(SUBTYPE, subtype)
                    .moveWord(MOSQUI_ANCHOR_X, 0x270).moveLong(MOSQUI_X_DELTA, -0x10000)
                    .moveLong(MOSQUI_ANIMATION_SCRIPT, 0x20B6CA).moveWord(EXPLOSION_POINTS_FRAME, pointsWord);
            write(state);
            return state;
        }
    }
}

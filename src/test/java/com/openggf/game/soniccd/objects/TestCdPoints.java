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

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdPoints.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdPoints.UnsupportedBoundary.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * ObjHUDPoints/ObjPoints (Level/_Objects/HUD and Points/Main.asm:11-69) at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. The points object is unscripted: Init selects oMapFrame directly.
 * The literal fixture is the reviewed USA MapSpr_Points include (HUD and Points/Data/Mappings (Points).asm),
 * hand-assembled here as bounded constructed bytes; no runtime disassembly read or fixture coordinate drives the body.
 */
class TestCdPoints {
    private static final int POINTS_MAP = 0x20AC64;
    /** MapSpr_Points: six counted frames (2,2,2,2,1,3 pieces), alignment zeros after frames 0..3, max tile 4. */
    private static final byte[] POINTS_MAPPINGS = bytes(
            0x00, 0x0C, 0x00, 0x18, 0x00, 0x24, 0x00, 0x30, 0x00, 0x3C, 0x00, 0x42,
            0x02, 0x00, 0x00, 0x00, 0x00, 0xF8, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00, 0x01, 0xF8, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00, 0x02, 0xF8, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00, 0x04, 0xF8, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x04, 0xFC,
            0x03, 0x00, 0x00, 0x00, 0x00, 0xF4, 0x00, 0x00, 0x00, 0x03, 0xFC, 0x00, 0x00, 0x00, 0x03, 0x04);

    @Test
    void initWritesTileMappingsFrameAndTimerThenFallsIntoTheFirstRise() {
        var h = new Harness();
        // MakePoints wrote only oID, oX, oY and oSubtype; every other byte is whatever the free slot held.
        h.write(h.slot(33).moveWord(Y_FRACTION, 0x1234).moveByte(PRIORITY, 5).moveByte(WIDTH, 0x33)
                .moveByte(SPRITE_FLAGS, 0x81).moveByte(Y_RADIUS, 0x0C).moveByte(COLLISION_TYPE, 0x47));
        var original = h.place(33, 0x270, 0x760, 0x82);

        var result = h.run(33);

        assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT)), result.effects());
        byte[] expected = original.copyBytes();
        expected[0x24] = 2;                  // ADDQ.B #2,oRoutine(a0)
        expected[0x01] = 0x05;               // ORI.B #4 keeps flip bit 0; the port cleared bit 7
        expected[0x02] = 0x06;               // MOVE.W #$6C6,oTile(a0)
        expected[0x03] = (byte) 0xC6;
        expected[0x04] = 0x00;               // MOVE.L #MapSpr_Points,oMap(a0)
        expected[0x05] = 0x20;
        expected[0x06] = (byte) 0xAC;
        expected[0x07] = 0x64;
        expected[0x1A] = 2;                  // MOVE.B oSubtype,oMapFrame / ANDI.B #$7F,oMapFrame
        expected[0x2A] = 23;                 // MOVE.B #24,oPntsTimer, then Main's SUBQ.B #1
        expected[0x0C] = 0x07;               // .Rise: SUBQ.W #2,oY(a0) on the integer word only
        expected[0x0D] = 0x5E;
        assertArrayEquals(expected, result.state().copyBytes(), "oPriority, oWidth, radius and fraction are never written");
        assertEquals(1, h.draw.observed.size());
        assertEquals(0x85, h.draw.observed.getFirst().spriteFlags(), "DrawObject sees the committed pre-clear image");
        assertFalse(result.deleted());
    }

    @Test
    void twentyFourCallsRiseTwentyThreeTimesThenDeleteAndStillDrawTheZeroedSlot() {
        var h = new Harness();
        h.place(33, 0x270, 0x760, 0x85);
        for (int call = 1; call <= 23; call++) {
            var r = h.run(33);
            assertEquals(0x760 - 2 * call, r.state().centreY(), "call " + call);
            assertEquals(24 - call, r.state().unsignedByte(POINTS_TIMER), "call " + call);
            assertEquals(5, r.state().unsignedByte(MAP_FRAME), "call " + call);
            assertFalse(r.deleted(), "call " + call);
        }
        var gone = h.run(33);                                                  // call 24
        assertTrue(gone.deleted());
        assertEquals(List.of(new CdPoints.Deleted(), call(MAIN), call(DRAW_OBJECT)), gone.effects());
        assertArrayEquals(new byte[64], gone.state().copyBytes());
        assertEquals(24, h.draw.observed.size(), "JMP DrawObject follows every routine, including the delete");
        var last = h.draw.observed.getLast();
        assertEquals(33, last.identity().wholeIndex());
        assertArrayEquals(new byte[64], last.copyBytes(), "the port received the zeroed slot; it must not test oID");
        assertThrows(IllegalArgumentException.class, () -> h.run(33), "ID 0 is no longer this handler's");
    }

    @ParameterizedTest(name = "subtype {0}")
    @CsvSource({"0x00", "0x01", "0x7F"})
    void aSubtypeWithoutBitSevenIsTheHudOwnerBoundaryBeforeAnyWrite(String subtype) {
        var h = new Harness();
        h.place(33, 0x270, 0x760, Integer.decode(subtype));
        var before = h.slot(33);
        var failure = assertThrows(CdPoints.UnsupportedPoints.class, () -> h.run(33));
        assertEquals(HUD_OWNER, failure.boundary());
        assertEquals(33, failure.wholeIndex());
        assertEquals(before, h.slot(33));
        assertTrue(h.draw.observed.isEmpty());
    }

    @ParameterizedTest(name = "subtype {0} -> oMapFrame {1}")
    @CsvSource({"0x80, 0", "0x81, 1", "0x83, 3", "0x85, 5", "0xFF, 0x7F"})
    void initKeepsTheSubtypesLowSevenBitsAsTheMappingFrame(String subtype, String frame) {
        var h = new Harness();
        h.place(33, 0x270, 0x760, Integer.decode(subtype));
        var s = h.run(33).state();
        assertEquals((int) Integer.decode(frame), s.unsignedByte(MAP_FRAME),
                "native writes the byte; the frame bound belongs to the DrawObjects mapping owner");
        assertEquals((int) Integer.decode(subtype), s.subtype(), "oSubtype is only read");
    }

    @Test
    void routineTwoDecrementsTheExistingTimerByteIncludingAWrapFromZero() {
        var h = new Harness();
        h.write(h.place(33, 0x270, 0x760, 0x80).moveByte(ROUTINE, 2).moveByte(POINTS_TIMER, 0));
        var r = h.run(33);
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT)), r.effects());
        assertEquals(0xFF, r.state().unsignedByte(POINTS_TIMER), "SUBQ.B #1 wraps and BNE rises");
        assertEquals(0x75E, r.state().centreY());
        assertEquals(0, r.state().word(TILE), "routine 2 performs no Init write");
        assertEquals(0, r.state().longBits(MAPPINGS));

        h.write(h.slot(33).moveByte(POINTS_TIMER, 1));
        assertTrue(h.run(33).deleted(), "a timer of one deletes on this call");
    }

    @Test
    void theRiseIsAWordSubtractionThatWrapsAndKeepsTheFraction() {
        var h = new Harness();
        h.write(h.place(33, 0x270, 0x0001, 0x80).moveWord(Y_FRACTION, 0x5555));
        var s = h.run(33).state();
        assertEquals(0xFFFF, s.centreY(), "SUBQ.W #2 wraps the integer word");
        assertEquals(0x5555, s.word(Y_FRACTION));
        assertEquals(0x270, s.centreX(), "Points never moves horizontally");
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"1", "4", "0xFE"})
    void routineOutsideTheTwoEntryIndexStopsBeforeAnyWrite(String routineHex) {
        var h = new Harness();
        h.write(h.place(33, 0x270, 0x760, 0x82).moveByte(ROUTINE, Integer.decode(routineHex)));
        var before = h.slot(33);
        var failure = assertThrows(CdPoints.UnsupportedPoints.class, () -> h.run(33));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(33, failure.wholeIndex());
        assertEquals(before, h.slot(33));
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void wrongSlotIdAndInvalidInputsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.write(h.slot(33).moveByte(ID, 0x18).moveByte(SUBTYPE, 0x82));
        var before = h.slot(33);
        assertThrows(IllegalArgumentException.class, () -> h.run(33));
        assertEquals(before, h.slot(33));
        assertThrows(NullPointerException.class, () -> new CdPoints.CallReturned(null));
        assertThrows(NullPointerException.class, () -> new CdPoints.Assets(null, POINTS_MAP));
        var fiveFrames = CdObjectVisualAssets.decodeStatic(bytes(
                0x00, 0x0A, 0x00, 0x10, 0x00, 0x16, 0x00, 0x1C, 0x00, 0x22,
                0x01, 0x00, 0x00, 0x00, 0x00, 0xFC, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFC,
                0x01, 0x00, 0x00, 0x00, 0x00, 0xFC, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFC,
                0x01, 0x00, 0x00, 0x00, 0x00, 0xFC), 5, 5);
        assertThrows(IllegalArgumentException.class, () -> new CdPoints.Assets(fiveFrames, POINTS_MAP),
                "MapSpr_Points declares six frames");
        var scripted = CdObjectVisualAssets.decode(POINTS_MAPPINGS, 6, bytes(0x00, 0x02, 0x03, 0x00, 0xFF), 1, 5);
        assertThrows(IllegalArgumentException.class, () -> new CdPoints.Assets(scripted, POINTS_MAP),
                "ObjPoints has no animation script");
        assertThrows(NullPointerException.class, () -> new CdPoints(null, assets(), new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdPoints(h.runtime, null, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdPoints(h.runtime, assets(), null));
        var a = assets();
        assertSame(a, new CdPoints(h.runtime, a, new RecordingDraw()).assets());
    }

    @Test
    void storageAliasesMatchTheNativeEquates() {
        assertEquals(0x2A, POINTS_TIMER.byteOffset(), "oPntsTimer EQU oVar2A");
        assertEquals(MOSQUI_ANCHOR_X.byteOffset(), POINTS_TIMER.byteOffset(), "same oVar2A storage, different owner");
        assertEquals(0x1C, CdPoints.OBJECT_ID);
        assertEquals(0x06C6, CdPoints.NATIVE_TILE_WORD);
        assertEquals(24, CdPoints.NATIVE_LIFETIME);
    }

    /** Opt-in supplied-media check through the production loaders; a missing property is an explicit skip. */
    @Test
    void authenticatedUsaAssetsBindThePointsMappingsAtTheirNativeAddress() throws Exception {
        var assets = loadUsa();
        var bound = CdPoints.Assets.fromObjectAssets(assets.openingObjects());
        assertEquals(POINTS_MAP, bound.mappingsAddress());
        assertArrayEquals(POINTS_MAPPINGS, bound.points().mappings().copy(), "the fixture is the authenticated include");
        assertTrue(bound.points().animations().isEmpty());

        var runtime = CdObjectPlacementRuntime.fromAssets(assets);
        var points = new CdPoints(runtime, bound, new RecordingDraw());
        runtime.pool().write(runtime.pool().state(32).moveByte(ID, CdPoints.OBJECT_ID).moveByte(SUBTYPE, 0x83)
                .moveWord(X, 0x270).moveWord(Y, 0x760));
        var s = points.run(32).state();
        assertEquals(POINTS_MAP, s.longBits(MAPPINGS));
        assertEquals(0x06C6, s.word(TILE));
        assertEquals(3, s.unsignedByte(MAP_FRAME));
        assertEquals(0x75E, s.centreY());
    }

    private static CdPoints.Effect call(CdPoints.NativeCall nativeCall) { return new CdPoints.CallReturned(nativeCall); }

    private static CdPoints.Assets assets() {
        return new CdPoints.Assets(CdObjectVisualAssets.decodeStatic(POINTS_MAPPINGS, 6, 5), POINTS_MAP);
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
    private static final class RecordingDraw implements CdPoints.DrawObjectCall {
        final List<CdObjectState> observed = new ArrayList<>();

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());   // no ID test: a just-deleted slot is still drawn
            observed.add(live);
            pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(0x270, 0x730, 0xBD, 0x00, 0x40, 0)));
        final RecordingDraw draw = new RecordingDraw();
        final CdPoints points = new CdPoints(runtime, assets(), draw);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        CdPoints.Result run(int whole) { return points.run(whole); }

        /** ObjExplosion_MakePoints' four child writes over whatever the free slot held (Explosion/Main.asm:33-36). */
        CdObjectState place(int whole, int x, int y, int subtype) {
            var state = slot(whole).moveByte(ID, CdPoints.OBJECT_ID).moveWord(X, x).moveWord(Y, y).moveByte(SUBTYPE, subtype);
            write(state);
            return state;
        }
    }
}

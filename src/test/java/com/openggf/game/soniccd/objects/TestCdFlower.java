package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdFlower.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdFlower.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * ObjFlower (Level/_Objects/Flower/Main.asm:7-162) at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * The literal fixtures are the reviewed USA Ani_Flower/MapSpr_Flower includes (Palmtree Panic/Objects/Flower/Data),
 * hand-assembled here as bounded constructed bytes. Floor probes use the same constructed terrain as TestCdMosqui
 * (solid floor top at world Y $780, angle $22). No runtime disassembly read or fixture coordinate drives the body.
 */
class TestCdFlower {
    private static final int FLOWER_MAP = 0x235EF4;
    /** Ani_Flower: seed 3,0,1,FF; planted 3,2,3,2,3,FC; growing 1,5,5,4,6,FC; grown $13,6,7,FF. */
    private static final byte[] FLOWER_SCRIPTS = bytes(
            0x00, 0x08, 0x00, 0x0C, 0x00, 0x12, 0x00, 0x18,
            0x03, 0x00, 0x01, 0xFF,
            0x03, 0x02, 0x03, 0x02, 0x03, 0xFC,
            0x01, 0x05, 0x05, 0x04, 0x06, 0xFC,
            0x13, 0x06, 0x07, 0xFF);
    /** MapSpr_Flower: eight counted frames (1,1,1,1,2,1,3,3 pieces), one alignment zero after frame 4, max tile $26. */
    private static final byte[] FLOWER_MAPPINGS = bytes(
            0x00, 0x10, 0x00, 0x16, 0x00, 0x1C, 0x00, 0x22, 0x00, 0x28, 0x00, 0x34, 0x00, 0x3A, 0x00, 0x4A,
            0x01, 0xF0, 0x01, 0x00, 0x00, 0xFC,
            0x01, 0xF0, 0x01, 0x08, 0x00, 0xFC,
            0x01, 0xF0, 0x05, 0x00, 0x02, 0xF8,
            0x01, 0xF0, 0x05, 0x00, 0x06, 0xF8,
            0x02, 0xE8, 0x09, 0x00, 0x1C, 0xF4, 0xF8, 0x00, 0x00, 0x22, 0xFC, 0x00,
            0x01, 0xF0, 0x05, 0x00, 0x23, 0xF8,
            0x03, 0xD0, 0x0A, 0x00, 0x0A, 0xF4, 0xE8, 0x09, 0x00, 0x1C, 0xF4, 0xF8, 0x00, 0x00, 0x22, 0xFC,
            0x03, 0xD0, 0x0A, 0x00, 0x13, 0xF4, 0xE8, 0x09, 0x00, 0x1C, 0xF4, 0xF8, 0x00, 0x00, 0x22, 0xFC);

    @Test
    void badnikFlowerInitOverwritesItsInitTileWithTheSeedTileAndFallsIntoTheSeed() {
        var h = new Harness();
        var original = h.converted(40, 0x270, 0x770, 1, 0);

        var result = h.run(40);

        assertEquals(List.of(call(INIT), angle(0x22), call(SEED), call(DRAW_OBJECT)), result.effects());
        byte[] expected = original.copyBytes();
        expected[0x01] = 0x05;               // ORI.B #4; frame 0 copies oFlags bit 0; the port cleared bit 7
        expected[0x18] = 1;                  // MOVE.B #1,oPriority(a0)
        expected[0x16] = 0;                  // MOVE.B #0,oYRadius(a0)
        expected[0x02] = 0x06;               // MOVE.W #$A6D7, then .NoFlags MOVE.W #$6D7,oTile(a0)
        expected[0x03] = (byte) 0xD7;
        expected[0x04] = 0x00;               // MOVE.L #MapSpr_Flower,oMap(a0)
        expected[0x05] = 0x23;
        expected[0x06] = 0x5E;
        expected[0x07] = (byte) 0xF4;
        expected[0x24] = 2;                  // MOVE.B #2,oRoutine(a0)
        expected[0x1C] = 0;                  // MOVE.W #2,oAnim(a0): oAnim 0, oPrevAnim 2, then AnimateObject's reset
        expected[0x1D] = 0;
        expected[0x1A] = 0;                  // seed frame 0, script index 1, delay 3
        expected[0x1B] = 1;
        expected[0x1E] = 3;
        expected[0x0C] = 0x07;               // probe $770 is 15 above the floor: .Fall ADDQ.W #2 on the integer word
        expected[0x0D] = 0x72;
        assertArrayEquals(expected, result.state().copyBytes(), "the Y fraction and every explosion leftover stay");
        assertEquals(1, h.draw.observed.size());
        assertEquals(0x85, h.draw.observed.getFirst().spriteFlags(), "DrawObject sees the committed pre-clear image");
    }

    @Test
    void seedFallsTwoPixelsKeepingTheFractionLandsOnePixelAboveTheFloorThenGrowsAndLoopsForever() {
        var h = new Harness();
        h.converted(40, 0x270, 0x770, 1, 0);
        int[] frames = {
                0, 0, 0, 0, 1, 1, 1, 1,                                // calls 1..8: seed script, falling
                2, 2, 2, 2, 3, 3, 3, 3, 2, 2, 2, 2, 3, 3, 3, 3, 3,     // calls 9..25: planted script, $FC on 25
                5, 5, 5, 5, 4, 4, 6, 6, 6};                            // calls 26..34: growing script, $FC on 34
        for (int n = 1; n <= 75; n++) {
            var r = h.run(40);
            var s = r.state();
            int frame = n <= frames.length ? frames[n - 1] : ((n - 35) / 20) % 2 == 0 ? 6 : 7;
            int routine = n <= 8 ? 2 : n <= 24 ? 4 : n <= 33 ? 6 : n == 34 ? 8 : 4;
            List<CdFlower.Effect> effects;
            if (n == 1) { effects = List.of(call(INIT), angle(0x22), call(SEED), call(DRAW_OBJECT)); }
            else if (n <= 9) { effects = List.of(angle(0x22), call(SEED), call(DRAW_OBJECT)); }
            else if (n <= 25) { effects = List.of(call(ANIMATE), call(DRAW_OBJECT)); }
            else if (n <= 34) { effects = List.of(call(GROWING), call(DRAW_OBJECT)); }
            else if (n == 35) { effects = List.of(call(DONE), call(DRAW_OBJECT)); }
            else { effects = List.of(call(ANIMATE), call(DRAW_OBJECT)); }
            assertEquals(effects, r.effects(), "call " + n);
            assertEquals(frame, s.unsignedByte(MAP_FRAME), "call " + n);
            assertEquals(routine, s.routine(), "call " + n);
            assertEquals(n <= 8 ? 0x770 + 2 * n : 0x77F, s.centreY(), "call " + n + ": lands at floor top - 1");
            assertEquals(0x4321, s.word(Y_FRACTION), "call " + n + ": word writes keep the fraction");
            assertEquals(n <= 8 ? 0 : 0x30, s.unsignedByte(Y_RADIUS), "call " + n);
            assertEquals(n <= 25 ? 0x06D7 : 0x26D7, s.word(TILE), "call " + n);
            assertEquals(0x270, s.centreX(), "call " + n);
        }
        for (int n = 76; n <= 375; n++) {
            var s = h.run(40).state();
            assertEquals(CdFlower.OBJECT_ID, s.id(), "call " + n + ": no despawn or delete path exists");
            assertEquals(4, s.routine(), "call " + n);
        }
        assertEquals(375, h.draw.observed.size(), "JMP DrawObject follows every routine");
    }

    @Test
    void aSeedAboveTheProbedBlocksStillReportsTheZeroedAngleByte() {
        var h = new Harness();
        h.converted(40, 0x270, 0x760, 1, 0);
        var result = h.run(40);
        assertEquals(List.of(call(INIT), angle(0), call(SEED), call(DRAW_OBJECT)), result.effects(),
                "ObjGetFloorDist zeroes primaryAngle and finds no block to write it");
        assertEquals(0x762, result.state().centreY());
        assertEquals(2, result.state().routine());
    }

    @ParameterizedTest(name = "oSubtype2 {0} -> committed tile {1}")
    @CsvSource({"0, 0xA6D7", "1, 0x26D7"})
    void subtypeZeroStopsAtTheSavedFlagsReadAfterCommittingTheNativeInitWrites(int subtype2, String tile) {
        var h = new Harness();
        var original = h.converted(40, 0x270, 0x770, 0, subtype2);
        var failure = assertThrows(CdFlower.UnsupportedFlower.class, () -> h.run(40));
        assertEquals(SAVED_FLAGS_INIT, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        int tileWord = Integer.decode(tile);
        byte[] expected = original.copyBytes();
        expected[0x01] = (byte) (original.spriteFlags() | 4);
        expected[0x18] = 1;
        expected[0x16] = 0;
        expected[0x02] = (byte) (tileWord >>> 8);  // ANDI.B #$7F,oTile(a0) only when oFlowerLoPrio is set
        expected[0x03] = (byte) tileWord;
        expected[0x04] = 0x00;
        expected[0x05] = 0x23;
        expected[0x06] = 0x5E;
        expected[0x07] = (byte) 0xF4;
        assertArrayEquals(expected, h.slot(40).copyBytes(), "native writes before BSR ObjFlower_GetSavedFlags are committed");
        assertEquals(0, h.slot(40).routine(), "nothing after the saved-flags read is invented");
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void aSubtypeZeroLandingStopsAtTheFlowerCountRecordAfterCommittingTheFloorAdjustment() {
        var h = new Harness();
        h.write(h.converted(40, 0x270, 0x780, 0, 0).moveByte(ROUTINE, 2).moveByte(Y_RADIUS, 0));
        var before = h.slot(40);
        var failure = assertThrows(CdFlower.UnsupportedFlower.class, () -> h.run(40));
        assertEquals(SAVED_FLAGS_LANDING, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        byte[] expected = before.copyBytes();
        expected[0x0C] = 0x07;                     // probe $780 is -1: ADD.W d1,oY(a0) precedes the subtype test
        expected[0x0D] = 0x7F;
        assertArrayEquals(expected, h.slot(40).copyBytes());
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void growingAndDoneRewriteTheirTileAnimationAndRoutineOnEveryEntry() {
        var h = new Harness();
        h.write(h.converted(40, 0x270, 0x77F, 1, 0).moveByte(ROUTINE, 6).moveWord(TILE, 0x1234)
                .moveByte(ANIMATION, 2).moveByte(PREVIOUS_ANIMATION, 2).moveByte(ANIMATION_TIME, 1)
                .moveByte(ANIMATION_FRAME, 1));
        var growing = h.run(40);
        assertEquals(List.of(call(GROWING), call(DRAW_OBJECT)), growing.effects());
        assertEquals(0x26D7, growing.state().word(TILE), "MOVE.W #$26D7,oTile(a0) on every Growing call");
        assertEquals(0, growing.state().unsignedByte(ANIMATION_TIME), "same animation ID: SUBQ.B only");
        assertEquals(6, growing.state().routine());

        h.write(h.slot(40).moveByte(ROUTINE, 8).moveByte(ANIMATION, 0x7F));
        var done = h.run(40);
        assertEquals(List.of(call(DONE), call(DRAW_OBJECT)), done.effects());
        assertEquals(4, done.state().routine());
        assertEquals(3, done.state().unsignedByte(ANIMATION));
        assertEquals(3, done.state().unsignedByte(PREVIOUS_ANIMATION), "AnimateObject reset from the previous ID 2");
        assertEquals(6, done.state().unsignedByte(MAP_FRAME));
        assertEquals(0x13, done.state().unsignedByte(ANIMATION_TIME));
        assertEquals(0x26D7, done.state().word(TILE), "Done keeps the grown tile");
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"1", "3", "0x0A", "0xFE"})
    void routineOutsideTheFiveEntryIndexStopsBeforeAnyWrite(String routineHex) {
        var h = new Harness();
        h.write(h.converted(40, 0x270, 0x770, 1, 0).moveByte(ROUTINE, Integer.decode(routineHex)));
        var before = h.slot(40);
        var failure = assertThrows(CdFlower.UnsupportedFlower.class, () -> h.run(40));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        assertEquals(before, h.slot(40));
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void wrongSlotIdAndInvalidInputsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.write(h.slot(40).moveByte(ID, 0x18).moveByte(SUBTYPE, 1));
        var before = h.slot(40);
        assertThrows(IllegalArgumentException.class, () -> h.run(40));
        assertEquals(before, h.slot(40));
        assertThrows(IllegalArgumentException.class,
                () -> new CdFlower.GlobalWrite(CdFlower.GlobalField.PRIMARY_ANGLE, 0x100));
        assertThrows(NullPointerException.class, () -> new CdFlower.GlobalWrite(null, 0));
        assertThrows(NullPointerException.class, () -> new CdFlower.CallReturned(null));
        assertThrows(NullPointerException.class, () -> new CdFlower.Assets(null, FLOWER_MAP));
        var oneScript = CdObjectVisualAssets.decode(FLOWER_MAPPINGS, 8, bytes(0x00, 0x02, 0x03, 0x00, 0xFF), 1, 39);
        assertThrows(IllegalArgumentException.class, () -> new CdFlower.Assets(oneScript, FLOWER_MAP));
        var unscripted = CdObjectVisualAssets.decodeStatic(FLOWER_MAPPINGS, 8, 39);
        assertThrows(IllegalArgumentException.class, () -> new CdFlower.Assets(unscripted, FLOWER_MAP));
        var probes = h.terrain.probes();
        assertThrows(NullPointerException.class, () -> new CdFlower(null, assets(), probes, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdFlower(h.runtime, null, probes, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdFlower(h.runtime, assets(), null, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new CdFlower(h.runtime, assets(), probes, null));
        var a = assets();
        assertSame(a, new CdFlower(h.runtime, a, probes, new RecordingDraw()).assets());
    }

    @Test
    void storageAliasesMatchTheNativeEquates() {
        assertEquals(0x29, SUBTYPE2.byteOffset(), "oFlowerLoPrio EQU oSubtype2");
        assertEquals(LAYER.byteOffset(), SUBTYPE2.byteOffset());
        assertEquals(0x1C, ANIMATION_WORD.byteOffset(), "MOVE.W #2,oAnim(a0) covers oAnim and oPrevAnim");
        assertEquals(0x1F, CdFlower.OBJECT_ID);
        assertEquals(0xA6D7, CdFlower.INIT_TILE_WORD);
        assertEquals(0x06D7, CdFlower.SEED_TILE_WORD);
        assertEquals(0x26D7, CdFlower.GROWING_TILE_WORD);
    }

    /** Opt-in supplied-media check through the production loaders; a missing property is an explicit skip. */
    @Test
    void authenticatedUsaAssetsBindTheFlowerTablesAtTheirNativeAddress() throws Exception {
        var assets = loadUsa();
        var bound = CdFlower.Assets.fromObjectAssets(assets.openingObjects());
        assertEquals(FLOWER_MAP, bound.mappingsAddress());
        assertArrayEquals(FLOWER_MAPPINGS, bound.flower().mappings().copy(), "the fixture is the authenticated include");
        assertArrayEquals(FLOWER_SCRIPTS, bound.flower().animationTable().copy());

        var runtime = CdObjectPlacementRuntime.fromAssets(assets);
        var flower = new CdFlower(runtime, bound, new CdPlayerTerrain(assets.terrain()), new RecordingDraw());
        runtime.pool().write(runtime.pool().state(40).moveByte(ID, CdFlower.OBJECT_ID).moveByte(SUBTYPE, 1)
                .moveWord(X, 0x270).moveWord(Y, 0x730));
        var result = flower.run(40);
        var s = result.state();
        assertEquals(call(INIT), result.effects().getFirst());
        assertInstanceOf(CdFlower.GlobalWrite.class, result.effects().get(1), "the seed probe reports primaryAngle");
        assertEquals(FLOWER_MAP, s.longBits(MAPPINGS));
        assertEquals(0x06D7, s.word(TILE));
        assertEquals(1, s.unsignedByte(PRIORITY));
        assertTrue(s.routine() == 2 || s.routine() == 4, "falling or planted, depending only on the actual floor");
    }

    private static CdFlower.Effect call(CdFlower.NativeCall nativeCall) { return new CdFlower.CallReturned(nativeCall); }
    private static CdFlower.Effect angle(int value) {
        return new CdFlower.GlobalWrite(CdFlower.GlobalField.PRIMARY_ANGLE, value);
    }

    private static CdFlower.Assets assets() {
        return new CdFlower.Assets(CdObjectVisualAssets.decode(FLOWER_MAPPINGS, 8, FLOWER_SCRIPTS, 4, 39), FLOWER_MAP);
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
    private static final class RecordingDraw implements CdFlower.DrawObjectCall {
        final List<CdObjectState> observed = new ArrayList<>();

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
        }
    }

    /**
     * The TestCdMosqui terrain: layout cell (2,7) covers world X $200..$2FF, Y $700..$7FF with chunk 2, whose block
     * row 8 is a full-height solid floor (top at world Y $780, angle $22) across every column.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2 + 7 * 42 + 2] = 2;
            for (int column = 0; column < 16; column++) {
                int at = 512 + (8 * 16 + column) * 2;
                chunks[at] = 0x20; chunks[at + 1] = 0x01;
            }
            ids[1] = 7; angles[7] = 0x22;
            Arrays.fill(heights, 7 * 16, 7 * 16 + 16, (byte) 16);
        }
        CdPlayerTerrain probes() {
            return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths));
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(0x270, 0x730, 0xBD, 0x00, 0x40, 0)));
        final Terrain terrain = new Terrain();
        final RecordingDraw draw = new RecordingDraw();
        final CdFlower flower = new CdFlower(runtime, assets(), terrain.probes(), draw);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        CdFlower.Result run(int whole) { return flower.run(whole); }

        /**
         * ObjExplosion_Done .MakeFlower's two writes (Explosion/Main.asm:104-105) over a finished badnik explosion:
         * the explosion's tile, map and animator bytes plus the Mosqui leftovers stay in the slot.
         */
        CdObjectState converted(int whole, int x, int y, int subtype, int subtype2) {
            var state = slot(whole).moveByte(ID, CdFlower.OBJECT_ID).moveByte(ROUTINE, 0).moveByte(SPRITE_FLAGS, 0x85)
                    .moveWord(TILE, 0x8680).moveLong(MAPPINGS, 0x23F088)
                    .moveWord(X, x).moveWord(X_FRACTION, 0x8000).moveWord(Y, y).moveWord(Y_FRACTION, 0x4321)
                    .moveByte(Y_RADIUS, 0x10).moveByte(X_RADIUS, 0x10).moveByte(PRIORITY, 1).moveByte(WIDTH, 0x10)
                    .moveByte(MAP_FRAME, 4).moveByte(ANIMATION_FRAME, 5).moveByte(ANIMATION, 1)
                    .moveByte(PREVIOUS_ANIMATION, 1).moveByte(ANIMATION_TIME, 3).moveByte(FLAGS, 0x81)
                    .moveByte(SAVED_FLAGS_ID, 10).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, subtype2)
                    .moveWord(EXPLOSION_POINTS_FRAME, 4);
            write(state);
            return state;
        }
    }
}

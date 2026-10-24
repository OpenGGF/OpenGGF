package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.openggf.game.soniccd.objects.CdGameOver.DeleteEntry.*;
import static com.openggf.game.soniccd.objects.CdGameOver.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdGameOver.SubCpuCommand.*;
import static com.openggf.game.soniccd.objects.CdGameOver.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * ObjGameOver ($3B): Level/_Objects/Game Over/Main.asm:7-118 at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * as included by Palmtree Panic/Act 1 Present.asm (R43_LEGACY_DATA_PREFIX 0 and no R3_SEMANTIC_GAME_OVER), so the
 * three helpers are plain FindObjSlot, DeleteObject and DrawObject (Level End/Main.asm:36-47). Every literal is
 * worked from that source and the hand-assembled Game Over/Data/Mappings.asm include; the matching USA bytes are
 * checked only by the optional retail asset test. No media root, native row, fixture coordinate or frame index
 * drives any case here, and no case is skipped.
 */
class TestCdGameOver {
    /** MOVE.L #MapSpr_GameOver,oMap(a0) (:36): the USA R11A long, re-proved only by the optional retail test. */
    private static final int GAME_MAP = 0x20F03E;
    /** MapSpr_TimeOver is the second two-word table, four bytes after MapSpr_GameOver (Mappings.asm:1-6). */
    private static final int TIME_MAP = GAME_MAP + 4;
    /**
     * Mappings.asm:1-58, hand-assembled. Two tables of signed relative words, each relative to ITS OWN label:
     * GameText 8 and GameOverText $14 from +0, TimeText $1C and TimeOverText $28 from +4. Then four counted bodies
     * of two five-byte pieces (Y, size, tile word, X), each followed by its real zero byte.
     */
    private static final byte[] GAME_OVER_MAPPINGS = bytes(
            0x00, 0x08, 0x00, 0x14,
            0x00, 0x1C, 0x00, 0x28,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xB8, 0xF8, 0x0D, 0x00, 0x08, 0xD8, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x10, 0x08, 0xF8, 0x0D, 0x00, 0x18, 0x28, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x00, 0xBC, 0xF8, 0x09, 0x00, 0x08, 0xDC, 0x00,
            0x02, 0xF8, 0x0D, 0x00, 0x0E, 0x04, 0xF8, 0x0D, 0x00, 0x16, 0x24, 0x00);

    @Test
    void livesRemainingWithoutTimeOverFadesPcmWritesPowerupEightAndDeletesUndrawn() {
        var h = new Harness(3, 0x03, 0x00);
        var parent = h.parent(32);
        var before = h.pool().capture().slots();

        var result = h.run(32);

        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.Deleted(LIVES_REMAIN),
                call(INIT)), result.effects(), "no allocation, no SCMD_GMOVERMUS and no Main/DrawObject");
        assertTrue(result.deleted());
        assertArrayEquals(new byte[64], result.state().copyBytes(), "JMP DeleteObject clears all 64 bytes");
        // MOVE.B #8 (:38) is a plain store; BCLR #0 (:39) reads and always writes; .NotTimeOver TST.B lives (:48).
        assertEquals(List.of("powerup=08", "read", "timeOver=00", "read"), h.log);
        assertEquals(new CdGameOver.Globals(3, 0x08, 0x00), h.globals.current(), "lives is read only");
        // Init's slot writes (:31-36) were committed before MOVE.B #8,powerup, and DeleteObject makes them moot.
        assertArrayEquals(afterInitWrites(parent.copyBytes(), GAME_MAP),
                h.globals.poolAtWrites.getFirst().slots().get(0).copyBytes());
        var after = h.pool().capture().slots();
        for (int i = 1; i < CdObjectPool.DYNAMIC_COUNT; i++) {
            assertEquals(before.get(i), after.get(i), "slot " + (i + CdObjectPool.FIRST_WHOLE_INDEX));
        }
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void timeOverWithLivesRemainingShowsTimeOverTextAndFallsIntoMainWithoutMusic() {
        var h = new Harness(2, 0x00, 0xFF);
        var parent = h.parent(32);
        var freeChild = h.slot(33);

        var result = h.run(32);

        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.OverTextCreated(33),
                call(INIT), call(MAIN), call(DRAW_OBJECT)), result.effects(),
                "lives != 0 at :85 branches into ObjGameOver_Main instead of SCMD_GMOVERMUS");
        // BCLR #0 leaves the other bits; :41 TST.B; :44 ADDQ.B #2 and :52 BSET #7 read the live byte; :85 TST.B.
        assertEquals(List.of("powerup=08", "read", "timeOver=FE", "read", "read", "powerup=0A", "read",
                "powerup=8A", "read", "draw 32"), h.log);
        assertEquals(new CdGameOver.Globals(2, 0x8A, 0xFE), h.globals.current());
        assertEquals(GAME_MAP, h.globals.poolAtWrites.getFirst().slots().get(0).longBits(MAPPINGS),
                ":36 stores MapSpr_GameOver before :43 replaces it");

        byte[] expected = afterInitWrites(parent.copyBytes(), TIME_MAP);
        putWord(expected, 0x08, 0x0088);   // Main: $120 >= $80 (BGE), ADD.W #8,oX(a0)
        assertArrayEquals(expected, result.state().copyBytes(), "the port cleared bit 7 of an already zero flags byte");
        assertArrayEquals(childWrites(freeChild.copyBytes(), 2, 0x8544, TIME_MAP), h.slot(33).copyBytes(),
                "the child copies the parent's routine, tile and replaced map");

        assertEquals(1, h.draw.observed.size());
        var drawn = h.draw.observed.getFirst();
        assertEquals(32, drawn.identity().wholeIndex());
        assertEquals(0x0088, drawn.centreX(), "DrawObject saw Main's committed move");
        assertEquals(0x00E0, drawn.word(X_FRACTION), "the absolute screen Y word at $0A");
    }

    @Test
    void noLivesWithoutTimeOverShowsGameOverTextPlaysMusicAndSkipsMainAndDraw() {
        var h = new Harness(0, 0x00, 0x80);
        var parent = h.parent(32);
        var freeChild = h.slot(33);

        var result = h.run(32);

        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.OverTextCreated(33),
                new CdGameOver.SubCpuRequest(SCMD_GMOVERMUS), call(INIT)), result.effects(),
                ":87-88 JMP SubCPUCmd returns from the dispatch without Main or DrawObject");
        // BCLR #0 tests only bit 0: $80 takes .NotTimeOver, whose TST.B lives falls into .GameOver.
        assertEquals(List.of("powerup=08", "read", "timeOver=80", "read", "read", "powerup=88", "read"), h.log);
        assertEquals(new CdGameOver.Globals(0, 0x88, 0x80), h.globals.current());
        assertArrayEquals(afterInitWrites(parent.copyBytes(), GAME_MAP), result.state().copyBytes(),
                "the parent stays at X $80 this dispatch");
        assertArrayEquals(childWrites(freeChild.copyBytes(), 2, 0x8544, GAME_MAP), h.slot(33).copyBytes());
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void noLivesWithTimeOverStillUsesTheGameOverMappingsAndPowerupEightyEight() {
        var h = new Harness(0, 0x00, 0x81);
        h.parent(32);

        var result = h.run(32);

        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.OverTextCreated(33),
                new CdGameOver.SubCpuRequest(SCMD_GMOVERMUS), call(INIT)), result.effects());
        // :41-42 TST.B lives / BEQ.S .GameOver skips the TIME OVER map and ADDQ.B #2 (:43-44).
        assertEquals(List.of("powerup=08", "read", "timeOver=80", "read", "read", "powerup=88", "read"), h.log);
        assertEquals(new CdGameOver.Globals(0, 0x88, 0x80), h.globals.current());
        assertEquals(GAME_MAP, result.state().longBits(MAPPINGS));
        assertEquals(GAME_MAP, h.slot(33).longBits(MAPPINGS));
        assertEquals(0x0080, result.state().centreX());
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void dirtyParentAndFreeButDirtyChildKeepEveryUnwrittenByte() {
        var h = new Harness(0, 0x00, 0x00);
        // DeadChkGone writes only oID (Sonic/Main.asm:2436-2437); a slot freed by ObjNull keeps its other bytes.
        var parent = h.parent(40).moveByte(SPRITE_FLAGS, 0x07).moveByte(PRIORITY, 5).moveByte(WIDTH, 0x10)
                .moveByte(MAP_FRAME, 3).moveWord(Y, 0x1234).moveWord(Y_FRACTION, 0x5678).moveWord(X_VELOCITY, 0x0123)
                .moveByte(COLLISION_TYPE, 0x47).moveByte(FLAGS, 0x81).moveByte(SUBTYPE, 9).moveLong(MOSQUI_X_DELTA, -1);
        h.write(parent);
        h.occupy(32, 35);
        var freeButDirty = h.slot(35).moveWord(X_FRACTION, 0xABCD).moveWord(Y, 0x0760).moveByte(SPRITE_FLAGS, 0x84)
                .moveByte(PRIORITY, 6).moveByte(ROUTINE, 0x0E).moveByte(MAP_FRAME, 9).moveWord(X_VELOCITY, 0x0123)
                .moveByte(SAVED_FLAGS_ID, 10).moveWord(TILE, 0x23A0).moveLong(MAPPINGS, 0x20B6E6).moveByte(SUBTYPE2, 4);
        h.write(freeButDirty);

        var result = h.run(40);

        assertEquals(new CdGameOver.OverTextCreated(35), result.effects().get(1));
        assertArrayEquals(afterInitWrites(parent.copyBytes(), GAME_MAP), result.state().copyBytes(),
                "flags, priority, width, oMapFrame, world Y, velocities, collision and oVar2C.. stay");
        byte[] child = childWrites(freeButDirty.copyBytes(), 2, 0x8544, GAME_MAP);
        assertArrayEquals(child, h.slot(35).copyBytes(), "the dirty child keeps flags $84, priority 6, world Y");
        assertEquals(0x00E0, h.slot(35).word(X_FRACTION), "the oYScr word overwrote the leftover $ABCD");
        for (int whole = 32; whole < 35; whole++) {
            assertEquals(0x10, h.slot(whole).id(), "occupant " + whole);
        }
    }

    @Test
    void aLaterChildRunsMainInTheSameLiveWalkWhileAnEarlierChildWaitsAPass() {
        var later = new Harness(0, 0x00, 0x00);
        later.parent(32);
        assertEquals(List.of(32, 33), later.walk(), "FindObjSlot chose 33, after the parent at 32");
        assertEquals(0x01B8, later.slot(33).centreX(), "$120 < $1C0: NEG.W d0, ADD.W -8");
        assertEquals(0x0080, later.slot(32).centreX());
        assertEquals(List.of("draw 33"), later.draws());
        assertEquals(List.of(32, 33), later.walk());
        assertEquals(List.of(0x0088, 0x01B0), List.of(later.slot(32).centreX(), later.slot(33).centreX()));
        assertEquals(List.of("draw 33", "draw 32", "draw 33"), later.draws());

        var earlier = new Harness(0, 0x00, 0x00);
        earlier.occupy(32, 33);
        earlier.parent(40);
        assertEquals(List.of(40), earlier.walk(), "the walk had already passed slot 33");
        assertEquals(CdGameOver.OBJECT_ID, earlier.slot(33).id());
        assertEquals(0x01C0, earlier.slot(33).centreX(), "the earlier child waits a pass");
        assertTrue(earlier.draws().isEmpty());
        assertEquals(List.of(33, 40), earlier.walk());
        assertEquals(List.of(0x01B8, 0x0088), List.of(earlier.slot(33).centreX(), earlier.slot(40).centreX()));
        assertEquals(List.of("draw 33", "draw 40"), earlier.draws());
    }

    @Test
    void bothTextsReachTheCentreAfterTwentyMainCallsAndThenOnlyDraw() {
        var h = new Harness(1, 0x00, 0x00);
        h.write(h.mainState(40, 0x0080, 0x0120, GAME_MAP, 0));
        h.write(h.mainState(41, 0x01C0, 0x0120, GAME_MAP, 1));
        for (int call = 1; call <= 20; call++) {
            assertEquals(List.of(call(MAIN), call(DRAW_OBJECT)), h.run(40).effects());
            h.run(41);
            assertEquals(0x0080 + 8 * call, h.slot(40).centreX(), "GAME call " + call);
            assertEquals(0x01C0 - 8 * call, h.slot(41).centreX(), "OVER call " + call);
        }
        var lastGame = h.slot(40);
        var lastOver = h.slot(41);
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT)), h.run(40).effects());
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT)), h.run(41).effects());
        assertEquals(lastGame, h.slot(40), "BEQ.S .Draw: no write at the destination");
        assertEquals(lastOver, h.slot(41));
        assertEquals(42, h.draw.observed.size(), "every Main call ends at DrawObject");
        assertTrue(h.log.stream().noneMatch(entry -> entry.startsWith("read") || entry.contains("=")),
                "Main reads and writes no global");
    }

    @ParameterizedTest(name = "oX {0} toward {1} -> {2}")
    @CsvSource({"0x0080, 0x0120, 0x0088", "0x01C0, 0x0120, 0x01B8", "0x0120, 0x0120, 0x0120",
            "0x8000, 0x0120, 0x8008", "0x7FF8, 0x0120, 0x7FF0", "0xFFF8, 0x0120, 0x0000",
            "0x0121, 0x0120, 0x0119", "0x0119, 0x0120, 0x0121", "0x0000, 0x8000, 0xFFF8",
            "0x7FFC, 0x7FFF, 0x8004"})
    void mainMovesEightTowardTheSignedDestinationAndWrapsOnlyTheXWord(String x, String destination, String moved) {
        var h = new Harness(1, 0x00, 0x00);
        var state = h.mainState(40, Integer.decode(x), Integer.decode(destination), TIME_MAP, 1)
                .moveWord(Y, 0x1234).moveByte(SPRITE_FLAGS, 0x03);
        h.write(state);
        var result = h.run(40);
        byte[] expected = state.copyBytes();
        putWord(expected, 0x08, Integer.decode(moved));   // CMP.W oX(a0),d1 / BEQ / BGE (signed) / NEG.W / ADD.W
        assertArrayEquals(expected, result.state().copyBytes(),
                "the $0A screen-Y word, world Y and the destination are never written by Main");
    }

    @Test
    void fullPoolDeletesTheParentAfterItsPowerupWritesWithoutMusicOrDraw() {
        var gameOver = new Harness(0, 0x00, 0x00);
        gameOver.parent(40);
        gameOver.occupy(32, 128);
        var before = gameOver.pool().capture().slots();
        var result = gameOver.run(40);
        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.OverTextSlotUnavailable(),
                new CdGameOver.Deleted(NO_FREE_SLOT), call(INIT)), result.effects(),
                "FindObjSlot NE falls into .Destroy: no SCMD_GMOVERMUS and no DrawObject");
        assertEquals(List.of("powerup=08", "read", "timeOver=00", "read", "read", "powerup=88"), gameOver.log,
                "BSET #7 (:52) precedes FindObjSlot (:60) and is not undone");
        assertArrayEquals(new byte[64], result.state().copyBytes());
        var after = gameOver.pool().capture().slots();
        for (int i = 0; i < CdObjectPool.DYNAMIC_COUNT; i++) {
            if (i != 40 - CdObjectPool.FIRST_WHOLE_INDEX) { assertEquals(before.get(i), after.get(i)); }
        }
        assertTrue(gameOver.draw.observed.isEmpty());

        var timeOver = new Harness(2, 0x00, 0x01);
        timeOver.parent(40);
        timeOver.occupy(32, 128);
        var timed = timeOver.run(40);
        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.OverTextSlotUnavailable(),
                new CdGameOver.Deleted(NO_FREE_SLOT), call(INIT)), timed.effects(),
                "the BNE into Main at :85-86 exists only on the found path");
        assertEquals(List.of("powerup=08", "read", "timeOver=00", "read", "read", "powerup=0A", "read", "powerup=8A"),
                timeOver.log);
        assertTrue(timed.deleted());
        assertTrue(timeOver.draw.observed.isEmpty());
    }

    @Test
    void eachLivesTestReadsThePortAtItsOwnSourceCut() {
        var h = new Harness(0, 0x00, 0x00);
        h.globals.livesAfterBset = 1;
        h.parent(32);
        var result = h.run(32);
        assertEquals(List.of(new CdGameOver.SubCpuRequest(SCMD_FADEPCM), new CdGameOver.OverTextCreated(33),
                call(INIT), call(MAIN), call(DRAW_OBJECT)), result.effects(),
                ":48 read lives 0 and :85 read the port's later value 1");
        assertEquals(0x0088, result.state().centreX());
        assertEquals(GAME_MAP, result.state().longBits(MAPPINGS));
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"1", "3", "4", "6", "0xFE"})
    void routineOutsideTheTwoEntryIndexStopsBeforeAnyWriteOrPortCall(String routine) {
        var h = new Harness(3, 0x00, 0x00);
        h.write(h.parent(40).moveByte(ROUTINE, Integer.decode(routine)));
        var before = h.pool().capture().slots();
        var failure = assertThrows(CdGameOver.UnsupportedGameOver.class, () -> h.run(40));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(40, failure.wholeIndex());
        assertEquals(before, h.pool().capture().slots());
        assertTrue(h.log.isEmpty(), "no global read or write, no DrawObject");
    }

    @Test
    void wrongIdNullPortsAndInvalidRecordsAreRejectedBeforeAnyWrite() {
        var h = new Harness(3, 0x00, 0x00);
        h.write(h.slot(40).moveByte(ID, 0x3A));
        var before = h.slot(40);
        assertThrows(IllegalArgumentException.class, () -> h.run(40));
        assertEquals(before, h.slot(40));
        h.parent(41);
        var parent = h.slot(41);
        assertThrows(NullPointerException.class, () -> h.gameOver.run(41, null));
        assertEquals(parent, h.slot(41));
        assertTrue(h.log.isEmpty());
        for (int bad : new int[]{-1, 0x100}) {
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Globals(bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Globals(0, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Globals(0, 0, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdGameOver.OverTextCreated(31));
        assertThrows(IllegalArgumentException.class, () -> new CdGameOver.OverTextCreated(128));
        assertThrows(NullPointerException.class, () -> new CdGameOver.SubCpuRequest(null));
        assertThrows(NullPointerException.class, () -> new CdGameOver.CallReturned(null));
        assertThrows(NullPointerException.class, () -> new CdGameOver.Deleted(null));
        assertThrows(NullPointerException.class, () -> new CdGameOver(null, assets(), new RecordingDraw(new ArrayList<>())));
        assertThrows(NullPointerException.class, () -> new CdGameOver(h.runtime, null, new RecordingDraw(new ArrayList<>())));
        assertThrows(NullPointerException.class, () -> new CdGameOver(h.runtime, assets(), null));
        var a = assets();
        assertSame(a, new CdGameOver(h.runtime, a, new RecordingDraw(new ArrayList<>())).assets());
    }

    @Test
    void theResultReloadsTheDrawPortsBitSevenWriteAndTheBodyNeverWritesTheFlags() {
        var h = new Harness(1, 0x00, 0x00);
        h.write(h.mainState(40, 0x0080, 0x0120, GAME_MAP, 0));
        h.draw.mode = RecordingDraw.Mode.SET_BIT7;
        assertEquals(0x80, h.run(40).state().spriteFlags());
        h.draw.mode = RecordingDraw.Mode.UNTOUCHED;
        assertEquals(0x80, h.run(40).state().spriteFlags(), "only DrawObject owns bit 7");
        h.draw.mode = RecordingDraw.Mode.CLEAR_BIT7;
        assertEquals(0x00, h.run(40).state().spriteFlags());
        assertEquals(List.of(0x0088, 0x0090, 0x0098), h.draw.observed.stream().map(CdObjectState::centreX).toList(),
                "each DrawObject saw that call's committed oX");
    }

    @Test
    void assetsKeepTheExactSharedBlobAndBothNativeTableAddresses() {
        byte[] input = GAME_OVER_MAPPINGS.clone();
        var assets = new CdGameOver.Assets(CdAssetBytes.copyOf(input), GAME_MAP);
        assertEquals(56, CdGameOver.Assets.MAPPING_BYTES);
        assertEquals(4, CdGameOver.Assets.TIME_OVER_TABLE_OFFSET);
        assertEquals(GAME_MAP, assets.gameOverMappingsAddress());
        assertEquals(TIME_MAP, assets.timeOverMappingsAddress());
        assertArrayEquals(GAME_OVER_MAPPINGS, assets.mappings().copy());
        input[1] = 0x7F;
        assertEquals(8, assets.mappings().unsignedByte(1), "the record owns immutable bytes");
        assertThrows(ReadOnlyBufferException.class, () -> assets.mappings().buffer().put((byte) 1));
        // Each table stays relative to its own root; the record neither compacts nor rebases a word.
        var m = assets.mappings();
        assertEquals(List.of(8, 20), List.of((int) (short) m.unsignedWord(0), (int) (short) m.unsignedWord(2)));
        assertEquals(List.of(32, 44), List.of(4 + (short) m.unsignedWord(4), 4 + (short) m.unsignedWord(6)));
        assertEquals(0x200000, new CdGameOver.Assets(CdAssetBytes.copyOf(GAME_OVER_MAPPINGS), 0x200000)
                .gameOverMappingsAddress());
        assertEquals(0x23FFC8, new CdGameOver.Assets(CdAssetBytes.copyOf(GAME_OVER_MAPPINGS), 0x23FFC8)
                .gameOverMappingsAddress(), "the last even base whose 56 bytes end at Word RAM $240000");
    }

    @Test
    void assetsRejectEveryMalformedOrUnboundedVariantOfTheTwoTableBlob() {
        assertThrows(NullPointerException.class, () -> new CdGameOver.Assets(null, GAME_MAP));
        for (int length : new int[]{0, 8, 55, 57}) {
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Assets(
                    CdAssetBytes.copyOf(Arrays.copyOf(GAME_OVER_MAPPINGS, length)), GAME_MAP), "length " + length);
        }
        for (int address : new int[]{GAME_MAP + 1, 0x1FFFFE, 0x23FFCA, 0x240000, -2}) {
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Assets(
                    CdAssetBytes.copyOf(GAME_OVER_MAPPINGS), address), "address " + Integer.toHexString(address));
        }
        int[][] words = {
                {0, 0x0004},   // GAME frame 0 inside the eight-byte header
                {2, 0x0038},   // GAME frame 1 at the include end
                {4, 0xFFFC},   // TIME frame 0 signed back to offset 0
                {6, 0x0004},   // TIME frame 1 aliases GameText: TimeText would then run to the end with 13 extra bytes
                {0, 0x0009}};  // GAME frame 0 one byte late: a gap after the header and a $F8 count
        for (int[] word : words) {
            byte[] bad = GAME_OVER_MAPPINGS.clone();
            putWord(bad, word[0], word[1]);
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Assets(CdAssetBytes.copyOf(bad), GAME_MAP),
                    "table word at " + word[0] + " = " + Integer.toHexString(word[1]));
        }
        int[][] body = {
                {8, 0x03},     // GameText count 3 crosses into GameOverText
                {19, 0x01},    // GameText alignment byte is not zero
                {10, 0x10},    // piece size byte outside the admitted nibble
                {55, 0xFF}};   // TimeOverText alignment byte is not zero
        for (int[] change : body) {
            byte[] bad = GAME_OVER_MAPPINGS.clone();
            bad[change[0]] = (byte) change[1];
            assertThrows(IllegalArgumentException.class, () -> new CdGameOver.Assets(CdAssetBytes.copyOf(bad), GAME_MAP),
                    "byte " + change[0] + " = " + Integer.toHexString(change[1]));
        }
    }

    @Test
    void nativeConstantsAndStorageAliasesMatchTheSource() {
        assertEquals(0x3B, CdGameOver.OBJECT_ID, "Palmtree Panic/Object Index (Act 1 Present).asm:66");
        assertEquals(0x8544, CdGameOver.NATIVE_TILE_WORD, "Game Over/Main.asm:35");
        // _Include/System.inc: RSSET 1 (:144); counting every RS.B, including the unnamed entry at :183,
        // SCMD_GMOVERMUS (:254) is the 110th value and SCMD_FADEPCM (:274) the 130th.
        assertEquals(0x6E, SCMD_GMOVERMUS.commandWord());
        assertEquals(0x82, SCMD_FADEPCM.commandWord());
        assertEquals(0x2A, GAME_OVER_DEST_X.byteOffset(), "oGmOverDestX EQU oVar2A (Game Over/Main.asm:7)");
        assertEquals(MOSQUI_ANCHOR_X.byteOffset(), GAME_OVER_DEST_X.byteOffset(), "overlapping oVar2A storage only");
        assertEquals(POINTS_TIMER.byteOffset(), GAME_OVER_DEST_X.byteOffset());
        // _Definitions.inc:37-39: oYScr rs.b 0 precedes oXSub, so MOVE.W #imm,oYScr writes the WORD at $0A.
        assertEquals(0x0A, X_FRACTION.byteOffset());
        assertEquals(SCREEN_Y.byteOffset(), X_FRACTION.byteOffset());
        assertEquals(0x0C, Y.byteOffset(), "world Y is never written by this object");
    }

    private static CdGameOver.Effect call(CdGameOver.NativeCall nativeCall) { return new CdGameOver.CallReturned(nativeCall); }

    private static CdGameOver.Assets assets() {
        return new CdGameOver.Assets(CdAssetBytes.copyOf(GAME_OVER_MAPPINGS), GAME_MAP);
    }

    /** Init's own slot writes (Game Over/Main.asm:31-36) over a dispatched routine-0 image. */
    private static byte[] afterInitWrites(byte[] before, int map) {
        byte[] b = before.clone();
        b[0x24] = 2;                       // ADDQ.B #2,oRoutine(a0)
        putWord(b, 0x0A, 0x00E0);          // MOVE.W #96+128,oYScr(a0): the WORD at $0A, not world Y at $0C
        putWord(b, 0x08, 0x0080);          // MOVE.W #0+128,oX(a0): the fraction word is the oYScr word
        putWord(b, 0x2A, 0x0120);          // MOVE.W #160+128,oGmOverDestX(a0)
        putWord(b, 0x02, 0x8544);          // MOVE.W #$8544,oTile(a0)
        putLong(b, 0x04, map);             // MOVE.L #MapSpr_GameOver,oMap(a0), or MapSpr_TimeOver (:43)
        return b;
    }

    /** .SpawnOverText (Game Over/Main.asm:76-83) over the free slot's previous bytes. */
    private static byte[] childWrites(byte[] before, int routine, int tile, int map) {
        byte[] b = before.clone();
        b[0x00] = 0x3B;                    // MOVE.B #$3B,oID(a1)
        b[0x24] = (byte) routine;          // MOVE.B oRoutine(a0),oRoutine(a1): the parent's advanced routine
        putWord(b, 0x02, tile);            // MOVE.W oTile(a0),oTile(a1)
        putLong(b, 0x04, map);             // MOVE.L oMap(a0),oMap(a1)
        b[0x1A] = 1;                       // MOVE.B #1,oMapFrame(a1)
        putWord(b, 0x0A, 0x00E0);          // MOVE.W #128+96,oYScr(a1)
        putWord(b, 0x08, 0x01C0);          // MOVE.W #320+128,oX(a1)
        putWord(b, 0x2A, 0x0120);          // MOVE.W #160+128,oGmOverDestX(a1)
        return b;
    }

    private static void putWord(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    private static void putLong(byte[] bytes, int at, int value) {
        putWord(bytes, at, value >>> 16);
        putWord(bytes, at + 2, value);
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

    /** Native DrawObject stand-in over the live pool; the shared queue is a separate owner. */
    private static final class RecordingDraw implements CdGameOver.DrawObjectCall {
        enum Mode { CLEAR_BIT7, SET_BIT7, UNTOUCHED }
        final List<String> log;
        Mode mode = Mode.CLEAR_BIT7;
        final List<CdObjectState> observed = new ArrayList<>();

        RecordingDraw(List<String> log) { this.log = log; }

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            log.add("draw " + slot.wholeIndex());
            switch (mode) {
                case CLEAR_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
                case SET_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() | 0x80));
                case UNTOUCHED -> { }
            }
        }
    }

    /** The caller's live global bytes: every source-cut access is logged; the pool is sampled at each write. */
    private static final class RecordingGlobals implements CdGameOver.GlobalsPort {
        final List<String> log;
        final CdObjectPool pool;
        final List<CdObjectPool.Snapshot> poolAtWrites = new ArrayList<>();
        int lives;
        int powerup;
        int timeOver;
        /** Test-only adversarial store change after BSET #7; -1 keeps lives constant as native code does. */
        int livesAfterBset = -1;

        RecordingGlobals(List<String> log, CdObjectPool pool, int lives, int powerup, int timeOver) {
            this.log = log;
            this.pool = pool;
            this.lives = lives;
            this.powerup = powerup;
            this.timeOver = timeOver;
        }

        CdGameOver.Globals current() { return new CdGameOver.Globals(lives, powerup, timeOver); }

        @Override public CdGameOver.Globals read() {
            log.add("read");
            return current();
        }

        @Override public void writePowerup(int value) {
            poolAtWrites.add(pool.capture());
            log.add(String.format(Locale.ROOT, "powerup=%02X", value));
            powerup = value;
            if (livesAfterBset >= 0 && (value & 0x80) != 0) { lives = livesAfterBset; }
        }

        @Override public void writeTimeOver(int value) {
            poolAtWrites.add(pool.capture());
            log.add(String.format(Locale.ROOT, "timeOver=%02X", value));
            timeOver = value;
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(0x270, 0x730, 0xBD, 0x00, 0x40, 0)));
        final List<String> log = new ArrayList<>();
        final RecordingGlobals globals;
        final RecordingDraw draw = new RecordingDraw(log);
        final CdGameOver gameOver = new CdGameOver(runtime, assets(), draw);

        Harness(int lives, int powerup, int timeOver) {
            globals = new RecordingGlobals(log, runtime.pool(), lives, powerup, timeOver);
        }

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        CdGameOver.Result run(int whole) { return gameOver.run(whole, globals); }
        List<String> draws() { return log.stream().filter(entry -> entry.startsWith("draw")).toList(); }

        /** DeadChkGone's unchecked FindObjSlot / MOVE.B #$3B,oID(a1) (Sonic/Main.asm:2436-2437): oID only. */
        CdObjectState parent(int whole) {
            var state = slot(whole).moveByte(ID, CdGameOver.OBJECT_ID);
            write(state);
            return state;
        }

        /** A routine-2 image as Init/.SpawnOverText leave it: only the fields Main and DrawObject read. */
        CdObjectState mainState(int whole, int x, int destination, int map, int mapFrame) {
            return slot(whole).moveByte(ID, CdGameOver.OBJECT_ID).moveByte(ROUTINE, 2).moveWord(TILE, 0x8544)
                    .moveLong(MAPPINGS, map).moveByte(MAP_FRAME, mapFrame).moveWord(X_FRACTION, 0x00E0)
                    .moveWord(X, x).moveWord(GAME_OVER_DEST_X, destination);
        }

        /** Live non-Game-Over occupants for whole slots [from, to): a nonzero oID is all FindObjSlot tests. */
        void occupy(int from, int to) {
            for (int whole = from; whole < to; whole++) {
                if (slot(whole).id() == 0) { write(slot(whole).moveByte(ID, 0x10).moveByte(ROUTINE, 2)); }
            }
        }

        /** One RunObjects visit of the dynamic slots, fetching each oID live (Object Functions.asm:11-31). */
        List<Integer> walk() {
            var visited = new ArrayList<Integer>();
            for (int whole = CdObjectPool.FIRST_WHOLE_INDEX; whole < CdObjectPool.END_WHOLE_INDEX; whole++) {
                if (slot(whole).id() == CdGameOver.OBJECT_ID) {
                    visited.add(whole);
                    run(whole);
                }
            }
            return visited;
        }
    }
}

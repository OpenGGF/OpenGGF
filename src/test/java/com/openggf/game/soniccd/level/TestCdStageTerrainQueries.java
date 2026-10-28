package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.player.CdPlayerGroundCollision;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit format fixtures, not authenticated Past gameplay, source-family or Frame admission. */
class TestCdStageTerrainQueries {
    @Test
    void namedFactoriesRejectNullWithoutChangingTheOldConstructorCalls() {
        assertThrows(NullPointerException.class, () -> CdTerrainCollision.fromStageTerrain(null));
        assertThrows(NullPointerException.class, () -> CdPlayerTerrain.fromStageTerrain(null));
        assertThrows(NullPointerException.class, () -> CdPlayerGroundCollision.fromStageTerrain(null));
        assertThrows(NullPointerException.class, () -> new CdTerrainCollision(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerTerrain(null));
        assertThrows(NullPointerException.class, () -> new CdPlayerGroundCollision((CdR11aTerrain) null));
    }

    @Test
    void pastPlacementUsesTheExistingNativeBaseAndRawWordValue() {
        var f = new Fixture(); f.fg[2] = 1; f.fillChunk(1, 0x2001);
        var terrain = f.terrain();
        assertEquals(CdStageTerrain.Shape.PPZ1_PAST, terrain.shape());
        assertEquals(54272, terrain.chunks().length()); assertEquals(800, terrain.collision().ids().length());
        var p = assertInstanceOf(CdTerrainCollision.Available.class,
                CdTerrainCollision.fromStageTerrain(terrain).lookup(16, 0, 0));
        assertEquals(new CdTerrainCollision.Address(0, 1, 1, 2), p.address());
        assertEquals(0x210002, p.address().nativeAddress());
        assertEquals(new CdR11aTerrain.BlockReference(0x2001), p.word());
    }

    @Test
    void nonzeroChunkOneAtOffsetZeroStillReturnsTheNativeBlankPointer() {
        var f = new Fixture(); f.fg[2] = 1; f.fillChunk(1, 0x2001);
        var p = assertInstanceOf(CdTerrainCollision.Blank.class,
                CdTerrainCollision.fromStageTerrain(f.terrain()).lookup(0, 0, 0));
        assertEquals(new CdTerrainCollision.Address(0, 1, 1, 0), p.address());
        assertEquals(0x210000, p.address().nativeAddress());
    }

    @Test
    void flaggedTwentyEightPairUsesActualFortyBitAndRemapsTwentyNineToFiftyOne() {
        var f = new Fixture(); f.fg[2] = (byte) 0xA8;
        f.fillChunk(0x28, 0x2002); f.fillChunk(0x51, 0x2001);
        var q = CdTerrainCollision.fromStageTerrain(f.terrain());
        var front = assertInstanceOf(CdTerrainCollision.Available.class, q.lookup(0, 0, 0x08));
        var back = assertInstanceOf(CdTerrainCollision.Available.class, q.lookup(0, 0, 0x140));
        assertEquals(new CdTerrainCollision.Address(0, 0xA8, 0x28, 0x4E00), front.address());
        assertEquals(0x2002, front.word().rawWord());
        assertEquals(new CdTerrainCollision.Address(0, 0xA8, 0x51, 0xA000), back.address());
        assertEquals(0x2001, back.word().rawWord());
    }

    @Test
    void finalResidentChunkDoesNotMakeTheFlaggedNextChunkAvailable() {
        var f = new Fixture(); f.fg[2] = (byte) 0xEA;
        var q = CdTerrainCollision.fromStageTerrain(f.terrain());
        var current = assertInstanceOf(CdTerrainCollision.Available.class, q.lookup(0, 0, 0));
        assertEquals(0xD200, current.address().chunkByteOffset());
        var missing = assertInstanceOf(CdTerrainCollision.Unavailable.class, q.lookup(0, 0, 0x40));
        assertEquals(new CdTerrainCollision.Address(0, 0xEA, 107, 0xD400), missing.address());
        assertEquals(CdTerrainCollision.UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS, missing.reason());
    }

    @Test
    void collisionId799IsReadFromItsActualMapDespiteTheSmallerDrawingBlockCount() {
        var f = new Fixture(); f.fg[2] = (byte) 0xE8; f.fillChunk(105, 0x231F);
        f.shape(799, 2, 0x44, 8, 8);
        var terrain = f.terrain(); assertEquals(660, terrain.blockCount());
        assertEquals(new CdPlayerTerrain.Result(3, OptionalInt.of(0x44)),
                CdPlayerTerrain.fromStageTerrain(terrain).floor(16, 20, 13, 0, 16, 0x40));
    }

    @Test
    void collisionId800StopsBeforeAnyAngleStoreWithoutBorrowingARenderingMask() {
        var f = new Fixture(); f.fg[2] = (byte) 0xE8; f.fillChunk(105, 0x2320);
        var q = CdPlayerTerrain.fromStageTerrain(f.terrain()); var writes = new ArrayList<Integer>();
        var stopped = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> q.floorWithPrefix(16, 20, 13, 0, 16, 0x40, writes::add));
        assertEquals(new CdPlayerTerrain.CollisionId(800), stopped.detail()); assertTrue(writes.isEmpty());
        assertInstanceOf(IllegalStateException.class, stopped.originalException());
        assertEquals("Collision ID requires unavailable resident bytes: 800", stopped.getMessage());
    }

    @Test
    void bitTenRemainsPartOfTheCollisionIndexInsteadOfTheDrawingIndex() {
        var f = new Fixture(); f.fg[2] = (byte) 0xE8; f.fillChunk(105, 0x2401);
        var writes = new ArrayList<Integer>();
        var stopped = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class, () ->
                CdPlayerTerrain.fromStageTerrain(f.terrain()).floorWithPrefix(16, 20, 13, 0, 16, 0x40, writes::add));
        assertEquals(new CdPlayerTerrain.CollisionId(0x401), stopped.detail()); assertTrue(writes.isEmpty());
    }

    @Test
    void flaggedZeroRetainsTheRealOddWordFaultAndOriginalException() {
        var f = new Fixture(); f.fg[2] = (byte) 0x80;
        var q = CdPlayerTerrain.fromStageTerrain(f.terrain()); var writes = new ArrayList<Integer>();
        var stopped = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> q.floorWithPrefix(0, 0, 13, 0, 16, 0, writes::add));
        assertEquals(new CdPlayerTerrain.TerrainWord(new CdTerrainCollision.Address(0, 0x80, 0, 0xFE01),
                CdTerrainCollision.UnavailableReason.ODD_NATIVE_WORD_ADDRESS), stopped.detail());
        assertSame(stopped.originalException(), stopped.getCause()); assertTrue(writes.isEmpty());
        assertEquals("Unavailable native collision word at $21fe01: ODD_NATIVE_WORD_ADDRESS", stopped.getMessage());
    }

    @Test
    void genuineAngleWritePrecedesAnUnavailableAdjacentWord() {
        var f = new Fixture(); f.fg[2] = 1; f.fg[2 + 42] = (byte) 0x80;
        f.fillChunk(1, 0x2001); f.shape(1, 1, 0x22, 0, 8);
        var q = CdPlayerTerrain.fromStageTerrain(f.terrain()); var writes = new ArrayList<Integer>();
        var stopped = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,
                () -> q.floorWithPrefix(16, 240, 13, 0, 16, 0, writes::add));
        assertEquals(List.of(0x22), writes);
        assertEquals(new CdPlayerTerrain.TerrainWord(new CdTerrainCollision.Address(0x80, 0x80, 0, 0xFE03),
                CdTerrainCollision.UnavailableReason.ODD_NATIVE_WORD_ADDRESS), stopped.detail());
    }

    @Test
    void fullHeightBackstepKeepsTheEarlierAngleAndItsDifferentFaultAddress() {
        var f = new Fixture(); f.fg[2] = (byte) 0x80; f.fg[2 + 42] = 1;
        f.fillChunk(1, 0x2001); f.shape(1, 1, 0x22, 16, 8);
        var writes = new ArrayList<Integer>();
        var stopped = assertThrows(CdPlayerTerrain.UnownedTerrainRead.class, () ->
                CdPlayerTerrain.fromStageTerrain(f.terrain()).floorWithPrefix(16, 256, 13, 0, 16, 0, writes::add));
        assertEquals(List.of(0x22), writes);
        assertEquals(new CdPlayerTerrain.TerrainWord(new CdTerrainCollision.Address(0, 0x80, 0, 0xFFE3),
                CdTerrainCollision.UnavailableReason.ODD_NATIVE_WORD_ADDRESS), stopped.detail());
    }

    @Test
    void callbackFailurePropagatesTheSameInstanceWithoutLaterStores() {
        var f = ordinary(8, 8, 0x22); var q = CdPlayerTerrain.fromStageTerrain(f.terrain());
        var original = new IllegalStateException("owned observer failure"); var writes = new ArrayList<Integer>();
        assertSame(original, assertThrows(IllegalStateException.class, () ->
                q.floorWithPrefix(17, 20, 13, 0, 16, 0, a -> { writes.add(a); throw original; })));
        assertEquals(List.of(0x22), writes);
    }

    @Test
    void firstAndExtensionQueriesUseActualPastHeightAndWidthBytes() {
        var q = CdPlayerTerrain.fromStageTerrain(ordinary(8, 8, 0x22).terrain());
        var expected = new CdPlayerTerrain.Result(3, OptionalInt.of(0x22));
        assertEquals(expected, q.floor(17, 20, 13, 0, 16, 0));
        assertEquals(expected, q.floorExtension(17, 20, 13, 0, 16, 0));
        assertEquals(expected, q.wall(20, 17, 13, 0, 16, 0));
        assertEquals(expected, q.wallExtension(20, 17, 13, 0, 16, 0));
    }

    @Test
    void fourReflectionsKeepLiteralAngleStoreOrderAndMetricMirrorSelection() {
        int[] words = {0x2001, 0x2801, 0x3001, 0x3801};
        int[] angles = {0x22, 0xDE, 0x5E, 0xA2};
        List<List<Integer>> expectedWrites = List.of(List.of(0x22), List.of(0x22, 0xDE),
                List.of(0x22, 0x62, 0x9E, 0x5E), List.of(0x22, 0xDE, 0x1E, 0xE2, 0xA2));
        for (int i = 0; i < words.length; i++) {
            var f = ordinary(8, 8, 0x22); f.fillChunk(1, words[i]);
            var writes = new ArrayList<Integer>(); var q = CdPlayerTerrain.fromStageTerrain(f.terrain());
            assertEquals(new CdPlayerTerrain.Result(3, OptionalInt.of(angles[i])),
                    q.floorWithPrefix(17, 20, 13, words[i] & 0x1000, 16, 0, writes::add));
            assertEquals(expectedWrites.get(i), writes);
        }
    }

    @Test
    void negativeHeightStep16OwnsTheNativeForcedZeroForBothEntries() {
        var q = CdPlayerTerrain.fromStageTerrain(ordinary(-8, 8, 0x22).terrain());
        var writes = new ArrayList<Integer>();
        assertEquals(new CdPlayerTerrain.Result(-5, OptionalInt.of(0)), q.floorWithPrefix(17, 20, 13, 0, 16, 0, writes::add));
        assertEquals(List.of(0x22, 0), writes);
        assertEquals(new CdPlayerTerrain.Result(-5, OptionalInt.of(0)), q.floorExtension(17, 20, 13, 0, 16, 0));
    }

    @Test
    void negativeHeightOtherStepPreservesFirstVersusExtensionComplement() {
        var q = CdPlayerTerrain.fromStageTerrain(ordinary(-8, 8, 0x22).terrain());
        assertEquals(new CdPlayerTerrain.Result(-21, OptionalInt.of(0x22)), q.floor(17, 20, 13, 0, -16, 0));
        assertEquals(new CdPlayerTerrain.Result(-5, OptionalInt.of(0x22)), q.floorExtension(17, 20, 13, 0, -16, 0));
    }

    @Test
    void zeroMaterialOrUnselectedSolidityDoesNotInventAnAngleWrite() {
        var f = ordinary(8, 8, 0x22); f.ids[1] = 0;
        var q = CdPlayerTerrain.fromStageTerrain(f.terrain());
        assertEquals(new CdPlayerTerrain.Result(27, OptionalInt.empty()), q.floor(17, 20, 13, 0, 16, 0));
        var other = CdPlayerTerrain.fromStageTerrain(ordinary(8, 8, 0x22).terrain());
        assertEquals(new CdPlayerTerrain.Result(27, OptionalInt.empty()), other.floor(17, 20, 14, 0, 16, 0));
    }

    @Test
    void sourceCoordinatesUseLowWordsWithoutAnEngineBoundsClamp() {
        var q = CdPlayerTerrain.fromStageTerrain(ordinary(8, 8, 0x22).terrain());
        assertEquals(new CdPlayerTerrain.Result(3, OptionalInt.of(0x22)), q.floor(0x10011, 0x10014, 13, 0, 16, 0));
        assertEquals(new CdPlayerTerrain.Result(3, OptionalInt.of(0x22)), q.floor(17, -65516, 13, 0, 16, 0));
    }

    @Test
    void nativeColumn64CanReadTheRetainedBackgroundHalf() {
        var f = new Fixture(); f.bg[2] = 2; f.fillChunk(2, 0x2001); f.shape(1, 1, 0x66, 8, 8);
        var p = assertInstanceOf(CdTerrainCollision.Available.class,
                CdTerrainCollision.fromStageTerrain(f.terrain()).lookup(0x4000, 0, 0));
        assertEquals(new CdTerrainCollision.Address(0x40, 2, 2, 512), p.address());
        assertEquals(0x2001, p.word().rawWord());
    }

    @Test
    void sourceWordArithmeticKeepsFlaggedLowByteAndWordRotates() {
        assertEquals(new CdTerrainCollision.Address(0, 0x80, 0, 0xFE01), CdTerrainCollision.addressOf(0, 0, 0x80, 0));
        assertEquals(new CdTerrainCollision.Address(0, 0xFF, 128, 0xFFFE), CdTerrainCollision.addressOf(0xF8, 0xF8, 0xFF, 0x40));
        assertEquals(new CdTerrainCollision.Address(0, 0x80, 1, 0), CdTerrainCollision.addressOf(0, 0, 0x80, 0x40));
    }

    @Test
    void sameImmutableQueriesIgnoreLaterSourceArrayChangesAndPublishNoInputWrites() {
        var f = ordinary(8, 8, 0x22); var t = f.terrain(); var q = CdPlayerTerrain.fromStageTerrain(t);
        byte[] chunks = t.chunks().copy(), ids = t.collision().ids().copy(), heights = t.collision().heights().copy();
        Arrays.fill(f.chunks, (byte) 0); Arrays.fill(f.ids, (byte) 0); Arrays.fill(f.heights, (byte) 0);
        assertEquals(new CdPlayerTerrain.Result(3, OptionalInt.of(0x22)), q.floor(17, 20, 13, 0, 16, 0));
        assertEquals(new CdPlayerTerrain.Result(3, OptionalInt.of(0x22)), q.floor(17, 20, 13, 0, 16, 0));
        assertArrayEquals(chunks, t.chunks().copy()); assertArrayEquals(ids, t.collision().ids().copy());
        assertArrayEquals(heights, t.collision().heights().copy());
    }

    @Test
    void groundCompositionAlignsIntegerPositionAndPreservesBothFractions() {
        var f = ordinary(8, 8, 0); var old = new CdPlayerGroundCollision.State(0x0040ABCD, 0x00201234, 0, 0, 3);
        var input = new CdPlayerGroundCollision.Input(old, 9, 19, 0, 0);
        assertEquals(new CdPlayerGroundCollision.Result(
                new CdPlayerGroundCollision.State(0x0040ABCD, 0x00241234, 0, 0, 3), 0, 0),
                CdPlayerGroundCollision.fromStageTerrain(f.terrain()).ground(input));
        assertSame(old, input.state());
    }

    @Test
    void objectSupportBypassesEvenAnUnavailablePastProbe() {
        var f = new Fixture(); f.fg[2] = (byte) 0x80;
        var old = new CdPlayerGroundCollision.State(0x00008000, 0x00001234, 0xC1, 0xA8, 0x55);
        assertEquals(new CdPlayerGroundCollision.Result(old, 0, 0),
                CdPlayerGroundCollision.fromStageTerrain(f.terrain()).ground(
                        new CdPlayerGroundCollision.Input(old, 0xFF, 0x80, 0, 0)));
    }

    @Test
    void groundFailureReturnsNoPartialActorOrAngleResult() {
        var f = new Fixture(); f.fg[2] = (byte) 0x80;
        var old = new CdPlayerGroundCollision.State(0x00008000, 0x00001234, 0, 0, 3);
        var input = new CdPlayerGroundCollision.Input(old, 0, 0, 0, 0);
        assertThrows(IllegalStateException.class, () -> CdPlayerGroundCollision.fromStageTerrain(f.terrain()).ground(input));
        assertSame(old, input.state()); assertEquals(0x00001234, old.yPosition());
    }

    @Test
    void sharedPresentFormatAndTheOldFacadeKeepTheSameLiteralQueries() {
        byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        fg[0] = 41; fg[1] = 7; fg[2] = 1; bg[0] = 5; bg[1] = 7;
        for (int at = 0; at < 512; at += 2) { chunks[at] = 0x20; chunks[at + 1] = 1; }
        ids[1] = 1; angles[1] = 0x22;
        Arrays.fill(heights, 16, 32, (byte) 8); Arrays.fill(widths, 16, 32, (byte) 8);
        byte[] blocks = new byte[6432], patterns = new byte[25600];
        var common = CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PRESENT,
                blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        var legacy = CdR11aTerrain.decode(blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        var expected = new CdPlayerTerrain.Result(3, OptionalInt.of(0x22));
        assertEquals(expected, CdPlayerTerrain.fromStageTerrain(common).floor(17, 20, 13, 0, 16, 0));
        assertEquals(expected, new CdPlayerTerrain(legacy).floor(17, 20, 13, 0, 16, 0));
        assertEquals(new CdTerrainCollision(legacy).lookup(16, 20, 0),
                CdTerrainCollision.fromStageTerrain(common).lookup(16, 20, 0));
        var input = new CdPlayerGroundCollision.Input(
                new CdPlayerGroundCollision.State(0x0040ABCD, 0x00201234, 0, 0, 3), 9, 19, 0, 0);
        var grounded = new CdPlayerGroundCollision.Result(
                new CdPlayerGroundCollision.State(0x0040ABCD, 0x00241234, 0x22, 0, 3), 0x22, 0x22);
        assertEquals(grounded, new CdPlayerGroundCollision(legacy).ground(input));
        assertEquals(grounded, CdPlayerGroundCollision.fromStageTerrain(common).ground(input));
    }

    @Test
    void pastFormatDoesNotWeakenTheStrictPresentFacadeOrAcceptForeignExtents() {
        var f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> CdR11aTerrain.decode(
                f.blocks, f.patterns, f.chunks, f.fg, f.bg, f.ids, f.angles, f.heights, f.widths));
        assertThrows(IllegalArgumentException.class, () -> CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PRESENT,
                f.blocks, f.patterns, f.chunks, f.fg, f.bg, f.ids, f.angles, f.heights, f.widths));
        assertThrows(IllegalArgumentException.class, () -> CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PAST,
                new byte[6432], f.patterns, f.chunks, f.fg, f.bg, f.ids, f.angles, f.heights, f.widths));
    }

    @Test
    void oldQueryGuardsStillRunBeforeAnyUnavailableProbeOrAngleStore() {
        var f = new Fixture(); f.fg[2] = (byte) 0x80;
        var q = CdPlayerTerrain.fromStageTerrain(f.terrain()); var writes = new ArrayList<Integer>();
        assertThrows(IllegalArgumentException.class, () -> q.floor(0, 0, -1, 0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> q.wall(0, 0, 16, 0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> q.floorWithPrefix(0, 0, 16, 0, 16, 0, writes::add));
        assertThrows(NullPointerException.class, () -> q.floorWithPrefix(0, 0, 16, 0, 16, 0, null));
        assertThrows(NullPointerException.class, () -> q.wallWithPrefix(0, 0, 16, 0, 16, 0, null));
        assertTrue(writes.isEmpty());
    }

    private static Fixture ordinary(int height, int width, int angle) {
        var f = new Fixture(); f.fg[2] = 1; f.fillChunk(1, 0x2001); f.shape(1, 1, angle, height, width); return f;
    }

    private static final class Fixture {
        final byte[] blocks = new byte[5280], patterns = new byte[23872], chunks = new byte[54272];
        final byte[] fg = new byte[170], bg = new byte[22], ids = new byte[800], angles = new byte[256];
        final byte[] heights = new byte[4096], widths = new byte[4096];
        Fixture() { fg[0] = 41; fg[1] = 3; bg[0] = 9; bg[1] = 1; }
        void fillChunk(int id, int word) {
            int first = (id - 1) * 512;
            for (int i = first; i < first + 512; i += 2) { chunks[i] = (byte) (word >>> 8); chunks[i + 1] = (byte) word; }
        }
        void shape(int block, int id, int angle, int height, int width) {
            ids[block] = (byte) id; angles[id] = (byte) angle;
            Arrays.fill(heights, id * 16, id * 16 + 16, (byte) height);
            Arrays.fill(widths, id * 16, id * 16 + 16, (byte) width);
        }
        CdStageTerrain terrain() {
            return CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PAST, blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        }
    }
}

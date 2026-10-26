package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.soniccd.level.CdTerrainCollision.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal expectations from GetLevelBlock, scddisasm 1ecc5f3, Collision Floor.asm:467-666. */
class TestCdTerrainCollision {
    @Test
    void coordinatesUseNativeWordsAndCombinedLayoutRamWithoutDimensionWrapping() {
        Fixture f = new Fixture();
        f.fg[2] = 2;
        f.fg[2 + 41] = 3;
        f.fg[2 + 7 * 42] = 4;
        f.bg[2] = 5;
        f.bg[2 + 7 * 6 + 5] = 6;
        var lookup = new CdTerrainCollision(f.decode());

        // x, y, native layout offset, original layout byte, effective chunk, byte offset.
        // Cleared cells return the base sentinel before calculating a local block offset.
        int[][] cases = {
                {0, 0, 0, 2, 2, 0x0200},
                {0x10000, 0x10000, 0, 2, 2, 0x0200},
                {-0x10000, -0x10000, 0, 2, 2, 0x0200},
                {0x8000, 0x8000, 0, 2, 2, 0x0200},
                {Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 2, 2, 0x0200},
                {0x10010, 0x10020, 0, 2, 2, 0x0242},
                {-0xFFF0, -0xFFE0, 0, 2, 2, 0x0242},
                {0x80000010, 0xFFFF0020, 0, 2, 2, 0x0242},
                {0, 0x1000, 0, 2, 2, 0x0200},
                {0x2900, 0, 0x029, 3, 3, 0x0400},
                {0x2A00, 0, 0x02A, 0, 0, 0},
                {0x4000, 0, 0x040, 5, 5, 0x0800},
                {0x45F0, 0x07F0, 0x3C5, 6, 6, 0x0BFE},
                {0x4600, 0, 0x046, 0, 0, 0},
                {0x7F00, 0, 0x07F, 0, 0, 0},
                {0, 0x0700, 0x380, 4, 4, 0x0600},
                {0, 0x0800, 0x400, 0, 0, 0},
                {0, 0x0F00, 0x780, 0, 0, 0},
                {-1, -1, 0x7FF, 0, 0, 0},
                {Integer.MAX_VALUE, Integer.MAX_VALUE, 0x7FF, 0, 0, 0}
        };
        for (int[] c : cases) {
            Placement result = lookup.lookup(c[0], c[1], 0);
            assertEquals(new Address(c[2], c[3], c[4], c[5]), result.address(), Arrays.toString(c));
            if (c[3] == 0) {
                assertInstanceOf(Blank.class, result);
            } else {
                assertInstanceOf(Available.class, result);
            }
        }
    }

    @Test
    void computedBasePointerIsBlankEvenWhenChunkOneContainsAnOriginalWord() {
        Fixture f = new Fixture();
        f.fg[2] = 1;
        word(f.chunks, 0, 0x7B23);
        word(f.chunks, 0x1FE, 0x2002);
        var lookup = new CdTerrainCollision(f.decode());

        var base = assertInstanceOf(Blank.class, lookup.lookup(0, 0, 0x40));
        assertEquals(new Address(0, 1, 1, 0), base.address());
        assertEquals(0x210000, base.address().nativeAddress());

        // An available original zero word is distinct from the pointer sentinel.
        var zeroWord = assertInstanceOf(Available.class, lookup.lookup(0x10, 0, 0));
        assertEquals(2, zeroWord.address().chunkByteOffset());
        assertEquals(0, zeroWord.word().rawWord());
        var last = assertInstanceOf(Available.class, lookup.lookup(0xF0, 0xF0, 0));
        assertEquals(0x1FE, last.address().chunkByteOffset());
        assertEquals(0x2002, last.word().rawWord());

        var cleared = assertInstanceOf(Blank.class, lookup.lookup(0x1F0, 0xF0, 0));
        assertEquals(new Address(1, 0, 0, 0), cleared.address());
    }

    @Test
    void baseSentinelMatchesAllExistingR11aOriginalWordRamProfiles() {
        var blank = new CdTerrainCollision(new Fixture().decode()).lookup(0, 0, 0);
        for (var variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            assertEquals(0x210000, profile.range(CdR11aAsset.LEVEL_CHUNKS).nativeAddress());
            assertEquals(profile.range(CdR11aAsset.LEVEL_CHUNKS).nativeAddress(),
                    blank.address().nativeAddress());
        }
    }

    @Test
    void oneBasedChunk121HasARealLastWordButItsHighPathRemapHasNoSuppliedBacking() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xF9;
        word(f.chunks, 0xF000, 0x2001);
        word(f.chunks, 0xF1FE, 0x2222);
        var lookup = new CdTerrainCollision(f.decode());

        var first = assertInstanceOf(Available.class, lookup.lookup(0, 0, 0));
        assertEquals(new Address(0, 0xF9, 121, 0xF000), first.address());
        assertEquals(0x2001, first.word().rawWord());
        var last = assertInstanceOf(Available.class, lookup.lookup(0xFF, 0xFF, 0));
        assertEquals(0xF1FE, last.address().chunkByteOffset());
        assertEquals(0x21F1FE, last.address().nativeAddress());
        assertEquals(0x2222, last.word().rawWord());

        var outside = assertInstanceOf(Unavailable.class, lookup.lookup(0, 0, 0x40));
        assertEquals(new Address(0, 0xF9, 122, 0xF200), outside.address());
        assertEquals(UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS, outside.reason());
        var outsideLast = assertInstanceOf(Unavailable.class, lookup.lookup(0xFF, 0xFF, 0x40));
        assertEquals(0xF3FE, outsideLast.address().chunkByteOffset());
        assertEquals(UnavailableReason.OUTSIDE_SUPPLIED_CHUNKS, outsideLast.reason());
    }

    @Test
    void ppLoopPolicyReadsOnlyQueryingActorsBitSixAndRemapsAfterIncrement() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0xA7;
        f.fg[3] = (byte) 0xA8;
        f.fg[4] = (byte) 0xA9;
        f.fg[5] = 0x28;
        word(f.chunks, 0x4C00, 0x2101); // native chunk $27
        word(f.chunks, 0x4E00, 0x2102); // native chunk $28
        word(f.chunks, 0x5000, 0x2103); // native chunk $29
        word(f.chunks, 0x5200, 0x2104); // native chunk $2A, not a layout reference
        word(f.chunks, 0xA000, 0x2105); // native chunk $51, not a layout reference
        var terrain = f.decode();
        var lookup = new CdTerrainCollision(terrain);
        byte[] layoutBefore = terrain.layout().ram().copy();
        byte[] chunksBefore = terrain.chunks().copy();
        byte[] idsBefore = terrain.collision().ids().copy();

        assertPlacement(lookup.lookup(0, 0, 0x40), 0x28, 0x4E00, 0x2102);
        assertPlacement(lookup.lookup(0x100, 0, 0), 0x28, 0x4E00, 0x2102);
        assertPlacement(lookup.lookup(0x100, 0, 0xBF), 0x28, 0x4E00, 0x2102);
        assertPlacement(lookup.lookup(0x100, 0, 0x40), 0x51, 0xA000, 0x2105);
        assertPlacement(lookup.lookup(0x100, 0, (byte) 0xFF), 0x51, 0xA000, 0x2105);
        assertPlacement(lookup.lookup(0x100, 0, 0x10040), 0x51, 0xA000, 0x2105);
        assertPlacement(lookup.lookup(0x200, 0, 0), 0x29, 0x5000, 0x2103);
        assertPlacement(lookup.lookup(0x200, 0, 0x40), 0x2A, 0x5200, 0x2104);
        assertPlacement(lookup.lookup(0x300, 0, 0x40), 0x28, 0x4E00, 0x2102);
        assertPlacement(lookup.lookup(0x100, 0, 0), 0x28, 0x4E00, 0x2102);
        assertArrayEquals(layoutBefore, terrain.layout().ram().copy());
        assertArrayEquals(chunksBefore, terrain.chunks().copy());
        assertArrayEquals(idsBefore, terrain.collision().ids().copy());
    }

    @Test
    void flaggedZeroIsNeitherLayoutBlankNorSignedExtendedAndCanSelectChunkOne() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0x80;
        word(f.chunks, 0x1FE, 0x2345); // unreferenced by decode's masked zero layout ID
        var lookup = new CdTerrainCollision(f.decode());

        var odd = assertInstanceOf(Unavailable.class, lookup.lookup(0, 0, 0));
        assertEquals(new Address(0, 0x80, 0, 0xFE01), odd.address());
        assertEquals(0x21FE01, odd.address().nativeAddress());
        assertEquals(UnavailableReason.ODD_NATIVE_WORD_ADDRESS, odd.reason());
        var lastOdd = assertInstanceOf(Unavailable.class, lookup.lookup(0xFF, 0xFF, 0));
        assertEquals(0xFFFF, lastOdd.address().chunkByteOffset());
        assertEquals(UnavailableReason.ODD_NATIVE_WORD_ADDRESS, lastOdd.reason());

        var base = assertInstanceOf(Blank.class, lookup.lookup(0, 0, 0x40));
        assertEquals(new Address(0, 0x80, 1, 0), base.address());
        var word = assertInstanceOf(Available.class, lookup.lookup(0xFF, 0xFF, 0x40));
        assertEquals(new Address(0, 0x80, 1, 0x1FE), word.address());
        assertEquals(0x2345, word.word().rawWord());
    }

    @Test
    void literalWordRotationsRetainFlaggedZeroAnd127Edges() {
        // CdR11aTerrain rejects layout IDs >121. Exercise the production address step
        // directly for those byte patterns, without weakening its immutable decoder.
        // x, y, layout byte, actor flags, effective ID, final low word of d1.
        int[][] cases = {
                {0, 0, 0x01, 0, 1, 0},
                {0, 0, 0x02, 0, 2, 0x0200},
                {0, 0, 0x28, 0x40, 0x28, 0x4E00},
                {0, 0, 0x79, 0, 121, 0xF000},
                {0, 0, 0x7F, 0, 127, 0xFC00},
                {0, 0, 0x80, 0, 0, 0xFE01},
                {0xF0, 0xF0, 0x80, 0, 0, 0xFFFF},
                {0, 0, 0x80, 0x40, 1, 0},
                {0xF0, 0xF0, 0x80, 0x40, 1, 0x01FE},
                {0, 0, 0xFF, 0, 127, 0xFC00},
                {0xF0, 0xF0, 0xFF, 0, 127, 0xFDFE},
                {0, 0, 0xFF, 0x40, 128, 0xFE00},
                {0xF0, 0xF0, 0xFF, 0x40, 128, 0xFFFE},
                {0, 0, 0xFE, 0x40, 127, 0xFC00},
                {0, 0, 0xA8, 0x40, 0x51, 0xA000},
                {0, 0, 0xA9, 0x40, 0x2A, 0x5200},
                {0xFFFF, 0xFFFF, 0x80, 0, 0, 0xFFFF},
                {0x8000, 0x8000, 0x02, 0, 2, 0x0200},
                {0x10010, 0x10020, 0x02, 0, 2, 0x0242}
        };
        for (int[] c : cases) {
            var address = CdTerrainCollision.addressOf(c[0], c[1], c[2], c[3]);
            assertEquals(c[2], address.layoutByte(), Arrays.toString(c));
            assertEquals(c[4], address.effectiveChunkId(), Arrays.toString(c));
            assertEquals(c[5], address.chunkByteOffset(), Arrays.toString(c));
        }
    }

    @Test
    void originalWordRetainsFlipsNativeSolidityAndUnusedHighBit() {
        Fixture f = new Fixture();
        f.fg[2] = 1;
        word(f.chunks, 2, 0xFB23);
        var lookup = new CdTerrainCollision(f.decode());

        var found = assertInstanceOf(Available.class, lookup.lookup(0x10, 0, 0));
        assertEquals(0xFB23, found.word().rawWord());
        assertEquals(803, found.word().collisionBlockIndex());
        assertTrue(found.word().flipX());
        assertTrue(found.word().flipY());
        assertEquals(3, found.word().solidity());
    }

    @Test
    void remappedUnreferencedWordsPreserve11BitIdentityWithoutResolvingShapeData() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0x81;
        word(f.chunks, 2, 0x2001);
        word(f.chunks, 0x202, 0x2401);
        word(f.chunks, 0x204, 0x2383); // collision index 899, last supplied byte
        word(f.chunks, 0x206, 0x2384); // collision index 900, first unavailable byte
        word(f.chunks, 0x208, 0x27FC); // drawing 1020, collision 2044: both unloaded
        f.ids[1] = 7;
        f.ids[899] = (byte) 0xFF;
        var lookup = new CdTerrainCollision(f.decode());

        var low = assertInstanceOf(Available.class, lookup.lookup(0x10, 0, 0));
        var high = assertInstanceOf(Available.class, lookup.lookup(0x10, 0, 0x40));
        assertEquals(1, low.word().renderBlockIndex());
        assertEquals(1, high.word().renderBlockIndex());
        assertEquals(1, low.word().collisionBlockIndex());
        assertEquals(1025, high.word().collisionBlockIndex());
        assertEquals(0x2001, low.word().rawWord());
        assertEquals(0x2401, high.word().rawWord());

        var last = assertInstanceOf(Available.class, lookup.lookup(0x20, 0, 0x40));
        assertEquals(899, last.word().collisionBlockIndex());
        var beyond = assertInstanceOf(Available.class, lookup.lookup(0x30, 0, 0x40));
        assertEquals(900, beyond.word().collisionBlockIndex());
        var unloaded = assertInstanceOf(Available.class, lookup.lookup(0x40, 0, 0x40));
        assertEquals(0x27FC, unloaded.word().rawWord());
        assertEquals(2044, unloaded.word().collisionBlockIndex());
        // Placement identity does not include collision-map contents. The solver
        // resolves them only after selecting its original-word solidity bit.
        f.ids[1] = 8;
        f.ids[899] = 9;
        var changedMap = new CdTerrainCollision(f.decode());
        assertEquals(low, changedMap.lookup(0x10, 0, 0));
        assertEquals(last, changedMap.lookup(0x20, 0, 0x40));
    }

    @Test
    void lookupsAndRetainedResultsAreDeterministicAcrossOtherActorsAndInputMutation() {
        Fixture f = new Fixture();
        f.fg[2] = (byte) 0x81;
        word(f.chunks, 2, 0x2001);
        word(f.chunks, 0x202, 0x2002);
        f.ids[1] = 3;
        f.ids[2] = 4;
        var lookup = new CdTerrainCollision(f.decode());
        var first = lookup.lookup(0x10, 0, 0);
        var secondActor = lookup.lookup(0x10, 0, 0x40);
        assertPlacement(first, 1, 2, 0x2001);
        assertPlacement(secondActor, 2, 0x202, 0x2002);
        Arrays.fill(f.fg, (byte) 0xFF);
        Arrays.fill(f.chunks, (byte) 0xFF);
        Arrays.fill(f.ids, (byte) 0xFF);
        assertEquals(first, lookup.lookup(0x10, 0, 0));
        assertEquals(secondActor, lookup.lookup(0x10, 0, 0x40));
    }

    private static void assertPlacement(Placement result, int chunk, int offset, int rawWord) {
        var available = assertInstanceOf(Available.class, result);
        assertEquals(chunk, available.address().effectiveChunkId());
        assertEquals(offset, available.address().chunkByteOffset());
        assertEquals(rawWord, available.word().rawWord());
    }

    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    private static final class Fixture {
        final byte[] blocks = new byte[6432];
        final byte[] patterns = new byte[25600];
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Fixture() {
            fg[0] = 41;
            fg[1] = 7;
            bg[0] = 5;
            bg[1] = 7;
        }

        CdR11aTerrain decode() {
            return CdR11aTerrain.decode(blocks, patterns, chunks, fg, bg, ids, angles, heights, widths);
        }
    }
}

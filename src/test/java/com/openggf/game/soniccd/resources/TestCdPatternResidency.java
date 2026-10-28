package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed format witnesses only: no original assets, native producer or captured runtime. */
class TestCdPatternResidency {
    @Test
    void spanBoundsAreFiniteAlignedAndCannotWrap() {
        assertEquals(new CdPatternResidency.Span(0, 32), new CdPatternResidency.Span(0, 32));
        assertEquals(32, new CdPatternResidency.Span(0xFFE0, 32).byteLength());
        int[][] invalid = {{-32,32},{0x10000,32},{1,32},{0,0},{0,-32},{0,31},
                {0,0x10020},{0xFFE0,64},{0x7FFFFFFF,32},{0,0x7FFFFFFF}};
        for (int[] c : invalid) assertThrows(IllegalArgumentException.class,
                () -> new CdPatternResidency.Span(c[0], c[1]));
    }

    @Test
    void profileRejectsOverlapButPreservesUnsortedLegacyOrdinals() {
        var high = new CdPatternResidency.Span(0x7400, 64);
        var low = new CdPatternResidency.Span(0x6400, 32);
        var input = new CdPatternResidency.Span[]{high,low};
        var profile = new CdPatternResidency.Profile(input);
        input[0] = low;
        assertSame(high, profile.span(0)); assertSame(low, profile.span(1));
        assertEquals(0, profile.locate(0x7420)); assertEquals(1, profile.locate(0x6400));
        assertThrows(IllegalArgumentException.class, () -> new CdPatternResidency.Profile(high,high));
        assertThrows(IllegalArgumentException.class, () -> new CdPatternResidency.Profile(
                high,new CdPatternResidency.Span(0x7420,64)));
        assertThrows(IllegalArgumentException.class, () -> new CdPatternResidency.Profile());
        assertThrows(NullPointerException.class, () -> new CdPatternResidency.Profile((CdPatternResidency.Span[])null));
        assertThrows(NullPointerException.class, () -> new CdPatternResidency.Profile(high,null));
        assertThrows(IndexOutOfBoundsException.class, () -> profile.span(2));
    }

    @Test
    void adjacentRegionsStaySeparateAndOutsideNeverBecomesAnIndex() {
        var p = new CdPatternResidency.Profile(new CdPatternResidency.Span(0x6400,32),
                new CdPatternResidency.Span(0x6420,32));
        assertEquals(0,p.locate(0x6400)); assertEquals(1,p.locate(0x6420));
        var fault = assertThrows(CdPatternResidency.Fault.class, () -> p.locate(0x6440));
        assertEquals(CdPatternResidency.Boundary.OUTSIDE_OWNED_WINDOW,fault.boundary());
        assertEquals(0x6440,fault.destinationWord());
    }

    @Test
    void unknownBytesAreCanonicalYetRemainUnreadable() {
        byte[] dirty = filled(32,0xA5), flags = new byte[32];
        flags[0] = 1; flags[31] = 1;
        var window = new CdPatternResidency.Window(32,dirty,flags);
        dirty[0] = 0; flags[1] = 1;
        assertEquals(OptionalInt.of(0xA5),window.knownByte(0));
        assertEquals(OptionalInt.empty(),window.knownByte(1));
        assertEquals(OptionalInt.of(0xA5),window.knownByte(31));
        byte[] canonical = new byte[32]; canonical[0]=(byte)0xA5;canonical[31]=(byte)0xA5;
        byte[] mask = new byte[32];mask[0]=1;mask[31]=1;
        assertEquals(new CdPatternResidency.Window(32,canonical,mask),window);
        assertEquals(new CdPatternResidency.Window(32,canonical,mask).hashCode(),window.hashCode());
        assertThrows(IndexOutOfBoundsException.class, () -> window.knownByte(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> window.knownByte(32));
    }

    @Test
    void masksAndCompleteWindowShapesRejectMalformedInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new CdPatternResidency.Window(32,new byte[31],new byte[32]));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPatternResidency.Window(32,new byte[32],new byte[31]));
        for (int value : new int[]{2,0x7F,0x80,0xFF}) {
            byte[] mask = new byte[32];mask[15]=(byte)value;
            assertThrows(IllegalArgumentException.class,
                    () -> new CdPatternResidency.Window(32,new byte[32],mask));
        }
    }

    @Test
    void profileValueRequiresEveryExactWindowAndBorrowsOnlyImmutableValues() {
        var p = new CdPatternResidency.Profile(new CdPatternResidency.Span(0,32));
        var window = new CdPatternResidency.Window(32,filled(32,0x81),filled(32,1));
        var supplied = new CdPatternResidency.Window[]{window};
        var value = new CdPatternResidency.Value(p,supplied);supplied[0]=null;
        assertSame(window,value.window(0));
        assertThrows(IllegalArgumentException.class, () -> new CdPatternResidency.Value(p,new CdPatternResidency.Window[0]));
        assertThrows(IllegalArgumentException.class, () -> new CdPatternResidency.Value(p,
                new CdPatternResidency.Window[]{new CdPatternResidency.Window(64,new byte[64],new byte[64])}));
        assertThrows(NullPointerException.class, () -> new CdPatternResidency.Value(p,new CdPatternResidency.Window[]{null}));
        assertThrows(NullPointerException.class, () -> new CdPatternResidency.Value(null,supplied));
        assertThrows(IndexOutOfBoundsException.class, () -> value.window(1));
    }

    @Test
    void unknownKnownZeroAndProfileIdentityAreDistinctValues() {
        var p = new CdPatternResidency.Profile(new CdPatternResidency.Span(0,32));
        var unknown = CdPatternResidency.unknown(p);
        var cleared = CdPatternResidency.clear(unknown);
        assertNotEquals(unknown,cleared);
        assertEquals(CdPatternResidency.unknown(new CdPatternResidency.Profile(
                new CdPatternResidency.Span(0,32))),unknown);
        assertNotEquals(CdPatternResidency.unknown(new CdPatternResidency.Profile(
                new CdPatternResidency.Span(32,32))),unknown);
        assertEquals(CdPatternResidency.clear(unknown).hashCode(),cleared.hashCode());
        assertEquals(OptionalInt.empty(),unknown.window(0).knownByte(0));
        assertArrayEquals(new byte[32],CdPatternResidency.readTile(cleared,0).copy());
        assertThrows(CdPatternResidency.Fault.class, () -> CdPatternResidency.readTile(unknown,0));
    }

    @Test
    void oneTileUsesTheSamePhysicalValueAndRetainsUnchangedWindowIdentities() {
        var p = new CdPatternResidency.Profile(new CdPatternResidency.Span(0x6400,64),
                new CdPatternResidency.Span(0x7400,32));
        var before = CdPatternResidency.unknown(p);
        var one = CdPatternResidency.writeTile(before,0x6420,bytes(filled(32,0x80)));
        var two = CdPatternResidency.writeTile(one,0x6420,bytes(filled(32,0xFF)));
        assertSame(before.window(1),one.window(1));assertSame(one.window(1),two.window(1));
        assertEquals(OptionalInt.empty(),one.window(0).knownByte(0));
        assertArrayEquals(filled(32,0x80),CdPatternResidency.readTile(one,0x321).copy());
        assertArrayEquals(filled(32,0xFF),CdPatternResidency.readTile(two,0x321).copy());
        assertEquals(OptionalInt.empty(),before.window(0).knownByte(32));
    }

    @Test
    void explicitClearTouchesOnlyTheAdmittedProfileAndRetainsOlderValues() {
        var p = new CdPatternResidency.Profile(new CdPatternResidency.Span(0x6400,64),
                new CdPatternResidency.Span(0x7400,32));
        var before = CdPatternResidency.writeTile(CdPatternResidency.unknown(p),0x6400,bytes(filled(32,0xA7)));
        var after = CdPatternResidency.clear(before);
        for (int id : new int[]{0x320,0x321,0x3A0}) assertArrayEquals(new byte[32],CdPatternResidency.readTile(after,id).copy());
        assertArrayEquals(filled(32,0xA7),CdPatternResidency.readTile(before,0x320).copy());
        assertThrows(CdPatternResidency.Fault.class, () -> CdPatternResidency.readTile(after,0x322));
        assertEquals(after,CdPatternResidency.clear(after));
    }

    @Test
    void malformedTileAndStateChecksPrecedeOwnership() {
        var s = CdPatternResidency.unknown(new CdPatternResidency.Profile(new CdPatternResidency.Span(0x6400,32)));
        assertEquals("state",assertThrows(NullPointerException.class,
                () -> CdPatternResidency.writeTile(null,-1,null)).getMessage());
        assertEquals("Tile destination must be an unsigned word aligned to $20",
                assertThrows(IllegalArgumentException.class, () -> CdPatternResidency.writeTile(s,-1,null)).getMessage());
        assertEquals("tile32",assertThrows(NullPointerException.class,
                () -> CdPatternResidency.writeTile(s,0x6500,null)).getMessage());
        assertEquals("tile32 must contain exactly 32 bytes",assertThrows(IllegalArgumentException.class,
                () -> CdPatternResidency.writeTile(s,0x6500,bytes(new byte[31]))).getMessage());
        assertEquals(CdPatternResidency.Boundary.OUTSIDE_OWNED_WINDOW,assertThrows(CdPatternResidency.Fault.class,
                () -> CdPatternResidency.writeTile(s,0x6500,bytes(new byte[32]))).boundary());
        assertEquals("state",assertThrows(NullPointerException.class, () -> CdPatternResidency.readTile(null,-1)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> CdPatternResidency.readTile(s,0x800));
    }

    @Test
    void legacyFacadeRetainsItsExactNullWidthAndOwnershipFailureOrder() {
        var s = CdR11aCameraArtResidency.devSeed(bytes(new byte[2240]),bytes(new byte[896]),bytes(new byte[2016]));
        assertEquals("state",assertThrows(NullPointerException.class,
                () -> CdR11aCameraArtResidency.writeTile(null,-1,null)).getMessage());
        assertEquals("Tile destination must be an unsigned word aligned to $20",
                assertThrows(IllegalArgumentException.class, () -> CdR11aCameraArtResidency.writeTile(s,-1,null)).getMessage());
        assertEquals("tile32",assertThrows(NullPointerException.class,
                () -> CdR11aCameraArtResidency.writeTile(s,0x6500,null)).getMessage());
        assertEquals("tile32 must contain exactly 32 bytes",assertThrows(IllegalArgumentException.class,
                () -> CdR11aCameraArtResidency.writeTile(s,0x6500,bytes(new byte[33]))).getMessage());
        assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,
                assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                        () -> CdR11aCameraArtResidency.writeTile(s,0x6500,bytes(new byte[32]))).boundary());
        assertEquals("state",assertThrows(NullPointerException.class, () -> CdR11aCameraArtResidency.readTile(null,-1)).getMessage());
        assertEquals("mosqui2240",assertThrows(NullPointerException.class,
                () -> CdR11aCameraArtResidency.devSeed(null,null,null)).getMessage());
    }

    @Test
    void legacyHashAndUnchangedSnapshotIdentityKeepTheirHistoricalContract() {
        var s = CdR11aCameraArtResidency.knownSeed(bytes(filled(2240,0x81)),bytes(filled(704,0x92)),bytes(filled(2944,0xA3)));
        int expectedState = 1;
        for (var r : CdR11aCameraArtResidency.Region.values()) {
            byte[] values = new byte[r.byteLength()], mask = new byte[r.byteLength()];
            if (r.ordinal() < 3) { Arrays.fill(values,(byte)(0x81 + 0x11*r.ordinal()));Arrays.fill(mask,(byte)1); }
            int regionHash = 31 * r.hashCode() + Arrays.hashCode(values);
            regionHash = 31 * regionHash + Arrays.hashCode(mask);
            assertEquals(regionHash,s.region(r).hashCode());expectedState=31*expectedState+regionHash;
        }
        assertEquals(expectedState,s.hashCode());
        var changed=CdR11aCameraArtResidency.writeTile(s,0x8120,bytes(filled(32,0xB4)));
        for(var r:CdR11aCameraArtResidency.Region.values()) {
            if(r!=CdR11aCameraArtResidency.Region.AT_8120)assertSame(s.region(r),changed.region(r));
        }
        assertArrayEquals(filled(32,0x92),CdR11aCameraArtResidency.readTile(s,0x409).copy());
        assertArrayEquals(filled(32,0xB4),CdR11aCameraArtResidency.readTile(changed,0x409).copy());
    }

    private static CdAssetBytes bytes(byte[] b) { return CdAssetBytes.copyOf(b); }
    private static byte[] filled(int length,int value) {
        byte[] b=new byte[length];Arrays.fill(b,(byte)value);return b;
    }
}

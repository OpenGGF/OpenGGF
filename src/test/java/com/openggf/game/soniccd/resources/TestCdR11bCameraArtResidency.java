package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal native destination/alias format fixtures; no actual native clear, art decode or World. */
class TestCdR11bCameraArtResidency {
    private static final int[][] SPANS = {{0x0000,0x5D40},{0x6400,0x6860},{0x68C0,0x6B20},
            {0x6B40,0xA160},{0xA400,0xA880},{0xAD00,0xB060},{0xB420,0xB4A0},
            {0xB500,0xBF40},{0xD000,0xD960},{0xDAE0,0xDFC0},{0xF5C0,0xF780}};

    @Test
    void literalElevenSpanProfileOwnsExactly1510NonPlayerTiles() {
        var regions=Region.values();assertEquals(11,regions.length);int tiles=0;
        for(int i=0;i<11;i++) {
            assertEquals(i,regions[i].ordinal());assertEquals(SPANS[i][0],regions[i].destinationWord());
            assertEquals(SPANS[i][1]-SPANS[i][0],regions[i].byteLength());
            assertEquals((SPANS[i][1]-SPANS[i][0])/32,regions[i].tileCount());tiles+=regions[i].tileCount();
            if(i!=0)assertTrue(SPANS[i-1][1]<SPANS[i][0]);
        }
        assertEquals(1510,tiles);
    }

    @Test
    void unknownConstructionKeepsEveryByteUnavailableAndStableSnapshots() {
        var s=unknown();
        for(var r:Region.values()) {
            assertSame(s.region(r),s.region(r));assertArrayEquals(new byte[r.byteLength()],s.region(r).knownMask().copy());
            for(int i=0;i<r.byteLength();i++)assertEquals(OptionalInt.empty(),s.region(r).knownByte(i));
            for(int address=r.destinationWord();address<r.destinationWord()+r.byteLength();address+=32)
                assertBoundary(s,address,Boundary.UNKNOWN_PATTERN);
        }
        assertEquals(unknown(),s);assertEquals(unknown().hashCode(),s.hashCode());
    }

    @Test
    void realGapsAndTheExistingSonicWindowRemainOutsideEvenAfterClear() {
        var before=unknown();var cleared=clear(before);
        int[] outside={0x5D40,0x63E0,0x6860,0x68A0,0x6B20,0xA160,0xA3E0,0xA880,
                0xACE0,0xB060,0xB400,0xB4A0,0xB4E0,0xBF40,0xCFE0,0xD960,0xDAC0,
                0xDFC0,0xF000,0xF2E0,0xF300,0xF5A0,0xF780,0xFFE0};
        for(var s:new State[]{before,cleared})for(int address:outside) {
            assertBoundary(s,address,Boundary.OUTSIDE_OWNED_WINDOW);
            var fault=assertThrows(OwnershipBoundary.class,()->writeTile(s,address,tile(0x5A)));
            assertEquals(Boundary.OUTSIDE_OWNED_WINDOW,fault.boundary());assertEquals(address,fault.destinationWord());
        }
    }

    @Test
    void eachFirstAndLastTileCommitPreservesAllOtherMasksBytesAndSnapshots() {
        var initial=unknown();
        for(var r:Region.values())for(int address:new int[]{r.destinationWord(),r.destinationWord()+r.byteLength()-32}) {
            int fill=0x80+r.ordinal();var after=writeTile(initial,address,tile(fill));
            assertSingle(initial,after,address,filled(32,fill));
            assertArrayEquals(filled(32,fill),readTile(after,address>>>5).copy());
            for(var other:Region.values())if(other!=r)assertSame(initial.region(other),after.region(other));
            assertBoundary(initial,address,Boundary.UNKNOWN_PATTERN);
        }
    }

    @Test
    void explicitClearMarksAll48320OwnedBytesKnownZeroWithoutChangingIngress() {
        var initial=unknown();var result=clear(initial);int bytes=0;
        for(var r:Region.values()) {
            byte[] mask=filled(r.byteLength(),1);assertArrayEquals(mask,result.region(r).knownMask().copy());
            for(int offset=0;offset<r.byteLength();offset++)assertEquals(OptionalInt.of(0),result.region(r).knownByte(offset));
            for(int address=r.destinationWord();address<r.destinationWord()+r.byteLength();address+=32)
                assertArrayEquals(new byte[32],readTile(result,address>>>5).copy());
            assertArrayEquals(new byte[r.byteLength()],initial.region(r).knownMask().copy());bytes+=r.byteLength();
        }
        assertEquals(48320,bytes);assertNotEquals(initial,result);
    }

    @Test
    void explicitClearReplacesDirtyValuesYetRetainsTheEarlierImmutablePrefix() {
        var initial=writeTile(writeTile(unknown(),0,tile(0xA1)),0xB420,tile(0xB2));
        var after=clear(initial);
        assertArrayEquals(new byte[32],readTile(after,0).copy());assertArrayEquals(new byte[32],readTile(after,0x5A1).copy());
        assertArrayEquals(filled(32,0xA1),readTile(initial,0).copy());assertArrayEquals(filled(32,0xB2),readTile(initial,0x5A1).copy());
        for(var r:Region.values())assertArrayEquals(filled(r.byteLength(),1),after.region(r).knownMask().copy());
        assertBoundary(after,0xF000,Boundary.OUTSIDE_OWNED_WINDOW);
    }

    @Test
    void clearIsAValueTransformNotImplicitSourceOrExecutionAuthority() {
        var once=clear(unknown());var twice=clear(once);
        assertEquals(once,twice);assertEquals(once.hashCode(),twice.hashCode());
        assertNotSame(once,twice);assertNotEquals(unknown(),once);
        assertThrows(NullPointerException.class,()->clear(null));
        assertEquals(OptionalInt.of(0),once.region(Region.AT_B420).knownByte(0));
    }

    @Test
    void knownZeroTileAndUnknownBackingZeroAreDifferentEvenBeforeWholeClear() {
        var before=unknown();var after=writeTile(before,0xB420,tile(0));
        assertNotEquals(before,after);assertArrayEquals(new byte[32],readTile(after,0x5A1).copy());
        assertBoundary(before,0xB420,Boundary.UNKNOWN_PATTERN);assertBoundary(after,0xB440,Boundary.UNKNOWN_PATTERN);
        assertSingle(before,after,0xB420,new byte[32]);
        var same=writeTile(unknown(),0xB420,tile(0));assertEquals(after,same);assertEquals(after.hashCode(),same.hashCode());
    }

    @Test
    void titleCardPixelsSurviveTheNativeCameraProducerGaps() {
        var title=commit(unknown(),0x6C00,109,0xA1);
        var disc=commit(title,0x6E40,12,0xB2);
        var springboard=commit(disc,0x7020,21,0xC3);
        byte[] expected=new byte[0x3620],known=new byte[0x3620];
        fill(expected,known,0x6B40,0x6C00,0x79A0,0xA1);
        fill(expected,known,0x6B40,0x6E40,0x6FC0,0xB2);
        fill(expected,known,0x6B40,0x7020,0x72C0,0xC3);
        assertRegion(springboard,Region.AT_6B40,expected,known);
        for(int address=0x6FC0;address<0x7020;address+=32)assertTile(springboard,address,0xA1);
        for(int address=0x72C0;address<0x73C0;address+=32)assertTile(springboard,address,0xA1);
        assertTile(title,0x6E40,0xA1);assertBoundary(title,0x6BE0,Boundary.UNKNOWN_PATTERN);
        for(var r:Region.values())if(r!=Region.AT_6B40)assertSame(title.region(r),springboard.region(r));
    }

    @Test
    void titleTextLogSceneryAndAntonShareOneCurrentValueWithTheirRealUntouchedGaps() {
        var title=commit(commit(unknown(),0x6C00,109,0xA1),0x7A00,52,0xA4);
        var log=commit(title,0x7960,16,0xB5);
        var scenery=commit(log,0x7B60,17,0xC6);
        var anton=commit(scenery,0x7DA0,22,0xD7);
        byte[] expected=new byte[0x3620],known=new byte[0x3620];
        fill(expected,known,0x6B40,0x6C00,0x79A0,0xA1);
        fill(expected,known,0x6B40,0x7A00,0x8080,0xA4);
        fill(expected,known,0x6B40,0x7960,0x7B60,0xB5);
        fill(expected,known,0x6B40,0x7B60,0x7D80,0xC6);
        fill(expected,known,0x6B40,0x7DA0,0x8060,0xD7);
        assertRegion(anton,Region.AT_6B40,expected,known);
        assertTile(anton,0x7D80,0xA4);assertTile(anton,0x8060,0xA4);
        assertTile(title,0x7A00,0xA4);assertTile(log,0x7A00,0xB5);
        assertBoundary(anton,0x8080,Boundary.UNKNOWN_PATTERN);
    }

    @Test
    void core746TilesUseTheFiniteDeclaredRangeWithoutOwningTheFollowingGap() {
        var before=unknown();var after=commit(before,0,746,0x22);
        assertRegion(after,Region.AT_0000,filled(23872,0x22),filled(23872,1));
        assertTile(after,0,0x22);assertTile(after,0x5D20,0x22);assertBoundary(after,0x5D40,Boundary.OUTSIDE_OWNED_WINDOW);
        for(var r:Region.values())if(r!=Region.AT_0000)assertSame(before.region(r),after.region(r));
        assertBoundary(before,0,Boundary.UNKNOWN_PATTERN);
    }

    @Test
    void lifeIconCommitsExactlyFourTilesBetweenHudAndMonitorWithoutBorrowingTheirBanks() {
        var before=commit(commit(unknown(),0xAD00,27,0x31),0xB500,82,0x42);
        var s=before;
        for(int tile=0;tile<4;tile++)s=writeTile(s,0xB420+tile*32,tile(0x10+tile));
        byte[] expected=new byte[128];
        for(int tile=0;tile<4;tile++)Arrays.fill(expected,tile*32,tile*32+32,(byte)(0x10+tile));
        assertRegion(s,Region.AT_B420,expected,filled(128,1));
        assertSame(before.region(Region.AT_AD00),s.region(Region.AT_AD00));
        assertSame(before.region(Region.AT_B500),s.region(Region.AT_B500));
        assertTile(s,0xAD00,0x31);assertTile(s,0xB500,0x42);assertBoundary(s,0xB4A0,Boundary.OUTSIDE_OWNED_WINDOW);
        assertBoundary(before,0xB420,Boundary.UNKNOWN_PATTERN);
    }

    @Test
    void firstUnownedSuccessorCannotEraseTheCompletedThirtyFiveTilePrefix() {
        var complete=commit(unknown(),0x6400,35,0x6A);
        var captured=complete;
        assertThrows(OwnershipBoundary.class,()->writeTile(captured,0x6860,tile(0xFF)));
        assertRegion(captured,Region.AT_6400,filled(1120,0x6A),filled(1120,1));
        assertBoundary(captured,0x6860,Boundary.OUTSIDE_OWNED_WINDOW);assertBoundary(unknown(),0x6840,Boundary.UNKNOWN_PATTERN);
    }

    @Test
    void tileWritesNeverWrapAtTheFinalProfileEndOrUnsignedWordBoundary() {
        var before=writeTile(unknown(),0xF760,tile(0x91));
        for(int address:new int[]{0xF780,0xFFE0})assertThrows(OwnershipBoundary.class,()->writeTile(before,address,tile(0x92)));
        for(int address:new int[]{-32,0x10000,0x10020,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,
                ()->writeTile(before,address,tile(0x92)));
        assertTile(before,0xF760,0x91);assertBoundary(before,0,Boundary.UNKNOWN_PATTERN);
    }

    @Test
    void readRejectsUnstrippedAttributesAndInvalidNativeIdWidthsBeforeLookingUpOwnership() {
        var s=unknown();
        for(int id:new int[]{-1,0x800,0x1000,0x2000,0xFFFF,Integer.MIN_VALUE,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->readTile(s,id));
        assertEquals("state",assertThrows(NullPointerException.class,()->readTile(null,0x800)).getMessage());
        assertBoundary(s,0xF000,Boundary.OUTSIDE_OWNED_WINDOW);
    }

    @Test
    void destinationAndTileShapeChecksKeepTheNativeFacadeOrder() {
        var s=unknown();
        assertEquals("state",assertThrows(NullPointerException.class,()->writeTile(null,-1,null)).getMessage());
        for(int at:new int[]{-1,1,0xB421,0x10000})assertThrows(IllegalArgumentException.class,()->writeTile(s,at,null));
        assertEquals("tile32",assertThrows(NullPointerException.class,()->writeTile(s,0xA880,null)).getMessage());
        for(int n:new int[]{0,31,33})assertThrows(IllegalArgumentException.class,
                ()->writeTile(s,0xA880,CdAssetBytes.copyOf(new byte[n])));
        assertThrows(OwnershipBoundary.class,()->writeTile(s,0xA880,tile(1)));
        assertEquals(unknown(),s);
    }

    @Test
    void immutableSourceSlicesCopiesAndMasksCannotEditACommittedValue() {
        byte[] input=filled(96,0xA5);var source=CdAssetBytes.copyOf(input).slice(32,32);
        var s=writeTile(unknown(),0xB420,source);input[32]=0;
        byte[] returned=readTile(s,0x5A1).copy();returned[0]=0;
        byte[] mask=s.region(Region.AT_B420).knownMask().copy();mask[0]=0;
        assertTile(s,0xB420,0xA5);assertEquals(OptionalInt.of(0xA5),s.region(Region.AT_B420).knownByte(0));
        assertEquals(1,s.region(Region.AT_B420).knownMask().unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class,()->readTile(s,0x5A1).buffer().put(0,(byte)0));
        assertThrows(ReadOnlyBufferException.class,()->s.region(Region.AT_B420).knownMask().buffer().put(0,(byte)0));
    }

    @Test
    void regionByteQueriesKeepExactBoundsAndDoNotTurnUnknownIntoZero() {
        var s=unknown();
        assertThrows(NullPointerException.class,()->s.region(null));
        for(var r:Region.values()) {
            assertThrows(IndexOutOfBoundsException.class,()->s.region(r).knownByte(-1));
            assertThrows(IndexOutOfBoundsException.class,()->s.region(r).knownByte(r.byteLength()));
            assertEquals(OptionalInt.empty(),s.region(r).knownByte(r.byteLength()-1));
        }
    }

    @Test
    void disjointWritesCommuteAsValuesAndEqualStatesHaveEqualHashes() {
        var a=writeTile(writeTile(unknown(),0x0000,tile(0x11)),0xB420,tile(0x22));
        var b=writeTile(writeTile(unknown(),0xB420,tile(0x22)),0x0000,tile(0x11));
        assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode());
        assertEquals(a.region(Region.AT_B420),b.region(Region.AT_B420));
        assertEquals(a.region(Region.AT_B420).hashCode(),b.region(Region.AT_B420).hashCode());
        assertNotEquals(a,unknown());assertNotEquals(a,null);assertNotEquals(a,new Object());
    }

    @Test
    void replayFromAnImmutableCapturedValueDoesNotReseedEarlierPhysicalAliases() {
        var title=commit(unknown(),0x6C00,109,0xA1);
        var captured=commit(title,0x6E40,12,0xB2);
        var first=commit(captured,0x7020,21,0xC3);
        var replay=commit(captured,0x7020,21,0xC3);
        assertEquals(first,replay);assertEquals(first.hashCode(),replay.hashCode());
        assertTile(captured,0x7020,0xA1);assertTile(first,0x7020,0xC3);
        assertTile(first,0x6FC0,0xA1);assertTile(first,0x6E40,0xB2);
    }

    @Test
    void latestSameAddressCommitWinsWithoutAProducerLabelOrASecondBank() {
        var first=writeTile(unknown(),0x7A00,tile(0x13));
        var second=writeTile(first,0x7A00,tile(0x24));
        var third=writeTile(second,0x7A00,tile(0x35));
        assertTile(first,0x7A00,0x13);assertTile(second,0x7A00,0x24);assertTile(third,0x7A00,0x35);
        assertSingle(second,third,0x7A00,filled(32,0x35));
        assertEquals(third,writeTile(unknown(),0x7A00,tile(0x35)));
    }

    @Test
    void legacyPresentOwnershipAndSeedUnknownRulesDoNotExpandWithThePastProfile() {
        var a=CdR11aCameraArtResidency.knownSeed(CdAssetBytes.copyOf(new byte[2240]),
                CdAssetBytes.copyOf(new byte[704]),CdAssetBytes.copyOf(new byte[2944]));
        assertEquals(9,CdR11aCameraArtResidency.Region.values().length);
        for(int address:new int[]{0,0x6500,0xB420,0xB500,0xF5C0}) {
            var fault=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                    ()->CdR11aCameraArtResidency.writeTile(a,address,tile(0)));
            assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,fault.boundary());
            assertTile(writeTile(unknown(),address,tile(0xE4)),address,0xE4);
        }
        for(var r:CdR11aCameraArtResidency.Region.values())if(r.ordinal()>=3)
            assertArrayEquals(new byte[r.byteLength()],a.region(r).knownMask().copy());
        assertNotEquals(a,unknown());assertNotEquals(unknown(),a);
    }

    private static State commit(State s,int address,int count,int value) {
        for(int i=0;i<count;i++)s=writeTile(s,address+i*32,tile(value));return s;
    }
    private static void assertTile(State s,int address,int value) {
        assertArrayEquals(filled(32,value),readTile(s,address>>>5).copy());
    }
    private static CdAssetBytes tile(int value) { return CdAssetBytes.copyOf(filled(32,value)); }
    private static byte[] filled(int length,int value) { byte[] b=new byte[length];Arrays.fill(b,(byte)value);return b; }
    private static void assertBoundary(State s,int address,Boundary reason) {
        var f=assertThrows(OwnershipBoundary.class,()->readTile(s,address>>>5));
        assertEquals(reason,f.boundary());assertEquals(address,f.destinationWord());assertEquals(address>>>5,f.nativePatternId());
    }
    private static void fill(byte[] values,byte[] known,int base,int start,int end,int value) {
        Arrays.fill(values,start-base,end-base,(byte)value);Arrays.fill(known,start-base,end-base,(byte)1);
    }
    private static void assertRegion(State s,Region r,byte[] values,byte[] known) {
        var snap=s.region(r);assertEquals(r,snap.region());assertArrayEquals(known,snap.knownMask().copy());
        for(int i=0;i<values.length;i++)assertEquals(known[i]==0?OptionalInt.empty():OptionalInt.of(values[i]&255),snap.knownByte(i),
                "region="+r+",byte="+i);
    }
    private static void assertSingle(State before,State after,int address,byte[] tile) {
        for(var r:Region.values()) {
            var snap=before.region(r);
            if(address<r.destinationWord()||address>=r.destinationWord()+r.byteLength()) {
                assertSame(snap,after.region(r));continue;
            }
            byte[] values=new byte[r.byteLength()],known=snap.knownMask().copy();
            for(int i=0;i<values.length;i++)if(known[i]!=0)values[i]=(byte)snap.knownByte(i).orElseThrow();
            int at=address-r.destinationWord();System.arraycopy(tile,0,values,at,32);Arrays.fill(known,at,at+32,(byte)1);
            assertRegion(after,r,values,known);
        }
    }
}

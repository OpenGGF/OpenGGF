package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.resources.CdR12aCameraArtResidency.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN. Literal original-USA R12A metadata/value oracles, N1ecc5f3a.
 * The complete35-art/36-destination rows below come from the final resource-closure
 * inventory. Native selected HUD/life writes supplement the union; no native clear,
 * decoded art, PLC producer, source lease, renderer, capture lifetime or World is supplied.
 */
class TestCdR12aCameraArtResidency {
    private static final int[][] SPANS = {{0x0000,0x6560},{0x6600,0x6800},{0x69C0,0xA1E0},
            {0xA400,0xA880},{0xAD00,0xB4E0},{0xB500,0xBF40},{0xD000,0xDFC0},{0xF5C0,0xF780}};
    private record NativeSpan(String source, int start, int byteLength) { }
    private static final NativeSpan[] PLC = {
            new NativeSpan("Art_Animals",0x71E0,0x280),
            new NativeSpan("Art_Anton",0x7B60,0x2C0),
            new NativeSpan("Art_Anton",0x8000,0x2C0),
            new NativeSpan("Art_BigRing",0x9100,0xD40),
            new NativeSpan("Art_BigRingFlash",0x7DE0,0x9A0),
            new NativeSpan("Art_Boulder",0x6D80,0x260),
            new NativeSpan("Art_Checkpoint",0xD960,0x180),
            new NativeSpan("Art_DiagonalSpring",0x9200,0x5C0),
            new NativeSpan("Art_Explosions",0xD000,0x8C0),
            new NativeSpan("Art_FloatBlock",0x6B40,0x1E0),
            new NativeSpan("Art_Flower",0xDAE0,0x4E0),
            new NativeSpan("Art_GoalPost",0x9EE0,0x300),
            new NativeSpan("Art_HUD",0xAD00,0x360),
            new NativeSpan("Art_LevelTiles",0x0000,0x6560),
            new NativeSpan("Art_LogShadow",0x7E00,0x200),
            new NativeSpan("Art_MonitorTimePosts",0xB500,0xA40),
            new NativeSpan("Art_Mosqui",0x74E0,0x8C0),
            new NativeSpan("Art_PataBata",0x74E0,0x640),
            new NativeSpan("Art_Platform",0x97C0,0x720),
            new NativeSpan("Art_Points",0xD8C0,0xA0),
            new NativeSpan("Art_Results",0x7880,0xF00),
            new NativeSpan("Art_Rings",0xF5C0,0x1C0),
            new NativeSpan("Art_RobotGenWithered",0x8000,0x1E0),
            new NativeSpan("Art_Signpost",0x8780,0x980),
            new NativeSpan("Art_Spikes",0x6600,0x100),
            new NativeSpan("Art_SpinningDisc",0x7000,0x180),
            new NativeSpan("Art_SpringWheel",0x6700,0x100),
            new NativeSpan("Art_Springboard",0x7B60,0x2A0),
            new NativeSpan("Art_Springs",0xA400,0x480),
            new NativeSpan("Art_Swing",0x8300,0x1C0),
            new NativeSpan("Art_TagaTaga",0x7B60,0x5A0),
            new NativeSpan("Art_Tamabboh",0x8580,0x760),
            new NativeSpan("Art_TitleCard",0x6C00,0xDA0),
            new NativeSpan("Art_TitleCardText",0x7A00,0x680),
            new NativeSpan("Art_TunnelDoor",0x69C0,0x180),
            new NativeSpan("Art_TunnelDoorSplash",0x74E0,0xAC0)
    };
    // Shared native Main599-614 and HUD197-716, included by Act2 Present19/57.
    // ResetScore uses seven64-byte slots; ordinary score uses six within that range.
    private static final NativeSpan[] DIRECT = {
            new NativeSpan("HUD reset score",0xB060,7*64),
            new NativeSpan("HUD score",0xB060,6*64),
            new NativeSpan("HUD minutes",0xB220,64),
            new NativeSpan("HUD seconds",0xB260,2*64),
            new NativeSpan("HUD centiseconds",0xB2E0,2*64),
            new NativeSpan("HUD rings",0xB360,3*64),
            new NativeSpan("LoadLifeIcon",0xB420,32*4),
            new NativeSpan("HUD lives",0xB4A0,64),
            new NativeSpan("HUD time bonus for act0001",0x8780,5*64),
            new NativeSpan("HUD ring bonus for act0001",0x88C0,5*64)
    };

    @Test void fullNativeMetadataUnionIsExactlyEightDisjointRegions1597TilesAnd51104Bytes() {
        assertEquals(36,PLC.length); assertEquals(35,Arrays.stream(PLC).map(NativeSpan::source).distinct().count());
        assertEquals(2,Arrays.stream(PLC).filter(x->x.source().equals("Art_Anton")).count());
        var expected=new BitSet(2048); for(var x:PLC)mark(expected,x.start(),x.byteLength());
        for(var x:DIRECT)mark(expected,x.start(),x.byteLength());
        var actual=new BitSet(2048); var regions=Region.values(); assertEquals(8,regions.length); int bytes=0;
        for(int i=0;i<regions.length;i++) {
            var r=regions[i]; assertEquals(i,r.ordinal()); assertEquals(SPANS[i][0],r.destinationWord());
            assertEquals(SPANS[i][1]-SPANS[i][0],r.byteLength()); assertEquals(r.byteLength()/32,r.tileCount());
            if(i>0)assertTrue(SPANS[i-1][1]<r.destinationWord());
            mark(actual,r.destinationWord(),r.byteLength()); bytes+=r.byteLength();
        }
        assertEquals(expected,actual); assertEquals(1597,actual.cardinality()); assertEquals(51104,bytes);
        assertEquals(0,actual.get(0xF000>>>5,0xF300>>>5).cardinality(),"Existing Sonic owner is excluded");
    }

    @Test void unknownConstructionRetainsEveryOwnedByteAsUnknownWithStableReadOnlySnapshots() {
        var s=unknown();
        for(var r:Region.values()) {
            assertSame(s.region(r),s.region(r)); assertArrayEquals(new byte[r.byteLength()],s.region(r).knownMask().copy());
            for(int i=0;i<r.byteLength();i++)assertEquals(OptionalInt.empty(),s.region(r).knownByte(i));
            assertBoundary(s,r.destinationWord(),Boundary.UNKNOWN_PATTERN);
            assertBoundary(s,r.destinationWord()+r.byteLength()-32,Boundary.UNKNOWN_PATTERN);
        }
        assertEquals(unknown(),s); assertEquals(unknown().hashCode(),s.hashCode());
    }

    @Test void explicitNativeClearValueMarksExactlyTheProfileKnownZeroWithoutExecutingOrMutatingIngress() {
        var before=withTile(withTile(unknown(),0,tile(0x81)),0xF760,tile(0x92)); var after=clear(before);
        for(var r:Region.values()) {
            assertArrayEquals(filled(r.byteLength(),1),after.region(r).knownMask().copy());
            for(int offset=0;offset<r.byteLength();offset++)assertEquals(OptionalInt.of(0),after.region(r).knownByte(offset));
            for(int at=r.destinationWord();at<r.destinationWord()+r.byteLength();at+=32)assertTile(after,at,0);
        }
        assertTile(before,0,0x81); assertTile(before,0xF760,0x92); assertBoundary(before,0x20,Boundary.UNKNOWN_PATTERN);
        assertNotEquals(unknown(),after); assertEquals(after,clear(after)); assertEquals(after.hashCode(),clear(after).hashCode());
        assertNotSame(after,clear(after)); assertThrows(NullPointerException.class,()->clear(null));
    }

    @Test void eachRegionFirstAndLastTileCommitPreservesEveryOtherByteMaskAndSnapshot() {
        var before=unknown();
        for(var r:Region.values())for(int at:new int[]{r.destinationWord(),r.destinationWord()+r.byteLength()-32}) {
            var after=withTile(before,at,tile(0x80+r.ordinal())); assertSingle(before,after,at,filled(32,0x80+r.ordinal()));
            for(var other:Region.values())if(other!=r)assertSame(before.region(other),after.region(other));
            assertBoundary(before,at,Boundary.UNKNOWN_PATTERN); assertTile(after,at,0x80+r.ordinal());
        }
    }

    @Test void titleAndAllEnemySplashAliasesShareOnePhysicalValueAndRetainActualProducerTails() {
        var title=commit(unknown(),0x6C00,3488,0xA1);
        var splash=commit(title,0x74E0,2752,0xB2);
        var mosqui=commit(splash,0x74E0,2240,0xC3);
        var pata=commit(mosqui,0x74E0,1600,0xD4);
        var anton=commit(pata,0x7B60,704,0xE5);
        byte[] expected=new byte[0x3820],known=new byte[0x3820];
        fill(expected,known,0x69C0,0x6C00,3488,0xA1);
        fill(expected,known,0x69C0,0x74E0,2752,0xB2);
        fill(expected,known,0x69C0,0x74E0,2240,0xC3);
        fill(expected,known,0x69C0,0x74E0,1600,0xD4);
        fill(expected,known,0x69C0,0x7B60,704,0xE5);
        assertRegion(anton,Region.AT_69C0,expected,known);
        assertTile(anton,0x7B20,0xC3); assertTile(anton,0x7E20,0xB2); assertTile(anton,0x7F80,0xB2);
        assertBoundary(anton,0x7FA0,Boundary.UNKNOWN_PATTERN); assertTile(title,0x74E0,0xA1); assertTile(splash,0x74E0,0xB2);
        for(var r:Region.values())if(r!=Region.AT_69C0)assertSame(title.region(r),anton.region(r));
    }

    @Test void resultsFlashAndSelectedHudBonusWritesUseTheSameNativeAddressesBeforeSignpostReplacement() {
        var results=commit(unknown(),0x7880,3840,0x11); var flash=commit(results,0x7DE0,2464,0x22);
        var bonus=commit(flash,0x8780,5*64,0x33); bonus=commit(bonus,0x88C0,5*64,0x44);
        var signpost=commit(bonus,0x8780,2432,0x55);
        byte[] expected=new byte[0x3820],known=new byte[0x3820];
        fill(expected,known,0x69C0,0x7880,3840,0x11); fill(expected,known,0x69C0,0x7DE0,2464,0x22);
        fill(expected,known,0x69C0,0x8780,2432,0x55); assertRegion(signpost,Region.AT_69C0,expected,known);
        assertTile(bonus,0x8780,0x33); assertTile(bonus,0x88C0,0x44); assertBoundary(bonus,0x8A00,Boundary.UNKNOWN_PATTERN);
        assertTile(results,0x7DE0,0x11); assertTile(flash,0x7DE0,0x22); assertTile(signpost,0x90E0,0x55);
        assertBoundary(signpost,0x9100,Boundary.UNKNOWN_PATTERN);
    }

    @Test void hudLifeIconAndLivesFormOneContiguousPhysicalRegionWithoutPreloadingItsNeighbours() {
        var initial=unknown(); var hud=commit(initial,0xAD00,864,0x21); var s=commit(hud,0xB060,7*64,0x32);
        s=commit(s,0xB220,5*64,0x43); s=commit(s,0xB360,3*64,0x54);
        s=commit(s,0xB420,32*4,0x65); s=commit(s,0xB4A0,64,0x76);
        byte[] expected=new byte[0x7E0],known=filled(0x7E0,1);
        Arrays.fill(expected,0,0x360,(byte)0x21); Arrays.fill(expected,0x360,0x520,(byte)0x32);
        Arrays.fill(expected,0x520,0x660,(byte)0x43); Arrays.fill(expected,0x660,0x720,(byte)0x54);
        Arrays.fill(expected,0x720,0x7A0,(byte)0x65); Arrays.fill(expected,0x7A0,0x7E0,(byte)0x76);
        assertRegion(s,Region.AT_AD00,expected,known); assertBoundary(hud,0xB060,Boundary.UNKNOWN_PATTERN);
        assertBoundary(s,0xB4E0,Boundary.OUTSIDE_OWNED_WINDOW); assertBoundary(s,0xB500,Boundary.UNKNOWN_PATTERN);
        for(var r:Region.values())if(r!=Region.AT_AD00)assertSame(initial.region(r),s.region(r));
    }

    @Test void checkpointOutputClosesItsActualHighBankGapWhileTheUncalledContinueCounterAddsNoOwnership() {
        var s=commit(unknown(),0xD000,2240,0x31); s=commit(s,0xD8C0,160,0x42);
        s=commit(s,0xD960,384,0x53); s=commit(s,0xDAE0,1248,0x64);
        byte[] expected=new byte[0xFC0],known=filled(0xFC0,1);
        Arrays.fill(expected,0,0x8C0,(byte)0x31); Arrays.fill(expected,0x8C0,0x960,(byte)0x42);
        Arrays.fill(expected,0x960,0xAE0,(byte)0x53); Arrays.fill(expected,0xAE0,0xFC0,(byte)0x64);
        assertRegion(s,Region.AT_D000,expected,known); assertTile(s,0xD960,0x53); assertTile(s,0xDFA0,0x64);
        assertBoundary(s,0xDFC0,Boundary.OUTSIDE_OWNED_WINDOW); assertBoundary(s,0xDFE0,Boundary.OUTSIDE_OWNED_WINDOW);
    }

    @Test void everyGapAndAllSonicTilesStayOutsideForUnknownAndExplicitlyClearedValues() {
        var owned=new BitSet(2048); for(var r:Region.values())mark(owned,r.destinationWord(),r.byteLength());
        for(var s:new State[]{unknown(),clear(unknown())})for(int id=0;id<2048;id++)if(!owned.get(id)) {
            int at=id<<5; assertBoundary(s,at,Boundary.OUTSIDE_OWNED_WINDOW);
            var failure=assertThrows(OwnershipBoundary.class,()->withTile(s,at,tile(0xFE)));
            assertEquals(Boundary.OUTSIDE_OWNED_WINDOW,failure.boundary()); assertEquals(at,failure.destinationWord());
        }
        assertFalse(owned.get(0xF000>>>5)); assertFalse(owned.get(0xF2E0>>>5)); assertFalse(owned.get(0xF300>>>5));
    }

    @Test void scalarWidthsAlignmentTileShapeAndNullChecksKeepTheFiniteFacadeOrder() {
        var s=unknown(); assertEquals("state",assertThrows(NullPointerException.class,()->withTile(null,-1,null)).getMessage());
        assertEquals("state",assertThrows(NullPointerException.class,()->readTile(null,0x800)).getMessage());
        for(int at:new int[]{-1,-32,1,0xB421,0x10000,Integer.MIN_VALUE,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->withTile(s,at,null));
        assertEquals("tile32",assertThrows(NullPointerException.class,()->withTile(s,0x6560,null)).getMessage());
        for(int length:new int[]{0,31,33})assertThrows(IllegalArgumentException.class,
                ()->withTile(s,0x6560,CdAssetBytes.copyOf(new byte[length])));
        for(int id:new int[]{-1,0x800,0x1000,0x2000,0xFFFF,Integer.MIN_VALUE,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->readTile(s,id));
        var last=withTile(s,0xF760,tile(0x91)); assertTile(last,0xF760,0x91);
        assertThrows(OwnershipBoundary.class,()->withTile(last,0xF780,tile(0x92)));
        assertThrows(OwnershipBoundary.class,()->withTile(last,0xFFE0,tile(0x92))); assertTile(last,0xF760,0x91);
    }

    @Test void immutableInputSlicesTileExportsAndMasksCannotEditCapturedValues() {
        byte[] input=filled(96,0xA5); var source=CdAssetBytes.copyOf(input).slice(32,32); var s=withTile(unknown(),0xB420,source);
        input[32]=0; byte[] exported=readTile(s,0xB420>>>5).copy(); exported[0]=0;
        byte[] mask=s.region(Region.AT_AD00).knownMask().copy(); mask[0x720]=0;
        assertTile(s,0xB420,0xA5); assertEquals(OptionalInt.of(0xA5),s.region(Region.AT_AD00).knownByte(0x720));
        assertEquals(1,s.region(Region.AT_AD00).knownMask().unsignedByte(0x720));
        assertThrows(ReadOnlyBufferException.class,()->readTile(s,0xB420>>>5).buffer().put(0,(byte)0));
        assertThrows(ReadOnlyBufferException.class,()->s.region(Region.AT_AD00).knownMask().buffer().put(0,(byte)0));
    }

    @Test void regionByteBoundariesAndKnowledgeEqualityNeverTreatUnknownCanonicalZeroAsKnownZero() {
        var before=unknown(); assertThrows(NullPointerException.class,()->before.region(null));
        for(var r:Region.values()) {
            assertThrows(IndexOutOfBoundsException.class,()->before.region(r).knownByte(-1));
            assertThrows(IndexOutOfBoundsException.class,()->before.region(r).knownByte(r.byteLength()));
            assertEquals(OptionalInt.empty(),before.region(r).knownByte(0));
            assertEquals(OptionalInt.empty(),before.region(r).knownByte(r.byteLength()-1));
        }
        var zero=withTile(before,0xB420,tile(0)); var equal=withTile(unknown(),0xB420,tile(0));
        assertNotEquals(before,zero); assertEquals(zero,equal); assertEquals(zero.hashCode(),equal.hashCode());
        assertEquals(zero.region(Region.AT_AD00),equal.region(Region.AT_AD00));
        assertEquals(zero.region(Region.AT_AD00).hashCode(),equal.region(Region.AT_AD00).hashCode());
        assertBoundary(zero,0xB440,Boundary.UNKNOWN_PATTERN); assertSingle(before,zero,0xB420,new byte[32]);
    }

    @Test void disjointWritesCommuteAndReplayRetainsEarlierPhysicalAliasesAsImmutableValues() {
        var first=withTile(withTile(unknown(),0,tile(0x11)),0xB420,tile(0x22));
        var reversed=withTile(withTile(unknown(),0xB420,tile(0x22)),0,tile(0x11));
        assertEquals(first,reversed); assertEquals(first.hashCode(),reversed.hashCode());
        var latest=withTile(first,0xB420,tile(0x33)); var replay=withTile(first,0xB420,tile(0x33));
        assertEquals(latest,replay); assertEquals(latest.hashCode(),replay.hashCode());
        assertSingle(first,latest,0xB420,filled(32,0x33)); assertTile(first,0xB420,0x22); assertTile(latest,0xB420,0x33);
        assertNotEquals(first,null); assertNotEquals(first,new Object());
    }

    @Test void facadeRegionsBorrowTheSingleKernelValueWithoutArtBanksOrASecondBacking() throws Exception {
        assertFalse(Modifier.isPublic(CdPatternResidency.class.getModifiers()));
        var s=withTile(unknown(),0xB420,tile(0x51));
        var field=State.class.getDeclaredField("value"); field.setAccessible(true);
        var backing=(CdPatternResidency.Value)field.get(s);
        var window=RegionSnapshot.class.getDeclaredField("window"); window.setAccessible(true);
        assertEquals(2,Arrays.stream(State.class.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())).count());
        assertEquals(2,Arrays.stream(RegionSnapshot.class.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())).count());
        for(var r:Region.values())assertSame(backing.window(r.ordinal()),window.get(s.region(r)));
    }

    private static void mark(BitSet mask,int start,int bytes) {
        assertEquals(0,start&31); assertEquals(0,bytes&31); assertTrue(bytes>0); assertTrue(start>=0&&start+bytes<=0x10000);
        mask.set(start>>>5,(start+bytes)>>>5);
    }
    private static State commit(State state,int destination,int bytes,int value) {
        assertEquals(0,bytes&31); var result=state;
        for(int at=destination;at<destination+bytes;at+=32)result=withTile(result,at,tile(value));
        return result;
    }
    private static byte[] filled(int size,int value) { byte[] b=new byte[size]; Arrays.fill(b,(byte)value); return b; }
    private static CdAssetBytes tile(int value) { return CdAssetBytes.copyOf(filled(32,value)); }
    private static void fill(byte[] values,byte[] known,int base,int start,int bytes,int value) {
        Arrays.fill(values,start-base,start-base+bytes,(byte)value); Arrays.fill(known,start-base,start-base+bytes,(byte)1);
    }
    private static void assertTile(State state,int destination,int value) { assertArrayEquals(filled(32,value),readTile(state,destination>>>5).copy()); }
    private static void assertBoundary(State state,int destination,Boundary boundary) {
        var failure=assertThrows(OwnershipBoundary.class,()->readTile(state,destination>>>5));
        assertEquals(boundary,failure.boundary()); assertEquals(destination,failure.destinationWord());
        assertEquals(destination>>>5,failure.nativePatternId());
    }
    private static void assertSingle(State before,State after,int destination,byte[] tile) {
        for(var r:Region.values()) {
            byte[] expectedMask=before.region(r).knownMask().copy();
            if(destination>=r.destinationWord()&&destination<r.destinationWord()+r.byteLength())
                Arrays.fill(expectedMask,destination-r.destinationWord(),destination-r.destinationWord()+32,(byte)1);
            assertArrayEquals(expectedMask,after.region(r).knownMask().copy());
            for(int i=0;i<r.byteLength();i++) {
                int address=r.destinationWord()+i; boolean written=address>=destination&&address<destination+32;
                var expected=written?OptionalInt.of(Byte.toUnsignedInt(tile[address-destination])):before.region(r).knownByte(i);
                assertEquals(expected,after.region(r).knownByte(i));
            }
        }
    }
    private static void assertRegion(State state,Region region,byte[] values,byte[] known) {
        assertEquals(region.byteLength(),values.length); assertArrayEquals(known,state.region(region).knownMask().copy());
        for(int i=0;i<values.length;i++)assertEquals(known[i]==0?OptionalInt.empty():OptionalInt.of(Byte.toUnsignedInt(values[i])),state.region(region).knownByte(i));
    }
}

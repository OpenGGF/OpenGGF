package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;
import static com.openggf.game.soniccd.scroll.CdR31aBackgroundScroll.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Constructed complete component values, not a World/Frame/controller ingress.
 * Literal vectors follow N1ecc5f3a CC Scroll199-627 and Scroll Flag Set102-224.
 * Tests authored before production. ALL JAVA/media/native execution UNRUN by author. */
class TestCdR31aBackgroundScroll {
    private static final byte[] COUNTS=HexFormat.of().parseHex("01030101010101010301030303030100");
    private static final byte[] BOUNDS=HexFormat.of().parseHex("003802507fff");

    @Test void finiteSurfaceRetainsOnlyOriginalAssetAndThreeImmutableViews() {
        assertEquals(List.of("fromAssets/1","initialize/2","ownsAssets/1","step/2"),Arrays.stream(CdR31aBackgroundScroll.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        assertEquals(14,State.class.getRecordComponents().length);assertEquals(4,Arrays.stream(CdR31aBackgroundScroll.class.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())).count());var ctor=CdR31aBackgroundScroll.class.getDeclaredConstructors();assertEquals(1,ctor.length);assertTrue(Modifier.isPrivate(ctor[0].getModifiers()));assertEquals(4,ctor[0].getParameterCount());
        for(var f:CdR31aBackgroundScroll.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){assertTrue(Modifier.isPrivate(f.getModifiers()));assertTrue(Modifier.isFinal(f.getModifiers()));assertTrue(f.getType()==CdR31aAssets.class||f.getType()==CdAssetBytes.class);}
        var seam=assertDoesNotThrow(()->CdR31aBackgroundScroll.class.getDeclaredMethod("fromTables",CdAssetBytes.class,CdAssetBytes.class,CdAssetBytes.class));assertFalse(Modifier.isPublic(seam.getModifiers()));
    }
    @Test void originalFactoryBorrowsActualCachedTablesAndRetainsExactAggregate() throws Exception {
        var a=actual();var component=fromAssets(a);assertTrue(component.ownsAssets(a));assertFalse(component.ownsAssets(null));assertFalse(fixture().ownsAssets(a));
        var found=new ArrayList<Object>();for(var f:CdR31aBackgroundScroll.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);found.add(f.get(component));}
        assertEquals(4,found.size());assertTrue(found.stream().anyMatch(v->v==a));for(var id:List.of(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS,CdR31aAssets.Asset.SCROLL_WATER_BOUNDS,CdR31aAssets.Asset.WOBBLE))assertTrue(found.stream().anyMatch(v->v==a.view(id)));
        byte[] copy=a.view(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS).copy();copy[0]=99;assertEquals(1,a.view(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS).unsignedByte(0));
        var result=component.step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0));assertEquals(Boundary.COMPLETE,result.boundary());assertEquals(254,result.deformationWordsWritten());assertEquals(240,result.hscrollLongsWritten());assertEquals(0x40,result.waterDeformWord());assertArrayEquals(new byte[224],result.state().hscrollRam().slice(0,224).copy());
    }
    @Test void originalThreeViewsHaveLiteralFullHashesAndStrictReadBounds() throws Exception {
        var a=actual();assertArrayEquals(COUNTS,a.view(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS).copy());assertArrayEquals(BOUNDS,a.view(CdR31aAssets.Asset.SCROLL_WATER_BOUNDS).copy());
        assertEquals("6dcfe3a302eac5f3c0efe96d992b050b7657c04d7906dcaa6bb49db6a1f5ac90",sha(a.view(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS)));
        assertEquals("677bb2193c2749569097a8311af9320d7269d37ad55c06dac13626006a2d5104",sha(a.view(CdR31aAssets.Asset.SCROLL_WATER_BOUNDS)));
        assertEquals("2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267",sha(a.view(CdR31aAssets.Asset.WOBBLE)));assertEquals(256,a.view(CdR31aAssets.Asset.WOBBLE).length());
        assertThrows(IndexOutOfBoundsException.class,()->a.view(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS).unsignedByte(16));assertThrows(IndexOutOfBoundsException.class,()->a.view(CdR31aAssets.Asset.SCROLL_WATER_BOUNDS).unsignedWord(5));
    }
    @Test void completeInputsRejectOnlyActualUnsignedWordsSignedDifferenceAndByteWidths() {
        new Input(65535,32768,-32768,255,65535);new InitInput(65535,32768);
        for(int at=0;at<5;at++){int field=at;for(int bad:at==2?new int[]{-32769,32768}:at==3?new int[]{-1,256}:new int[]{-1,65536}){int[] v={0,0,0,0,0};v[field]=bad;assertThrows(IllegalArgumentException.class,()->new Input(v[0],v[1],v[2],v[3],v[4]));}}
        assertThrows(IllegalArgumentException.class,()->new InitInput(-1,0));assertThrows(IllegalArgumentException.class,()->new InitInput(0,65536));
    }
    @Test void initializeUsesDistinctUnsignedRatiosAndOnlyFifteenLongClears() {
        var before=dirty();var after=fixture().initialize(before,new InitInput(0x1234,0x38C));byte[] deform=before.deformationRam().copy();Arrays.fill(deform,0,60,(byte)0);
        same(new State(0x00D81234,0x014E0000,0x01B35678,0x014E1357,0x01239ABC,0x014E2468,before.crossingRam(),before.scrollFlagsBg(),before.scrollFlagsBg2(),before.scrollFlagsBg3(),before.cloudRam(),bytes(deform),before.hscrollRam(),before.vscroll()),after);
        assertSame(before.cloudRam(),after.cloudRam());assertSame(before.hscrollRam(),after.hscrollRam());assertSame(before.crossingRam(),after.crossingRam());assertEquals(0x4567ABCD,before.cameraBgY());
    }
    @ParameterizedTest @CsvSource({"0,0","239,0","240,0","241,1","1312,536","1313,536","65535,536"})
    void initializeUnsignedCarryAndSignedClampBranches(int y,int expected) {assertEquals(expected<<16,fixture().initialize(dirty(),new InitInput(0,y)).cameraBgY());}
    @ParameterizedTest @CsvSource({"0,0,0,0","32768,1536,3072,2048","65535,3069,6141,4095"})
    void initializeLogicalShiftsPreserveAllThreeXFractions(int x,int bg,int bg2,int bg3) {var a=fixture().initialize(dirty(),new InitInput(x,0));assertEquals((bg<<16)|0x1234,a.cameraBgX());assertEquals((bg2<<16)|0x5678,a.cameraBg2X());assertEquals((bg3<<16)|0x9ABC,a.cameraBg3X());}
    @Test void lockedEarlyReturnReadsNoTablesAndPreservesActualWaterWordAndStateIdentity() {
        var c=fromTables(bytes(new byte[0]),bytes(new byte[0]),bytes(new byte[0]));var s=dirty();var r=c.step(s,new Input(65535,65535,-32768,0x80,0xFEDC));assertSame(s,r.state());assertEquals(0xFEDC,r.waterDeformWord());assertEquals(Boundary.SCROLL_LOCKED,r.boundary());assertEquals(0,r.deformationWordsWritten());assertEquals(0,r.hscrollLongsWritten());
    }
    @Test void zeroCompleteSliceWrites240LongsAndPreservesBothRealTails() {
        var s=base(0,0,0,0,0,0,new byte[8],0,0,0);var r=fixture().step(s,new Input(0,0,0,0,0));assertEquals(254,r.deformationWordsWritten());assertEquals(240,r.hscrollLongsWritten());assertEquals(0x40,r.waterDeformWord());assertEquals(Boundary.COMPLETE,r.boundary());
        assertEquals(0xAA00,r.state().scrollFlagsBg());assertEquals(0,r.state().scrollFlagsBg2());assertEquals(0,r.state().scrollFlagsBg3());assertArrayEquals(new byte[]{16,16,16,0,16,0,0,0},r.state().crossingRam().copy());
        assertArrayEquals(new byte[960],r.state().hscrollRam().slice(0,960).copy());assertArrayEquals(filled(64,0x5A),r.state().hscrollRam().slice(960,64).copy());assertArrayEquals(filled(4,0x33),r.state().deformationRam().slice(508,4).copy());assertSame(s.cloudRam(),r.state().cloudRam());assertEquals(0,r.state().vscroll());
    }
    @Test void horizontalCrossingsUseNativeLongDeltasAndMergeOnlyHighFlagBytes() {
        byte[] cross={16,16,16,0x66,16,0x55,0x44,0x33};var s=base(0x000FF800,0,0x000FEC00,0,0x000FF400,0,cross,0x12CD,0x34EF,0x56AB);var r=fixture().step(s,new Input(0,0,256,0,0)).state();assertEquals(0x00100400,r.cameraBgX());assertEquals(0x00100400,r.cameraBg2X());assertEquals(0x00100400,r.cameraBg3X());assertEquals(0xFECD,r.scrollFlagsBg());assertEquals(0xEF,r.scrollFlagsBg2());assertEquals(0xAB,r.scrollFlagsBg3());assertArrayEquals(new byte[]{0,16,0,0x66,0,0x55,0x44,0x33},r.crossingRam().copy());
    }
    @Test void negativeCrossingsSetLeftBitsAndPreservePadding() {
        byte[] cross={0,16,0,0x66,0,0x55,0x44,0x33};var r=fixture().step(base(0x00100400,0,0x00100400,0,0x00100400,0,cross,0,0,0),new Input(0,0,-256,0,0)).state();assertEquals(0x000FF800,r.cameraBgX());assertEquals(0x000FEC00,r.cameraBg2X());assertEquals(0x000FF400,r.cameraBg3X());assertEquals(0x5400,r.scrollFlagsBg());assertArrayEquals(new byte[]{16,16,16,0x66,16,0x55,0x44,0x33},r.crossingRam().copy());
    }
    @Test void longPositionWrapStillUsesThePositiveSourceDeltaForFlags() {
        byte[] cross={0,16,0,0,0,0,0,0};var r=fixture().step(base(0xFFFFFFF0,0,0xFFFFFFF0,0,0xFFFFFFF0,0,cross,0,0,0),new Input(0,0,2,0,0)).state();assertEquals(8,r.cameraBgX());assertEquals(32,r.cameraBg2X());assertEquals(16,r.cameraBg3X());assertEquals(0xA800,r.scrollFlagsBg());
    }
    @Test void dirtyCrossingByteSuppressesEqualityRatherThanBeingMaskedToBitFour() {
        var r=fixture().step(base(0,0,0,0,0,0,new byte[]{1,1,1,2,1,3,4,5},0,0,0),new Input(0,0,0,0,0)).state();assertEquals(0,r.scrollFlagsBg());assertArrayEquals(new byte[]{1,1,1,2,1,3,4,5},r.crossingRam().copy());
    }
    @Test void yWordStoresKeepAllFractionsAndVscrollUsesCompletedForegroundWord() {
        var r=fixture().step(dirty(),new Input(0x4321,0x38C,0,0,0)).state();assertEquals(0x014EABCD,r.cameraBgY());assertEquals(0x014E1357,r.cameraBg2Y());assertEquals(0x014E2468,r.cameraBg3Y());assertEquals(0x038C014E,r.vscroll());
    }
    @Test void verticalDirectionUsesWrappedSignedWordNotUnsignedAbsoluteComparison() {
        byte[] cross={1,0,1,0,1,0,0,0};var r=fixture().step(base(0,0x80000011,0,0,0,0,cross,0,0,0),new Input(0,0,0,0,0)).state();assertEquals(0x0100,r.scrollFlagsBg());assertEquals(0x11,r.cameraBgY());assertEquals(16,r.crossingRam().unsignedByte(1));
    }
    @Test void accumulatorLongWrapAndAllSixtyFourCurveLongsRemainRaw() {
        var s=base(0,0,0,0,0,0,new byte[8],0,0,0);byte[] d=s.deformationRam().copy();put(d,0,0xFFFF8000);put(d,60,0xFFFF8000);put(d,312,0xFFFF0000);s=deform(s,d);var r=fixture().step(s,new Input(0,0,0,0,0));assertEquals(0x4000,r.state().deformationRam().unsignedLong(0));assertEquals(0,r.state().deformationRam().unsignedLong(60));assertEquals(0x7C00,r.state().deformationRam().unsignedLong(312));assertEquals(0x33333333L,r.state().deformationRam().unsignedLong(508));
    }
    @Test void firstPerspectiveRetainsTheEarlierNegativeDeltaScratchFraction() {
        var r=fixture().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(1,0,-1,0,0));assertEquals(1,r.state().deformationRam().unsignedWord(0x148));assertEquals(0,r.state().deformationRam().unsignedWord(0x146));assertEquals(0,r.state().deformationRam().unsignedWord(0x13C));for(int at=0x1D8;at<=0x1EA;at+=2)assertEquals(1,r.state().deformationRam().unsignedWord(at));
    }
    @Test void positiveDivsOverflowLeavesNumeratorBeforeExtWordInBothPerspectives() {
        var r=fixture().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0x7FFF,0,0,0,0));assertArrayEquals(HexFormat.of().parseHex("0006000500040003000200010000"),r.state().deformationRam().slice(0x13C,14).copy());assertArrayEquals(HexFormat.of().parseHex("0000000100020003000400050006000700080009"),r.state().deformationRam().slice(0x1D8,20).copy());
    }
    @Test void negativeDivsOverflowExtendsUnchangedNumeratorLowWordZero() {
        var r=fixture().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0x8000,0,0,0,0));assertArrayEquals(new byte[14],r.state().deformationRam().slice(0x13C,14).copy());assertArrayEquals(new byte[20],r.state().deformationRam().slice(0x1D8,20).copy());
    }
    @ParameterizedTest @CsvSource({"0,240","241,239","254,233","908,234","1312,240","65535,240"})
    void finiteNativeRowsRetainUnwrittenHscrollTail(int cameraY,int rows) {var r=fixture().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,cameraY,0,0,0));assertEquals(rows,r.hscrollLongsWritten());assertArrayEquals(filled(1024-rows*4,0x5A),r.state().hscrollRam().slice(rows*4,1024-rows*4).copy());}
    @Test void interpolationReadsSignedAdjacentWobbleBytesAndKeepsForegroundHalf() {
        var r=wobble().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(16,0,0,0,0xFFC0));assertEquals(0,r.waterDeformWord());assertEquals(0xFFF00080L,r.state().hscrollRam().unsignedLong(56*4));assertEquals(0xFFF0FF81L,r.state().hscrollRam().unsignedLong(57*4));assertEquals(0xFFF00002L,r.state().hscrollRam().unsignedLong(58*4));
    }
    @Test void finalWaterRowsReadEvenWobbleOffsetsForForegroundAndSeparateCurveForBackground() {
        var r=wobble().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(16,0x520,0,0,0));assertEquals(0x00700000L,r.state().hscrollRam().unsignedLong(168*4));assertEquals(0xFFF20000L,r.state().hscrollRam().unsignedLong(169*4));assertEquals(240,r.hscrollLongsWritten());
    }
    @Test void waterPhaseUsesHighByteOfWholeIncrementedWordIncludingLowByteCarry() {var r=wobble().step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0xC0));assertEquals(0x100,r.waterDeformWord());assertEquals(0xFF81,r.state().hscrollRam().unsignedLong(56*4));}
    @Test void reachedRepeatReadFaultRetainsEarlierDeformationPrefixWithoutWaterOrHscrollWrites() {
        var c=fromTables(bytes(new byte[0]),bytes(BOUNDS),bytes(new byte[256]));var s=base(0,0,0,0,0,0,new byte[8],0,0,0);var f=assertThrows(ScrollFault.class,()->c.step(s,new Input(0,0,0,0,0x1234)));assertEquals(Access.REPEAT_COUNTS,f.access());assertEquals(14,f.byteOffset());assertEquals(1,f.bytes());assertEquals(172,f.prefix().deformationWordsWritten());assertEquals(0x1234,f.prefix().waterDeformWord());assertEquals(0,f.prefix().hscrollLongsWritten());assertArrayEquals(s.hscrollRam().copy(),f.prefix().state().hscrollRam().copy());assertEquals(Boundary.STOPPED,f.prefix().boundary());assertEquals(0xAA00,f.prefix().state().scrollFlagsBg());
    }
    @Test void unusedSixteenthRepeatByteIsNotReadByConstructedFormatSlice() {var c=fromTables(bytes(Arrays.copyOf(COUNTS,15)),bytes(BOUNDS),bytes(new byte[256]));assertEquals(240,c.step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0)).hscrollLongsWritten());assertFalse(c.ownsAssets(null));}
    @Test void excessConstructedRepeatCountStopsAtFirstOwnedDeformationWordBoundary() {
        byte[] counts=COUNTS.clone();counts[14]=(byte)255;var c=fromTables(bytes(counts),bytes(BOUNDS),bytes(new byte[256]));var f=assertThrows(ScrollFault.class,()->c.step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0x1234)));assertEquals(Access.DEFORMATION_WRITE,f.access());assertEquals(512,f.byteOffset());assertEquals(2,f.bytes());assertEquals(256,f.prefix().deformationWordsWritten());assertEquals(0x1234,f.prefix().waterDeformWord());assertEquals(0,f.prefix().hscrollLongsWritten());
    }
    @Test void waterBoundReadOccursAfterDeformationAndActualWaterWordIncrement() {
        var c=fromTables(bytes(COUNTS),bytes(new byte[0]),bytes(new byte[256]));var f=assertThrows(ScrollFault.class,()->c.step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0xFFC0)));assertEquals(Access.WATER_BOUNDS,f.access());assertEquals(0,f.byteOffset());assertEquals(2,f.bytes());assertEquals(254,f.prefix().deformationWordsWritten());assertEquals(0,f.prefix().waterDeformWord());assertEquals(0,f.prefix().hscrollLongsWritten());
    }
    @Test void missingSecondBoundStopsAfterFirstOriginalTwentyFourLineInterpolation() {var c=fromTables(bytes(COUNTS),bytes(Arrays.copyOf(BOUNDS,2)),bytes(new byte[256]));var f=assertThrows(ScrollFault.class,()->c.step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0)));assertEquals(Access.WATER_BOUNDS,f.access());assertEquals(2,f.byteOffset());assertEquals(80,f.prefix().hscrollLongsWritten());}
    @Test void firstActualWobbleReadKeepsFiftySixCompletedHscrollLongs() {
        var c=fromTables(bytes(COUNTS),bytes(BOUNDS),bytes(new byte[0]));var f=assertThrows(ScrollFault.class,()->c.step(base(0,0,0,0,0,0,new byte[8],0,0,0),new Input(0,0,0,0,0)));assertEquals(Access.WOBBLE,f.access());assertEquals(0,f.byteOffset());assertEquals(56,f.prefix().hscrollLongsWritten());assertEquals(0x40,f.prefix().waterDeformWord());assertArrayEquals(new byte[224],f.prefix().state().hscrollRam().slice(0,224).copy());assertArrayEquals(filled(800,0x5A),f.prefix().state().hscrollRam().slice(224,800).copy());
    }
    @Test void pureRepeatedCallsNeverRetainHistoryOrMutateAnyCompleteInputByte() {var c=fixture();var before=dirty();var input=new Input(0x1234,0x38C,-1,0,0x1234);var a=c.step(before,input);var b=c.step(before,input);same(dirty(),before);same(a.state(),b.state());assertEquals(a.waterDeformWord(),b.waterDeformWord());byte[] copy=a.state().deformationRam().copy();copy[0]^=1;same(a.state(),b.state());assertThrows(java.nio.ReadOnlyBufferException.class,()->a.state().hscrollRam().buffer().put(0,(byte)1));}
    @Test void nullInputsNeverManufactureStateOrSourceDefaults() {assertThrows(NullPointerException.class,()->fromAssets(null));assertThrows(NullPointerException.class,()->fromTables(null,bytes(BOUNDS),bytes(new byte[256])));assertThrows(NullPointerException.class,()->fixture().initialize(null,new InitInput(0,0)));assertThrows(NullPointerException.class,()->fixture().step(dirty(),null));}

    private static CdR31aBackgroundScroll fixture(){return fromTables(bytes(COUNTS),bytes(BOUNDS),bytes(new byte[256]));}
    private static CdR31aBackgroundScroll wobble(){byte[] w=new byte[256];w[0]=(byte)128;w[1]=127;w[2]=(byte)254;return fromTables(bytes(COUNTS),bytes(BOUNDS),bytes(w));}
    private static State dirty(){return deform(base(0x11111234,0x4567ABCD,0x22225678,0x76541357,0x33339ABC,0x87652468,new byte[]{1,2,3,4,5,6,7,8},0xABCD,0xBCDE,0xCDEF),filled(512,0x33));}
    private static State base(int x,int y,int x2,int y2,int x3,int y3,byte[] crossing,int f,int f2,int f3){return new State(x,y,x2,y2,x3,y3,bytes(crossing),f,f2,f3,bytes(filled(512,0x22)),bytes(deformationWithDirtyTail()),bytes(filled(1024,0x5A)),0x12345678);}
    private static byte[] deformationWithDirtyTail(){byte[] d=new byte[512];Arrays.fill(d,508,512,(byte)0x33);return d;}
    private static State deform(State s,byte[] d){return new State(s.cameraBgX(),s.cameraBgY(),s.cameraBg2X(),s.cameraBg2Y(),s.cameraBg3X(),s.cameraBg3Y(),s.crossingRam(),s.scrollFlagsBg(),s.scrollFlagsBg2(),s.scrollFlagsBg3(),s.cloudRam(),bytes(d),s.hscrollRam(),s.vscroll());}
    private static byte[] filled(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return b;}
    private static CdAssetBytes bytes(byte[] b){return CdAssetBytes.copyOf(b);}
    private static void put(byte[] b,int at,int value){b[at]=(byte)(value>>>24);b[at+1]=(byte)(value>>>16);b[at+2]=(byte)(value>>>8);b[at+3]=(byte)value;}
    private static void same(State e,State a){assertEquals(e.cameraBgX(),a.cameraBgX());assertEquals(e.cameraBgY(),a.cameraBgY());assertEquals(e.cameraBg2X(),a.cameraBg2X());assertEquals(e.cameraBg2Y(),a.cameraBg2Y());assertEquals(e.cameraBg3X(),a.cameraBg3X());assertEquals(e.cameraBg3Y(),a.cameraBg3Y());assertArrayEquals(e.crossingRam().copy(),a.crossingRam().copy());assertEquals(e.scrollFlagsBg(),a.scrollFlagsBg());assertEquals(e.scrollFlagsBg2(),a.scrollFlagsBg2());assertEquals(e.scrollFlagsBg3(),a.scrollFlagsBg3());assertArrayEquals(e.cloudRam().copy(),a.cloudRam().copy());assertArrayEquals(e.deformationRam().copy(),a.deformationRam().copy());assertArrayEquals(e.hscrollRam().copy(),a.hscrollRam().copy());assertEquals(e.vscroll(),a.vscroll());}
    private static String sha(CdAssetBytes b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.copy()));}
    private static CdR31aAssets retained;
    private static synchronized CdR31aAssets actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media absent");Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage());}return retained;}
}

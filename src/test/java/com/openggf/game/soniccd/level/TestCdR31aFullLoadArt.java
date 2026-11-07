package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.resources.CdR31aSectionArt.*;

/** Authored before production. Actual retained USA data plus explicitly constructed complete values.
 * All Java UNRUN. No native invocation, clock, clear, World, FULL or GPU admission is claimed.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR31aFullLoadArt {
    private static final SavedDecoder DIRTY = new SavedDecoder(Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC);
    @Test void exactRetainedOwnerAndOldInventoriesArePreserved() throws Exception {
        var a=actual();var p=CdR31aLoadPlan.fromAssets(a);var art=fromAssets(a);
        assertTrue(p.ownsAssets(a));assertTrue(art.ownsAssets(a));assertFalse(p.ownsAssets(null));assertFalse(art.ownsAssets(null));
        assertSame(a.identity(),p.identity());assertSame(a.identity(),art.identity());assertSame(a.loadMetadata(),p.sources());
        assertEquals(67,CdR31aAssets.Asset.values().length);assertEquals(37,CdR31aLoadMetadata.SourceId.values().length);
        assertEquals(19,p.plcs().size());assertEquals(4,p.sections().size());assertEquals(a.bounds(),p.bounds());assertEquals(a.initialCentre(),p.initialCentre());
        assertThrows(NullPointerException.class,()->CdR31aLoadPlan.fromAssets(null));assertThrows(NullPointerException.class,()->fromAssets(null));
        var f=TestCdR31aAssets.actual();var other=CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R31A.stage());
        assertEquals(a.identity(),other.identity());assertFalse(p.ownsAssets(other));assertFalse(art.ownsAssets(other));
    }
    @Test void allNativeReadersHaveLiteralIndependentSpansAndProducts() throws Exception {
        var a=actual();var p=CdR31aLoadPlan.fromAssets(a);var image=TestCdR31aAssets.actual().image();int extra=0;
        var lines=NATIVE_READERS.strip().lines().toList();assertEquals(37,lines.size());assertEquals(37,p.nativeReaders().size());
        for(int i=0;i<lines.size();i++) {var words=lines.get(i).split(" ");var id=CdR31aLoadMetadata.SourceId.valueOf(words[0]);var r=p.nativeReader(id);var s=a.loadMetadata().source(id);
            assertEquals(i,id.ordinal());assertSame(s,r.source());assertEquals(Integer.parseInt(words[1]),r.prepareConsumedBytes());assertEquals(Integer.parseInt(words[2]),r.consumedBytes());assertEquals(Integer.parseInt(words[3]),r.byteLength());assertEquals(words[4],r.sha256());
            var bytes=p.nativeView(id);assertSame(bytes,p.nativeView(id));assertEquals(r.byteLength(),bytes.length());assertEquals(words[4],hash(bytes));assertArrayEquals(image.readFile(s.fileOffset(),bytes.length()),bytes.copy());
            assertArrayEquals(p.sources().canonicalView(id).copy(),bytes.slice(0,s.includeBytes()).copy());assertThrows(IndexOutOfBoundsException.class,()->bytes.unsignedByte(bytes.length()));
            assertThrows(java.nio.ReadOnlyBufferException.class,()->bytes.buffer().put(0,(byte)1));if(bytes.length()>s.includeBytes())extra++;
        }
        assertEquals(17,extra);assertThrows(UnsupportedOperationException.class,()->p.nativeReaders().clear());
    }
    @Test void everyNewNeighborByteStillRequiresTheOriginalWholeImage() throws Exception {
        var f=TestCdR31aAssets.actual();var p=CdR31aLoadPlan.fromAssets(f.assets());int checked=0;
        for(var r:p.nativeReaders())if(r.byteLength()>r.source().includeBytes()){
            byte[] changed=f.image().readFile(0,262144);changed[r.source().fileOffset()+r.byteLength()-1]^=1;
            var image=com.openggf.game.soniccd.media.CdMmdImage.parse(changed);
            assertThrows(java.io.IOException.class,()->CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,image),CdStagePayload.R31A.stage()));checked++;
        }
        assertEquals(17,checked);
    }
    @Test void nativeCodeAndDirectOperandsAreLiteralR31NotRegionalOffsets() throws Exception {
        var image=TestCdR31aAssets.actual().image();
        assertEquals("700045f90020fac070001012670461000fca700161000fc4",HexFormat.of().formatHex(image.readFile(0x15E4,24)));
        assertEquals("43f900232f3c47f12000",HexFormat.of().formatHex(image.readFile(0x1AEE,10)));
        assertEquals("43fa0050700072003038f700b0596504540160f8",HexFormat.of().formatHex(image.readFile(0xBEF6,20)));
        var a=actual();var p=CdR31aLoadPlan.fromAssets(a);assertSame(a.view(CdR31aAssets.Asset.LEVEL_BLOCKS),p.blocksReader());assertEquals(3960,p.blocksReader().length());assertEquals(0x236C76L,p.blocksNativeAddress());
        assertSame(a.view(CdR31aAssets.Asset.ART_LIFE_ICONS),p.lifeIcon());assertEquals(0x232F3CL,p.lifeIconNativeAddress());assertEquals(384,p.lifeIcon().length());
    }
    @Test void levelStartAndActualConditionalAbsenceAreDistinctMetadataBranches() throws Exception {
        var p=CdR31aLoadPlan.fromAssets(actual());assertEquals(List.of(3,1),p.levelStartOperations().stream().map(CdR31aLoadPlan.PlcOperation::nativeId).toList());
        assertEquals(2,p.titleSlideOutOperation().nativeId());assertEquals(CdR31aLoadPlan.Delivery.QUEUED,p.titleSlideOutOperation().delivery());
        for(int spawn=1;spawn<256;spawn++){var s=p.levelDataSelection(spawn,-1,0x4A0);assertEquals(CdR31aLoadPlan.Branch.LOAD_SECTION,s.branch());assertEquals(4,s.operation().orElseThrow().nativeId());}
        for(int flags=0;flags<256;flags++){var s=p.levelDataSelection(0,flags,-1);if((flags&2)==0){assertEquals(CdR31aLoadPlan.Branch.NO_PLC_TITLE_BIT_CLEAR,s.branch());assertTrue(s.operation().isEmpty());}else{assertEquals(CdR31aLoadPlan.Branch.APPEND_STANDARD,s.branch());assertEquals(2,s.operation().orElseThrow().nativeId());}}
        assertThrows(IllegalArgumentException.class,()->p.levelDataSelection(256,0,0));assertThrows(IllegalArgumentException.class,()->p.levelDataSelection(0,256,0));
    }
    @ParameterizedTest @CsvSource({"0,0,2,7","1183,0,2,7","1184,1,4,8","4095,1,4,8","4096,2,5,9","6143,2,5,9","6144,3,6,10","65534,3,6,10"})
    void sectionGeometryUsesUnsignedStrictLessThan(int camera,int index,int initial,int update) throws Exception {
        var a=actual();var art=fromAssets(a);var s=fresh(art,a);var loaded=art.initSection(s,camera);assertEquals((index*2<<8)|0x5A,loaded.sectionWord());assertEquals(art.appendList(s,initial).queue(),loaded.queue());assertSame(s.codeTable(),loaded.codeTable());assertSame(s.savedDecoder(),loaded.savedDecoder());
        var updated=art.updateSection(s,camera);assertEquals((index*2<<8)|0x5A,updated.sectionWord());assertEquals(art.initList(s,update).queue(),updated.queue());assertEquals(Writer.CLEARED,updated.savedDecoder().writer());assertEquals(0,updated.processBudgetWord());assertSame(updated,art.updateSection(updated,camera));
    }
    @Test void ffffCameraStopsAtTheNextRangeReadBeforeSectionOrQueueWrites() throws Exception {
        var a=actual();var art=fromAssets(a);var s=fresh(art,a);for(boolean init:List.of(true,false)){
            var e=assertThrows(SectionArtException.class,()->{if(init)art.initSection(s,65535);else art.updateSection(s,65535);});assertEquals(Reason.SECTION_TABLE,e.reason());sameTuple(s,e.state());assertSame(s.residency(),e.state().residency());assertTrue(e.state().stopped());}
        assertThrows(UnsupportedOperationException.class,()->CdR31aLoadPlan.fromAssets(a).sectionIndex(65535));
    }
    @Test void sparseResidencyIsExactlyActualPlcUnionPlusFourLifeTiles() throws Exception {
        var a=actual();var r=CdR31aArtResidency.unknown(a);var expected=List.of(new CdR31aArtResidency.Window(0,0x56C0),new CdR31aArtResidency.Window(0x6000,0x300),new CdR31aArtResidency.Window(0x63C0,0x44C0),new CdR31aArtResidency.Window(0xAD00,0x360),new CdR31aArtResidency.Window(0xB420,0x80),new CdR31aArtResidency.Window(0xB500,0xA40),new CdR31aArtResidency.Window(0xD000,0xFC0),new CdR31aArtResidency.Window(0xF5C0,0x1C0));
        assertEquals(expected,r.windows());assertEquals(0,knownTiles(r));assertThrows(UnsupportedOperationException.class,()->r.windows().clear());
        for(var w:r.windows())for(int offset=0;offset<w.byteLength();offset++)assertTrue(r.knownByte(w.destinationWord()+offset).isEmpty());
        var unknown=assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(r,0));assertEquals(CdR31aArtResidency.Boundary.UNKNOWN_PATTERN,unknown.boundary());
        var absent=assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(r,0x56C0/32));assertEquals(CdR31aArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,absent.boundary());
    }
    @Test void completeIngressRetainsDirtyTableSavedFieldsAndUnknownPixels() throws Exception {
        var a=actual();var art=fromAssets(a);var s=fresh(art,a);assertEquals(0xA55A,s.sectionWord());assertEquals(0x2345,s.processBudgetWord());assertEquals(0x3456,s.hintWord());assertEquals(0x4567,s.padWord());assertSame(DIRTY,s.savedDecoder());assertFalse(s.stopped());assertTrue(s.decoder().isEmpty());assertEquals(0,knownTiles(s.residency()));assertArrayEquals(dirty().copy(),s.codeTable().copy());
    }
    @Test void allNineteenListsKeepNativeRowsAliasesAndClearedTuple() throws Exception {
        var a=actual();var art=fromAssets(a);var before=fresh(art,a);for(var p:a.loadMetadata().plcs()){var s=art.initList(before,p.nativeId());byte[] bytes=new byte[96];for(int i=0;i<p.entries().size();i++){var e=p.entries().get(i);descriptor(bytes,6*i,a.loadMetadata().source(e.source()).nativeAddress(),e.vramAddress());}assertArrayEquals(bytes,s.queue().bytes().copy());assertSame(before.codeTable(),s.codeTable());assertSame(before.residency(),s.residency());assertEquals(0xA55A,s.sectionWord());assertEquals(Writer.CLEARED,s.savedDecoder().writer());assertEquals(0,s.remainingTilesWord());assertEquals(0,s.processBudgetWord());assertEquals(0,s.hintWord());assertEquals(0,s.padWord());}
    }
    @Test void appendOverflowPreservesOneCompletedRowAndOriginalCause() throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.appendList(fresh(art,a),1);assertEquals(15,a.loadMetadata().plcs().get(1).entries().size());var e=assertThrows(SectionArtException.class,()->art.appendList(before,2));var cause=(CdR11aPlcBuffer.BufferBoundary)e.getCause();assertEquals(CdR11aPlcBuffer.Access.SOURCE_WRITE,cause.access());assertEquals(96,cause.byteOffset());assertArrayEquals(before.queue().bytes().slice(0,90).copy(),e.state().queue().bytes().slice(0,90).copy());assertEquals(a.loadMetadata().source(CdR31aLoadMetadata.SourceId.SPIKES_HV2_GFX).nativeAddress(),e.state().queue().bytes().unsignedLong(90));assertTrue(e.state().stopped());assertThrows(IllegalStateException.class,()->art.prepare(e.state()));
    }
    @ParameterizedTest @CsvSource({"14,2","15,1","16,0"})
    void fullNativeAppendCountsPreserveOnlyCompletedRows(int filled,int completed) throws Exception {
        var a=actual();var art=fromAssets(a);var ring=a.loadMetadata().source(CdR31aLoadMetadata.SourceId.RING_GFX);
        byte[] raw=new byte[96];for(int i=0;i<filled;i++)descriptor(raw,i*6,ring.nativeAddress(),0xF5C0);
        var before=ingress(art,a,CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw)));
        var failure=assertThrows(SectionArtException.class,()->art.appendList(before,2));
        var cause=(CdR11aPlcBuffer.BufferBoundary)failure.getCause();
        assertEquals(filled==16?CdR11aPlcBuffer.Access.SOURCE_SCAN:CdR11aPlcBuffer.Access.SOURCE_WRITE,cause.access());assertEquals(96,cause.byteOffset());
        assertArrayEquals(Arrays.copyOf(raw,filled*6),failure.state().queue().bytes().slice(0,filled*6).copy());
        for(int i=0;i<completed;i++){var row=a.loadMetadata().plcs().get(2).entries().get(i);int cursor=(filled+i)*6;assertEquals(a.loadMetadata().source(row.source()).nativeAddress(),failure.state().queue().bytes().unsignedLong(cursor));assertEquals(row.vramAddress(),failure.state().queue().bytes().unsignedWord(cursor+4));}
        assertSame(before.residency(),failure.state().residency());assertSame(before.codeTable(),failure.state().codeTable());assertTrue(failure.state().stopped());
    }
    @Test void everyActualSourceQueuesThroughExistingDecoderWithExactNativeCursorAndProduct() throws Exception {
        var a=actual();var art=fromAssets(a);var p=CdR31aLoadPlan.fromAssets(a);for(var r:p.nativeReaders()){int dest=destination(a,r.source().id());var before=queued(art,a,r.source().nativeAddress(),dest);var prepared=art.prepare(before);assertEquals(r.prepareConsumedBytes(),prepared.decoder().orElseThrow().inputOffset());var done=drain(art,prepared);assertEquals(r.consumedBytes(),done.decoder().orElseThrow().inputOffset());var out=a.loadMetadata().decoded(r.source().id()).bytes();for(int at=0;at<out.length();at+=32)assertArrayEquals(out.slice(at,32).copy(),CdR31aArtResidency.readTile(done.residency(),(dest+at)/32).copy(),r.source().id().name());assertEquals(0,done.queue().headSource());assertEquals(0,knownTiles(before.residency()));}
    }
    @Test void fastAndSlowBudgetsUseActualHighByteAndNativePop() throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.prepare(queued(art,a,a.loadMetadata().source(CdR31aLoadMetadata.SourceId.RING_GFX).nativeAddress(),0xF5C0));var slow=art.serviceSlow(before,0xFF);assertEquals(11,slow.remainingTilesWord());assertEquals(3,knownTiles(slow.residency()));assertEquals(0xF620,slow.queue().headDestination());var fast=art.serviceFast(before);assertEquals(fast,art.serviceSlow(before,0x100));assertEquals(14,knownTiles(fast.residency()));assertEquals(CdR11aPlcBuffer.pop(CdR11aPlcBuffer.withHeadDestination(before.queue(),0xF800)),fast.queue());assertSame(before.savedDecoder(),fast.savedDecoder());assertEquals(5,fast.processBudgetWord());
    }
    @Test void immediateThreeThenOnePreservePendingQueueAndRespectOrderedOverlaps() throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.appendList(fresh(art,a),2);var done=art.loadImmediateList(art.loadImmediateList(before,3),1);sameTupleExceptTable(before,done);var expected=new HashMap<Integer,byte[]>();paintList(expected,a,3);paintList(expected,a,1);image(expected,done.residency());assertEquals(0,knownTiles(before.residency()));assertFalse(Arrays.equals(before.codeTable().copy(),done.codeTable().copy()));
    }
    @Test void ramLoopReturnsExistingBlockProductAndChangesOnlyCodeTable() throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.appendList(fresh(art,a),2);var result=art.decodeBlocksToRam(before);assertSame(a.blocksDecode().bytes(),result.blocks());assertEquals(5248,result.blocks().length());sameTupleExceptTable(before,result.state());assertSame(before.residency(),result.state().residency());
    }
    @ParameterizedTest @CsvSource({"0,0","1,128","2,256","128,0","129,128","130,256"})
    void lifeUsesLiteralPeriodMaskWithActiveQueueNonwrites(int period,int offset) throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.prepare(art.initList(fresh(art,a),3));var done=art.loadLifeIcon(before,period);sameTuple(before,done);assertEquals(4,knownTiles(done.residency()));for(int i=0;i<4;i++)assertArrayEquals(a.view(CdR31aAssets.Asset.ART_LIFE_ICONS).slice(offset+i*32,32).copy(),CdR31aArtResidency.readTile(done.residency(),(0xB420+i*32)/32).copy());
    }
    @Test void missingLifePeriodAndUnknownSourceKeepFirstReadAndTerminalOriginalTuple() throws Exception {
        var a=actual();var art=fromAssets(a);var before=fresh(art,a);var e=assertThrows(SectionArtException.class,()->art.loadLifeIcon(before,3));assertEquals(Reason.LIFE_ICON_SOURCE,e.reason());assertEquals(0x2330BCL,e.sourcePointer());sameTuple(before,e.state());assertSame(before.residency(),e.state().residency());
        var unknown=queued(art,a,0x200100,0);var bad=assertThrows(SectionArtException.class,()->art.prepare(unknown));assertEquals(Reason.UNKNOWN_SOURCE,bad.reason());sameTuple(unknown,bad.state());assertSame(unknown.residency(),bad.state().residency());
    }
    @Test void activeImmediateAndRamRejectWithoutOverwritingContinuation() throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.prepare(art.initList(fresh(art,a),3));for(boolean ram:List.of(true,false)){var e=assertThrows(SectionArtException.class,()->{if(ram)art.decodeBlocksToRam(before);else art.loadImmediateList(before,1);});assertEquals(Reason.ACTIVE_QUEUED_DECODE,e.reason());sameTuple(before,e.state());assertSame(before.residency(),e.state().residency());}
    }
    @Test void badListAndDestinationPreserveReachedFailureRatherThanClamping() throws Exception {
        var a=actual();var art=fromAssets(a);var before=fresh(art,a);var missing=assertThrows(SectionArtException.class,()->art.appendList(before,19));assertEquals(Reason.PLC_LIST,missing.reason());sameTuple(before,missing.state());
        var p=art.prepare(queued(art,a,a.loadMetadata().source(CdR31aLoadMetadata.SourceId.RING_GFX).nativeAddress(),0x56C0));var bad=assertThrows(SectionArtException.class,()->art.serviceFast(p));assertEquals(Reason.RESIDENCY,bad.reason());assertEquals(0,knownTiles(bad.state().residency()));assertTrue(bad.state().stopped());
    }
    @Test void foreignStateAndActiveIngressDoNotGainCustodyFromEqualValues() throws Exception {
        var a=actual();var art=fromAssets(a);var before=fresh(art,a);var other=fromAssets(a);assertThrows(IllegalArgumentException.class,()->other.prepare(before));var f=TestCdR31aAssets.actual();var foreign=CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R31A.stage());
        assertThrows(IllegalArgumentException.class,()->art.fromValues(dirty(),CdR31aArtResidency.unknown(foreign),0,CdR11aPlcBuffer.cleared(),Optional.empty(),DIRTY,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->art.fromValues(dirty(),before.residency(),0,before.queue(),Optional.empty(),DIRTY,1,0,0,0));
        var active=art.prepare(art.initList(before,3));assertThrows(IllegalArgumentException.class,()->art.fromValues(dirty(),active.residency(),active.sectionWord(),active.queue(),active.decoder(),active.savedDecoder(),active.remainingTilesWord(),0,0,0));
        assertThrows(IllegalArgumentException.class,()->art.fromValues(CdAssetBytes.copyOf(new byte[511]),before.residency(),0,before.queue(),Optional.empty(),DIRTY,0,0,0,0));
    }
    @Test void explicitClearAndTileArePureValuesWithoutAClockOrCaptureOwner() throws Exception {
        var a=actual();var art=fromAssets(a);var before=art.prepare(art.initList(fresh(art,a),3));var after=art.clearCurrentPatterns(before);sameTuple(before,after);assertEquals(0,knownTiles(before.residency()));for(var w:after.residency().windows())for(int at=0;at<w.byteLength();at++)assertEquals(0,after.residency().knownByte(w.destinationWord()+at).orElseThrow());
        var b=new byte[32];Arrays.fill(b,(byte)0xA5);var tile=art.writeCurrentTile(before,0x6000,CdAssetBytes.copyOf(b));sameTuple(before,tile);assertEquals(1,knownTiles(tile.residency()));assertArrayEquals(b,CdR31aArtResidency.readTile(tile.residency(),0x300).copy());
        assertThrows(IllegalArgumentException.class,()->CdR31aArtResidency.withTile(before.residency(),1,CdAssetBytes.copyOf(b)));assertThrows(IllegalArgumentException.class,()->CdR31aArtResidency.withTile(before.residency(),0,CdAssetBytes.copyOf(new byte[31])));
    }
    private static CdR31aAssets actual() throws Exception{return TestCdR31aAssets.actual().assets();}
    private static String hash(CdAssetBytes b){return TestCdR31aAssets.hash(b.copy());}
    private static CdAssetBytes dirty(){byte[] b=new byte[512];Arrays.fill(b,(byte)0x5A);return CdAssetBytes.copyOf(b);}
    private static State fresh(CdR31aSectionArt art,CdR31aAssets a){return ingress(art,a,CdR11aPlcBuffer.cleared());}
    private static State ingress(CdR31aSectionArt art,CdR31aAssets a,CdR11aPlcBuffer.State q){return art.fromValues(dirty(),CdR31aArtResidency.unknown(a),0xA55A,q,Optional.empty(),DIRTY,0,0x2345,0x3456,0x4567);}
    private static void descriptor(byte[] b,int at,long source,int dest){ByteBuffer.wrap(b).putInt(at,(int)source).putShort(at+4,(short)dest);}
    private static State queued(CdR31aSectionArt art,CdR31aAssets a,long source,int dest){byte[] b=new byte[96];descriptor(b,0,source,dest);return ingress(art,a,CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(b)));}
    private static State drain(CdR31aSectionArt art,State s){int calls=0;while(s.remainingTilesWord()!=0||s.queue().headSource()!=0){s=art.serviceFast(art.prepare(s));assertTrue(++calls<2000);}return s;}
    private static int destination(CdR31aAssets a,CdR31aLoadMetadata.SourceId id){for(var p:a.loadMetadata().plcs())for(var e:p.entries())if(e.source()==id)return e.vramAddress();throw new AssertionError(id);}
    private static void paintList(Map<Integer,byte[]> out,CdR31aAssets a,int id)throws Exception{for(var e:a.loadMetadata().plcs().get(id).entries()){var b=a.loadMetadata().decoded(e.source()).bytes();for(int at=0;at<b.length();at+=32)out.put(e.vramAddress()+at,b.slice(at,32).copy());}}
    private static void image(Map<Integer,byte[]> expected,CdR31aArtResidency.State s){for(var w:s.windows())for(int at=0;at<w.byteLength();at++){int addr=w.destinationWord()+at;var tile=expected.get(addr&~31);var v=s.knownByte(addr);if(tile==null)assertTrue(v.isEmpty(),Integer.toHexString(addr));else assertEquals(Byte.toUnsignedInt(tile[addr&31]),v.orElseThrow());}}
    private static int knownTiles(CdR31aArtResidency.State s){int n=0;for(var w:s.windows())for(int at=0;at<w.byteLength();at+=32)if(s.knownByte(w.destinationWord()+at).isPresent())n++;return n;}
    private static void sameTupleExceptTable(State a,State b){assertEquals(a.sectionWord(),b.sectionWord());assertSame(a.queue(),b.queue());assertSame(a.decoder().orElse(null),b.decoder().orElse(null));assertSame(a.savedDecoder(),b.savedDecoder());assertEquals(a.remainingTilesWord(),b.remainingTilesWord());assertEquals(a.processBudgetWord(),b.processBudgetWord());assertEquals(a.hintWord(),b.hintWord());assertEquals(a.padWord(),b.padWord());}
    private static void sameTuple(State a,State b){sameTupleExceptTable(a,b);assertSame(a.codeTable(),b.codeTable());}
    private static final String NATIVE_READERS="""
STAGE_GFX 99 10162 10162 f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37
CHECKPOINT_GFX 53 212 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
GOAL_GFX 88 449 449 1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250
TITLE_CARD_GFX 73 940 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
TITLE_CARD_TEXT_GFX 61 270 270 3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8
TENTOU_GFX 92 790 790 68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097
SPIKE_BOMB_GFX 46 194 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
FLIPPER_GFX 84 614 614 a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6
BUMPER_GFX 74 533 533 0ef1d0fde20d3a729e08fdcd878d69b1804174aba1bc0b71d799fa2f10cc68a4
SPRING45_GFX 77 780 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
SPRING_GFX 82 547 547 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847
HUD_GFX 51 301 301 fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5
MONITOR_TIME_GFX 107 1541 1541 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902
EXPLOSION_GFX 91 1257 1257 c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe
POINTS_GFX 31 101 101 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176
FLOWER_GFX 77 603 603 cb53ead8bf4ba0a230ce61ea6f67d77ff99b435f411e1a8c4f4c57df1d358069
RING_GFX 48 244 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
SPIKES_HV2_GFX 63 218 218 a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80
BLOCK_GFX 57 228 228 578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23
METAL_PLATFORM_GFX 65 294 294 6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7
SPIKES_HV4_GFX 71 370 370 06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941
BOUNCE_PLATFORM_GFX_AB 64 212 212 f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d
ONE_WAY_BARRIER_GFX 42 125 125 7afe047cfa59195eb46cce051fe096f0bbd9eb6700c2e2864ee542914312cd96
FIRE_SHOOT_GFX 65 385 385 07fb31e4617550dd44b728cf705423a478fd5b35eb7817456d2641e54b06a85f
RETRACT_BLOCK_GFX 44 187 187 1a993438d70b8c6bcbe842c0bde53879ee4e0d850fc1ae999d4bb6780cb70bdb
POCKET_GFX_A 52 179 179 16a912563b1b30bee11e12fa4fbd3608e627737be559d0875b7c05b0ffd5b6d0
SPIKE_CHAIN_GFX 63 217 217 618541e54d2d6679a14bf42340e9ad90ed9d4de222586ba721129e359d41e3f0
ANIMALS_GFX 68 432 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
KAMA_KAMA_GFX 96 926 926 9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437
GA_GFX 92 1281 1281 cf09b74a48b1b800d46e5e12483a8b0f2f16041f0762beb1d331f14a7e3efbd8
ROBOT_TRANSPORT_GFX_A 79 332 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
AMY_ROSE_GFX 112 1998 1998 9b3ec92dafac2c1b7baa8d21b25403f5736350b08862787dfd9bf599f21724ea
METAL_SONIC_GFX 99 2479 2479 89f9db5a9c5833c64cf47442c56d30b8a909955ce1b557040dfef3ff60e5d0df
RESULTS_GFX 67 1354 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
SIGNPOST_GFX 117 1501 1501 c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d
BIG_RING_GFX 61 1034 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
BIG_RING_FLASH 38 385 385 e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b
""";
}

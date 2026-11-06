package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bProfile;
import com.openggf.game.soniccd.level.CdR12bSectionSources;
import com.openggf.game.soniccd.level.CdR12bDirectArtSources;
import com.openggf.game.soniccd.level.CdR12bLoadPlan;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import static com.openggf.game.soniccd.level.CdR12bSectionSources.SourceId.*;
import static com.openggf.game.soniccd.resources.CdR12bSectionArt.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Source-first original-USA component witnesses. ALL JAVA UNRUN. No World/service/current producer authority. */
class TestCdR12bSectionArt {
    private static CdR12bTerrainAssets assets;
    private static CdR12bSectionSources sources;
    private static CdR12bSectionArt art;
    private static final SavedDecoder DIRTY=new SavedDecoder(Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC);
    @BeforeAll static void genuineDetachedUsaBinding() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Required original USA media");assertFalse(root.isBlank());
        Path cue=Path.of(root).resolve("USA/Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){assets=CdR12bTerrainAssets.fromPrograms(p);}
        sources=assets.sectionSources();art=fromAssets(assets);
    }

    @Test void completeExplicitIngressPreservesEveryQueueTableSavedAndUnknownCurrentValue() {
        byte[] raw=new byte[96];for(int i=0;i<96;i++)raw[i]=(byte)(3+i*7);
        var queue=CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw));var table=dirty();var current=CdR12bCameraArtResidency.unknown();
        var state=art.fromValues(table,current,0xA55A,queue,Optional.empty(),DIRTY,0,0x2345,0x3456,0x4567);
        assertSame(queue,state.queue());assertArrayEquals(raw,state.queue().bytes().copy());assertSame(table,state.codeTable());assertSame(current,state.residency());
        assertSame(DIRTY,state.savedDecoder());assertEquals(0xA55A,state.sectionWord());assertEquals(0x2345,state.processBudgetWord());
        assertEquals(0x3456,state.hintWord());assertEquals(0x4567,state.padWord());assertTrue(state.decoder().isEmpty());assertFalse(state.stopped());
        image(Map.of(),state.residency());
    }

    @Test void allNineteenNativeListsCopyExactRowsWithoutFuturePrepareOrCurrentKnowledge() {
        var before=fresh();
        for(var list:assets.loadPlan().plcs()) {
            var state=art.initList(before,list.nativeId());byte[] expected=new byte[96];
            for(int i=0;i<list.entries().size();i++){var e=list.entries().get(i);descriptor(expected,i*6,e.sourceNativeAddress(),e.vramAddress());}
            assertArrayEquals(expected,state.queue().bytes().copy());assertSame(before.codeTable(),state.codeTable());assertSame(before.residency(),state.residency());
            assertTrue(state.decoder().isEmpty());assertEquals(0,state.remainingTilesWord());assertEquals(Writer.CLEARED,state.savedDecoder().writer());
        }
        image(Map.of(),before.residency());
    }

    @Test void fivePastSectionsUseActualThresholdsInitialUpdateIdsAndOnlyTheSectionHighByte() {
        int[] camera={0,0xB00,0x1500,0x1800,0x1C00},initial={2,6,7,8,9};
        for(int i=0;i<camera.length;i++) {
            var before=fresh();var loaded=art.loadSection(before,camera[i]);
            assertEquals((i*2<<8)|0x5A,loaded.sectionWord());assertEquals(art.initList(before,initial[i]).queue(),loaded.queue());
            assertSame(before.residency(),loaded.residency());assertSame(loaded,art.updateSection(loaded,camera[i]));
            var updated=art.updateSection(art.withSectionWord(before,0xFE5A),camera[i]);
            assertEquals((i*2<<8)|0x5A,updated.sectionWord());assertEquals(art.initList(before,10+i).queue(),updated.queue());
        }
        var before=fresh();var failure=assertThrows(SectionArtException.class,()->art.loadSection(before,0xFFFF));
        assertEquals(Reason.SECTION_TABLE,failure.reason());terminal(before,failure.state());
    }

    @Test void fourteenRowAppendOverflowRetainsBothInBoundsRowsAndThenClosesAdmission() {
        var before=art.initList(fresh(),1);byte[] expected=before.queue().bytes().copy();
        descriptor(expected,84,sources.source(ART_SPRING_WHEEL).nativeAddress(),0x6700);
        descriptor(expected,90,sources.source(ART_TUNNEL_DOOR).nativeAddress(),0x6800);
        var failure=assertThrows(SectionArtException.class,()->art.appendList(before,1));
        assertEquals(Reason.DESCRIPTOR_BUFFER,failure.reason());assertArrayEquals(expected,failure.state().queue().bytes().copy());
        assertSame(before.codeTable(),failure.state().codeTable());assertSame(before.residency(),failure.state().residency());
        assertThrows(IllegalStateException.class,()->art.clearPlcs(failure.state()));
        assertEquals(0,before.queue().bytes().unsignedLong(84));
    }

    @Test void fullSixteenRowQueueRetainsTheShippedEightyEightBytePopAndUnclearedTail() {
        byte[] raw=new byte[96];for(int i=0;i<16;i++)descriptor(raw,i*6,sources.source(ART_RINGS).nativeAddress(),0xF5C0);
        var input=art.fromValues(dirty(),CdR12bCameraArtResidency.unknown(),0xA55A,CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw)),Optional.empty(),DIRTY,0,0,0,0);
        var prepared=art.prepare(input);byte[] expected=prepared.queue().bytes().copy(),copy=expected.clone();System.arraycopy(copy,6,expected,0,88);
        var done=art.serviceFast(prepared);assertArrayEquals(expected,done.queue().bytes().copy());
        assertEquals(prepared.savedDecoder(),done.savedDecoder());assertEquals(5,done.processBudgetWord());assertEquals(0,done.remainingTilesWord());
        assertArrayEquals(copy,prepared.queue().bytes().copy());assertEquals(14,knownTiles(done.residency()));
    }

    @Test void stageAndMainImmediateListsPublishOnlyTheirFifteenSourcesAndKeepAllPendingQueueFields() throws Exception {
        var before=art.appendList(fresh(),2);var stage=art.loadImmediateList(before,3);var done=art.loadImmediateList(stage,1);
        var expected=new HashMap<Integer,byte[]>();paintList(expected,3);image(expected,stage.residency());paintList(expected,1);image(expected,done.residency());
        queueExceptTable(before,done);assertSame(before.queue(),done.queue());assertTrue(done.decoder().isEmpty());
        assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(done.residency(),0x83C0>>>5));
        assertEquals(15,java.util.stream.Stream.concat(assets.loadPlan().plcs().get(3).entries().stream(),assets.loadPlan().plcs().get(1).entries().stream()).map(CdR12bLoadPlan.PlcEntry::sourceNativeAddress).distinct().count());
    }

    @Test void allThirtySevenAuthenticSourcesServiceThroughTheSharedQueuedKernelAtTheirActualDestinations() throws Exception {
        for(var source:sources.sources()) {
            int destination=destination(source.nativeAddress());var input=queued(source.id(),destination);var done=drain(input);
            var expected=new HashMap<Integer,byte[]>();paint(expected,source.id(),destination);image(expected,done.residency());
            assertEquals(0,done.remainingTilesWord());assertEquals(0,done.queue().headSource());
            assertEquals(source.outputBytes()/32,knownTiles(done.residency()));image(Map.of(),input.residency());
        }
    }

    @Test void initialAndUpdateListsOverwriteTheSamePhysicalWindowsRatherThanKeepingSeparateSourceBanks() throws Exception {
        var before=art.loadImmediateList(art.loadImmediateList(fresh(),3),1);var first=drain(art.loadSection(before,0));
        var expected=new HashMap<Integer,byte[]>();paintList(expected,3);paintList(expected,1);paintList(expected,2);image(expected,first.residency());
        var second=drain(art.updateSection(first,0x1500));paintList(expected,12);image(expected,second.residency());
        assertEquals(4<<8|0x5A,second.sectionWord());assertArrayEquals(reference(ART_MOSQUI).slice(0,32).copy(),CdR12bCameraArtResidency.readTile(second.residency(),0x7D40>>>5).copy());
    }

    @Test void slowBudgetUsesOnlyScrollLockHighByteAndFinalPopKeepsSavedWordsAndBudget() {
        var prepared=art.prepare(queued(ART_EXPLOSIONS,0xD000));var three=art.serviceSlow(prepared,1);var eighteen=art.serviceSlow(prepared,0x100);
        assertEquals(67,three.remainingTilesWord());assertEquals(52,eighteen.remainingTilesWord());assertEquals(0xD060,three.queue().headDestination());assertEquals(0xD240,eighteen.queue().headDestination());
        assertEquals(3,knownTiles(three.residency()));assertEquals(18,knownTiles(eighteen.residency()));
        var rings=art.prepare(queued(ART_RINGS,0xF5C0));var done=art.serviceFast(rings);assertEquals(rings.savedDecoder(),done.savedDecoder());assertEquals(5,done.processBudgetWord());
    }

    @Test void fullSnapshotIngressRoundTripsActiveDecoderAndRejectsMismatchingTableCursorOrCount() {
        var snapshot=art.serviceSlow(art.prepare(queued(ART_EXPLOSIONS,0xD000)),0);
        var restored=art.fromValues(snapshot.codeTable(),snapshot.residency(),snapshot.sectionWord(),snapshot.queue(),snapshot.decoder(),snapshot.savedDecoder(),snapshot.remainingTilesWord(),snapshot.processBudgetWord(),snapshot.hintWord(),snapshot.padWord());
        assertEquals(snapshot,restored);assertEquals(art.serviceFast(snapshot),art.serviceFast(restored));
        byte[] wrong=snapshot.codeTable().copy();wrong[511]^=1;
        assertThrows(IllegalArgumentException.class,()->art.fromValues(CdAssetBytes.copyOf(wrong),snapshot.residency(),snapshot.sectionWord(),snapshot.queue(),snapshot.decoder(),snapshot.savedDecoder(),snapshot.remainingTilesWord(),snapshot.processBudgetWord(),snapshot.hintWord(),snapshot.padWord()));
        assertThrows(IllegalArgumentException.class,()->art.fromValues(snapshot.codeTable(),snapshot.residency(),snapshot.sectionWord(),CdR11aPlcBuffer.cleared(),snapshot.decoder(),snapshot.savedDecoder(),snapshot.remainingTilesWord(),snapshot.processBudgetWord(),0,0));
        assertThrows(IllegalArgumentException.class,()->art.fromValues(snapshot.codeTable(),snapshot.residency(),snapshot.sectionWord(),snapshot.queue(),snapshot.decoder(),snapshot.savedDecoder(),snapshot.remainingTilesWord()-1,snapshot.processBudgetWord(),0,0));
    }

    @Test void genuineR12bBlocksNeedOnlyTheCanonical4468BytesAndReturnTheExisting5664ByteProduct() {
        var before=art.appendList(fresh(),2);var result=art.decodeBlocksToRam(before);
        assertEquals(4468,assets.view(CdR12bProfile.Asset.LEVEL_BLOCKS).length());assertSame(assets.blocksDecode().bytes(),result.blocks());assertEquals(5664,result.blocks().length());
        assertEquals("cd6c01762faf27ecb8092b0c085e4c2aa2b92b7ead4c490836e14bce213cd23f",hash(result.blocks().copy()));
        queueExceptTable(before,result.state());assertSame(before.residency(),result.state().residency());
        var nativeDecoder=CdNemesisPlcDecoder.prepare(assets.view(CdR12bProfile.Asset.LEVEL_BLOCKS),0x23BA2C,dirty());
        while(nativeDecoder.remainingTiles()!=0)nativeDecoder=CdNemesisPlcDecoder.decodeTile(nativeDecoder).state();
        assertEquals(4468,nativeDecoder.inputOffset());
    }

    @Test void rawLifePeriodAndMarkerSelectFourActualTilesWithoutServicingAnActiveQueue() {
        var before=art.prepare(art.initList(fresh(),3));var raw=assets.directArtSources().view(CdR12bDirectArtSources.View.LIFE_ICON);
        for(int period:new int[]{0,1,2,0x80,0x81,0x82}) {
            var done=art.loadLifeIcon(before,period);queue(before,done);var expected=new HashMap<Integer,byte[]>();
            for(int tile=0;tile<4;tile++)expected.put(0xB420+tile*32,raw.slice((period&0x7F)*128+tile*32,32).copy());
            image(expected,done.residency());
        }
        var failure=assertThrows(SectionArtException.class,()->art.loadLifeIcon(before,3));assertEquals(Reason.LIFE_ICON_SOURCE,failure.reason());assertEquals(0x236456L,failure.sourcePointer());terminal(before,failure.state());
    }

    @Test void activeQueuedDecodeRejectsImmediateOrRamBeforeAnyTableOrPatternWrite() {
        var before=art.prepare(art.initList(fresh(),3));
        var immediate=assertThrows(SectionArtException.class,()->art.loadImmediateList(before,1));assertEquals(Reason.ACTIVE_QUEUED_DECODE,immediate.reason());terminal(before,immediate.state());
        var ram=assertThrows(SectionArtException.class,()->art.decodeBlocksToRam(before));assertEquals(Reason.ACTIVE_QUEUED_DECODE,ram.reason());terminal(before,ram.state());
    }

    @Test void aCompletedShieldUploadUsesTheSharedRawCopyAndActualCallerTailWhilePreservingTheWholeQueue() {
        var before=art.prepare(art.initList(fresh(),3));byte[] cpu=new byte[0x480];Arrays.fill(cpu,(byte)0xD7);
        var runtime=CdPowerupArtRuntime.fromRawSources(assets.directArtSources());var request=runtime.prepareActiveCopy(CdAssetBytes.copyOf(cpu),6);
        var done=art.writeShieldUpload(before,request);queue(before,done);var expected=new HashMap<Integer,byte[]>();
        for(int tile=0;tile<36;tile++)expected.put(0xA880+tile*32,request.bytes().slice(tile*32,32).copy());image(expected,done.residency());assertEquals(36,knownTiles(done.residency()));
        for(int i=1024;i<1152;i++)assertEquals(0xD7,request.bytes().unsignedByte(i));
    }

    @Test void residencyFailureRetainsOneCompleteTileAndTheNativeWholeBudgetPreadvance() throws Exception {
        var before=art.prepare(queued(ART_RINGS,0xF760));var failure=assertThrows(SectionArtException.class,()->art.serviceFast(before));var stopped=failure.state();
        assertEquals(Reason.RESIDENCY,failure.reason());assertTrue(stopped.stopped());assertEquals(13,stopped.remainingTilesWord());assertEquals(17,stopped.processBudgetWord());
        assertEquals(0xF9A0,stopped.queue().headDestination());assertEquals(1,knownTiles(stopped.residency()));
        assertArrayEquals(reference(ART_RINGS).slice(0,32).copy(),CdR12bCameraArtResidency.readTile(stopped.residency(),0xF760>>>5).copy());
        assertEquals(0,knownTiles(before.residency()));assertThrows(IllegalStateException.class,()->art.prepare(stopped));
    }

    @Test void shortenedDeclaredSourceRejectsTheFiniteRegistryJoinBeforeCreatingAFacade() {
        var rings=sources.source(ART_RINGS);
        var shortView=sources.view(ART_RINGS,CdR12bSectionSources.ReaderKind.NATIVE).slice(0,rings.expectedNativeConsumedBytes()-1);
        assertThrows(IllegalArgumentException.class,()->declaredRingReader(shortView));
    }

    @Test void declaredPayloadCodeHoleRetainsPreparedValuesWithoutCommittingPartialTileRows() {
        byte[] bad=sources.view(ART_RINGS,CdR12bSectionSources.ReaderKind.NATIVE).copy();bad[2]=(byte)0xFF;bad[3]=0;bad[4]=0;
        var owner=declaredRingReader(CdAssetBytes.copyOf(bad));var prepared=owner.prepare(queued(owner,ART_RINGS,0xF5C0));
        var failure=assertThrows(SectionArtException.class,()->owner.serviceFast(prepared));
        assertEquals(Reason.DECODE_TILE,failure.reason());assertTrue(failure.state().stopped());assertEquals(0,knownTiles(failure.state().residency()));
        assertEquals(14,failure.state().remainingTilesWord());assertEquals(18,failure.state().processBudgetWord());
        assertEquals(0xF800,failure.state().queue().headDestination());assertSame(prepared.codeTable(),failure.state().codeTable());
        var cause=(CdNemesisPlcDecoder.DecodeException)failure.getCause();assertEquals(CdNemesisPlcDecoder.Reason.UNSUPPORTED_CODE_LENGTH,cause.reason());
        assertEquals(0,cause.diagnostic().emittedRows().length());
    }

    @Test void declaredPrepareFaultRetainsEveryCompletedCodeTableStoreAndTheHeaderCount() {
        byte[] bad=sources.view(ART_RINGS,CdR12bSectionSources.ReaderKind.NATIVE).copy();
        bad[2]=(byte)0x80;bad[3]=1;bad[4]=0;bad[5]=(byte)0x81;bad[6]=1;bad[7]=2;
        var owner=declaredRingReader(CdAssetBytes.copyOf(bad));var before=queued(owner,ART_RINGS,0xF5C0);
        var failure=assertThrows(SectionArtException.class,()->owner.prepare(before));assertEquals(Reason.DECODE_PREPARE,failure.reason());
        var state=failure.state();assertEquals(14,state.remainingTilesWord());assertTrue(state.decoder().isEmpty());assertSame(before.residency(),state.residency());
        for(int i=0;i<128;i++)assertEquals(0x0100,state.codeTable().unsignedWord(i*2));
        assertArrayEquals(before.codeTable().slice(256,256).copy(),state.codeTable().slice(256,256).copy());
        assertEquals(0,knownTiles(state.residency()));assertThrows(IllegalStateException.class,()->owner.prepare(state));
    }

    @Test void unknownQueuedSourceAndForeignFacadeStateCannotAcquireBindingsOrRetry() {
        byte[] raw=new byte[96];descriptor(raw,0,0x200100,0);
        var input=art.fromValues(dirty(),CdR12bCameraArtResidency.unknown(),0,CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw)),Optional.empty(),DIRTY,0,0,0,0);
        var failure=assertThrows(SectionArtException.class,()->art.prepare(input));assertEquals(Reason.UNKNOWN_SOURCE,failure.reason());terminal(input,failure.state());
        var other=fromAssets(assets);assertThrows(IllegalArgumentException.class,()->other.clearPlcs(fresh()));
        assertThrows(NullPointerException.class,()->fromAssets(null));
    }

    /** Declared malformed source component, never a public authenticated-asset ingress. */
    private static CdR12bSectionArt declaredRingReader(CdAssetBytes replacement) {
        var registry=mock(CdR12bSectionSources.class);when(registry.identity()).thenReturn(assets.identity());when(registry.sources()).thenReturn(sources.sources());
        for(var source:sources.sources())when(registry.view(source.id(),CdR12bSectionSources.ReaderKind.NATIVE)).thenReturn(source.id()==ART_RINGS?replacement:sources.view(source.id(),CdR12bSectionSources.ReaderKind.NATIVE));
        for(var source:sources.sources())when(registry.source(source.nativeAddress())).thenReturn(source);
        var declared=mock(CdR12bTerrainAssets.class);when(declared.identity()).thenReturn(assets.identity());when(declared.profile()).thenReturn(assets.profile());when(declared.loadPlan()).thenReturn(assets.loadPlan());when(declared.sectionSources()).thenReturn(registry);when(declared.directArtSources()).thenReturn(assets.directArtSources());when(declared.view(CdR12bProfile.Asset.LEVEL_BLOCKS)).thenReturn(assets.view(CdR12bProfile.Asset.LEVEL_BLOCKS));
        return fromAssets(declared);
    }

    private static State fresh(){return art.fromValues(dirty(),CdR12bCameraArtResidency.unknown(),0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),DIRTY,0,0x2345,0x3456,0x4567);}
    private static State queued(CdR12bSectionSources.SourceId id,int dest){return queued(art,id,dest);}
    private static State queued(CdR12bSectionArt owner,CdR12bSectionSources.SourceId id,int dest){byte[] raw=new byte[96];descriptor(raw,0,sources.source(id).nativeAddress(),dest);return owner.fromValues(dirty(),CdR12bCameraArtResidency.unknown(),0xA55A,CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(raw)),Optional.empty(),DIRTY,0,0,0,0);}
    private static State drain(State state){int calls=0;while(state.queue().headSource()!=0 || state.remainingTilesWord()!=0){state=art.prepare(state);state=art.serviceFast(state);assertTrue(++calls<2000);}return state;}
    private static int destination(long ptr){for(var list:assets.loadPlan().plcs())for(var row:list.entries())if(row.sourceNativeAddress()==ptr)return row.vramAddress();throw new AssertionError("No actual native destination");}
    private static void descriptor(byte[] raw,int at,long source,int destination){ByteBuffer.wrap(raw).putInt(at,(int)source).putShort(at+4,(short)destination);}
    private static CdAssetBytes dirty(){byte[] bytes=new byte[512];Arrays.fill(bytes,(byte)0x5A);return CdAssetBytes.copyOf(bytes);}
    private static CdAssetBytes reference(CdR12bSectionSources.SourceId id) throws Exception {var s=sources.source(id);var result=CdNemesisAsset.decode(sources.view(id,CdR12bSectionSources.ReaderKind.JAVA).copy(),s.outputBytes(),s.outputBytes());assertEquals(s.decodedSha256(),hash(result.bytes().copy()));return result.bytes();}
    private static void paint(Map<Integer,byte[]> image,CdR12bSectionSources.SourceId id,int dest) throws Exception {var bytes=reference(id);for(int at=0;at<bytes.length();at+=32)image.put((dest+at)&0xFFFF,bytes.slice(at,32).copy());}
    private static void paintList(Map<Integer,byte[]> image,int list) throws Exception {for(var e:assets.loadPlan().plcs().get(list).entries())paint(image,sources.source(e.sourceNativeAddress()).id(),e.vramAddress());}
    private static int knownTiles(CdR12bCameraArtResidency.State state){int n=0;for(var r:CdR12bCameraArtResidency.Region.values())for(int at=0;at<r.byteLength();at+=32)if(state.region(r).knownByte(at).isPresent())n++;return n;}
    private static void image(Map<Integer,byte[]> expected,CdR12bCameraArtResidency.State actual){for(var r:CdR12bCameraArtResidency.Region.values())for(int at=0;at<r.byteLength();at++){int dest=r.destinationWord()+at;var tile=expected.get(dest&~31);var value=actual.region(r).knownByte(at);if(tile==null)assertTrue(value.isEmpty(),"Unexpected knowledge at "+Integer.toHexString(dest));else assertEquals(Byte.toUnsignedInt(tile[dest&31]),value.orElseThrow());}}
    private static void queueExceptTable(State a,State b){assertEquals(a.sectionWord(),b.sectionWord());assertSame(a.queue(),b.queue());assertSame(a.decoder().orElse(null),b.decoder().orElse(null));assertSame(a.savedDecoder(),b.savedDecoder());assertEquals(a.remainingTilesWord(),b.remainingTilesWord());assertEquals(a.processBudgetWord(),b.processBudgetWord());assertEquals(a.hintWord(),b.hintWord());assertEquals(a.padWord(),b.padWord());}
    private static void queue(State a,State b){queueExceptTable(a,b);assertSame(a.codeTable(),b.codeTable());}
    private static void terminal(State before,State after){assertTrue(after.stopped());assertEquals(before.sectionWord(),after.sectionWord());assertEquals(before.queue(),after.queue());assertSame(before.codeTable(),after.codeTable());assertSame(before.residency(),after.residency());}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new AssertionError(e);}}
}

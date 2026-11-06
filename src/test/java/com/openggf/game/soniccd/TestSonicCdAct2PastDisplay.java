package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.level.CdR12bSectionSources;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aPlcBuffer;
import com.openggf.game.soniccd.resources.CdR11aSectionArt;
import com.openggf.game.soniccd.resources.CdR12bCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR12bSectionArt;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static com.openggf.game.soniccd.SonicCdPastDisplay.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-first, ALL JAVA/GL UNRUN. Genuine R12B current is produced only by the actual
 * authenticated native-list/queued component over original USA bytes; never prefilled/faked.
 * SAT/planes and complete caller-owned player windows below are explicit projection fixtures,
 * not World, live-frame, native service, player-source or renderer authority.
 */
class TestSonicCdAct2PastDisplay {
    private static CdR12bTerrainAssets assets;
    private static CdR12bSectionArt art;
    private static CdR12bSectionArt.State stage;
    private static CdR12bSectionArt.State main;
    @BeforeAll static void authenticCurrentComesFromOriginalUsaSourcesAfterProgramClose() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Required original USA source");assertFalse(root.isBlank());
        Path cue=Path.of(root).resolve("USA/Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){assets=CdR12bTerrainAssets.fromPrograms(p);}
        art=CdR12bSectionArt.fromAssets(assets);
        var initial=art.fromValues(bytes(new byte[512]),CdR12bCameraArtResidency.unknown(),0xA55A,CdR11aPlcBuffer.cleared(),
                Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
        stage=art.loadImmediateList(initial,3);main=art.loadImmediateList(stage,1);
    }

    @Test void actualPastOnlyBlockWindowAndCompletePlanesProjectThroughTheSharedConverter() throws Exception {
        int id=0x6980>>>5;
        var input=input(Optional.empty(),new byte[640],uniform(0xD800|id),uniform(0x9800|id),main.residency(),player(1,2,0,false));
        var result=SonicCdAct2PastDisplay.project(input);var image=scene(result);
        assertEquals(version(reference(CdR12bSectionSources.SourceId.ART_BLOCK).slice(0,32)),image.nativePatterns().get(id));
        assertEquals(0xD800|id,image.foreground().words64x32()[2047]);
        assertEquals(0x9800|id,image.background().words64x32()[2047]);
        assertSame(input,result.inputs());assertEquals(1614,Arrays.stream(CdR12bCameraArtResidency.Region.values()).mapToInt(CdR12bCameraArtResidency.Region::tileCount).sum());
    }

    @Test void disabledDisplayRetainsSelectedValuesWithoutSamplingAnyPlaneCurrentPlayerOrCpuAuthority() {
        byte[] dirtySat=new byte[640];dirtySat[639]=1;byte[] scroll=new byte[1024];ByteBuffer.wrap(scroll).putInt(0,0x12345678);
        var input=new SonicCdAct2PastDisplay.Inputs(Optional.empty(),bytes(dirtySat),assets.loadPlan().initialPalette(),bytes(scroll),
                0x89ABCDEF,false,null,null,null,null);
        var image=assertInstanceOf(Disabled.class,SonicCdAct2PastDisplay.project(input).projection()).scene();
        assertFalse(image.displayEnabled());assertNull(image.foreground());assertNull(image.background());assertTrue(image.nativePatterns().isEmpty());
        assertTrue(image.sprites().tiles().isEmpty());assertEquals(0x12345678,image.packedHscroll256()[0]);
        assertEquals(0x89ABCDEF,image.vscrollLongBits());assertArrayEquals(input.palette().copy(),image.selectedPalette128());
    }

    @Test void selectedFormatExtentsAndEnabledNullOwnersRejectBeforeProjection() {
        var p=player(0,0,0,false);
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastDisplay.Inputs(Optional.empty(),bytes(new byte[639]),bytes(new byte[128]),bytes(new byte[1024]),0,false,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastDisplay.Inputs(Optional.empty(),bytes(new byte[640]),bytes(new byte[127]),bytes(new byte[1024]),0,false,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastDisplay.Inputs(Optional.empty(),bytes(new byte[640]),bytes(new byte[128]),bytes(new byte[1023]),0,false,null,null,null,null));
        assertThrows(NullPointerException.class,()->input(Optional.empty(),new byte[640],null,uniform(0),stage.residency(),p));
        assertThrows(NullPointerException.class,()->input(Optional.empty(),new byte[640],uniform(0),uniform(0),null,p));
        assertThrows(NullPointerException.class,()->input(Optional.empty(),new byte[640],uniform(0),uniform(0),stage.residency(),null));
    }

    @Test void missingCpuAndSatMismatchPrecedeUnknownArtAndKeepExactByteLocations() {
        byte[] nonzero=new byte[640];nonzero[639]=1;
        failure(SonicCdAct2PastDisplay.project(input(Optional.empty(),nonzero,uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false))),
                Boundary.MISSING_CPU_AUTHORITY,639,-1,-1);
        var cpu=cpu(one(0),32,128,128,new byte[640]);byte[] changed=cpu.bytes();changed[5]^=1;
        failure(SonicCdAct2PastDisplay.project(input(Optional.of(cpu),changed,uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false))),
                Boundary.SAT_MISMATCH,5,-1,-1);
    }

    @Test void duplicateGenuineCpuObjectsCompareByBytesCountAndProvenanceRatherThanIdentity() {
        var a=cpu(one(0),32,128,128,new byte[640]);var b=cpu(one(0),32,128,128,new byte[640]);
        assertNotSame(a,b);assertArrayEquals(a.bytes(),b.bytes());assertEquals(a.provenance(),b.provenance());
        var first=input(Optional.of(a),a.bytes(),uniform(0),uniform(0),stage.residency(),player(0,3,0,false));
        var equivalent=input(Optional.of(b),b.bytes(),uniform(0),uniform(0),stage.residency(),player(0,3,0,false));
        assertEquals(first,equivalent);assertEquals(first.hashCode(),equivalent.hashCode());
        assertDoesNotThrow(()->SonicCdAct2PastDisplay.validateSelected(SonicCdAct2PastDisplay.project(first),equivalent));
    }

    @Test void equalSatBytesWithDifferentNativeProvenanceCannotSubstituteSelectedCpu() {
        var a=cpu(one(0),32,128,128,new byte[640]);var b=cpu(one(0),33,128,128,new byte[640]);
        assertArrayEquals(a.bytes(),b.bytes());assertNotEquals(a.provenance(),b.provenance());
        var first=input(Optional.of(a),a.bytes(),uniform(0),uniform(0),stage.residency(),player(0,0,0,false));
        var other=input(Optional.of(b),a.bytes(),uniform(0),uniform(0),stage.residency(),first.player());
        assertNotEquals(first,other);
        assertThrows(IllegalArgumentException.class,()->SonicCdAct2PastDisplay.validateSelected(SonicCdAct2PastDisplay.project(first),other));
    }

    @Test void callerResidentPlayerWindowTakesPrecedenceIncludingTheLastTileBeyondOrdinaryDma() {
        var p=player(0x11,0x3C,7,true);var cpu=cpu(one(0x797),32,128,128,new byte[640]);
        var image=scene(SonicCdAct2PastDisplay.project(input(Optional.of(cpu),cpu.bytes(),uniform(0x780),uniform(0x797),CdR12bCameraArtResidency.unknown(),p)));
        assertEquals(version(p.residentBank().slice(0,32)),image.nativePatterns().get(0x780));
        assertEquals(version(p.residentBank().slice(23*32,32)),image.nativePatterns().get(0x797));
        assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(CdR12bCameraArtResidency.unknown(),0x797));
        failure(SonicCdAct2PastDisplay.project(input(Optional.empty(),new byte[640],uniform(0x798),uniform(0),CdR12bCameraArtResidency.unknown(),p)),
                Boundary.OUTSIDE_OWNED_WINDOW,-1,0x798,0xF300);
    }

    @Test void pastOwnedUnknownAndTrueGapBoundariesRemainDistinctForPlanesAndSat() {
        failure(SonicCdAct2PastDisplay.project(input(Optional.empty(),new byte[640],uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false))),
                Boundary.UNKNOWN_PATTERN,-1,0,0);
        int owned=0x6980>>>5,gap=0x63A0>>>5;
        failure(SonicCdAct2PastDisplay.project(input(Optional.empty(),new byte[640],uniform(0),uniform(0xF800|owned),stage.residency(),player(0,0,0,false))),
                Boundary.UNKNOWN_PATTERN,-1,owned,0x6980);
        failure(SonicCdAct2PastDisplay.project(input(Optional.empty(),new byte[640],uniform(0),uniform(0xF800|gap),stage.residency(),player(0,0,0,false))),
                Boundary.OUTSIDE_OWNED_WINDOW,-1,gap,0x63A0);
        var cpu=cpu(one(gap),32,128,128,new byte[640]);
        failure(SonicCdAct2PastDisplay.project(input(Optional.of(cpu),cpu.bytes(),uniform(0),uniform(0),stage.residency(),player(0,0,0,false))),
                Boundary.OUTSIDE_OWNED_WINDOW,-1,gap,0x63A0);
    }

    @Test void offscreenLastBackgroundCellIsStillPartOfTheSelectedPhysicalImage() {
        int id=0x6980>>>5;int[] words=new int[2048];words[2047]=0xF800|id;
        failure(SonicCdAct2PastDisplay.project(input(Optional.empty(),new byte[640],uniform(0),plane(words),stage.residency(),player(0,0,0,false))),
                Boundary.UNKNOWN_PATTERN,-1,id,0x6980);
    }

    @Test void planesAndSatShareOneFrozenPatternVersionAndKeepLiteralSatGeometry() {
        var cpu=cpu(List.of(new CdObjectDrawQueue.RawPiece(2,0,0xD800,1),new CdObjectDrawQueue.RawPiece(10,0,0x2001,9)),32,128,128,new byte[640]);
        var image=scene(SonicCdAct2PastDisplay.project(input(Optional.of(cpu),cpu.bytes(),uniform(0xD800),uniform(0x9800),stage.residency(),player(0,0,0,false))));
        assertSame(image.nativePatterns().get(0),image.sprites().patternVersions().get(PatternAtlasRange.OBJECTS.base()));
        assertEquals(List.of(new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT,0x20001,1,false,false,false,9,10,8,8,true,0,false,1),
                new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT,0x20000,2,true,true,true,1,2,8,8,true,0,false,1)),image.sprites().tiles());
        assertEquals(0xD800,image.foreground().words64x32()[0]);assertEquals(0x9800,image.background().words64x32()[0]);
    }

    @Test void realSectionOverwriteCannotChangeAnOlderRetainedCurrentPatternVersion() throws Exception {
        var before=drain(art.loadSection(main,0));var after=drain(art.updateSection(before,0xB00));
        var pata=reference(CdR12bSectionSources.SourceId.ART_PATA_BATA);var mosqui=reference(CdR12bSectionSources.SourceId.ART_MOSQUI);
        int at=0;while(at<pata.length() && Arrays.equals(pata.slice(at,32).copy(),mosqui.slice(at,32).copy()))at+=32;
        assertTrue(at<pata.length(),"Actual source pair differs inside the shared physical window");int id=(0x7D40+at)>>>5;
        var first=input(Optional.empty(),new byte[640],uniform(id),uniform(id),before.residency(),player(0,0,0,false));
        var later=input(Optional.empty(),new byte[640],uniform(id),uniform(id),after.residency(),first.player());
        var selected=SonicCdAct2PastDisplay.project(first);var changed=SonicCdAct2PastDisplay.project(later);
        assertEquals(version(pata.slice(at,32)),scene(selected).nativePatterns().get(id));
        assertEquals(version(mosqui.slice(at,32)),scene(changed).nativePatterns().get(id));assertNotEquals(selected,changed);
        assertThrows(IllegalArgumentException.class,()->SonicCdAct2PastDisplay.validateSelected(selected,later));
        assertSame(before.residency(),selected.inputs().current());
    }

    @Test void laterCpuTableOrPlayerLiveFrameCannotReplaceEarlierSelectedValues() {
        var firstCpu=cpu(one(0),32,128,128,new byte[640]);var laterCpu=cpu(one(1),32,128,128,new byte[640]);
        var first=input(Optional.of(firstCpu),firstCpu.bytes(),uniform(0),uniform(0),stage.residency(),player(0x11,0x33,1,false));
        var selected=SonicCdAct2PastDisplay.project(first);
        var later=input(Optional.of(laterCpu),firstCpu.bytes(),first.foreground(),first.background(),first.current(),first.player());
        failure(SonicCdAct2PastDisplay.project(later),Boundary.SAT_MISMATCH,5,-1,-1);
        assertThrows(IllegalArgumentException.class,()->SonicCdAct2PastDisplay.validateSelected(selected,later));
        var playerScene=input(Optional.empty(),new byte[640],uniform(0x780),uniform(0x780),CdR12bCameraArtResidency.unknown(),first.player());
        var newCue=input(Optional.empty(),new byte[640],playerScene.foreground(),playerScene.background(),playerScene.current(),player(0xEE,0x33,2,true));
        assertEquals(scene(SonicCdAct2PastDisplay.project(playerScene)),scene(SonicCdAct2PastDisplay.project(newCue)));
        assertNotEquals(playerScene,newCue);
        assertThrows(IllegalArgumentException.class,()->SonicCdAct2PastDisplay.validateSelected(SonicCdAct2PastDisplay.project(playerScene),newCue));
    }

    @Test void validationComparesAllTenInputsByContentAndAcceptsEquivalentBlockedSelections() {
        var base=base();var selection=SonicCdAct2PastDisplay.project(base);
        var emptyCpu=cpu(List.of(),32,128,128,new byte[640]);byte[] changedSat=new byte[640];changedSat[639]=1;
        var variants=List.of(
                input(Optional.of(emptyCpu),base.selectedSat().copy(),base.foreground(),base.background(),base.current(),base.player()),
                input(base.selectedCpu(),changedSat,base.foreground(),base.background(),base.current(),base.player()),
                new SonicCdAct2PastDisplay.Inputs(base.selectedCpu(),base.selectedSat(),bytes(fill(128,1)),base.hscroll(),0,true,base.foreground(),base.background(),base.current(),base.player()),
                new SonicCdAct2PastDisplay.Inputs(base.selectedCpu(),base.selectedSat(),base.palette(),bytes(fill(1024,1)),0,true,base.foreground(),base.background(),base.current(),base.player()),
                new SonicCdAct2PastDisplay.Inputs(base.selectedCpu(),base.selectedSat(),base.palette(),base.hscroll(),1,true,base.foreground(),base.background(),base.current(),base.player()),
                new SonicCdAct2PastDisplay.Inputs(base.selectedCpu(),base.selectedSat(),base.palette(),base.hscroll(),0,false,base.foreground(),base.background(),base.current(),base.player()),
                input(base.selectedCpu(),base.selectedSat().copy(),uniform(1),base.background(),base.current(),base.player()),
                input(base.selectedCpu(),base.selectedSat().copy(),base.foreground(),uniform(1),base.current(),base.player()),
                input(base.selectedCpu(),base.selectedSat().copy(),base.foreground(),base.background(),main.residency(),base.player()),
                input(base.selectedCpu(),base.selectedSat().copy(),base.foreground(),base.background(),base.current(),player(1,0,0,false)),
                input(base.selectedCpu(),base.selectedSat().copy(),base.foreground(),base.background(),base.current(),player(0,1,0,false)),
                input(base.selectedCpu(),base.selectedSat().copy(),base.foreground(),base.background(),base.current(),player(0,0,1,false)),
                input(base.selectedCpu(),base.selectedSat().copy(),base.foreground(),base.background(),base.current(),player(0,0,0,true)));
        for(var variant:variants){assertNotEquals(base,variant);assertThrows(IllegalArgumentException.class,()->SonicCdAct2PastDisplay.validateSelected(selection,variant));}
        assertDoesNotThrow(()->SonicCdAct2PastDisplay.validateSelected(selection,base()));
        var unknown=input(Optional.empty(),new byte[640],uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false));
        var blocked=SonicCdAct2PastDisplay.project(unknown);assertInstanceOf(Blocked.class,blocked.projection());
        assertDoesNotThrow(()->SonicCdAct2PastDisplay.validateSelected(blocked,input(Optional.empty(),new byte[640],uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false))));
    }

    @Test void selectedArraysCollectionsAndCapturedSourceVersionsRemainImmutable() {
        byte[] sat=new byte[640],palette=assets.loadPlan().initialPalette().copy(),scroll=new byte[1024],cpuArt=fill(768,1),resident=fill(768,2);
        int[] words=new int[2048];var input=new SonicCdAct2PastDisplay.Inputs(Optional.empty(),bytes(sat),bytes(palette),bytes(scroll),0,true,plane(words),plane(words),stage.residency(),new CdPlayerArtRuntime.State(cpuArt,resident,0,false));
        var selected=SonicCdAct2PastDisplay.project(input);var picture=scene(selected);sat[0]=1;palette[0]^=1;scroll[0]=1;words[0]=1;cpuArt[0]=3;resident[0]=4;
        assertEquals(0,picture.foreground().words64x32()[0]);assertEquals(0,picture.packedHscroll256()[0]);assertEquals(0,input.selectedSat().unsignedByte(0));
        assertEquals(1,input.player().cpuBuffer().unsignedByte(0));assertEquals(2,input.player().residentBank().unsignedByte(0));
        picture.foreground().words64x32()[0]=1;picture.selectedPalette128()[0]^=1;picture.packedHscroll256()[0]=1;
        assertEquals(0,picture.foreground().words64x32()[0]);assertArrayEquals(input.palette().copy(),picture.selectedPalette128());
        assertThrows(UnsupportedOperationException.class,()->picture.nativePatterns().clear());assertThrows(UnsupportedOperationException.class,()->picture.sprites().tiles().add(null));
    }

    @Test void commonProjectionKindsAndFiniteBlockedReplayMetadataAreReusedWithoutNewTypes() {
        var unknown=input(Optional.empty(),new byte[640],uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false));
        var first=SonicCdAct2PastDisplay.project(unknown);var second=SonicCdAct2PastDisplay.project(input(Optional.empty(),new byte[640],uniform(0),uniform(0),CdR12bCameraArtResidency.unknown(),player(0,0,0,false)));
        assertEquals(first,second);assertEquals(first.hashCode(),second.hashCode());
        assertEquals(new DisplayFailure(Boundary.UNKNOWN_PATTERN,-1,0,0),assertInstanceOf(Blocked.class,first.projection()).failure());
        var disabled=new Disabled(new RetainedPlaneSceneRenderer.Scene(null,null,new int[256],0,new byte[128],false,SpritePresentation.Frame.empty(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new SonicCdAct2PastDisplay.Selection(unknown,disabled));
        assertThrows(NullPointerException.class,()->SonicCdAct2PastDisplay.project(null));
        assertThrows(NullPointerException.class,()->SonicCdAct2PastDisplay.validateSelected(null,unknown));
    }

    private static SonicCdAct2PastDisplay.Inputs base(){return input(Optional.empty(),new byte[640],uniform(0),uniform(0),stage.residency(),player(0,0,0,false));}
    private static SonicCdAct2PastDisplay.Inputs input(Optional<CdObjectDrawQueue.CpuSat> cpu,byte[] sat,CdR11aBackgroundPlane.State fg,CdR11aBackgroundPlane.State bg,CdR12bCameraArtResidency.State current,CdPlayerArtRuntime.State player){
        return new SonicCdAct2PastDisplay.Inputs(cpu,bytes(sat),assets.loadPlan().initialPalette(),bytes(new byte[1024]),0,true,fg,bg,current,player);
    }
    private static CdR12bSectionArt.State drain(CdR12bSectionArt.State state){int calls=0;while(state.queue().headSource()!=0 || state.remainingTilesWord()!=0){state=art.prepare(state);state=art.serviceFast(state);assertTrue(++calls<2000);}return state;}
    private static CdAssetBytes reference(CdR12bSectionSources.SourceId id) throws Exception {var s=assets.sectionSources().source(id);var result=CdNemesisAsset.decode(assets.sectionSources().view(id,CdR12bSectionSources.ReaderKind.JAVA).copy(),s.outputBytes(),s.outputBytes());return result.bytes();}
    /** Sixteen native palette nibbles are stored least-significant first in each version word. */
    private static SpritePresentation.PatternVersion version(CdAssetBytes bytes){
        String hex=java.util.HexFormat.of().formatHex(bytes.copy());assertEquals(64,hex.length());long[] words=new long[4];
        for(int i=0;i<4;i++)words[i]=Long.parseUnsignedLong(new StringBuilder(hex.substring(i*16,i*16+16)).reverse().toString(),16);
        return new SpritePresentation.PatternVersion(words[0],words[1],words[2],words[3]);
    }
    private static CdR11aBackgroundPlane.State plane(int[] words){assertEquals(2048,words.length);var raw=ByteBuffer.allocate(4096);for(int word:words)raw.putShort((short)word);return new CdR11aBackgroundPlane.State(bytes(raw.array()));}
    private static CdR11aBackgroundPlane.State uniform(int word){int[] values=new int[2048];Arrays.fill(values,word);return plane(values);}
    private static CdPlayerArtRuntime.State player(int cpu,int resident,int frame,boolean dirty){return new CdPlayerArtRuntime.State(fill(768,cpu),fill(768,resident),frame,dirty);}
    private static byte[] fill(int size,int value){byte[] bytes=new byte[size];Arrays.fill(bytes,(byte)value);return bytes;}
    private static CdAssetBytes bytes(byte[] bytes){return CdAssetBytes.copyOf(bytes);}
    private static List<CdObjectDrawQueue.RawPiece> one(int id){return List.of(new CdObjectDrawQueue.RawPiece(0,0,id,0));}
    private static RetainedPlaneSceneRenderer.Scene scene(SonicCdAct2PastDisplay.Selection selected){return assertInstanceOf(Ready.class,selected.projection()).scene();}
    private static void failure(SonicCdAct2PastDisplay.Selection selected,Boundary kind,int offset,int id,int destination){assertEquals(new DisplayFailure(kind,offset,id,destination),assertInstanceOf(Blocked.class,selected.projection()).failure());}

    /** Existing public queue factory with explicit component ports and no private authority fabrication. */
    private static CdObjectDrawQueue.CpuSat cpu(List<CdObjectDrawQueue.RawPiece> pieces, int physicalSlot,
                                               int xWord, int screenYWord, byte[] initialSat) {
        var ref = new CdObjectDrawQueue.CdSlotRef(physicalSlot);
        int[] flags = {0};
        var slots = new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef queried) {
                assertEquals(ref, queried);
                return new CdObjectDrawQueue.LiveDrawSlot(1, flags[0], xWord, screenYWord, screenYWord,
                        0x200000, 0, 0);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef queried, boolean value) {
                assertEquals(ref, queried);
                flags[0] = (flags[0] & ~0x80) | (value ? 0x80 : 0);
            }
        };
        CdObjectDrawQueue.CdDrawMappings mappings = (address, staticPiece, frame) -> {
            assertEquals(0x200000, address);
            assertFalse(staticPiece);
            assertEquals(0, frame);
            return new CdObjectDrawQueue.FrameMapping(pieces.size(), pieces);
        };
        var camera = new CdObjectDrawQueue.CameraWords(0, 0);
        try (var queue = new CdObjectDrawQueue(slots, mappings, initialSat)) {
            queue.beginPass();
            assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED, queue.drawObject(
                    new CdObjectDrawQueue.DrawCallCut(ref, 0, xWord, screenYWord, 16, 16, 2), camera));
            var output = queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera, camera, camera));
            assertEquals(CdObjectDrawQueue.Phase.CLOSED_CUT, queue.phase());
            assertSame(output, queue.cpuSat());
            return output;
        }
    }
}

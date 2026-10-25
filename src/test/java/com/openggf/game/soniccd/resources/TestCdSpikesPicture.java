package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source/picture specifications only. Author has run no Java, media loader, native, GL or test command. */
class TestCdSpikesPicture {
    private static final int MAP=0x20C1F0, SPIKES_BASE=12521;
    private static final int[] FIRST={0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490};
    private static final int[] COUNT={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46};
    private static final int[] BASE={10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374,12456,12475};
    // N Spikes/Data/Mappings.asm:1-23 and Main.asm:87-89, independently transcribed full include/EVEN.
    private static final byte[] RAW=HexFormat.of().parseHex("000204f0050000f000050004f0f005000000000500040000");
    private static final int[][] PIECES={{-16,5,0,-16},{0,5,4,-16},{-16,5,0,0},{0,5,4,0}};

    @Test void newPublicSurfaceIsOnlyTheAcceptedFinalIntOverloadAndHasNoCheckedException() {
        var arities=new TreeSet<Integer>();
        for(var method:CdObjectPresentation.class.getDeclaredMethods())if(method.getName().equals("usaR11aCurrentCameraDevPreloadedArt")) {
            arities.add(method.getParameterCount());assertEquals(0,method.getExceptionTypes().length);
            assertEquals(CdObjectPresentation.PatternSource.class,method.getReturnType());
            assertEquals(int.class,method.getParameterTypes()[method.getParameterCount()-1]);
        }
        assertEquals(new TreeSet<>(List.of(23,24,25,26,27)),arities);
    }

    @Test void all567NativeAndLogicalBindingsKeepEveryOld559IdAndItsExactCurrentVersion() throws Exception {
        var f=fixture();var state=seeded(f);var old=oldBank(f,state);var now=bank(f,state,SPIKES_BASE);
        var natives=new TreeSet<Integer>();var logicals=new TreeSet<Integer>();
        for(int span=0;span<FIRST.length;span++)for(int t=0;t<COUNT[span];t++) {
            int id=FIRST[span]+t;var sample=now.resolve(id);assertEquals(old.resolve(id),sample);
            assertEquals(BASE[span]+t,sample.logicalPatternId());assertEquals(expectedOld(f,state,span,t),sample.version());
            assertTrue(natives.add(id));assertTrue(logicals.add(sample.logicalPatternId()));
        }
        assertEquals(559,natives.size());assertEquals(559,logicals.size());
        for(int t=0;t<8;t++) {
            int id=0x320+t;var sample=now.resolve(id);assertEquals(SPIKES_BASE+t,sample.logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(state,id)),sample.version());
            assertTrue(natives.add(id));assertTrue(logicals.add(sample.logicalPatternId()));
            assertThrows(UnsupportedOperationException.class,()->old.resolve(id));
        }
        assertEquals(567,natives.size());assertEquals(567,logicals.size());assertEquals(15,FIRST.length);
        for(int i=0;i<FIRST.length;i++)assertTrue(0x328<=FIRST[i]||FIRST[i]+COUNT[i]<=0x320,"Spikes must not overlap old physical ranges");
        assertEquals(BASE[11],now.resolve(0x328).logicalPatternId(),"adjacent Tunnel Door retains its own binding");
        for(int id=0;id<0x800;id++)if(!natives.contains(id)) {int absent=id;assertThrows(UnsupportedOperationException.class,()->now.resolve(absent));}
        for(int bad:new int[]{-1,0x800,0x8320})assertThrows(IllegalArgumentException.class,()->now.resolve(bad));
        for(int id=0x328;id<0x370;id++)if(!natives.contains(id)) {int gap=id;assertThrows(UnsupportedOperationException.class,()->now.resolve(gap));}
    }

    @Test void oldC25StillWorksButC26ReadsFirstAndLastUnknownSpikesBeforeAnyEmptySat() throws Exception {
        var f=fixture();var legacy=diagonalSeed(f);var old=oldBank(f,legacy);
        assertThrows(UnsupportedOperationException.class,()->old.resolve(0x320));
        assertThrows(UnsupportedOperationException.class,()->old.resolve(0x327));
        var first=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,legacy,SPIKES_BASE));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,first.boundary());assertEquals(0x320,first.nativePatternId());
        // Actual eight-tile decode is supplied only at explicit test DEV ingress; the picture never decodes it.
        var seven=write(legacy,0x6400,f.objects().sectionArtSources().source(CdR11aAsset.ART_SPIKES).decode().bytes(),7);
        var last=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,seven,SPIKES_BASE));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,last.boundary());assertEquals(0x327,last.nativePatternId());
        assertTrue(CdObjectPresentation.convert(emptySat(),old).tiles().isEmpty());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,seven,SPIKES_BASE));
        assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,seeded(f),SPIKES_BASE)).tiles().isEmpty());
        assertArrayEquals(new byte[256],legacy.region(CdR11aCameraArtResidency.Region.AT_6400).knownMask().copy());
    }

    @Test void allFifteenOldLogicalSpansAndBothNewRangeEndsAreValidatedEagerly() throws Exception {
        var f=fixture();var state=seeded(f);
        for(int bad:new int[]{-1,Integer.MAX_VALUE-6,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->bank(f,state,bad));
        for(int i=0;i<BASE.length;i++)for(int overlap:new int[]{BASE[i]+COUNT[i]-1,BASE[i]-7})
            assertThrows(IllegalArgumentException.class,()->bank(f,state,overlap));
        assertEquals(Integer.MAX_VALUE,bank(f,state,Integer.MAX_VALUE-7).resolve(0x327).logicalPatternId());
        assertEquals(0,bank(f,state,0).resolve(0x320).logicalPatternId());
        assertThrows(NullPointerException.class,()->bank(f,null,SPIKES_BASE));
    }

    @Test void replacementOfCurrent320FreezesNewBytesAndNeverFallsBackToOriginalProducer() throws Exception {
        var f=fixture();var state=seeded(f);var before=bank(f,state,SPIKES_BASE);
        var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var sat=sat(mappings,0,0x0320);
        var frozen=CdObjectPresentation.convert(sat,before);
        byte[] replacement=CdR11aCameraArtResidency.readTile(state,0x320).copy();replacement[0]^=0x5A;
        var bytes=CdAssetBytes.copyOf(replacement);var changed=CdR11aCameraArtResidency.writeTile(state,0x6400,bytes);
        var after=bank(f,changed,SPIKES_BASE);var expected=CdObjectPresentation.patternVersion(bytes);
        assertEquals(expected,after.resolve(0x320).version());assertNotEquals(before.resolve(0x320).version(),expected);
        for(int t=1;t<8;t++)assertEquals(before.resolve(0x320+t),after.resolve(0x320+t));
        for(int i=0;i<FIRST.length;i++)for(int t=0;t<COUNT[i];t++)assertEquals(before.resolve(FIRST[i]+t),after.resolve(FIRST[i]+t));
        var picture=CdObjectPresentation.convert(sat,after);assertEquals(expected,picture.patternVersions().get(SPIKES_BASE));assertNotEquals(frozen,picture);
        replacement[0]^=0x7F;assertEquals(expected,after.resolve(0x320).version());
        assertEquals(frozen,CdObjectPresentation.convert(sat,before));assertEquals(frozen,CdObjectPresentation.convert(sat,bank(f,state,SPIKES_BASE)));
        assertEquals(changed,CdR11aCameraArtResidency.writeTile(state,0x6400,bytes));
    }

    @Test void c26KeepsNowPlayerPowerupAndCurrentCameraWindowsRatherThanPinningOldVersions() throws Exception {
        var f=fixture();var state=seeded(f);var old=bank(f,state,SPIKES_BASE);
        byte[] player=f.player().residentBank().copy(),powerup=f.powerup().residentBank().copy();player[0]^=0x12;powerup[0]^=0x34;
        var later=new Fixture(f.assets(),f.objects(),new CdPlayerArtRuntime.State(f.player().cpuBuffer().copy(),player,0,false),
                new CdPowerupArtRuntime.State(f.powerup().cpuBuffer().copy(),powerup));
        byte[] camera=CdR11aCameraArtResidency.readTile(state,0x3A0).copy();camera[0]^=0x56;
        var next=CdR11aCameraArtResidency.writeTile(state,0x7400,CdAssetBytes.copyOf(camera));
        var now=bank(later,next,SPIKES_BASE);var c25=oldBank(later,next);
        for(int i=0;i<FIRST.length;i++)for(int t=0;t<COUNT[i];t++)assertEquals(c25.resolve(FIRST[i]+t),now.resolve(FIRST[i]+t));
        assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(player).slice(0,32)),now.resolve(0x780).version());
        assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(powerup).slice(0,32)),now.resolve(0x544).version());
        assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(camera)),now.resolve(0x3A0).version());
        for(int id:new int[]{0x780,0x544,0x3A0})assertNotEquals(old.resolve(id).version(),now.resolve(id).version());
        for(int t=0;t<8;t++)assertEquals(old.resolve(0x320+t),now.resolve(0x320+t));
    }

    @Test void actualMappingQueuesFourPiecesAndConvertsSixteenCellsEightPatternsWithEveryMirrorAndAttribute() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var source=bank(f,seeded(f),SPIKES_BASE);
        assertArrayEquals(RAW,f.objects().view(CdR11aObjectAssets.Table.SPIKES_MAPPINGS).bytes().copy());
        assertEquals(1,f.objects().spikes().mappingFrames().size());assertTrue(f.objects().spikes().animations().isEmpty());
        int[][] x={{0x170,0x170,0x180,0x180},{0x180,0x180,0x170,0x170}};
        int[][] y={{0xF0,0x100,0xF0,0x100},{0x100,0xF0,0x100,0xF0}};
        for(int base:new int[]{0x0320,0xA320,0x6320,0xDB20})for(int flip=0;flip<4;flip++) {
            var sat=sat(mappings,flip,base);assertEquals(4,sat.spriteCount());byte[] raw=sat.bytes();
            for(int p=0;p<4;p++) {
                assertEquals(x[flip&1][p],word(raw,p*8+6));assertEquals(y[(flip>>>1)&1][p],word(raw,p*8));
                assertEquals(5,raw[p*8+2]&255);assertEquals(p+1,raw[p*8+3]&255);
                assertEquals(((base+PIECES[p][2])&0xFFFF)^((flip&3)<<11),word(raw,p*8+4));
                assertEquals(new Provenance(3,0,new CdSlotRef(61),p),sat.provenance().get(p));
            }
            assertArrayEquals(new byte[4],Arrays.copyOfRange(raw,32,36));
            var frame=CdObjectPresentation.convert(sat,source);assertEquals(16,frame.tiles().size());assertEquals(8,frame.patternVersions().size());
            var ids=new TreeSet<Integer>();
            // Reverse complete native pieces; each literal size05 is 2x2, with column-major pattern offsets.
            for(int piece=3;piece>=0;piece--)for(int row=0;row<2;row++)for(int col=0;col<2;col++) {
                int at=(3-piece)*4+row*2+col;var tile=frame.tiles().get(at);
                int word=((base+PIECES[piece][2])&0xFFFF)^((flip&3)<<11);
                boolean hf=(word&0x800)!=0,vf=(word&0x1000)!=0;
                int id=SPIKES_BASE+PIECES[piece][2]+col*2+row;ids.add(id);
                assertEquals(id,tile.patternId());assertEquals((word>>>13)&3,tile.palette());assertEquals((word&0x8000)!=0,tile.priority());
                assertEquals(hf,tile.hFlip());assertEquals(vf,tile.vFlip());
                assertEquals(x[flip&1][piece]-128+(hf?1-col:col)*8,tile.x());
                assertEquals(y[(flip>>>1)&1][piece]-128+(vf?1-row:row)*8,tile.y());
                assertEquals(source.resolve(0x320+id-SPIKES_BASE).version(),frame.patternVersions().get(id));
            }
            assertEquals(8,ids.size());
        }
    }

    @Test void actualStdQueueServicesTheEightTilePrefixAndReplaysItsPartialSnapshotsWithoutAnySeedFallback() throws Exception {
        var f=fixture();var source=f.objects().sectionArtSources().source(CdR11aAsset.ART_SPIKES);
        assertEquals(10,f.objects().sectionArtSources().sources().size());assertEquals(1,source.canonicalPlcId());assertEquals(0,source.canonicalRowIndex());
        assertEquals(0x238018,source.canonicalPlc().sourceRange().nativeAddress());assertEquals(0x6400,source.canonicalPlc().vramAddress());
        assertEquals(0x8008,source.readerBytes().unsignedWord(0));assertEquals(8,source.tileCount());
        assertEquals(86,source.decode().consumedInputBytes());assertEquals(87,source.readerBytes().length());
        var owner=CdR11aSectionArt.fromAssets(f.assets());var ingress=owner.devState(diagonalSeed(f),CdAssetBytes.copyOf(new byte[512]),0);
        var queued=owner.initList(ingress,1);assertEquals(0x238018L,queued.queue().headSource());assertEquals(0x6400,queued.queue().headDestination());
        var ready=owner.prepare(queued);assertEquals(8,ready.remainingTilesWord());assertEquals(ingress.residency(),ready.residency());
        var first=owner.serviceSlow(ready,0);assertEquals(5,first.remainingTilesWord());assertEquals(0x6460,first.queue().headDestination());
        var second=owner.serviceSlow(first,0);assertEquals(2,second.remainingTilesWord());assertEquals(0x64C0,second.queue().headDestination());
        var done=owner.serviceSlow(second,0);assertEquals(0,done.remainingTilesWord());assertEquals(2,done.processBudgetWord());assertEquals(second.savedDecoder(),done.savedDecoder());
        assertEquals(0x23806FL,done.decoder().orElseThrow().sourcePointer());
        var next=f.assets().loadPlan().plcs().get(1).entries().get(1);
        assertEquals(Integer.toUnsignedLong(next.sourceRange().nativeAddress()),done.queue().headSource());assertEquals(next.vramAddress(),done.queue().headDestination());
        for(int cut=0;cut<3;cut++) {
            var state=List.of(first,second,done).get(cut);int known=new int[]{3,6,8}[cut];
            for(int t=0;t<known;t++)assertArrayEquals(source.decode().bytes().slice(t*32,32).copy(),CdR11aCameraArtResidency.readTile(state.residency(),0x320+t).copy());
            if(known<8) {int unknown=0x320+known;assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(state.residency(),unknown));}
        }
        assertEquals(done,owner.serviceSlow(owner.serviceSlow(first,0),0));
        var fast=owner.serviceFast(ready);assertEquals(0,fast.remainingTilesWord());assertEquals(11,fast.processBudgetWord());assertEquals(done.residency(),fast.residency());
        assertEquals(567,countBindings(bank(f,done.residency(),SPIKES_BASE)));
        var outside=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(done.residency(),0x328));
        assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,outside.boundary());
    }

    private record Fixture(CdR11aAssets assets,CdR11aObjectAssets objects,CdPlayerArtRuntime.State player,CdPowerupArtRuntime.State powerup) { }
    private static Fixture fixture() throws Exception {
        String configured=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(configured!=null&&!configured.isBlank(),"Set soniccd.media.root for retained actual USA assets");
        Path cue;try(var files=Files.list(Path.of(configured).resolve("USA"))) {var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        CdR11aAssets assets;try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {assets=CdR11aAssetLoader.load(programs,CdReleaseVariant.USA,CdStageKey.parse("R11A"));}
        // Explicit DEV RAM ingress, never authenticated glyphs or a native cold-state assertion.
        return new Fixture(assets,assets.openingObjects(),new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]));
    }
    private static CdR11aCameraArtResidency.State diagonalSeed(Fixture f) throws Exception {
        var o=f.objects();var state=CdR11aCameraArtResidency.devSeed(o.initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_PLANT).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
        state=write(state,0x6E80,o.sectionArtSources().source(CdR11aAsset.ART_BOULDER).decode().bytes(),19);
        return write(state,0x9200,o.sectionArtSources().source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes(),46);
    }
    private static CdR11aCameraArtResidency.State seeded(Fixture f) throws Exception {
        return write(diagonalSeed(f),0x6400,f.objects().sectionArtSources().source(CdR11aAsset.ART_SPIKES).decode().bytes(),8);
    }
    private static CdR11aCameraArtResidency.State write(CdR11aCameraArtResidency.State state,int destination,CdAssetBytes bytes,int count) {
        for(int t=0;t<count;t++)state=CdR11aCameraArtResidency.writeTile(state,destination+t*32,bytes.slice(t*32,32));return state;
    }
    private static CdObjectPresentation.PatternSource oldBank(Fixture f,CdR11aCameraArtResidency.State state) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14]);
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int spikes) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],spikes);
    }
    private static com.openggf.graphics.SpritePresentation.PatternVersion expectedOld(Fixture f,CdR11aCameraArtResidency.State state,int span,int tile) throws Exception {
        var o=f.objects();CdAssetBytes bytes=switch(span) {
            case 0->f.player().residentBank();case 1->o.initialArtBank().binding(CdR11aAsset.ART_RINGS).bytes();
            case 2,3,4,13,14->CdR11aCameraArtResidency.readTile(state,FIRST[span]+tile);
            case 5->o.contactArtBank().binding(CdR11aAsset.ART_EXPLOSIONS).bytes();case 6->o.contactArtBank().binding(CdR11aAsset.ART_POINTS).bytes();
            case 7->o.contactArtBank().binding(CdR11aAsset.ART_FLOWER).bytes();case 8->f.powerup().residentBank();
            case 9->o.floatBlockArtBank().binding(CdR11aAsset.ART_FLOAT_BLOCK).bytes();case 10->o.springArtBank().binding(CdR11aAsset.ART_SPRINGS).bytes();
            case 11->o.tunnelDoorArtBank().binding(CdR11aAsset.ART_TUNNEL_DOOR).bytes();case 12->o.monitorTimeArtBank().binding(CdR11aAsset.ART_MONITOR_TIME_POSTS).bytes();
            default->throw new AssertionError("Unknown old span");
        };
        return CdObjectPresentation.patternVersion(bytes.length()==32?bytes:bytes.slice(tile*32,32));
    }
    private static int countBindings(CdObjectPresentation.PatternSource source) {
        int count=0;for(int id=0;id<0x800;id++)try {source.resolve(id);count++;}catch(UnsupportedOperationException expected) { }return count;
    }
    private static CpuSat sat(CdDrawMappings mappings,int flip,int base) {
        var slots=new EmptySlots(){LiveDrawSlot live=new LiveDrawSlot(0x26,4|flip,0x100,0x80,0,MAP,0,base);
            @Override public LiveDrawSlot read(CdSlotRef ref){assertEquals(61,ref.wholeIndex());return live;}
            @Override public void writeOnScreenBit(CdSlotRef ref,boolean on){assertEquals(61,ref.wholeIndex());var s=live;live=new LiveDrawSlot(s.idByte(),(s.spriteFlagsByte()&127)|(on?128:0),s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());}
        };
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE])) {
            var camera=new CameraWords(0,0);queue.beginPass();assertEquals(EnqueueResult.QUEUED,queue.drawObject(new DrawCallCut(new CdSlotRef(61),4|flip,0x100,0x80,8,16,3),camera));return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));
        }
    }
    private static CpuSat emptySat() {
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(new EmptySlots(),(a,b,c)->{throw new AssertionError("No mapping read for empty SAT");},new byte[SAT_BYTE_SIZE])) {
            var camera=new CameraWords(0,0);queue.beginPass();return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));
        }
    }
    private static class EmptySlots implements CdDrawSlots {
        @Override public LiveDrawSlot read(CdSlotRef ref){throw new AssertionError("No slot read expected");}
        @Override public void writeOnScreenBit(CdSlotRef ref,boolean on){throw new AssertionError("No slot write expected");}
    }
    private static int word(byte[] bytes,int at){return ((bytes[at]&255)<<8)|(bytes[at+1]&255);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

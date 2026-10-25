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

/** Disc-backed current Checkpoint picture and declared single-row PLC specifications. */
class TestCdCheckpointPicture {
    private static final int MAP=0x23F120,SPIKES_BASE=12521,CHECKPOINT_BASE=12529;
    private static final int[] FIRST={0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490};
    private static final int[] COUNT={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46};
    private static final int[] BASE={10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374,12456,12475};
    private static final byte[] RAW=HexFormat.of().parseHex("0008001e002a003604e8030000f8e80308000008010004f808010804000002f8010006f8f8010806000002f8010008f8f8010808000002f801000af8f801080a0000");
    private static final int[][][] PIECES={{{-24,3,0,-8},{-24,3,0x800,0},{8,1,4,-8},{8,1,0x804,0}},{{-8,1,6,-8},{-8,1,0x806,0}},{{-8,1,8,-8},{-8,1,0x808,0}},{{-8,1,10,-8},{-8,1,0x80A,0}}};

    @Test void all579PatternsPreserveThePrevious567BindingsAndRejectEveryOtherNativeId() throws Exception {
        var f=fixture();var state=completeSeed(f);var old=previousBank(f,state,SPIKES_BASE);var now=bank(f,state,CHECKPOINT_BASE);
        var natives=new TreeSet<Integer>();var logical=new TreeSet<Integer>();
        for(int span=0;span<FIRST.length;span++)for(int t=0;t<COUNT[span];t++) {
            int id=FIRST[span]+t;var sample=now.resolve(id);assertEquals(old.resolve(id),sample);
            assertEquals(BASE[span]+t,sample.logicalPatternId());assertTrue(natives.add(id));assertTrue(logical.add(sample.logicalPatternId()));
        }
        for(int t=0;t<8;t++) {int id=0x320+t;var sample=now.resolve(id);assertEquals(old.resolve(id),sample);
            assertEquals(SPIKES_BASE+t,sample.logicalPatternId());assertTrue(natives.add(id));assertTrue(logical.add(sample.logicalPatternId()));}
        for(int t=0;t<12;t++) {int id=0x6CB+t;var sample=now.resolve(id);
            assertEquals(CHECKPOINT_BASE+t,sample.logicalPatternId());assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(state,id)),sample.version());
            assertThrows(UnsupportedOperationException.class,()->old.resolve(id));assertTrue(natives.add(id));assertTrue(logical.add(sample.logicalPatternId()));}
        assertEquals(579,natives.size());assertEquals(579,logical.size());
        for(int id=0;id<2048;id++)if(!natives.contains(id)) {int absent=id;assertThrows(UnsupportedOperationException.class,()->now.resolve(absent));}
        // Adjacent Points and Flower keep the original bindings in the same current policy.
        assertEquals(old.resolve(0x6CA),now.resolve(0x6CA));assertEquals(old.resolve(0x6D7),now.resolve(0x6D7));
    }

    @Test void allSixteenLogicalSpansAndBothCheckpointRangeEndsAreCheckedBeforeEmptySat() throws Exception {
        var f=fixture();var state=completeSeed(f);
        for(int bad:new int[]{-1,Integer.MAX_VALUE-10,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->bank(f,state,bad));
        for(int i=0;i<BASE.length;i++)for(int base:new int[]{BASE[i]-11,BASE[i]+COUNT[i]-1})
            assertThrows(IllegalArgumentException.class,()->bank(f,state,base));
        for(int base:new int[]{SPIKES_BASE-11,SPIKES_BASE+7})assertThrows(IllegalArgumentException.class,()->bank(f,state,base));
        assertEquals(Integer.MAX_VALUE,bank(f,state,Integer.MAX_VALUE-11).resolve(0x6D6).logicalPatternId());
        assertEquals(0,bank(f,state,0).resolve(0x6CB).logicalPatternId());
        assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,state,CHECKPOINT_BASE)).tiles().isEmpty());
    }

    @Test void firstAndLastUnknownCheckpointRejectEagerlyWithoutChangingTheOldPolicy() throws Exception {
        var f=fixture();var before=seeded(f);var old=previousBank(f,before,SPIKES_BASE);
        assertTrue(CdObjectPresentation.convert(emptySat(),old).tiles().isEmpty());
        var first=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,before,CHECKPOINT_BASE));
        assertEquals(0x6CB,first.nativePatternId());
        var partial=write(before,0xD960,f.objects().sectionArtSources().source(CdR11aAsset.ART_CHECKPOINT).decode().bytes(),11);
        var last=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,partial,CHECKPOINT_BASE));
        assertEquals(0x6D6,last.nativePatternId());assertArrayEquals(new byte[384],before.region(CdR11aCameraArtResidency.Region.AT_D960).knownMask().copy());
    }

    @Test void changedCurrentCheckpointBytesFreezeTheNewPictureRatherThanReselectingProducerArt() throws Exception {
        var f=fixture();var state=completeSeed(f);var before=bank(f,state,CHECKPOINT_BASE);
        byte[] replacement=CdR11aCameraArtResidency.readTile(state,0x6CB).copy();replacement[0]^=0x5A;
        var expected=CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(replacement));
        var changed=CdR11aCameraArtResidency.writeTile(state,0xD960,CdAssetBytes.copyOf(replacement));var now=bank(f,changed,CHECKPOINT_BASE);
        assertEquals(expected,now.resolve(0x6CB).version());assertNotEquals(before.resolve(0x6CB),now.resolve(0x6CB));
        for(int id=0;id<2048;id++) {if(id==0x6CB)continue;try {assertEquals(before.resolve(id),now.resolve(id));}catch(UnsupportedOperationException ex) {int absent=id;assertThrows(UnsupportedOperationException.class,()->now.resolve(absent));}}
        replacement[0]^=0x7F;assertEquals(expected,now.resolve(0x6CB).version());
        assertEquals(before.resolve(0x6CB),bank(f,state,CHECKPOINT_BASE).resolve(0x6CB));
    }

    @Test void actualFourFrameMappingsRetainLiteralPiecesAndOrderedConvertedAttributesForAllFlips() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var source=bank(f,completeSeed(f),CHECKPOINT_BASE);
        assertArrayEquals(RAW,f.objects().view(CdR11aObjectAssets.Table.CHECKPOINT_MAPPINGS).bytes().copy());
        for(int frame=0;frame<4;frame++)for(int flip=0;flip<4;flip++) {
            int[][] pieces=PIECES[frame];var decoded=mappings.resolve(MAP,false,frame).pieces();assertEquals(pieces.length,decoded.size());
            for(int p=0;p<pieces.length;p++)assertEquals(new RawPiece(pieces[p][0],pieces[p][1],pieces[p][2],pieces[p][3]),decoded.get(p));
            var sat=sat(mappings,frame,flip,0xA6CB);assertEquals(pieces.length,sat.spriteCount());byte[] raw=sat.bytes();int cells=0;
            for(int p=0;p<pieces.length;p++) {
                int[] piece=pieces[p];int width=((piece[1]>>>2)+1)*8,height=((piece[1]&3)+1)*8;
                int x=0x180+((flip&1)==0?piece[3]:-piece[3]-width),y=0x100+((flip&2)==0?piece[0]:-piece[0]-height);
                int word=((0xA6CB+piece[2])&0xFFFF)^((flip&3)<<11);
                assertEquals(x,word(raw,p*8+6));assertEquals(y,word(raw,p*8));assertEquals(word,word(raw,p*8+4));
                assertEquals(piece[1],raw[p*8+2]&255);assertEquals(p+1,raw[p*8+3]&255);cells+=(width/8)*(height/8);
            }
            var picture=CdObjectPresentation.convert(sat,source);assertEquals(cells,picture.tiles().size());int at=0;
            for(int p=pieces.length-1;p>=0;p--) {
                int[] piece=pieces[p];int cols=(piece[1]>>>2)+1,rows=(piece[1]&3)+1;
                int tileWord=((0xA6CB+piece[2])&0xFFFF)^((flip&3)<<11);boolean h=(tileWord&0x800)!=0,v=(tileWord&0x1000)!=0;
                for(int row=0;row<rows;row++)for(int col=0;col<cols;col++) {
                    var tile=picture.tiles().get(at++);int id=(tileWord&0x7FF)+col*rows+row;
                    assertEquals(CHECKPOINT_BASE+id-0x6CB,tile.patternId());assertEquals((tileWord>>>13)&3,tile.palette());assertTrue(tile.priority());assertEquals(h,tile.hFlip());assertEquals(v,tile.vFlip());
                    assertEquals(word(raw,p*8+6)-128+(h?cols-1-col:col)*8,tile.x());assertEquals(word(raw,p*8)-128+(v?rows-1-row:row)*8,tile.y());
                    assertEquals(source.resolve(id).version(),picture.patternVersions().get(tile.patternId()));
                }
            }
        }
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(MAP,false,4));
    }

    @Test void nativeLevelListStopsAtEarlierUnownedTerrainInsteadOfSilentlySkippingToCheckpoint() throws Exception {
        var f=fixture();var owner=CdR11aSectionArt.fromAssets(f.assets());var initial=owner.devState(seeded(f),CdAssetBytes.copyOf(new byte[512]),0);
        var queued=owner.initList(initial,0);var first=f.assets().loadPlan().plcs().get(0).entries().getFirst();
        assertEquals(Integer.toUnsignedLong(first.sourceRange().nativeAddress()),queued.queue().headSource());
        var stop=assertThrows(CdR11aSectionArt.SectionArtException.class,()->owner.prepare(queued));
        assertEquals(CdR11aSectionArt.Reason.UNKNOWN_SOURCE,stop.reason());assertEquals(queued.residency(),stop.state().residency());
        assertThrows(IllegalStateException.class,()->owner.prepare(stop.state()));
    }

    @Test void actualCheckpointSourceServicesFourSlowCutsAndOneFastCutInAnExplicitSingleRowComponentList() throws Exception {
        var f=fixture();var source=f.objects().sectionArtSources().source(CdR11aAsset.ART_CHECKPOINT);
        assertEquals(11,f.objects().sectionArtSources().sources().size());assertEquals(0,source.canonicalPlcId());assertEquals(1,source.canonicalRowIndex());
        assertEquals(0x235DC6,source.canonicalPlc().sourceRange().nativeAddress());assertEquals(0xD960,source.canonicalPlc().vramAddress());
        assertEquals(0x800C,source.readerBytes().unsignedWord(0));assertEquals(211,source.decode().consumedInputBytes());assertEquals(212,source.readerBytes().length());
        var actual=f.assets().loadPlan().plcs().get(0);
        var declared=new com.openggf.game.soniccd.level.CdR11aLoadPlan.PlcList(0,actual.bodyRange(),List.of(source.canonicalPlc()));
        var owner=new CdR11aSectionArt(List.of(declared),f.assets().loadPlan().sections(),List.of(new CdR11aSectionArt.SourceProgram(source.canonicalPlc(),source.readerBytes())));
        var before=owner.devState(seeded(f),CdAssetBytes.copyOf(new byte[512]),0);var ready=owner.prepare(owner.initList(before,0));
        assertEquals(12,ready.remainingTilesWord());assertEquals(before.residency(),ready.residency());var cuts=new java.util.ArrayList<CdR11aSectionArt.State>();cuts.add(ready);
        for(int i=0;i<4;i++)cuts.add(owner.serviceSlow(cuts.getLast(),0));
        for(int cut=0;cut<=4;cut++) {
            var state=cuts.get(cut);assertEquals(12-cut*3,state.remainingTilesWord());
            for(int tile=0;tile<cut*3;tile++)assertArrayEquals(source.decode().bytes().slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(state.residency(),0x6CB+tile).copy());
            var replay=state;for(int i=cut;i<4;i++)replay=owner.serviceSlow(replay,0);assertEquals(cuts.getLast(),replay);
        }
        var done=cuts.getLast();assertEquals(1,done.processBudgetWord());assertEquals(0,done.queue().headSource());
        assertEquals(cuts.get(3).savedDecoder(),done.savedDecoder());assertEquals(0x235E9AL,done.decoder().orElseThrow().sourcePointer());
        var fast=owner.serviceFast(ready);assertEquals(done.residency(),fast.residency());assertEquals(7,fast.processBudgetWord());
        assertEquals(579,countBindings(bank(f,done.residency(),CHECKPOINT_BASE)));
    }

    private static int countBindings(CdObjectPresentation.PatternSource source) {
        int n=0;for(int id=0;id<2048;id++)try {source.resolve(id);n++;}catch(UnsupportedOperationException expected) { }return n;
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
    private static CdObjectPresentation.PatternSource oldDiagonalBank(Fixture f,CdR11aCameraArtResidency.State state) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14]);
    }
    private static CdObjectPresentation.PatternSource previousBank(Fixture f,CdR11aCameraArtResidency.State state,int spikes) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],spikes);
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int checkpoint) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],SPIKES_BASE,checkpoint);
    }
    private static CdR11aCameraArtResidency.State completeSeed(Fixture f) throws Exception {
        return write(seeded(f),0xD960,f.objects().sectionArtSources().source(CdR11aAsset.ART_CHECKPOINT).decode().bytes(),12);
    }
    private static CpuSat sat(CdDrawMappings mappings,int frame,int flip,int base) {
        var slots=new EmptySlots(){LiveDrawSlot live=new LiveDrawSlot(0x13,4|flip,0x100,0x80,0,MAP,frame,base);
            @Override public LiveDrawSlot read(CdSlotRef ref){assertEquals(61,ref.wholeIndex());return live;}
            @Override public void writeOnScreenBit(CdSlotRef ref,boolean on){assertEquals(61,ref.wholeIndex());var s=live;live=new LiveDrawSlot(s.idByte(),(s.spriteFlagsByte()&127)|(on?128:0),s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());}
        };
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE])) {
            var camera=new CameraWords(0,0);queue.beginPass();assertEquals(EnqueueResult.QUEUED,queue.drawObject(new DrawCallCut(new CdSlotRef(61),4|flip,0x100,0x80,24,8,4),camera));return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));
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

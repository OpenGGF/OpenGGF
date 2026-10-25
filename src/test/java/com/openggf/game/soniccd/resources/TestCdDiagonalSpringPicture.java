package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aLoadPlan;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
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
import java.util.Set;
import java.util.TreeSet;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Declared source/picture checks, separate from body, World and native timing parity. */
class TestCdDiagonalSpringPicture {
    private static final int MAP = 0x209BF0;
    private static final int[] FIRST = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374};
    private static final int[] COUNT = {24,14,70,28,63,70,5,39,36,15,36,12,82,19};
    private static final int[] BASE = {10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374,12456};
    private static final int DIAGONAL_BASE = 12475;
    // Literal N Spring/Data/Mappings.asm:54-82. Enclosing EVEN is byte199 of the full include.
    private static final int[][][] PIECES = {
            {{8,0,0,-16},{0,0,1,-16},{8,0,2,-8},{-16,8,3,-16},{-8,0,6,-16},{-8,9,7,-8},{8,4,13,0}},
            {{8,0,0,-16},{0,0,15,-16},{8,0,16,-8},{-16,5,17,-16},{-8,0,5,0},{0,0,21,-8},{0,5,22,0}},
            {{8,0,0,-16},{0,0,26,-16},{8,0,27,-8},{-32,8,3,0},{-24,14,28,0},{-8,0,40,-16},{-16,2,41,-8},{0,1,44,0}}
    };

    @Test void declaredThirdRootKeepsItsRelativeHeadersAllPiecesAndRealFinalEven() {
        byte[] full = new byte[200];
        int[] starts = {86,122,158};
        for (int frame=0;frame<3;frame++) {
            int relative=starts[frame]-80;full[80+frame*2]=(byte)(relative>>>8);full[81+frame*2]=(byte)relative;
            int p=starts[frame];full[p++]=(byte)PIECES[frame].length;
            for(int[] piece:PIECES[frame]) {full[p++]=(byte)piece[0];full[p++]=(byte)piece[1];full[p++]=(byte)(piece[2]>>>8);full[p++]=(byte)piece[2];full[p++]=(byte)piece[3];}
        }
        assertEquals(0,full[199]);
        var resolver=CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(MAP-80,CdAssetBytes.copyOf(full),80,3)),List.of());
        for(int frame=0;frame<3;frame++)assertPieces(resolver.resolve(MAP,false,frame).pieces(),frame);
        assertThrows(UnsupportedOperationException.class,()->resolver.resolve(MAP,false,3));
        assertThrows(UnsupportedOperationException.class,()->resolver.resolve(MAP,true,0));
        assertThrows(UnsupportedOperationException.class,()->resolver.resolve(MAP+2,false,0));
    }

    @Test void actualThirdRootQueuesAllFramesFlipsAndCompleteWordAttributes() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var source=bank(f,seeded(f),DIAGONAL_BASE);
        assertEquals(MAP,f.objects().view(CdR11aObjectAssets.Table.SPRING_MAPPINGS).range().nativeAddress()+80);
        var seen=new TreeSet<Integer>();
        for(int frame=0;frame<3;frame++)for(int flip=0;flip<4;flip++) {
            assertPieces(mappings.resolve(MAP,false,frame).pieces(),frame);
            var sat=sat(mappings,frame,flip,0xA490);assertEquals(PIECES[frame].length,sat.spriteCount());var raw=sat.bytes();
            for(int i=0;i<PIECES[frame].length;i++) {
                var p=PIECES[frame][i];int w=((p[1]>>>2)&3)+1,h=(p[1]&3)+1;
                int x=(flip&1)==0?p[3]:-p[3]-w*8,y=(flip&2)==0?p[0]:-p[0]-h*8;
                assertEquals(0x180+x,word(raw,i*8+6));assertEquals(0x100+y,word(raw,i*8));
                assertEquals((0xA490+p[2])^((flip&1)*0x800)^(((flip>>>1)&1)*0x1000),word(raw,i*8+4));
                assertEquals(p[1],raw[i*8+2]&255);
            }
            var picture=CdObjectPresentation.convert(sat,source);
            for(var tile:picture.tiles()) {
                seen.add(tile.patternId());assertTrue(tile.priority());assertEquals(1,tile.palette());
                assertEquals((flip&1)!=0,tile.hFlip());assertEquals((flip&2)!=0,tile.vFlip());
                assertEquals(source.resolve(0x490+tile.patternId()-DIAGONAL_BASE).version(),picture.patternVersions().get(tile.patternId()));
            }
        }
        assertEquals(range(DIAGONAL_BASE,46),seen);
        // Full tile WORD addition precedes flip XOR, not masked native-ID arithmetic.
        assertEquals((0xFFF0+28)&0xFFFF,word(sat(mappings,2,0,0xFFF0).bytes(),4*8+4));
        assertThrows(UnsupportedOperationException.class,()->mappings.resolve(MAP,false,255));
    }

    @Test void old513RemainsValidWhileNew559RejectsFirstAndLastUnknownDiagonalTilesBeforeEmptySat() throws Exception {
        var f=fixture();var legacy=boulderSeed(f);var old=oldBank(f,legacy);assertNotNull(old.resolve(0x374));
        assertThrows(UnsupportedOperationException.class,()->old.resolve(0x490));
        var first=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,legacy,DIAGONAL_BASE));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,first.boundary());assertEquals(0x490,first.nativePatternId());
        var source=f.objects().sectionArtSources().source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes();
        var lastState=write(legacy,0x9200,source,45);
        var last=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,lastState,DIAGONAL_BASE));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,last.boundary());assertEquals(0x4BD,last.nativePatternId());
        var empty=new CdObjectDrawQueue(new EmptySlots(),(a,b,c)->{throw new AssertionError();},new byte[SAT_BYTE_SIZE]);
        empty.beginPass();var c=new CameraWords(0,0);var sat=empty.finishAfterCamera(new DrawCameras(c,c,c));
        assertTrue(CdObjectPresentation.convert(sat,old).tiles().isEmpty());
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,lastState,DIAGONAL_BASE));
    }

    @Test void all559CurrentBindingsPreserveEveryOldLogicalIdAndNoOtherKnownAddressIsAdmitted() throws Exception {
        var f=fixture();var state=seeded(f);var old=oldBank(f,state);var now=bank(f,state,DIAGONAL_BASE);
        var logical=new TreeSet<Integer>();var nativeIds=new TreeSet<Integer>();
        for(int i=0;i<FIRST.length;i++)for(int t=0;t<COUNT[i];t++) {
            int id=FIRST[i]+t;nativeIds.add(id);assertEquals(old.resolve(id),now.resolve(id));assertTrue(logical.add(now.resolve(id).logicalPatternId()));
        }
        var bytes=f.objects().sectionArtSources().source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes();
        for(int t=0;t<46;t++) {
            int id=0x490+t;nativeIds.add(id);var tile=now.resolve(id);
            assertEquals(DIAGONAL_BASE+t,tile.logicalPatternId());assertEquals(CdObjectPresentation.patternVersion(bytes.slice(t*32,32)),tile.version());assertTrue(logical.add(tile.logicalPatternId()));
        }
        assertEquals(559,logical.size());assertEquals(559,nativeIds.size());
        for(int id=0;id<0x800;id++)if(!nativeIds.contains(id)) {int q=id;assertThrows(UnsupportedOperationException.class,()->now.resolve(q));}
        for(int bad:new int[]{-1,0x800,0x8490})assertThrows(IllegalArgumentException.class,()->now.resolve(bad));
    }

    @Test void diagonalLogicalBoundsAndEveryOldSpanOverlapAreRejectedEagerly() throws Exception {
        var f=fixture();var state=seeded(f);
        for(int bad:new int[]{-1,Integer.MAX_VALUE-44,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->bank(f,state,bad));
        for(int i=0;i<BASE.length;i++)for(int overlap:new int[]{BASE[i]+COUNT[i]-1,BASE[i]-45})assertThrows(IllegalArgumentException.class,()->bank(f,state,overlap));
        assertEquals(DIAGONAL_BASE+45,bank(f,state,DIAGONAL_BASE).resolve(0x4BD).logicalPatternId());
        assertEquals(Integer.MAX_VALUE,bank(f,state,Integer.MAX_VALUE-45).resolve(0x4BD).logicalPatternId());
        assertThrows(NullPointerException.class,()->bank(f,null,DIAGONAL_BASE));
    }

    @Test void currentPhysicalOverwriteFollowsBigRingAddressAliasAndNeverReselectsSpringProducer() throws Exception {
        var f=fixture();var state=seeded(f);var old=bank(f,state,DIAGONAL_BASE);
        var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var sat=sat(mappings,2,0,0x2490);var frozen=CdObjectPresentation.convert(sat,old);
        // Explicit constructed bytes, NOT decoded BigRing art or a claim that native PLC18 ran.
        byte[] ingress=new byte[106*32];for(int t=0;t<106;t++)Arrays.fill(ingress,t*32,(t+1)*32,(byte)(t^0xA5));
        var bytes=CdAssetBytes.copyOf(ingress);var before=bank(f,write(state,0x9100,bytes,8),DIAGONAL_BASE);
        for(int t=0;t<46;t++)assertEquals(old.resolve(0x490+t),before.resolve(0x490+t));
        var prefix=write(state,0x9100,bytes,9);var one=bank(f,prefix,DIAGONAL_BASE);
        assertEquals(CdObjectPresentation.patternVersion(bytes.slice(8*32,32)),one.resolve(0x490).version());
        for(int t=1;t<46;t++)assertEquals(old.resolve(0x490+t),one.resolve(0x490+t));
        var complete=write(state,0x9100,bytes,106);var now=bank(f,complete,DIAGONAL_BASE);
        for(int t=0;t<46;t++)assertEquals(CdObjectPresentation.patternVersion(bytes.slice((t+8)*32,32)),now.resolve(0x490+t).version());
        for(int i=0;i<FIRST.length;i++)for(int t=0;t<COUNT[i];t++)assertEquals(old.resolve(FIRST[i]+t),now.resolve(FIRST[i]+t));
        assertThrows(UnsupportedOperationException.class,()->now.resolve(0x4BE)); // Known physical byte is not a new picture binding.
        assertEquals(frozen,CdObjectPresentation.convert(sat,old));
        assertEquals(frozen,CdObjectPresentation.convert(sat,bank(f,state,DIAGONAL_BASE)));
        assertEquals(prefix,write(state,0x9100,bytes,9));
    }

    @Test void authenticFiniteProgramServicesOnlyCompleteTilesAndRestoresPartialStateWithoutReplayDecode() throws Exception {
        var f=fixture();assertNotNull(CdR11aSectionArt.fromAssets(f.assets()));
        var source=f.objects().sectionArtSources().source(CdR11aAsset.ART_DIAGONAL_SPRING);assertEquals(779,source.decode().readerInputRange().length());assertEquals(780,source.readerBytes().length());
        var actual=f.assets().loadPlan().plcs().get(1);assertEquals(source.canonicalPlc(),actual.entries().get(5));
        // Declared single-row selection of the actual program, not execution of full native Std list.
        var owner=new CdR11aSectionArt(List.of(new CdR11aLoadPlan.PlcList(1,actual.bodyRange(),List.of(source.canonicalPlc()))),
                f.assets().loadPlan().sections(),List.of(new CdR11aSectionArt.SourceProgram(source.canonicalPlc(),source.readerBytes())));
        var initial=owner.devState(boulderSeed(f),CdAssetBytes.copyOf(new byte[512]),0);
        var prepared=owner.prepare(owner.initList(initial,1));assertEquals(46,prepared.remainingTilesWord());
        var slow=prepared;
        for(int call=1;call<=16;call++) {
            slow=owner.serviceSlow(slow,0);int done=Math.min(46,call*3);assertEquals(46-done,slow.remainingTilesWord());
            for(int t=0;t<done;t++)assertArrayEquals(source.decode().bytes().slice(t*32,32).copy(),CdR11aCameraArtResidency.readTile(slow.residency(),0x490+t).copy());
            if(done<46) {int next=0x490+done;var cut=slow;assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(cut.residency(),next));}
        }
        assertEquals(3,slow.processBudgetWord());assertEquals(0,slow.queue().headSource());
        var fast=prepared;for(int call=0;call<3;call++)fast=owner.serviceFast(fast);
        assertEquals(0,fast.remainingTilesWord());assertEquals(9,fast.processBudgetWord());assertEquals(slow.residency(),fast.residency());
        var partial=owner.serviceSlow(prepared,0);var replay=partial;for(int call=1;call<16;call++)replay=owner.serviceSlow(replay,0);
        assertEquals(slow,replay);assertEquals(43,partial.remainingTilesWord());
        // prepare consumed header/table75 bytes and the real two-byte initial prefetch.
        assertEquals(77,prepared.decoder().orElseThrow().inputOffset());
        assertEquals(0x234659,prepared.queue().headSource());
    }

    private record Fixture(CdR11aAssets assets,CdR11aObjectAssets objects,CdPlayerArtRuntime.State player,CdPowerupArtRuntime.State powerup) { }
    private static Fixture fixture() throws Exception {
        String configured=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(configured!=null&&!configured.isBlank(),"Set soniccd.media.root");
        Path cue;try(var files=Files.list(Path.of(configured).resolve("USA"))) {var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        CdR11aAssets assets;try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {assets=CdR11aAssetLoader.load(source,CdReleaseVariant.USA,CdStageKey.parse("R11A"));}
        return new Fixture(assets,assets.openingObjects(),new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]));
    }
    private static CdR11aCameraArtResidency.State boulderSeed(Fixture f) throws Exception {
        var o=f.objects();var state=CdR11aCameraArtResidency.devSeed(o.initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_PLANT).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
        return write(state,0x6E80,o.sectionArtSources().source(CdR11aAsset.ART_BOULDER).decode().bytes(),19);
    }
    private static CdR11aCameraArtResidency.State seeded(Fixture f) throws Exception {return write(boulderSeed(f),0x9200,f.objects().sectionArtSources().source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes(),46);}
    private static CdR11aCameraArtResidency.State write(CdR11aCameraArtResidency.State state,int destination,CdAssetBytes bytes,int count) {for(int t=0;t<count;t++)state=CdR11aCameraArtResidency.writeTile(state,destination+t*32,bytes.slice(t*32,32));return state;}
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int diagonal) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],diagonal);
    }
    private static CdObjectPresentation.PatternSource oldBank(Fixture f,CdR11aCameraArtResidency.State state) throws Exception {
        int[] b=BASE;var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13]);
    }
    private static CpuSat sat(CdDrawMappings mappings,int frame,int flip,int base) {
        var slots=new EmptySlots(){LiveDrawSlot live=new LiveDrawSlot(0x0A,4|flip,0x100,0x80,0,MAP,frame,base);
            @Override public LiveDrawSlot read(CdSlotRef ref){return live;}
            @Override public void writeOnScreenBit(CdSlotRef ref,boolean value){var s=live;live=new LiveDrawSlot(s.idByte(),(s.spriteFlagsByte()&127)|(value?128:0),s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());}
        };
        var q=new CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE]);q.beginPass();var c=new CameraWords(0,0);
        assertEquals(EnqueueResult.QUEUED,q.drawObject(new DrawCallCut(new CdSlotRef(32),4|flip,0x100,0x80,24,12,4),c));return q.finishAfterCamera(new DrawCameras(c,c,c));
    }
    private static class EmptySlots implements CdDrawSlots {
        @Override public LiveDrawSlot read(CdSlotRef ref){throw new AssertionError("No slot read expected");}
        @Override public void writeOnScreenBit(CdSlotRef ref,boolean value){throw new AssertionError("No slot write expected");}
    }
    private static void assertPieces(List<RawPiece> actual,int frame) {assertEquals(PIECES[frame].length,actual.size());for(int i=0;i<actual.size();i++){int[] p=PIECES[frame][i];assertEquals(new RawPiece(p[0],p[1],p[2],p[3]),actual.get(i));}}
    private static Set<Integer> range(int first,int count){var s=new TreeSet<Integer>();for(int i=0;i<count;i++)s.add(first+i);return s;}
    private static int word(byte[] bytes,int offset){return ((bytes[offset]&255)<<8)|(bytes[offset+1]&255);}
}

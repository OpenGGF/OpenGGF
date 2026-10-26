package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-derived Boulder joins. Constructed slots are not a World/route or native render certificate. */
class TestCdBoulderPicture {
    private static final int MAP = 0x209654;
    private static final byte[] MAPPING = HexFormat.of().parseHex("000202f8020000ecf00f0003f400");
    private static final int[] FIRST = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8};
    private static final int[] COUNT = {24,14,70,28,63,70,5,39,36,15,36,12,82};
    private static final int[] BASE = {10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374};
    private static final int BOULDER_BASE = 12456;

    @Test void declaredBoulderFrameHasExactlyTheTwoSourcePiecesAndRealAlignmentByte() {
        var mappings = CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(
                MAP, CdAssetBytes.copyOf(MAPPING), 0, 1)), List.of());
        assertEquals(14, MAPPING.length); assertEquals(0, MAPPING[13]);
        assertEquals(List.of(new RawPiece(-8,2,0,-20), new RawPiece(-16,15,3,-12)),
                mappings.resolve(MAP,false,0).pieces());
        assertThrows(UnsupportedOperationException.class, () -> mappings.resolve(MAP,false,1));
        assertThrows(UnsupportedOperationException.class, () -> mappings.resolve(MAP,true,0));
        assertThrows(UnsupportedOperationException.class, () -> mappings.resolve(MAP+2,false,0));
    }

    @Test void actualUsaMappingJoinsAndQueuePreservesFlipsPriorityAndFullWordAddition() throws Exception {
        var f = fixture(); var resolver = CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());
        assertEquals(MAP, f.objects().view(CdR11aObjectAssets.Table.BOULDER_MAPPINGS).range().nativeAddress());
        assertArrayEquals(MAPPING,f.objects().view(CdR11aObjectAssets.Table.BOULDER_MAPPINGS).bytes().copy());
        var camera = seeded(f); var source = bank(f,camera,BASE,BOULDER_BASE);
        for (int flips=0; flips<4; flips++) {
            int flags=4|flips; var sat=sat(resolver,flags,0x8374); var raw=sat.bytes();
            assertEquals(2,sat.spriteCount());
            int[][] pieces={{-20,-8,1,3,0},{-12,-16,4,4,3}};
            for(int i=0;i<2;i++) {
                var p=pieces[i]; int x=(flips&1)==0?p[0]:-p[0]-p[2]*8;
                int y=(flips&2)==0?p[1]:-p[1]-p[3]*8;
                assertEquals(0x100+y,word(raw,8*i)); assertEquals(0x180+x,word(raw,8*i+6));
                assertEquals(i==0?2:15,raw[8*i+2]&255);
                assertEquals((0x8374+p[4])^((flips&1)*0x800)^(((flips>>>1)&1)*0x1000),word(raw,8*i+4));
            }
            var picture=CdObjectPresentation.convert(sat,source); assertEquals(19,picture.tiles().size());
            var used=new TreeSet<Integer>();
            for(var tile:picture.tiles()) {
                used.add(tile.patternId()); assertTrue(tile.priority()); assertEquals(0,tile.palette());
                assertEquals((flips&1)!=0,tile.hFlip()); assertEquals((flips&2)!=0,tile.vFlip());
                assertEquals(source.resolve(0x374+tile.patternId()-BOULDER_BASE).version(),picture.patternVersions().get(tile.patternId()));
            }
            assertEquals(logicalRange(BOULDER_BASE,19),used);
        }
        // Software bucket4 does not set hardware bit15. Addition is a complete WORD before native-ID masking.
        assertEquals(0x0374,word(sat(resolver,4,0x0374).bytes(),4));
        for(var tile:CdObjectPresentation.convert(sat(resolver,4,0x0374),source).tiles()) assertFalse(tile.priority());
        assertEquals(0x0001,word(sat(resolver,4,0xFFFE).bytes(),12));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(MAP,false,255));
        assertThrows(UnsupportedOperationException.class, () -> resolver.resolve(MAP,true,0));
    }

    @Test void old494PolicyStaysUsableButNewBoulderPolicyRejectsUnknownBytesEagerly() throws Exception {
        var f=fixture(); var legacy=legacySeed(f); var old=oldBank(f,legacy);
        assertNotNull(old.resolve(0x3A0)); assertThrows(UnsupportedOperationException.class,()->old.resolve(0x374));
        var error=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,legacy,BASE,BOULDER_BASE));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,error.boundary());
        assertEquals(0x374,error.nativePatternId());
        var withoutLast=write(legacy,0x6E80,f.objects().sectionArtSources().source(CdR11aAsset.ART_BOULDER).decode().bytes(),18);
        var last=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,withoutLast,BASE,BOULDER_BASE));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,last.boundary()); assertEquals(0x386,last.nativePatternId());
        var empty = new CdObjectDrawQueue(new EmptySlots(), (address,mode,frame)->{throw new AssertionError();},new byte[SAT_BYTE_SIZE]);
        empty.beginPass(); var c=new CameraWords(0,0); var sat=empty.finishAfterCamera(new DrawCameras(c,c,c));
        assertEquals(0,CdObjectPresentation.convert(sat,old).tiles().size());
        // Admission occurs before any SAT could hide the missing current tiles.
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,legacy,BASE,BOULDER_BASE));
    }

    @Test void all513BindingsUseCurrentBoulderVersionsAndPreserveEveryOldLogicalId() throws Exception {
        var f=fixture(); var current=seeded(f); var old=oldBank(f,current); var now=bank(f,current,BASE,BOULDER_BASE);
        var nativeIds=new TreeSet<Integer>(); var logicalIds=new HashSet<Integer>();
        for(int span=0;span<FIRST.length;span++)for(int t=0;t<COUNT[span];t++) {
            int id=FIRST[span]+t; nativeIds.add(id); assertEquals(old.resolve(id),now.resolve(id));
            assertTrue(logicalIds.add(now.resolve(id).logicalPatternId()));
        }
        var bytes=f.objects().sectionArtSources().source(CdR11aAsset.ART_BOULDER).decode().bytes();
        assertEquals(608,bytes.length());
        for(int t=0;t<19;t++) {int id=0x374+t;nativeIds.add(id);var tile=now.resolve(id);
            assertEquals(BOULDER_BASE+t,tile.logicalPatternId()); assertTrue(logicalIds.add(tile.logicalPatternId()));
            assertEquals(CdObjectPresentation.patternVersion(bytes.slice(t*32,32)),tile.version());
        }
        assertEquals(513,nativeIds.size()); assertEquals(513,logicalIds.size());
        for(int id=0;id<2048;id++) {int q=id;if(!nativeIds.contains(id))assertThrows(UnsupportedOperationException.class,()->now.resolve(q));}
        assertThrows(IllegalArgumentException.class,()->now.resolve(-1));
        assertThrows(IllegalArgumentException.class,()->now.resolve(0x800));
        assertThrows(IllegalArgumentException.class,()->now.resolve(0x8374));
    }

    @Test void boulderLogicalBoundsAndEveryOldSpanOverlapRejectBeforeEmptySatAdmission() throws Exception {
        var f=fixture();var current=seeded(f);
        for(int invalid:new int[]{-1,Integer.MAX_VALUE-17,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->bank(f,current,BASE,invalid));
        for(int span=0;span<BASE.length;span++)for(int collision:new int[]{BASE[span]+COUNT[span]-1,BASE[span]-18})
            assertThrows(IllegalArgumentException.class,()->bank(f,current,BASE,collision));
        var adjacent=bank(f,current,BASE,BOULDER_BASE);assertEquals(BOULDER_BASE+18,adjacent.resolve(0x386).logicalPatternId());
        var max=bank(f,current,BASE,Integer.MAX_VALUE-18);assertEquals(Integer.MAX_VALUE,max.resolve(0x386).logicalPatternId());
        var oldInvalid=BASE.clone();oldInvalid[0]=-1;
        assertThrows(IllegalArgumentException.class,()->bank(f,current,oldInvalid,BOULDER_BASE));
    }

    @Test void relocatedAntonChangesOnlyIts18BoulderAliasesAndKeepsFrozenOldPictures() throws Exception {
        var f=fixture();var initial=seeded(f);var old=bank(f,initial,BASE,BOULDER_BASE);
        var sat=sat(CdObjectMappings.fromOpeningAssets(f.assets(),f.objects()),4,0x0374);
        var frozen=CdObjectPresentation.convert(sat,old);
        var anton=f.objects().sectionArtSources().source(CdR11aAsset.ART_ANTON).decode().bytes();
        var prefix=write(initial,0x6E00,anton,3);var first3=bank(f,prefix,BASE,BOULDER_BASE);
        for(int t=0;t<19;t++)assertEquals(old.resolve(0x374+t),first3.resolve(0x374+t));
        prefix=write(initial,0x6E00,anton,5);var first5=bank(f,prefix,BASE,BOULDER_BASE);
        assertEquals(CdObjectPresentation.patternVersion(anton.slice(4*32,32)),first5.resolve(0x374).version());
        for(int t=1;t<19;t++)assertEquals(old.resolve(0x374+t),first5.resolve(0x374+t));
        var complete=write(initial,0x6E00,anton,22);var now=bank(f,complete,BASE,BOULDER_BASE);
        for(int t=0;t<18;t++) {
            assertEquals(BOULDER_BASE+t,now.resolve(0x374+t).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(anton.slice((t+4)*32,32)),now.resolve(0x374+t).version());
        }
        assertEquals(old.resolve(0x386),now.resolve(0x386));
        assertEquals(frozen,CdObjectPresentation.convert(sat,old));
        assertThrows(UnsupportedOperationException.class,()->now.resolve(0x370));
        assertThrows(UnsupportedOperationException.class,()->now.resolve(0x387));
        // Returning to the prior immutable generation performs no service or decode.
        assertEquals(frozen,CdObjectPresentation.convert(sat,bank(f,initial,BASE,BOULDER_BASE)));
    }

    @Test void actualBoulderPlcServiceCompletesSevenSlowCallsThenPreparesAnimalsWithoutPixelWrites() throws Exception {
        var f=fixture();var owner=CdR11aSectionArt.fromAssets(f.assets());
        var seed=owner.devState(legacySeed(f),CdAssetBytes.copyOf(new byte[512]),0);
        var state=owner.prepare(owner.initList(seed,8));assertEquals(19,state.remainingTilesWord());
        var prepared=state;
        for(int call=1;call<=7;call++) {
            state=owner.serviceSlow(state,0);assertEquals(Math.max(0,19-call*3),state.remainingTilesWord());
            int completed=Math.min(19,call*3);var decoded=f.objects().sectionArtSources().source(CdR11aAsset.ART_BOULDER).decode().bytes();
            for(int tile=0;tile<completed;tile++)assertArrayEquals(decoded.slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(state.residency(),0x374+tile).copy());
        }
        assertEquals(3,state.processBudgetWord());
        var next=f.assets().loadPlan().plcs().get(8).entries().get(1);
        assertNotEquals(CdR11aAsset.ART_BOULDER,next.source());
        assertEquals(Integer.toUnsignedLong(next.sourceRange().nativeAddress()),state.queue().headSource());
        var done=state;var ready=owner.prepare(done);
        assertEquals(CdR11aAsset.ART_ANIMALS,next.source());
        assertFalse(ready.stopped());assertEquals(20,ready.remainingTilesWord());
        assertEquals(3,ready.processBudgetWord());assertEquals(0x7100,ready.queue().headDestination());
        assertEquals(done.residency(),ready.residency(),"ProcessPLCs preparation writes no current tile or mask");
        var decoder=ready.decoder().orElseThrow();
        assertEquals(0x2381C0L,decoder.nativeAddress());assertEquals(0x0014,decoder.headerWord());
        assertFalse(decoder.xorMode());assertEquals(89,decoder.inputOffset());
        assertEquals(0x238219L,ready.queue().headSource());assertEquals(0x6CD5,decoder.readWord());
        assertEquals(16,decoder.shiftWord());assertEquals(CdR11aSectionArt.Writer.NORMAL,ready.savedDecoder().writer());
        assertArrayEquals(java.util.Arrays.copyOfRange(done.queue().bytes().copy(),4,96),
                java.util.Arrays.copyOfRange(ready.queue().bytes().copy(),4,96));
        var animal=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(done.residency(),0x388));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,animal.boundary());
        var stillUnknown=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,
                ()->CdR11aCameraArtResidency.readTile(ready.residency(),0x388));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,stillUnknown.boundary());
        // An adjacent finite-reader byte never registers Signpost as an executable producer.
        assertThrows(UnsupportedOperationException.class,()->f.objects().sectionArtSources().source(CdR11aAsset.ART_SIGNPOST));
        var replay=prepared;for(int call=0;call<7;call++)replay=owner.serviceSlow(replay,0);assertEquals(done,replay);
        assertEquals(ready,owner.prepare(replay));
    }

    private record Fixture(CdR11aAssets assets,CdR11aObjectAssets objects,CdPlayerArtRuntime.State player,
                           CdPowerupArtRuntime.State powerup) { }
    private static Fixture fixture() throws Exception {
        var configured=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(configured!=null&&!configured.isBlank(),"Set soniccd.media.root");
        Path cue;try(var files=Files.list(Path.of(configured).resolve("USA"))) {
            var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        CdR11aAssets assets;try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assets=CdR11aAssetLoader.load(source,CdReleaseVariant.USA,CdStageKey.parse("R11A"));
        }
        return new Fixture(assets,assets.openingObjects(),new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),
                new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]));
    }
    private static CdR11aCameraArtResidency.State legacySeed(Fixture f) throws Exception {
        return CdR11aCameraArtResidency.devSeed(f.objects().initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),
                f.objects().frontierArtBank().binding(CdR11aAsset.ART_3D_PLANT).bytes(),
                f.objects().frontierArtBank().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
    }
    private static CdR11aCameraArtResidency.State seeded(Fixture f) throws Exception {
        return write(legacySeed(f),0x6E80,f.objects().sectionArtSources().source(CdR11aAsset.ART_BOULDER).decode().bytes(),19);
    }
    private static CdR11aCameraArtResidency.State write(CdR11aCameraArtResidency.State state,int destination,CdAssetBytes bytes,int count) {
        for(int tile=0;tile<count;tile++)state=CdR11aCameraArtResidency.writeTile(state,destination+tile*32,bytes.slice(tile*32,32));return state;
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int[] b,int boulder) throws Exception {
        return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),f.objects(),f.objects().frontierArtBank(),
                f.objects().contactArtBank(),f.objects().floatBlockArtBank(),f.objects().springArtBank(),f.objects().tunnelDoorArtBank(),
                f.objects().monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],boulder);
    }
    private static CdObjectPresentation.PatternSource oldBank(Fixture f,CdR11aCameraArtResidency.State state) throws Exception {
        int[] b=BASE;return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),f.objects(),f.objects().frontierArtBank(),
                f.objects().contactArtBank(),f.objects().floatBlockArtBank(),f.objects().springArtBank(),f.objects().tunnelDoorArtBank(),
                f.objects().monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12]);
    }
    private static CpuSat sat(CdDrawMappings mappings,int flags,int base) {
        var slots=new EmptySlots(){LiveDrawSlot live=new LiveDrawSlot(0x1B,flags,0x100,0x80,0x9999,MAP,0,base);
            @Override public LiveDrawSlot read(CdSlotRef ref){assertEquals(32,ref.wholeIndex());return live;}
            @Override public void writeOnScreenBit(CdSlotRef ref,boolean value){var s=live;live=new LiveDrawSlot(s.idByte(),(s.spriteFlagsByte()&127)|(value?128:0),s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());}
        };
        var q=new CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE]);q.beginPass();var c=new CameraWords(0,0);
        assertEquals(EnqueueResult.QUEUED,q.drawObject(new DrawCallCut(new CdSlotRef(32),flags,0x100,0x80,16,16,4),c));
        return q.finishAfterCamera(new DrawCameras(c,c,c));
    }
    private static class EmptySlots implements CdDrawSlots {
        @Override public LiveDrawSlot read(CdSlotRef ref){throw new AssertionError("Empty SAT must not dereference a slot");}
        @Override public void writeOnScreenBit(CdSlotRef ref,boolean value){throw new AssertionError("Empty SAT must not write a slot");}
    }
    private static int word(byte[] b,int offset){return ((b[offset]&255)<<8)|(b[offset+1]&255);}
    private static Set<Integer> logicalRange(int first,int count){var s=new TreeSet<Integer>();for(int i=0;i<count;i++)s.add(first+i);return s;}
}

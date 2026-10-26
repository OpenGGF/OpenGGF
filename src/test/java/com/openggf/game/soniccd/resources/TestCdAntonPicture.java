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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE-ONLY specifications: actual USA owners are media-opt-in through the public loader.
 * CPU SAT/value pictures only; no graphics, natural route, cold native history or parity claim.
 */
class TestCdAntonPicture {
    private static final int MAP=0x20B3AA,ANTON_BASE=12541;
    private static final int[] FIRST={0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320,0x6CB};
    private static final int[] COUNT={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8,12};
    private static final int[] BASE={10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374,12456,12475,12521,12529};
    private static final byte[] RAW=HexFormat.of().parseHex(
            "00080022003c005605ed050000f4fd080004f405040007fc04050009f8fc0000110e"
            + "05ec050000f4fc080004f404040007fc0405000df8fb00001112"
            + "05ed050012f4fd080004f405040007fc04050009f8fc0000110e"
            + "05ec050012f4fc080004f404040007fc0405000df8fb00001112");
    private static final int[][][] PIECES={
            {{-19,5,0,-12},{-3,8,4,-12},{5,4,7,-4},{4,5,9,-8},{-4,0,17,14}},
            {{-20,5,0,-12},{-4,8,4,-12},{4,4,7,-4},{4,5,13,-8},{-5,0,17,18}},
            {{-19,5,18,-12},{-3,8,4,-12},{5,4,7,-4},{4,5,9,-8},{-4,0,17,14}},
            {{-20,5,18,-12},{-4,8,4,-12},{4,4,7,-4},{4,5,13,-8},{-5,0,17,18}}
    };

    @Test void coldLegacySeedAllowsC28AndEmptySatWithAll22AntonPatternsStillUnknown() throws Exception {
        var f=fixture();var state=seeded(f);var previous=oldBank(f,state,BASE);var current=bank(f,state,BASE,ANTON_BASE);
        assertArrayEquals(new byte[0x2C0],state.region(CdR11aCameraArtResidency.Region.AT_8120).knownMask().copy());
        assertTrue(CdObjectPresentation.convert(emptySat(),current).tiles().isEmpty());
        int n=0;
        for(int span=0;span<17;span++)for(int t=0;t<COUNT[span];t++) {
            int id=FIRST[span]+t;assertEquals(previous.resolve(id),current.resolve(id));
            assertEquals(BASE[span]+t,current.resolve(id).logicalPatternId());n++;
        }
        assertEquals(579,n);
        for(int tile=0;tile<22;tile++)assertUnknown(current,0x409+tile);
        assertArrayEquals(new byte[0x2C0],state.region(CdR11aCameraArtResidency.Region.AT_8120).knownMask().copy());
    }

    @Test void onlyReachedFirstInteriorAndLastUnknownPatternsStopAndASelectedTileAloneCanBecomeKnown() throws Exception {
        var f=fixture();var cold=seeded(f);var before=bank(f,cold,BASE,ANTON_BASE);
        for(int selected:new int[]{0x409,0x414,0x41E}) {
            assertUnknown(before,selected);
            var selectedSat=singleTileSat(selected);
            var stop=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdObjectPresentation.convert(selectedSat,before));
            assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,stop.boundary());assertEquals(selected,stop.nativePatternId());
            // Explicit test-only current bytes, not an authenticated producer or an asserted cold seed.
            byte[] declared=testOnlyTile(selected);
            var written=CdR11aCameraArtResidency.writeTile(cold,selected*32,CdAssetBytes.copyOf(declared));
            var after=bank(f,written,BASE,ANTON_BASE);
            assertEquals(ANTON_BASE+selected-0x409,after.resolve(selected).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(declared)),after.resolve(selected).version());
            var frame=CdObjectPresentation.convert(selectedSat,after);assertEquals(1,frame.tiles().size());
            assertEquals(after.resolve(selected).version(),frame.patternVersions().get(ANTON_BASE+selected-0x409));
            for(int tile=0;tile<22;tile++)if(0x409+tile!=selected)assertUnknown(after,0x409+tile);
            assertUnknown(before,selected);assertUnknown(bank(f,cold,BASE,ANTON_BASE),selected);
            for(int tile=0;tile<22;tile++)assertEquals(tile+0x409==selected?1:0,
                    written.region(CdR11aCameraArtResidency.Region.AT_8120).knownMask().unsignedByte(tile*32));
        }
    }

    @Test void metadataAndAllSeventeenLogicalSpanBoundariesValidateWhileAntonIsUnknown() throws Exception {
        var f=fixture();var state=seeded(f);
        assertEquals(0x2409,f.objects().tileWord(2,0));assertEquals(0x2370,f.objects().tileWord(2,1));
        var region=CdR11aCameraArtResidency.Region.AT_8120;
        assertEquals(0x8120,region.destinationWord());assertEquals(0x2C0,region.byteLength());assertEquals(22,region.tileCount());
        for(int invalid:new int[]{-1,Integer.MIN_VALUE,Integer.MAX_VALUE-20,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->bank(f,state,BASE,invalid));
        for(int span=0;span<17;span++)for(int base:new int[]{BASE[span]-21,BASE[span]+COUNT[span]-1})
            assertThrows(IllegalArgumentException.class,()->bank(f,state,BASE,base));
        // Separate old logical spans so both exactly touching half-open boundaries are observable.
        int[] separated=new int[17];for(int span=0;span<17;span++)separated[span]=10000+span*1000;
        for(int span=0;span<17;span++) {
            assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,state,separated,separated[span]-22)).tiles().isEmpty());
            assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,state,separated,separated[span]+COUNT[span])).tiles().isEmpty());
            for(int base:new int[]{separated[span]-21,separated[span]+COUNT[span]-1})
                assertThrows(IllegalArgumentException.class,()->bank(f,state,separated,base));
        }
        var one=CdR11aCameraArtResidency.writeTile(state,0x83C0,CdAssetBytes.copyOf(testOnlyTile(0x41E)));
        assertEquals(Integer.MAX_VALUE,bank(f,one,BASE,Integer.MAX_VALUE-21).resolve(0x41E).logicalPatternId());
        var first=CdR11aCameraArtResidency.writeTile(state,0x8120,CdAssetBytes.copyOf(testOnlyTile(0x409)));
        assertEquals(0,bank(f,first,BASE,0).resolve(0x409).logicalPatternId());
    }

    @Test void oldC27ArgumentAndUnknownIngressPrecedenceRunsBeforeNewBaseValidation() throws Exception {
        var f=fixture();var state=seeded(f);int[] bad=BASE.clone();bad[1]=bad[0];
        var old=assertThrows(IllegalArgumentException.class,()->oldBank(f,state,bad));
        var now=assertThrows(IllegalArgumentException.class,()->bank(f,state,bad,-1));
        assertEquals(old.getMessage(),now.getMessage());
        // Recreate only the documented three-window DEV ingress: old Boulder is still unknown.
        var cold=legacySeed(f);
        var oldStop=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->oldBank(f,cold,BASE));
        var nowStop=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,cold,BASE,-1));
        assertEquals(oldStop.boundary(),nowStop.boundary());assertEquals(0x374,nowStop.nativePatternId());
    }

    @Test void knownCanonicalSpanHas601LogicalPatternsAndAllOtherNativeAndFinalIdLimitsStayClosed() throws Exception {
        var f=fixture();var state=canonical(f,seeded(f));var old=oldBank(f,state,BASE);var now=bank(f,state,BASE,ANTON_BASE);
        var natives=new TreeSet<Integer>();var logical=new TreeSet<Integer>();
        for(int span=0;span<17;span++)for(int t=0;t<COUNT[span];t++) {
            int id=FIRST[span]+t;assertEquals(old.resolve(id),now.resolve(id));
            assertEquals(BASE[span]+t,now.resolve(id).logicalPatternId());assertTrue(natives.add(id));assertTrue(logical.add(BASE[span]+t));
        }
        for(int tile=0;tile<22;tile++) {
            int id=0x409+tile;assertTrue(natives.add(id));assertTrue(logical.add(now.resolve(id).logicalPatternId()));
            assertEquals(ANTON_BASE+tile,now.resolve(id).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(state,id)),now.resolve(id).version());
            assertThrows(UnsupportedOperationException.class,()->old.resolve(id));
        }
        assertEquals(601,natives.size());assertEquals(601,logical.size());
        for(int id=0;id<2048;id++)if(!natives.contains(id)) {
            int absent=id;assertThrows(UnsupportedOperationException.class,()->now.resolve(absent));
            assertThrows(UnsupportedOperationException.class,()->old.resolve(absent));
        }
        for(int invalid:new int[]{-1,Integer.MIN_VALUE,0x800,0xC09,0xFFFF,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,()->now.resolve(invalid));
            assertThrows(IllegalArgumentException.class,()->old.resolve(invalid));
        }
        // 601 is a span extent; only this explicitly written fixture has all22 canonical tiles known.
    }

    @Test void capturedKnownnessBytesSourcesAndFramesAreImmutableAcrossDifferingCurrentWrites() throws Exception {
        var f=fixture();var state=seeded(f);var cold=bank(f,state,BASE,ANTON_BASE);
        byte[] declared=testOnlyTile(17);var known=CdR11aCameraArtResidency.writeTile(state,0x8120,CdAssetBytes.copyOf(declared));
        var source=bank(f,known,BASE,ANTON_BASE);var sample=source.resolve(0x409);
        var picture=CdObjectPresentation.convert(singleTileSat(0xA409),source);
        byte[] changed=declared.clone();changed[0]^=0x5A; // Explicit declared test mutation guarantees different pixels.
        var updated=CdR11aCameraArtResidency.writeTile(known,0x8120,CdAssetBytes.copyOf(changed));
        var after=bank(f,updated,BASE,ANTON_BASE);var expected=CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(changed));
        assertNotEquals(sample.version(),expected);assertEquals(expected,after.resolve(0x409).version());
        assertEquals(sample,source.resolve(0x409));assertUnknown(cold,0x409);
        assertEquals(sample.version(),picture.patternVersions().get(ANTON_BASE));
        Arrays.fill(declared,(byte)0);Arrays.fill(changed,(byte)0);
        byte[] detached=CdR11aCameraArtResidency.readTile(known,0x409).copy();Arrays.fill(detached,(byte)0xA5);
        assertEquals(sample,source.resolve(0x409));assertEquals(expected,after.resolve(0x409).version());
        assertEquals(picture,CdObjectPresentation.convert(singleTileSat(0xA409),bank(f,known,BASE,ANTON_BASE)));
        assertThrows(UnsupportedOperationException.class,()->picture.tiles().clear());
        assertThrows(UnsupportedOperationException.class,()->picture.patternVersions().clear());
    }

    @Test void actualFourFramePicturesKeepFiveLiteralPiecesAllFlipsAndCurrentNativeVersions() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());
        var source=bank(f,canonical(f,seeded(f)),BASE,ANTON_BASE);
        assertArrayEquals(RAW,f.objects().view(CdR11aObjectAssets.Table.ANTON_MAPPINGS).bytes().copy());
        for(int frame=0;frame<4;frame++)for(int flip=0;flip<4;flip++)for(int base:new int[]{0x2409,0xA409}) {
            int[][] pieces=PIECES[frame];var decoded=mappings.resolve(MAP,false,frame).pieces();assertEquals(5,decoded.size());
            for(int p=0;p<5;p++)assertEquals(new RawPiece(pieces[p][0],pieces[p][1],pieces[p][2],pieces[p][3]),decoded.get(p));
            var sat=sat(mappings,MAP,frame,flip,base);assertEquals(5,sat.spriteCount());byte[] raw=sat.bytes();
            for(int p=0;p<5;p++) {
                int[] piece=pieces[p];int width=((piece[1]>>>2)+1)*8,height=((piece[1]&3)+1)*8;
                assertEquals(0x180+((flip&1)==0?piece[3]:-piece[3]-width),word(raw,p*8+6));
                assertEquals(0x100+((flip&2)==0?piece[0]:-piece[0]-height),word(raw,p*8));
                assertEquals(((base+piece[2])&0xFFFF)^((flip&3)<<11),word(raw,p*8+4));
            }
            var picture=CdObjectPresentation.convert(sat,source);assertEquals(14,picture.tiles().size());int at=0;
            for(int p=4;p>=0;p--) {
                int[] piece=pieces[p];int cols=(piece[1]>>>2)+1,rows=(piece[1]&3)+1;
                int tileWord=((base+piece[2])&0xFFFF)^((flip&3)<<11);boolean h=(tileWord&0x800)!=0,v=(tileWord&0x1000)!=0;
                for(int row=0;row<rows;row++)for(int col=0;col<cols;col++) {
                    int id=(tileWord&0x7FF)+col*rows+row;var tile=picture.tiles().get(at++);
                    assertEquals(ANTON_BASE+id-0x409,tile.patternId());assertEquals(1,tile.palette());
                    assertEquals((base&0x8000)!=0,tile.priority());assertEquals(h,tile.hFlip());assertEquals(v,tile.vFlip());
                    assertEquals(word(raw,p*8+6)-128+(h?cols-1-col:col)*8,tile.x());
                    assertEquals(word(raw,p*8)-128+(v?rows-1-row:row)*8,tile.y());
                    assertEquals(source.resolve(id).version(),picture.patternVersions().get(tile.patternId()));
                }
            }
        }
    }

    @Test void actualPlc9CompletesPataHeadBeforePreparingAntonAndRetainsTheThreeTileServicePrefix() throws Exception {
        var f=fixture();var sources=f.objects().sectionArtSources();assertEquals(13,sources.sources().size());
        var actual=f.assets().loadPlan().plcs().get(9);assertEquals(3,actual.entries().size());
        var pata=sources.source(CdR11aAsset.ART_PATA_BATA);var anton=sources.source(CdR11aAsset.ART_ANTON);
        var tam=sources.source(CdR11aAsset.ART_TAMABBOH);
        assertSame(pata,sources.source(actual.entries().get(0)));assertSame(anton,sources.source(actual.entries().get(1)));
        assertSame(tam,sources.source(actual.entries().get(2)));
        assertEquals(50,pata.tileCount());assertEquals(22,anton.tileCount());assertEquals(59,tam.tileCount());
        assertEquals(0x7600,actual.entries().get(0).vramAddress());assertEquals(0x8120,actual.entries().get(1).vramAddress());
        assertEquals(0x8500,actual.entries().get(2).vramAddress());
        var owner=CdR11aSectionArt.fromAssets(f.assets());var initial=owner.devState(seeded(f),CdAssetBytes.copyOf(new byte[512]),0);
        var queued=owner.initList(initial,9);var pataReady=owner.prepare(queued);assertEquals(50,pataReady.remainingTilesWord());
        assertEquals(initial.residency(),pataReady.residency());
        var pataDone=owner.serviceFast(owner.serviceFast(owner.serviceFast(pataReady)));
        assertEquals(0,pataDone.remainingTilesWord());assertEquals(Integer.toUnsignedLong(actual.entries().get(1).sourceRange().nativeAddress()),pataDone.queue().headSource());
        for(int tile=0;tile<50;tile++)assertArrayEquals(pata.decode().bytes().slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(pataDone.residency(),0x3B0+tile).copy());
        assertSame(pataDone,owner.serviceSlow(pataDone,0)); // CPU preparation is a separate cut.
        var ready=owner.prepare(pataDone);assertEquals(22,ready.remainingTilesWord());assertEquals(pataDone.residency(),ready.residency());
        var before=bank(f,ready.residency(),BASE,ANTON_BASE);assertUnknown(before,0x409);
        var prefix=owner.serviceSlow(ready,0);assertEquals(19,prefix.remainingTilesWord());assertEquals(0x8180,prefix.queue().headDestination());
        var current=bank(f,prefix.residency(),BASE,ANTON_BASE);
        for(int tile=0;tile<3;tile++) {
            var expected=anton.decode().bytes().slice(tile*32,32);
            assertArrayEquals(expected.copy(),CdR11aCameraArtResidency.readTile(prefix.residency(),0x409+tile).copy());
            assertEquals(CdObjectPresentation.patternVersion(expected),current.resolve(0x409+tile).version());
        }
        for(int tile=3;tile<22;tile++)assertUnknown(current,0x409+tile);
        assertUnknown(before,0x409);assertUnknown(before,0x40B);
        var saved=prefix.savedDecoder();var cuts=new ArrayList<CdR11aSectionArt.State>();cuts.add(prefix);
        while(cuts.getLast().remainingTilesWord()!=0) {assertTrue(cuts.size()<9);cuts.add(owner.serviceSlow(cuts.getLast(),0));}
        var done=cuts.getLast();assertEquals(0,done.remainingTilesWord());
        assertEquals(Integer.toUnsignedLong(actual.entries().get(2).sourceRange().nativeAddress()),done.queue().headSource());
        assertEquals(cuts.get(cuts.size()-2).savedDecoder(),done.savedDecoder(),"FixBugs=0 final completion retains the prior saved decoder");
        assertEquals(saved,prefix.savedDecoder());assertEquals(19,prefix.remainingTilesWord());
        for(var cut:cuts) {
            var replay=cut;int bound=0;while(replay.remainingTilesWord()!=0) {assertTrue(bound++<8);replay=owner.serviceSlow(replay,0);}
            assertEquals(done,replay);
        }
        assertUnknown(current,0x40C);assertUnknown(before,0x409);
        for(int tile=0;tile<22;tile++)assertEquals(CdObjectPresentation.patternVersion(anton.decode().bytes().slice(tile*32,32)),
                bank(f,done.residency(),BASE,ANTON_BASE).resolve(0x409+tile).version());
        // Resolution/conversion never prepares or services the queue; all service calls above are explicit.
    }

    @Test void actualRelocatedPlcWritesOnePhysicalStateReusesEighteenBoulderIdsAndKeepsCanonicalAndLastBoulder() throws Exception {
        var f=fixture();var state=canonical(f,seeded(f));var before=bank(f,state,BASE,ANTON_BASE);
        var oldPicture=CdObjectPresentation.convert(singleTileSat(0x374),before);var oldVersion=before.resolve(0x374).version();
        var owner=CdR11aSectionArt.fromAssets(f.assets());var actual=f.assets().loadPlan().plcs().get(6).entries().getFirst();
        assertEquals(CdR11aAsset.ART_ANTON,actual.source());assertEquals(0x6E00,actual.vramAddress());
        var initial=owner.devState(state,CdAssetBytes.copyOf(new byte[512]),0);var ready=owner.prepare(owner.initList(initial,6));
        assertEquals(22,ready.remainingTilesWord());var done=owner.serviceFast(owner.serviceFast(ready));assertEquals(0,done.remainingTilesWord());
        var current=bank(f,done.residency(),BASE,ANTON_BASE);var anton=f.objects().sectionArtSources().source(CdR11aAsset.ART_ANTON);
        for(int tile=0;tile<22;tile++) {
            int id=0x370+tile;
            assertArrayEquals(anton.decode().bytes().slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(done.residency(),id).copy());
            if(tile<4) {int unsupported=id;assertThrows(UnsupportedOperationException.class,()->current.resolve(unsupported));}
            else {
                assertEquals(BASE[13]+id-0x374,current.resolve(id).logicalPatternId());
                assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(done.residency(),id)),current.resolve(id).version());
            }
            assertEquals(before.resolve(0x409+tile),current.resolve(0x409+tile));
        }
        assertEquals(before.resolve(0x386),current.resolve(0x386));
        assertEquals(oldVersion,oldPicture.patternVersions().get(BASE[13]));assertEquals(oldVersion,before.resolve(0x374).version());
        // Actual bytes may be equal or blank. Guaranteed changes below are explicitly test-only current writes.
        var mutated=done.residency();
        for(int tile=0;tile<22;tile++) {
            byte[] bytes=CdR11aCameraArtResidency.readTile(mutated,0x370+tile).copy();
            if(tile>=4)bytes[0]=(byte)(CdR11aCameraArtResidency.readTile(state,0x370+tile).unsignedByte(0)^0x5A);
            mutated=CdR11aCameraArtResidency.writeTile(mutated,0x6E00+tile*32,CdAssetBytes.copyOf(bytes));
        }
        var changed=bank(f,mutated,BASE,ANTON_BASE);
        for(int id=0x374;id<=0x385;id++) {
            assertNotEquals(before.resolve(id).version(),changed.resolve(id).version());
            assertEquals(before.resolve(id).logicalPatternId(),changed.resolve(id).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(mutated,id)),changed.resolve(id).version());
        }
        assertEquals(before.resolve(0x386),changed.resolve(0x386));
        for(int tile=0;tile<22;tile++)assertEquals(before.resolve(0x409+tile),changed.resolve(0x409+tile));
        for(int id=0x370;id<0x374;id++) {int unsupported=id;assertThrows(UnsupportedOperationException.class,()->changed.resolve(unsupported));}
        assertEquals(oldVersion,oldPicture.patternVersions().get(BASE[13]));
    }


    @Test void knownRelocatedBytesCannotFillUnknownCanonicalPixelsOrAdmitTheLowerFourIds() throws Exception {
        var f=fixture();var cold=seeded(f);var before=bank(f,cold,BASE,ANTON_BASE);
        var actual=f.objects().sectionArtSources().source(CdR11aAsset.ART_ANTON).decode().bytes();
        var relocated=write(cold,0x6E00,actual,22);var current=bank(f,relocated,BASE,ANTON_BASE);
        for(int tile=0;tile<22;tile++) {
            assertUnknown(current,0x409+tile);assertUnknown(before,0x409+tile);
            assertArrayEquals(actual.slice(tile*32,32).copy(),CdR11aCameraArtResidency.readTile(relocated,0x370+tile).copy());
        }
        for(int id=0x370;id<0x374;id++) {int unsupported=id;assertThrows(UnsupportedOperationException.class,()->current.resolve(unsupported));}
        for(int id=0x374;id<=0x385;id++) {
            assertEquals(BASE[13]+id-0x374,current.resolve(id).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(relocated,id)),current.resolve(id).version());
        }
        assertEquals(before.resolve(0x386),current.resolve(0x386));
        assertTrue(CdObjectPresentation.convert(emptySat(),current).tiles().isEmpty());
    }

    private record Fixture(CdR11aAssets assets,CdR11aObjectAssets objects,CdPlayerArtRuntime.State player,CdPowerupArtRuntime.State powerup) { }
    private static Fixture fixture() throws Exception {
        String configured=System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured!=null&&!configured.isBlank(),"Set soniccd.media.root for retained actual USA assets");
        Path cue;try(var files=Files.list(Path.of(configured).resolve("USA"))) {
            var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());cue=cues.getFirst();
        }
        CdR11aAssets assets;try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assets=CdR11aAssetLoader.load(programs,CdReleaseVariant.USA,CdStageKey.parse("R11A"));
        }
        // Explicit DEV RAM ingress, never native cold history or authenticated blank pixels.
        return new Fixture(assets,assets.openingObjects(),new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),
                new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]));
    }
    private static CdR11aCameraArtResidency.State legacySeed(Fixture f) throws Exception {
        var o=f.objects();return CdR11aCameraArtResidency.devSeed(o.initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),
                o.frontierArtBank().binding(CdR11aAsset.ART_3D_PLANT).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
    }
    private static CdR11aCameraArtResidency.State seeded(Fixture f) throws Exception {
        var sources=f.objects().sectionArtSources();var state=legacySeed(f);
        state=write(state,0x6E80,sources.source(CdR11aAsset.ART_BOULDER).decode().bytes(),19);
        state=write(state,0x9200,sources.source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes(),46);
        state=write(state,0x6400,sources.source(CdR11aAsset.ART_SPIKES).decode().bytes(),8);
        return write(state,0xD960,sources.source(CdR11aAsset.ART_CHECKPOINT).decode().bytes(),12);
    }
    private static CdR11aCameraArtResidency.State canonical(Fixture f,CdR11aCameraArtResidency.State state) throws Exception {
        // Explicit test fixture after the cold-policy assertions, never production preload.
        return write(state,0x8120,f.objects().sectionArtSources().source(CdR11aAsset.ART_ANTON).decode().bytes(),22);
    }
    private static CdR11aCameraArtResidency.State write(CdR11aCameraArtResidency.State state,int destination,CdAssetBytes bytes,int count) {
        for(int t=0;t<count;t++)state=CdR11aCameraArtResidency.writeTile(state,destination+t*32,bytes.slice(t*32,32));return state;
    }
    private static CdObjectPresentation.PatternSource oldBank(Fixture f,CdR11aCameraArtResidency.State state,int[] b) throws Exception {
        var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),
                o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,
                b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16]);
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int[] b,int anton) throws Exception {
        var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),
                o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,
                b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],anton);
    }
    private static void assertUnknown(CdObjectPresentation.PatternSource source,int id) {
        var stop=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->source.resolve(id));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,stop.boundary());assertEquals(id,stop.nativePatternId());
    }
    private static byte[] testOnlyTile(int salt) {
        byte[] bytes=new byte[32];for(int i=0;i<32;i++)bytes[i]=(byte)(salt+i*13);return bytes;
    }
    private static CpuSat singleTileSat(int tileWord) {
        var declared=CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(0x123456,
                CdAssetBytes.copyOf(HexFormat.of().parseHex("0002010000000000")),0,1)),List.of());
        return sat(declared,0x123456,0,0,tileWord);
    }
    private static CpuSat sat(CdDrawMappings mappings,int pointer,int frame,int flip,int base) {
        var slots=new EmptySlots(){LiveDrawSlot live=new LiveDrawSlot(0x3F,4|flip,0x100,0x80,0,pointer,frame,base);
            @Override public LiveDrawSlot read(CdSlotRef ref){assertEquals(55,ref.wholeIndex());return live;}
            @Override public void writeOnScreenBit(CdSlotRef ref,boolean on){assertEquals(55,ref.wholeIndex());var s=live;live=new LiveDrawSlot(s.idByte(),
                    (s.spriteFlagsByte()&127)|(on?128:0),s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());}
        };
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE])) {
            var camera=new CameraWords(0,0);queue.beginPass();
            assertEquals(EnqueueResult.QUEUED,queue.drawObject(new DrawCallCut(new CdSlotRef(55),4|flip,0x100,0x80,24,19,4),camera));
            return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));
        }
    }
    private static CpuSat emptySat() {
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(new EmptySlots(),
                (a,b,c)->{throw new AssertionError("No mapping read for empty SAT");},new byte[SAT_BYTE_SIZE])) {
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

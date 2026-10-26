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

/** Source-only CPU picture specifications; actual USA owners are opt-in. ALL JAVA UNRUN by this author. */
class TestCdCollapsePlatformPicture {
    private static final int PLATFORM_BASE=12563;
    private static final int[] FIRST={0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320,0x6CB,0x409};
    private static final int[] COUNT={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8,12,22};
    private static final int[] BASE={10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374,12456,12475,12521,12529,12541};
    private static final int[] CP_ROOT={0x20C520,0x20C5EC,0x20C5AE,0x20C7AC};
    private static final int[] CP_FRAMES={1,6,7,6};
    private static final int[][] CP_DIRECTORY={{2},{12,12,103,204,305,356},{14,20,26,32,38,44,50},{12,28,54,80,106,132}};
    private static final int[][] CP_COUNTS={{23},{18,18,20,20,10,10},{1,1,1,1,1,1,2},{3,5,5,5,5,5}};
    private static final byte[][] CP_RAW={
            HexFormat.of().parseHex("000217d005080dd8d005080de8d005080df8d005080d08d005080d18e0050811d8e0050811e8e0050811f8e005081108e005081118f0050019d8f0050815e8f0050815f8f005081508f00508151800050825d80005001de800050021f800050021080005081d1810050019f810050019081005001918"),
            HexFormat.of().parseHex("000c000c006700cc0131016412e005000dd0e005000de0e005000df0e005000d00e005000d10e005000d20f0050011d0f0050011e0f0050011f0f005001100f005001110f00500112000050015d000050015e000050015f000050015000005001510000500152014d005000de0d005000df0d005000d00d005000d10e0050011e0e0050011f0e005001100e005001110f0050015e0f0050015f0f005001500f00500151000050029e000050033f00005003300000500331010050031e01005002bf01005002b001005002b1014d005000de0d005000df0d005000d00d005000d10e0050011e0e0050011f0e005001100e005001110f0050015e0f0050015f0f005001500f00500151000050033e000050033f0000500330000050033101005002be01005002bf01005002b001005002b100ad005000df0d005000d00e0050011f0e005001100f0050015f0f0050015000005001df0000500210010050019f010050019000ad005000df0d005000d00e0050011f0e005001100f0050015f0f00500150000050021f00005081d0010050019f0100500190000"),
            HexFormat.of().parseHex("000e0014001a00200026002c003201f8050019f801f8050825f801f805001df801f8050021f801f805081df801f8050815f802e805080df8f8050811f800"),
            HexFormat.of().parseHex("000c001c00360050006a008403e005000df8f0050011f800050015f805d005000df8e0050011f8f0050015f800050029f810050031f805d005000df8e0050011f8f0050015f800050033f81005002bf805d005000df8e0050011f8f0050015f80005001df810050019f805d005000df8e0050011f8f0050015f80005081df810050019f805d005000df8e0050011f8f0050015f800050021f810050019f8")
    };

    @Test void coldAntonAndPlatformUnknownAllowC29FactoryAndEmptySatWithoutReadingEitherSpan() throws Exception {
        var f=fixture();var state=seeded(f);var old=oldBank(f,state,BASE);var now=bank(f,state,BASE,PLATFORM_BASE);
        assertTrue(CdObjectPresentation.convert(emptySat(),now).tiles().isEmpty());int known=0;
        for(int span=0;span<17;span++)for(int tile=0;tile<COUNT[span];tile++){assertEquals(old.resolve(FIRST[span]+tile),now.resolve(FIRST[span]+tile));known++;}
        assertEquals(579,known);for(int tile=0;tile<22;tile++)assertUnknown(now,0x409+tile);for(int tile=0;tile<57;tile++)assertUnknown(now,0x4BE+tile);
        assertEquals(0x9000,CdR11aCameraArtResidency.Region.AT_9000.destinationWord());assertEquals(0x1400,CdR11aCameraArtResidency.Region.AT_9000.byteLength());
        assertEquals(9,CdR11aCameraArtResidency.Region.values().length);assertEquals(0x97C0,(0x490+46)*32);assertEquals(0x9EE0,(0x4BE+57)*32);
        assertArrayEquals(new byte[0x2C0],state.region(CdR11aCameraArtResidency.Region.AT_8120).knownMask().copy());
    }

    @Test void selectedFirstMiddleLastPlatformUnknownAreStrictAndUnrelatedUnknownIsNeverRead() throws Exception {
        var f=fixture();var cold=seeded(f);var before=bank(f,cold,BASE,PLATFORM_BASE);
        for(int selected:new int[]{0x4BE,0x4DA,0x4F6}) {
            assertUnknown(before,selected);var sat=singleTileSat(0x4000|selected);
            var fault=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdObjectPresentation.convert(sat,before));assertEquals(selected,fault.nativePatternId());
            byte[] declared=testOnlyTile(selected);var known=CdR11aCameraArtResidency.writeTile(cold,selected*32,CdAssetBytes.copyOf(declared));
            var source=bank(f,known,BASE,PLATFORM_BASE);var picture=CdObjectPresentation.convert(sat,source);assertEquals(1,picture.tiles().size());
            assertEquals(PLATFORM_BASE+selected-0x4BE,picture.tiles().getFirst().patternId());assertEquals(2,picture.tiles().getFirst().palette());
            assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(declared)),source.resolve(selected).version());
            for(int tile=0;tile<57;tile++)if(0x4BE+tile!=selected)assertUnknown(source,0x4BE+tile);
            for(int tile=0;tile<22;tile++)assertUnknown(source,0x409+tile);
            assertUnknown(before,selected);assertTrue(CdObjectPresentation.convert(emptySat(),source).tiles().isEmpty());
            var mask=known.region(CdR11aCameraArtResidency.Region.AT_9000).knownMask();int start=0x97C0-0x9000;
            for(int tile=0;tile<57;tile++)for(int b=0;b<32;b++)assertEquals(tile+0x4BE==selected?1:0,mask.unsignedByte(start+tile*32+b));
        }
    }

    @Test void allEighteenExplicitLogicalSpanBoundariesAndOverflowValidateEvenForEmptySat() throws Exception {
        var f=fixture();var cold=seeded(f);
        for(int invalid:new int[]{-1,Integer.MIN_VALUE,Integer.MAX_VALUE-55,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->bank(f,cold,BASE,invalid));
        for(int span=0;span<18;span++)for(int base:new int[]{BASE[span]-56,BASE[span]+COUNT[span]-1})assertThrows(IllegalArgumentException.class,()->bank(f,cold,BASE,base));
        int[] separated=new int[18];for(int span=0;span<18;span++)separated[span]=10000+span*1000;
        for(int span=0;span<18;span++) {
            assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,cold,separated,separated[span]-57)).tiles().isEmpty());
            assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,cold,separated,separated[span]+COUNT[span])).tiles().isEmpty());
            for(int base:new int[]{separated[span]-56,separated[span]+COUNT[span]-1})assertThrows(IllegalArgumentException.class,()->bank(f,cold,separated,base));
        }
        var first=CdR11aCameraArtResidency.writeTile(cold,0x97C0,CdAssetBytes.copyOf(testOnlyTile(1)));assertEquals(0,bank(f,first,BASE,0).resolve(0x4BE).logicalPatternId());
        var last=CdR11aCameraArtResidency.writeTile(cold,0x9EC0,CdAssetBytes.copyOf(testOnlyTile(2)));assertEquals(Integer.MAX_VALUE,bank(f,last,BASE,Integer.MAX_VALUE-56).resolve(0x4F6).logicalPatternId());
    }

    @Test void unchangedC28ValidationAndOldEagerUnknownPrecedenceRunFirst() throws Exception {
        var f=fixture();var state=seeded(f);int[] bad=BASE.clone();bad[1]=bad[0];
        var previous=assertThrows(IllegalArgumentException.class,()->oldBank(f,state,bad));var current=assertThrows(IllegalArgumentException.class,()->bank(f,state,bad,-1));assertEquals(previous.getMessage(),current.getMessage());
        int[] badAnton=BASE.clone();badAnton[17]=-1;
        assertEquals(assertThrows(IllegalArgumentException.class,()->oldBank(f,state,badAnton)).getMessage(),assertThrows(IllegalArgumentException.class,()->bank(f,state,badAnton,-1)).getMessage());
        var cold=legacySeed(f);var old=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->oldBank(f,cold,BASE));var now=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,cold,BASE,-1));
        assertEquals(old.boundary(),now.boundary());assertEquals(0x374,now.nativePatternId());
    }

    @Test void explicitKnownCurrentStateHasNineteenSpans658PatternsAndStrictUnadmittedNativeIds() throws Exception {
        var f=fixture();var state=known(f);var old=oldBank(f,state,BASE);var source=bank(f,state,BASE,PLATFORM_BASE);
        var natives=new TreeSet<Integer>();var logical=new TreeSet<Integer>();
        for(int span=0;span<18;span++)for(int tile=0;tile<COUNT[span];tile++) {
            int id=FIRST[span]+tile;assertEquals(old.resolve(id),source.resolve(id));assertEquals(BASE[span]+tile,source.resolve(id).logicalPatternId());assertTrue(natives.add(id));assertTrue(logical.add(source.resolve(id).logicalPatternId()));
        }
        assertEquals(601,natives.size());
        for(int tile=0;tile<57;tile++){int id=0x4BE+tile;assertTrue(natives.add(id));assertTrue(logical.add(source.resolve(id).logicalPatternId()));assertEquals(PLATFORM_BASE+tile,source.resolve(id).logicalPatternId());assertEquals(CdObjectPresentation.patternVersion(CdR11aCameraArtResidency.readTile(state,id)),source.resolve(id).version());assertThrows(UnsupportedOperationException.class,()->old.resolve(id));}
        assertEquals(658,natives.size());assertEquals(658,logical.size());
        for(int id=0;id<2048;id++)if(!natives.contains(id)){int absent=id;assertThrows(UnsupportedOperationException.class,()->source.resolve(absent));}
        for(int bad:new int[]{-1,Integer.MIN_VALUE,2048,0x44BE,0xFFFF,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->source.resolve(bad));
        var outside=CdR11aCameraArtResidency.writeTile(state,0x9EE0,CdAssetBytes.copyOf(testOnlyTile(3)));assertThrows(UnsupportedOperationException.class,()->bank(f,outside,BASE,PLATFORM_BASE).resolve(0x4F7));
    }

    @Test void fourStaticRootPicturesPreserveLiteralGeometryPaletteTwoRawFlipsAndFinalSatSelection() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var state=known(f);var source=bank(f,state,BASE,PLATFORM_BASE);
        var visuals=List.of(f.objects().collapsePlatform1(),f.objects().collapsePlatform2(),f.objects().collapsePlatform3(),f.objects().collapsePlatform4());var union=new TreeSet<Integer>();
        for(int root=0;root<4;root++) {
            assertArrayEquals(CP_RAW[root],visuals.get(root).mappings().copy());assertEquals(CP_FRAMES[root],visuals.get(root).mappingFrames().size());assertTrue(visuals.get(root).animations().isEmpty());
            for(int frame=0;frame<CP_FRAMES[root];frame++)for(int flip=0;flip<4;flip++) {
                var pieces=mappings.resolve(CP_ROOT[root],false,frame).pieces();int offset=CP_DIRECTORY[root][frame];assertEquals(CP_COUNTS[root][frame],pieces.size());
                var sat=sat(mappings,CP_ROOT[root],frame,flip,0x44BE);assertEquals(pieces.size(),sat.spriteCount());byte[] bytes=sat.bytes();
                for(int p=0;p<pieces.size();p++) {
                    int at=offset+1+p*5;var piece=pieces.get(p);int expectedWord=word(CP_RAW[root],at+2);
                    assertEquals(new RawPiece(CP_RAW[root][at],5,expectedWord,CP_RAW[root][at+4]),piece);
                    assertEquals(0x100+((flip&2)==0?piece.y():-piece.y()-16),word(bytes,p*8));assertEquals(0x180+((flip&1)==0?piece.x():-piece.x()-16),word(bytes,p*8+6));
                    assertEquals(((0x44BE+expectedWord)&0xFFFF)^(flip<<11),word(bytes,p*8+4));assertEquals(5,bytes[p*8+2]&255);assertEquals(p+1,bytes[p*8+3]&255);
                }
                var picture=CdObjectPresentation.convert(sat,source);assertEquals(pieces.size()*4,picture.tiles().size());int at=0;
                for(int p=pieces.size()-1;p>=0;p--) {
                    int tileWord=word(bytes,p*8+4);boolean h=(tileWord&0x800)!=0,v=(tileWord&0x1000)!=0;
                    for(int row=0;row<2;row++)for(int col=0;col<2;col++) {
                        int id=(tileWord&0x7FF)+col*2+row;union.add(id);var tile=picture.tiles().get(at++);
                        assertEquals(PLATFORM_BASE+id-0x4BE,tile.patternId());assertEquals(2,tile.palette());assertFalse(tile.priority());assertEquals(h,tile.hFlip());assertEquals(v,tile.vFlip());
                        assertEquals(word(bytes,p*8+6)-128+(h?1-col:col)*8,tile.x());assertEquals(word(bytes,p*8)-128+(v?1-row:row)*8,tile.y());assertEquals(source.resolve(id).version(),picture.patternVersions().get(tile.patternId()));
                    }
                }
            }
        }
        var expected=new TreeSet<Integer>();for(int tile=0x4CB;tile<=0x4EC;tile++)expected.add(tile);for(int tile=0x4EF;tile<=0x4F4;tile++)expected.add(tile);assertEquals(expected,union);assertEquals(40,union.size());
    }

    @Test void aliasedBigRingExtentWritesChangeCurrentOverlapAndKeepSourcesMasksAndPicturesImmutable() throws Exception {
        var f=fixture();var state=known(f);var before=bank(f,state,BASE,PLATFORM_BASE);var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var sat=sat(mappings,CP_ROOT[1],0,0,0x44BE);var frozen=CdObjectPresentation.convert(sat,before);
        // Declared current-byte writes at the REAL BigRing alias interval [9100,9E40).
        // NOT decoded BigRing art, a source-identity surrogate, or execution of PLC18.
        byte[] declared=new byte[106*32];for(int tile=0;tile<106;tile++)Arrays.fill(declared,tile*32,(tile+1)*32,(byte)(tile^0xA5));var bytes=CdAssetBytes.copyOf(declared);
        var touching=bank(f,write(state,0x9100,bytes,54),BASE,PLATFORM_BASE);for(int tile=0;tile<57;tile++)assertEquals(before.resolve(0x4BE+tile),touching.resolve(0x4BE+tile));
        var prefix=write(state,0x9100,bytes,55);var one=bank(f,prefix,BASE,PLATFORM_BASE);assertEquals(CdObjectPresentation.patternVersion(bytes.slice(54*32,32)),one.resolve(0x4BE).version());
        for(int tile=1;tile<57;tile++)assertEquals(before.resolve(0x4BE+tile),one.resolve(0x4BE+tile));
        var complete=write(state,0x9100,bytes,106);var current=bank(f,complete,BASE,PLATFORM_BASE);
        for(int tile=0;tile<52;tile++)assertEquals(CdObjectPresentation.patternVersion(bytes.slice((54+tile)*32,32)),current.resolve(0x4BE+tile).version());
        for(int tile=52;tile<57;tile++)assertEquals(before.resolve(0x4BE+tile),current.resolve(0x4BE+tile));
        assertEquals(frozen,CdObjectPresentation.convert(sat,before));assertEquals(frozen,CdObjectPresentation.convert(sat,bank(f,state,BASE,PLATFORM_BASE)));assertNotEquals(frozen,CdObjectPresentation.convert(sat,current));
        byte[] detached=CdR11aCameraArtResidency.readTile(state,0x4BE).copy();Arrays.fill(detached,(byte)0);Arrays.fill(declared,(byte)0);assertEquals(frozen,CdObjectPresentation.convert(sat,before));
        assertThrows(UnsupportedOperationException.class,()->frozen.tiles().clear());assertThrows(UnsupportedOperationException.class,()->frozen.patternVersions().clear());
        var cold=seeded(f);var unknown=bank(f,cold,BASE,PLATFORM_BASE);var coldAlias=bank(f,write(cold,0x9100,bytes,106),BASE,PLATFORM_BASE);
        for(int tile=0;tile<52;tile++)assertEquals(CdObjectPresentation.patternVersion(bytes.slice((54+tile)*32,32)),coldAlias.resolve(0x4BE+tile).version());
        for(int tile=52;tile<57;tile++)assertUnknown(coldAlias,0x4BE+tile);assertUnknown(unknown,0x4BE);assertUnknown(unknown,0x4F6);
        assertEquals(prefix,write(state,0x9100,bytes,55));
    }

    private record Fixture(CdR11aAssets assets,CdR11aObjectAssets objects,CdPlayerArtRuntime.State player,CdPowerupArtRuntime.State powerup) { }
    private static Fixture fixture() throws Exception {
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root for retained actual USA assets");
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))) {var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        CdR11aAssets assets;try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {assets=CdR11aAssetLoader.load(programs,CdReleaseVariant.USA,CdStageKey.parse("R11A"));}
        return new Fixture(assets,assets.openingObjects(),new CdPlayerArtRuntime.State(new byte[0x300],new byte[0x300],0,false),new CdPowerupArtRuntime.State(new byte[0x480],new byte[0x480]));
    }
    private static CdR11aCameraArtResidency.State legacySeed(Fixture f) throws Exception {
        var o=f.objects();return CdR11aCameraArtResidency.devSeed(o.initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_PLANT).bytes(),o.frontierArtBank().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
    }
    private static CdR11aCameraArtResidency.State seeded(Fixture f) throws Exception {
        var state=legacySeed(f);var sources=f.objects().sectionArtSources();state=write(state,0x6E80,sources.source(CdR11aAsset.ART_BOULDER).decode().bytes(),19);
        state=write(state,0x9200,sources.source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes(),46);state=write(state,0x6400,sources.source(CdR11aAsset.ART_SPIKES).decode().bytes(),8);return write(state,0xD960,sources.source(CdR11aAsset.ART_CHECKPOINT).decode().bytes(),12);
    }
    private static CdR11aCameraArtResidency.State known(Fixture f) throws Exception {
        var sources=f.objects().sectionArtSources();var state=write(seeded(f),0x8120,sources.source(CdR11aAsset.ART_ANTON).decode().bytes(),22);
        return write(state,0x97C0,sources.source(CdR11aAsset.ART_PLATFORM).decode().bytes(),57); // Explicit fixture ingress only.
    }
    private static CdR11aCameraArtResidency.State write(CdR11aCameraArtResidency.State state,int address,CdAssetBytes bytes,int count) {
        for(int tile=0;tile<count;tile++)state=CdR11aCameraArtResidency.writeTile(state,address+tile*32,bytes.slice(tile*32,32));return state;
    }
    private static CdObjectPresentation.PatternSource oldBank(Fixture f,CdR11aCameraArtResidency.State state,int[] b) throws Exception {
        var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],b[17]);
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int[] b,final int collapsePlatformLogicalBase) throws Exception {
        var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],b[17],collapsePlatformLogicalBase);
    }
    private static void assertUnknown(CdObjectPresentation.PatternSource source,int id) {
        var fault=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->source.resolve(id));assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,fault.boundary());assertEquals(id,fault.nativePatternId());
    }
    private static byte[] testOnlyTile(int salt){byte[] bytes=new byte[32];for(int b=0;b<32;b++)bytes[b]=(byte)(salt+b*13);return bytes;}
    private static CpuSat singleTileSat(int tileWord) {
        var mappings=CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(0x123456,CdAssetBytes.copyOf(HexFormat.of().parseHex("0002010000000000")),0,1)),List.of());return sat(mappings,0x123456,0,0,tileWord);
    }
    private static CpuSat sat(CdDrawMappings mappings,int pointer,int frame,int flip,int base) {
        var slots=new EmptySlots(){LiveDrawSlot live=new LiveDrawSlot(0x20,4|flip,0x100,0x80,0,pointer,frame,base);
            @Override public LiveDrawSlot read(CdSlotRef ref){assertEquals(40,ref.wholeIndex());return live;}
            @Override public void writeOnScreenBit(CdSlotRef ref,boolean on){assertEquals(40,ref.wholeIndex());var s=live;live=new LiveDrawSlot(s.idByte(),(s.spriteFlagsByte()&127)|(on?128:0),s.xWord(),s.yWord(),s.screenYWord(),s.mappingAddressBits(),s.mapFrameByte(),s.baseTileWord());}
        };
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE])) {var camera=new CameraWords(0,0);queue.beginPass();assertEquals(EnqueueResult.QUEUED,queue.drawObject(new DrawCallCut(new CdSlotRef(40),4|flip,0x100,0x80,48,34,3),camera));return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));}
    }
    private static CpuSat emptySat() {
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(new EmptySlots(),(a,b,c)->{throw new AssertionError("No mapping read for empty SAT");},new byte[SAT_BYTE_SIZE])) {var camera=new CameraWords(0,0);queue.beginPass();return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));}
    }
    private static class EmptySlots implements CdDrawSlots {
        @Override public LiveDrawSlot read(CdSlotRef ref){throw new AssertionError("No slot read expected");}
        @Override public void writeOnScreenBit(CdSlotRef ref,boolean on){throw new AssertionError("No slot write expected");}
    }
    private static int word(byte[] bytes,int at){return ((bytes[at]&255)<<8)|(bytes[at+1]&255);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

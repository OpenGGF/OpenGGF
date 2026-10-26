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

/** Source-only CPU picture specifications with actual USA owners. ALL JAVA UNRUN by this author. */
class TestCdTamabbohPicture {
    private static final int TAMABBOH_BASE=12620;
    private static final int[] FIRST={0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320,0x6CB,0x409,0x4BE};
    private static final int[] COUNT={24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8,12,22,57};
    private static final int[] BASE={10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,12326,12362,12374,12456,12475,12521,12529,12541,12563};
    private static final int[] ROOT={0x20B968,0x20B97E,0x20BA70}, FRAMES={3,3,4};
    private static final int[][] DIRECTORY={{6,34,50},{6,12,28},{8,14,20,31}};
    private static final byte[] BODY=HexFormat.of().parseHex("00060022003203f10d0000f0010c0008f00908000cf80006000c001c01f00f002bf003f20d0000f0020c000ff00a080013f802f0080016f7f80e0019ef00");
    private static final byte[] MISSILE=HexFormat.of().parseHex("0008000e0014001f01fc000025fc01fc000026fc02f8040027f800041027f802f8040029f800041029f8");

    @Test void unknown440AndLazyAntonPlatformPermitFactoryEmptyAndUnrelatedSat() throws Exception {
        var f=fixture();var state=seeded(f);var old=oldBank(f,state,BASE);var source=bank(f,state,BASE,TAMABBOH_BASE);
        assertTrue(CdObjectPresentation.convert(emptySat(),source).tiles().isEmpty());
        assertEquals(CdObjectPresentation.convert(singleTileSat(0x27AE),old),CdObjectPresentation.convert(singleTileSat(0x27AE),source));
        assertUnknown(source,0x440);assertUnknown(source,0x409);assertUnknown(source,0x4BE);
        assertArrayEquals(new byte[0x2C0],state.region(CdR11aCameraArtResidency.Region.AT_8120).knownMask().copy());
        assertEquals(0,state.region(CdR11aCameraArtResidency.Region.AT_8480).knownMask().unsignedByte(0x380));
        assertEquals(9,CdR11aCameraArtResidency.Region.values().length);
    }

    @Test void selectedUnknown440FaultsAt8800AndC29StillRejectsKnown440() throws Exception {
        var f=fixture();var cold=seeded(f);var source=bank(f,cold,BASE,TAMABBOH_BASE);
        var fault=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdObjectPresentation.convert(singleTileSat(0x2440),source));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,fault.boundary());assertEquals(0x440,fault.nativePatternId());assertEquals(0x8800,fault.destinationWord());
        var known=CdR11aCameraArtResidency.writeTile(cold,0x8800,CdAssetBytes.copyOf(testOnlyTile(9)));
        assertThrows(UnsupportedOperationException.class,()->oldBank(f,known,BASE).resolve(0x440));
        assertEquals(TAMABBOH_BASE,bank(f,known,BASE,TAMABBOH_BASE).resolve(0x440).logicalPatternId());assertUnknown(source,0x440);
    }

    @Test void exact32CurrentBytesAddOnlyOneIdentityBetween43fAnd441AndDelegateInvalidWidths() throws Exception {
        var f=fixture();byte[] declared=testOnlyTile(0x31);var state=CdR11aCameraArtResidency.writeTile(seeded(f),0x8800,CdAssetBytes.copyOf(declared));
        var old=oldBank(f,state,BASE);var source=bank(f,state,BASE,TAMABBOH_BASE);
        assertArrayEquals(declared,CdR11aCameraArtResidency.readTile(state,0x440).copy());
        assertEquals(CdObjectPresentation.patternVersion(CdAssetBytes.copyOf(declared)),source.resolve(0x440).version());
        assertEquals(BASE[3]+27,source.resolve(0x43F).logicalPatternId());assertEquals(BASE[4],source.resolve(0x441).logicalPatternId());
        assertEquals(old.resolve(0x43F),source.resolve(0x43F));assertEquals(old.resolve(0x441),source.resolve(0x441));
        for(int invalid:new int[]{-1,Integer.MIN_VALUE,2048,0x2440,0xFFFF,Integer.MAX_VALUE}) {
            var previous=assertThrows(IllegalArgumentException.class,()->old.resolve(invalid));var current=assertThrows(IllegalArgumentException.class,()->source.resolve(invalid));
            assertEquals(previous.getClass(),current.getClass());assertEquals(previous.getMessage(),current.getMessage());
        }
    }

    @Test void all658InheritedIdentitiesVersionsAndExclusionsSurviveExactlyOneNewBinding() throws Exception {
        var f=fixture();var state=write(known(f),0x8500,testOnlyBank(59,0x45),59);var old=oldBank(f,state,BASE);var source=bank(f,state,BASE,TAMABBOH_BASE);
        var natives=new TreeSet<Integer>();var logical=new TreeSet<Integer>();
        for(int span=0;span<FIRST.length;span++)for(int tile=0;tile<COUNT[span];tile++) {
            int id=FIRST[span]+tile;assertEquals(old.resolve(id),source.resolve(id));assertEquals(BASE[span]+tile,source.resolve(id).logicalPatternId());
            assertTrue(natives.add(id));assertTrue(logical.add(source.resolve(id).logicalPatternId()));
        }
        assertEquals(658,natives.size());assertEquals(658,logical.size());assertTrue(natives.add(0x440));assertTrue(logical.add(source.resolve(0x440).logicalPatternId()));
        assertEquals(659,natives.size());assertEquals(659,logical.size());
        for(int id=0;id<2048;id++)if(!natives.contains(id)) {
            int absent=id;var previous=assertThrows(UnsupportedOperationException.class,()->old.resolve(absent));var current=assertThrows(UnsupportedOperationException.class,()->source.resolve(absent));
            assertEquals(previous.getClass(),current.getClass());assertEquals(previous.getMessage(),current.getMessage());
        }
    }

    @Test void allNineteenFirstLastLogicalOverlapsRejectAndBothAdjacenciesAllowEmptySat() throws Exception {
        var f=fixture();var state=seeded(f);int[] separated=new int[19];for(int span=0;span<19;span++)separated[span]=10000+span*1000;
        for(int span=0;span<19;span++) {
            for(int overlap:new int[]{separated[span],separated[span]+COUNT[span]-1})assertThrows(IllegalArgumentException.class,()->bank(f,state,separated,overlap));
            for(int adjacent:new int[]{separated[span]-1,separated[span]+COUNT[span]}) {
                var source=bank(f,state,separated,adjacent);assertTrue(CdObjectPresentation.convert(emptySat(),source).tiles().isEmpty());
                assertUnknown(source,0x440);assertUnknown(source,0x409);assertUnknown(source,0x4BE);
            }
        }
    }

    @Test void negativeBasesRejectZeroAndMaxSingletonAreLegalAndOldOverflowNeverWraps() throws Exception {
        var f=fixture();var cold=seeded(f);for(int invalid:new int[]{-1,Integer.MIN_VALUE})assertThrows(IllegalArgumentException.class,()->bank(f,cold,BASE,invalid));
        var state=CdR11aCameraArtResidency.writeTile(cold,0x8800,CdAssetBytes.copyOf(testOnlyTile(7)));
        for(int valid:new int[]{0,Integer.MAX_VALUE}) {
            var source=bank(f,state,BASE,valid);assertTrue(CdObjectPresentation.convert(emptySat(),source).tiles().isEmpty());assertEquals(valid,source.resolve(0x440).logicalPatternId());
        }
        int[] bounded=BASE.clone();bounded[18]=Integer.MAX_VALUE-56;assertTrue(CdObjectPresentation.convert(emptySat(),bank(f,cold,bounded,0)).tiles().isEmpty());
        assertThrows(IllegalArgumentException.class,()->bank(f,cold,bounded,Integer.MAX_VALUE));
        int[] overflow=BASE.clone();overflow[18]=Integer.MAX_VALUE-55;assertThrows(IllegalArgumentException.class,()->bank(f,cold,overflow,0));
    }

    @Test void unchangedC29ValidationAndOldEagerUnknownFailureOrderRunFirst() throws Exception {
        var f=fixture();var state=seeded(f);int[] overlap=BASE.clone();overlap[1]=overlap[0];int[] anton=BASE.clone();anton[17]=-1;int[] platform=BASE.clone();platform[18]=Integer.MAX_VALUE-55;
        for(int[] invalid:List.of(overlap,anton,platform)) {
            var previous=assertThrows(IllegalArgumentException.class,()->oldBank(f,state,invalid));var current=assertThrows(IllegalArgumentException.class,()->bank(f,state,invalid,-1));
            assertEquals(previous.getMessage(),current.getMessage());
        }
        var cold=legacySeed(f);var previous=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->oldBank(f,cold,BASE));
        var current=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->bank(f,cold,BASE,-1));
        assertEquals(previous.boundary(),current.boundary());assertEquals(previous.destinationWord(),current.destinationWord());assertEquals(0x374,current.nativePatternId());
    }

    @Test void public59TileCommitsReuseAll58PlantRampAliasesAndExactCurrentBytes() throws Exception {
        var f=fixture();var initial=seeded(f);var before=bank(f,initial,BASE,TAMABBOH_BASE);var bytes=testOnlyBank(59,0x53);var state=write(initial,0x8500,bytes,59);
        var source=bank(f,state,BASE,TAMABBOH_BASE);var ids=new TreeSet<Integer>();
        for(int offset=0;offset<59;offset++) {
            int id=0x428+offset,expected=aliasId(offset);assertEquals(expected,source.resolve(id).logicalPatternId());assertTrue(ids.add(expected));
            assertArrayEquals(bytes.slice(offset*32,32).copy(),CdR11aCameraArtResidency.readTile(state,id).copy());
            assertEquals(CdObjectPresentation.patternVersion(bytes.slice(offset*32,32)),source.resolve(id).version());
            if(offset!=24)assertEquals(before.resolve(id).logicalPatternId(),expected);
        }
        assertEquals(59,ids.size());assertEquals(58,ids.stream().filter(id->id!=TAMABBOH_BASE).count());
        for(int id=0x424;id<0x428;id++)assertEquals(before.resolve(id),source.resolve(id));for(int id=0x463;id<0x480;id++)assertEquals(before.resolve(id),source.resolve(id));assertUnknown(before,0x440);
    }

    @Test void offset23CommitLeaves440UnknownAndOffset24CommitMakesOnlyThatGapKnown() throws Exception {
        var f=fixture();var cold=seeded(f);var bytes=testOnlyBank(59,0x67);
        // Explicit public commits, NOT native service-call timing: count24 ends at zero-based offset23.
        var prefix=write(cold,0x8500,bytes,24);var before=bank(f,prefix,BASE,TAMABBOH_BASE);
        assertEquals(CdObjectPresentation.patternVersion(bytes.slice(23*32,32)),before.resolve(0x43F).version());assertUnknown(before,0x440);
        var committed=CdR11aCameraArtResidency.writeTile(prefix,0x8800,bytes.slice(24*32,32));var after=bank(f,committed,BASE,TAMABBOH_BASE);
        assertEquals(CdObjectPresentation.patternVersion(bytes.slice(24*32,32)),after.resolve(0x440).version());assertEquals(before.resolve(0x441),after.resolve(0x441));assertUnknown(before,0x440);
        for(int b=0;b<32;b++) {
            assertEquals(0,prefix.region(CdR11aCameraArtResidency.Region.AT_8480).knownMask().unsignedByte(0x380+b));
            assertTrue(prefix.region(CdR11aCameraArtResidency.Region.AT_8480).knownByte(0x380+b).isEmpty());
            assertEquals(1,committed.region(CdR11aCameraArtResidency.Region.AT_8480).knownMask().unsignedByte(0x380+b));
        }
    }

    @Test void plantRampReloadsRestoreTheir58AliasesWithoutTouching440() throws Exception {
        var f=fixture();var bytes=testOnlyBank(59,0x79);var state=write(seeded(f),0x8500,bytes,59);var before=bank(f,state,BASE,TAMABBOH_BASE);
        var frontier=f.objects().frontierArtBank();var plant=frontier.binding(CdR11aAsset.ART_3D_PLANT);var ramp=frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        var reloaded=write(write(state,plant.plc().vramAddress(),plant.bytes(),plant.tileCount()),ramp.plc().vramAddress(),ramp.bytes(),ramp.tileCount());var source=bank(f,reloaded,BASE,TAMABBOH_BASE);
        assertEquals(before.resolve(0x440),source.resolve(0x440));assertArrayEquals(bytes.slice(24*32,32).copy(),CdR11aCameraArtResidency.readTile(reloaded,0x440).copy());
        for(int offset=0;offset<59;offset++)if(offset!=24) {
            var expected=offset<24?plant.bytes().slice((offset+4)*32,32):ramp.bytes().slice((offset-25)*32,32);
            assertEquals(aliasId(offset),source.resolve(0x428+offset).logicalPatternId());assertEquals(CdObjectPresentation.patternVersion(expected),source.resolve(0x428+offset).version());
        }
    }

    @Test void retainedStatesSourcesMasksAndPicturesSurviveCurrentMutationAndNonwrites() throws Exception {
        var f=fixture();var cold=seeded(f);var input=testOnlyTile(0x19);var state=CdR11aCameraArtResidency.writeTile(cold,0x8800,CdAssetBytes.copyOf(input));
        var source=bank(f,state,BASE,TAMABBOH_BASE);var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var selected=sat(mappings,ROOT[0],2,0,0x2428);
        var frozen=CdObjectPresentation.convert(selected,source);var mask=state.region(CdR11aCameraArtResidency.Region.AT_8480).knownMask();
        var changed=CdR11aCameraArtResidency.writeTile(state,0x8800,CdAssetBytes.copyOf(testOnlyTile(0x2A)));var current=bank(f,changed,BASE,TAMABBOH_BASE);
        assertNotEquals(source.resolve(0x440).version(),current.resolve(0x440).version());assertNotEquals(frozen,CdObjectPresentation.convert(selected,current));
        assertEquals(frozen,CdObjectPresentation.convert(selected,source));assertEquals(frozen,CdObjectPresentation.convert(selected,bank(f,state,BASE,TAMABBOH_BASE)));
        assertArrayEquals(mask.copy(),changed.region(CdR11aCameraArtResidency.Region.AT_8480).knownMask().copy());
        for(var region:CdR11aCameraArtResidency.Region.values())if(region!=CdR11aCameraArtResidency.Region.AT_8480)assertEquals(state.region(region),changed.region(region));
        for(int id:new int[]{0x43F,0x441})assertEquals(source.resolve(id),current.resolve(id));
        assertThrows(IllegalArgumentException.class,()->CdR11aCameraArtResidency.writeTile(state,0x8801,CdAssetBytes.copyOf(input)));
        assertThrows(IllegalArgumentException.class,()->CdR11aCameraArtResidency.writeTile(state,0x8800,CdAssetBytes.copyOf(new byte[31])));
        assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.writeTile(state,0xA880,CdAssetBytes.copyOf(input)));
        Arrays.fill(input,(byte)0);byte[] detached=CdR11aCameraArtResidency.readTile(state,0x440).copy();Arrays.fill(detached,(byte)0);byte[] flags=mask.copy();Arrays.fill(flags,(byte)0);
        assertArrayEquals(mask.copy(),state.region(CdR11aCameraArtResidency.Region.AT_8480).knownMask().copy());assertEquals(frozen,CdObjectPresentation.convert(selected,source));assertUnknown(bank(f,cold,BASE,TAMABBOH_BASE),0x440);
        assertThrows(UnsupportedOperationException.class,()->frozen.tiles().clear());assertThrows(UnsupportedOperationException.class,()->frozen.patternVersions().clear());
        assertEquals(changed,CdR11aCameraArtResidency.writeTile(state,0x8800,CdAssetBytes.copyOf(testOnlyTile(0x2A))));
    }

    @Test void tenRealViewsProjectFourHvWholeWordsAndPalettePriorityRetain59UnionAndMissileRepeats() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var state=write(seeded(f),0x8500,testOnlyBank(59,0x3D),59);var source=bank(f,state,BASE,TAMABBOH_BASE);
        var visuals=List.of(f.objects().tamabbohNormal(),f.objects().tamabbohDamaged(),f.objects().tamabbohMissile());
        assertArrayEquals(BODY,visuals.get(0).mappings().copy());assertArrayEquals(BODY,visuals.get(1).mappings().copy());assertArrayEquals(MISSILE,visuals.get(2).mappings().copy());
        assertArrayEquals(HexFormat.of().parseHex("000400081d0001ff3b02fc00"),visuals.get(0).animationTable().copy());
        assertArrayEquals(HexFormat.of().parseHex("000400160500010001000100010001000100010001fc0b02fc00"),visuals.get(1).animationTable().copy());
        assertArrayEquals(HexFormat.of().parseHex("00020200010203ff"),visuals.get(2).animationTable().copy());
        var union=new TreeSet<Integer>();var ids=new TreeSet<Integer>();int views=0;
        for(int root=0;root<3;root++)for(int frame=0;frame<FRAMES[root];frame++) {
            views++;var pieces=mappings.resolve(ROOT[root],false,frame).pieces();int offset=(root==1?22:0)+DIRECTORY[root][frame];byte[] raw=root==2?MISSILE:BODY;assertEquals(raw[offset]&255,pieces.size());
            for(int flip=0;flip<4;flip++)for(int priority:new int[]{0,0x8000}) {
                var sat=sat(mappings,ROOT[root],frame,flip,0x2428|priority);byte[] bytes=sat.bytes();assertEquals(pieces.size(),sat.spriteCount());
                for(int p=0;p<pieces.size();p++) {
                    var piece=pieces.get(p);int at=offset+1+p*5,width=(piece.sizeByte()>>>2)+1,height=(piece.sizeByte()&3)+1;
                    assertEquals(new RawPiece(raw[at],raw[at+1]&255,word(raw,at+2),raw[at+4]),piece);
                    assertEquals(0x180+((flip&1)==0?piece.x():-piece.x()-width*8),word(bytes,p*8+6));assertEquals(0x100+((flip&2)==0?piece.y():-piece.y()-height*8),word(bytes,p*8));
                    assertEquals(((0x2428+priority+piece.rawTileWord())&0xFFFF)^(flip<<11),word(bytes,p*8+4));
                }
                var picture=CdObjectPresentation.convert(sat,source);int tileAt=0;
                for(int p=pieces.size()-1;p>=0;p--) {
                    var piece=pieces.get(p);int width=(piece.sizeByte()>>>2)+1,height=(piece.sizeByte()&3)+1,tileWord=word(bytes,p*8+4);boolean h=(tileWord&0x800)!=0,v=(tileWord&0x1000)!=0;
                    for(int row=0;row<height;row++)for(int col=0;col<width;col++) {
                        int id=(tileWord&0x7FF)+col*height+row;union.add(id);var tile=picture.tiles().get(tileAt++);ids.add(tile.patternId());
                        assertEquals(aliasId(id-0x428),tile.patternId());assertEquals(1,tile.palette());assertEquals(priority!=0,tile.priority());assertEquals(h,tile.hFlip());assertEquals(v,tile.vFlip());
                        assertEquals(word(bytes,p*8+6)-128+(h?width-1-col:col)*8,tile.x());assertEquals(word(bytes,p*8)-128+(v?height-1-row:row)*8,tile.y());
                        assertEquals(source.resolve(id).version(),picture.patternVersions().get(tile.patternId()));
                    }
                }
                assertEquals(tileAt,picture.tiles().size());if(root==2&&frame>=2){assertEquals(4,picture.tiles().size());assertEquals(2,picture.patternVersions().size());}
            }
        }
        var expected=new TreeSet<Integer>();for(int id=0x428;id<=0x462;id++)expected.add(id);assertEquals(10,views);assertEquals(expected,union);assertEquals(59,ids.size());assertEquals(58,ids.stream().filter(id->id!=TAMABBOH_BASE).count());
    }

    @Test void onlyColumnTwoOfSharedFrameTwoRealThreeByOnePieceSelects440() throws Exception {
        var f=fixture();var mappings=CdObjectMappings.fromOpeningAssets(f.assets(),f.objects());var cold=seeded(f);var unknown=bank(f,cold,BASE,TAMABBOH_BASE);
        var state=CdR11aCameraArtResidency.writeTile(cold,0x8800,CdAssetBytes.copyOf(testOnlyTile(0x71)));var source=bank(f,state,BASE,TAMABBOH_BASE);int[][] relative={{7,-16},{-15,-16},{7,8},{-15,8}};
        for(int root:new int[]{ROOT[0],ROOT[1]})for(int flip=0;flip<4;flip++) {
            var pieces=mappings.resolve(root,false,2).pieces();assertEquals(new RawPiece(-16,8,0x16,-9),pieces.getFirst());var selected=sat(mappings,root,2,flip,0x2428);
            assertEquals(0x243E^(flip<<11),word(selected.bytes(),4));var fault=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdObjectPresentation.convert(selected,unknown));assertEquals(0x8800,fault.destinationWord());
            var picture=CdObjectPresentation.convert(selected,source);var hidden=picture.tiles().stream().filter(tile->tile.patternId()==TAMABBOH_BASE).toList();assertEquals(1,hidden.size());
            assertEquals(0x100+relative[flip][0],hidden.getFirst().x());assertEquals(0x80+relative[flip][1],hidden.getFirst().y());assertEquals(1,hidden.getFirst().palette());assertFalse(hidden.getFirst().priority());
        }
    }

    private static int aliasId(int offset) {
        if(offset<0||offset>=59)throw new AssertionError("Outside actual Tamabboh59");
        return offset<24?BASE[3]+4+offset:offset==24?TAMABBOH_BASE:BASE[4]+offset-25;
    }
    private static CdAssetBytes testOnlyBank(int tiles,int salt) {
        byte[] bytes=new byte[tiles*32];for(int tile=0;tile<tiles;tile++)System.arraycopy(testOnlyTile(salt+tile*7),0,bytes,tile*32,32);return CdAssetBytes.copyOf(bytes);
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
        var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],b[17],b[18]);
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f,CdR11aCameraArtResidency.State state,int[] b,final int tamabbohLogicalBase) throws Exception {
        var o=f.objects();return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(),f.powerup(),o,o.frontierArtBank(),o.contactArtBank(),o.floatBlockArtBank(),o.springArtBank(),o.tunnelDoorArtBank(),o.monitorTimeArtBank(),state,b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],b[17],b[18],tamabbohLogicalBase);
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
        try(var queue=new com.openggf.game.soniccd.objects.CdObjectDrawQueue(slots,mappings,new byte[SAT_BYTE_SIZE])) {var camera=new CameraWords(0,0);queue.beginPass();assertEquals(EnqueueResult.QUEUED,queue.drawObject(new DrawCallCut(new CdSlotRef(40),4|flip,0x100,0x80,15,16,4),camera));return queue.finishAfterCamera(new DrawCameras(camera,camera,camera));}
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

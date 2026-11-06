package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdDiscHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.*;
import static com.openggf.game.soniccd.level.CdStageTerrain.Plane.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Required original USA CUE ingress; all decoder witnesses come from independent source/media research.
 * No World, warp return, current art, floor/child, renderer or parity claim. Java author checks are UNRUN. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bRetailData {
    private static final String MMD = "14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64";
    private static final String MANIFEST = """
LEVEL_CHUNKS 10000 54272 4d31c5228e3338a739382360f1af25421c5e3ddfea9915cf4e569dd2822f3bc3
LEVEL_COLLISION 3b274 900 f665bf1e7531d7451d982c1e37b34e4899a562a6ff86336ffe00fa45242342e9
LAYOUT_FG 3b640 170 0a11c9da21942514208086b6516e6a6ed3177cbca72806d642c62c545faf222b
LAYOUT_BG 3b6ea 22 117166a3f48d7116f098af950ff923311c4062bc004c6ae33165f2edd75e6963
LEVEL_BLOCKS 3ba2c 4468 343daafa79c6f4964aeeedab74c3b1a8d4bb3bcb7c3aadd37ee348fb99132a7b
ART_LEVEL_TILES 3cba0 10802 c47ef93e567aa11c9726a7d2f4db49b9093700989fe685de301e889af137c8af
COL_ANGLE_MAP 39174 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 39274 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 3a274 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
LEVEL_DATA_INDEX f02a 16 84c2da1839eb82e274a9ed5c0f4870f599209654086c006a2a3ef9479a61726c
LEVEL_LAYOUTS 3b5f8 72 0c5ca7cb062310b35fc7f9d039c397ad3f4f0ad1bf1eff5724e2237906918d77
LEVEL_COL_INDEX 1a56 32 d787e684f62191c78644c363e4b512117c29e1b1ab9153ea925b6708fc2d79a7
CAM_BOUNDS 2910 12 fadb413a244c2a9cc5336b23ff20a78505d3353f4cd32a51c9b2ca63b16afa97
LEVEL_START_LOC 29d4 4 9d2ad78f5ddd7b07d1a2145471d7331829111f8e9a8cbda83855db181580f6d0
PALETTE_TABLE 5c0 48 61fb9e45ba5cfdd2247fd899ecbb489ed1693c6dafbb189e7f359d61b7c3d1e3
PAL_SONIC 6f0 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 710 96 6ba4727e35858ac2701d5942ac4e733d169ac607a46fe778fbe0dde3fb42d993
PLC_LISTS f03a 616 b6cca97acae551eb32344f305de50e126cd21d7712e3f41149a992d184198a5f
SECTION_DATA cbc0 30 fae8d6839b690d9e9cf3dc91954fa36ca21d3d259d85b633fd9a8ea076202cce
OBJECT_LAYOUTS 76f8 3930 1e32466be6a13b758700341964febb20d5deee1bb50a915e836a56aa0f3558c2
SPEC_CHUNKS 29d8 4 674d922d5583edfb5fba68eb6619dd121fe0387c3bbf2e0504d95420827b2f2f
BG_CAMERA_SECT_IDS 33aa 34 0c92ec374afc09f10b4348843a553bf8bf67c18641b7b88d3dd107d3f23d22f0
BG_CAMERA_SECTS 33cc 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
PALETTE_CYCLE_DATA 2c0 84 42c4b898e82d548a0ed3806eca7d398e3c7abaa0cd967c529c9ad1a8ca2891bc
            """;

    @Test void genuineOriginalUsaIngressBindsOnlyTheR12bOriginalFileAndExistingTerrainOwner() throws Exception {
        var f = retail(); var a = f.assets();
        assertEquals(CdReleaseVariant.USA, a.identity().variant()); assertEquals(CdStagePayload.R12B.stage(), a.identity().stage());
        assertEquals(CdPeriod.PAST, a.identity().stage().period()); assertEquals(CdNativeFile.R12B, a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain()); assertEquals(MMD, a.identity().mmdSha256());
        assertEquals(f.identity().normalizedSha256(), a.identity().observedWholeDiscSha256());
        assertEquals(MMD, hash(f.original().readFile(0, 262144))); assertTrue(CdR12bProfile.originalShape(f.original()));
        assertNotEquals(MMD, hash(f.act2Present().readFile(0, 262144))); assertNotEquals(MMD, hash(f.act1Past().readFile(0, 262144)));
        var t = a.terrain(); assertSame(CdStageTerrain.Shape.PPZ2_PAST, t.shape());
        assertEquals(708, t.blockCount()); assertEquals(797, t.patternCount()); assertEquals(106, t.chunkCount());
        assertEquals(900, t.collision().ids().length()); assertEquals(256, t.collision().angles().length());
        assertEquals(4096, t.collision().heights().length()); assertEquals(4096, t.collision().widths().length());
    }

    @Test void allTwentyFourViewsPinLiteralOrdinalsExtentsHashesAndOriginalDomain() throws Exception {
        var a = retail().assets(); var lines = MANIFEST.lines().filter(line -> !line.isBlank()).toList();
        assertEquals(24, lines.size()); assertEquals(24, CdR12bProfile.Asset.values().length);
        for (int i = 0; i < lines.size(); i++) {
            var columns = lines.get(i).split(" "); var asset = CdR12bProfile.Asset.values()[i]; var range = a.profile().range(asset);
            assertEquals(columns[0], asset.name()); assertEquals(Integer.parseInt(columns[1], 16), range.fileOffset());
            assertEquals(Integer.parseInt(columns[2]), range.length()); assertEquals(CdNativeFile.R12B, range.file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, range.domain()); assertEquals(0x200000 + range.fileOffset(), range.nativeAddress());
            assertEquals(range.fileOffset() + range.length(), range.endExclusive()); assertEquals(columns[3], hash(a.view(asset).copy()), asset.name());
        }
        assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void independentlyDecodedTerrainHashesAndCanonicalJavaReaderCursorsAreExact() throws Exception {
        var f = retail(); var a = f.assets();
        // Independent J16 r11a_asset_check prefix-tree/word-XOR reference, original full USA member.
        // Prefix-only cursor4467 is separate from its Java-lookahead control4468; no extra byte is supplied.
        assertEquals(5664, a.blocksDecode().bytes().length()); assertEquals(4468, a.blocksDecode().consumedInputBytes());
        assertEquals("cd6c01762faf27ecb8092b0c085e4c2aa2b92b7ead4c490836e14bce213cd23f", hash(a.blocksDecode().bytes().copy()));
        assertEquals(25504, a.levelTilesDecode().bytes().length()); assertEquals(10802, a.levelTilesDecode().consumedInputBytes());
        assertEquals("5f09700683b979ae263721baffcc8b47b1dbf03d2ef0715bb89bd8f26f355b7d", hash(a.levelTilesDecode().bytes().copy()));
        assertEquals(0x80B1, a.view(LEVEL_BLOCKS).unsignedWord(0)); assertEquals(0x831D, a.view(ART_LEVEL_TILES).unsignedWord(0));
        assertEquals(a.profile().range(LEVEL_BLOCKS), a.profile().readerRange(LEVEL_BLOCKS));
        assertEquals(a.profile().range(ART_LEVEL_TILES), a.profile().readerRange(ART_LEVEL_TILES));
        assertEquals(0x83, f.original().readFile(0x3CBA0, 1)[0] & 255, "Real following tile header stays outside the block view");
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(a.view(LEVEL_BLOCKS).slice(0,4467).copy(),5664,8192));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(a.view(ART_LEVEL_TILES).slice(0,10801).copy(),25504,25504));
        int maximum = 0;
        for (int at = 0; at < a.terrain().blocks().length(); at += 2) maximum = Math.max(maximum, a.terrain().blocks().unsignedWord(at) & 0x7FF);
        assertEquals(796, maximum);
    }

    @Test void originalLayoutsAndAllClearedNativeCellsRetainTheR12bDimensions() throws Exception {
        var layout = retail().assets().terrain().layout();
        assertEquals(42, layout.columns(FOREGROUND)); assertEquals(4, layout.rows(FOREGROUND));
        assertEquals(10, layout.columns(BACKGROUND)); assertEquals(2, layout.rows(BACKGROUND));
        assertEquals("b5c7b0e1c55e5d42a7ecc18709c17301dd75c40f0ea8fd0c6d899b3937cc60aa", hash(layout.ram().copy()));
        assertEquals(0, layout.cell(FOREGROUND,0,0).rawByte()); assertEquals(1, layout.cell(FOREGROUND,0,1).rawByte());
        assertEquals(58, layout.cell(FOREGROUND,41,3).rawByte()); assertEquals(101, layout.cell(BACKGROUND,0,0).rawByte());
        assertEquals(106, layout.cell(BACKGROUND,9,0).rawByte()); assertEquals(102, layout.cell(BACKGROUND,9,1).rawByte());
        for (int y=0;y<16;y++) for (int x=0;x<64;x++) {
            if (x>=42 || y>=4) assertEquals(0,layout.nativeCell(FOREGROUND,x,y).rawByte());
            if (x>=10 || y>=2) assertEquals(0,layout.nativeCell(BACKGROUND,x,y).rawByte());
        }
    }

    @Test void originalIndexBoundsColdCentreAndPaletteMetadataUseTheirOwnSources() throws Exception {
        var a = retail().assets(); var p = a.loadPlan();
        assertEquals(new CdR11bLoadPlan.InitialCentre(0x50,0x185),p.initialCentre());
        assertEquals(new CdR11bLoadPlan.Bounds(4,0,0x2897,0,0x310,0x60),p.bounds());
        assertEquals(3,p.levelIndex().levelArt().plcId()); assertEquals(0x23CBA0,p.levelIndex().levelArt().nativeAddress());
        assertEquals(2,p.levelIndex().blocks().plcId()); assertEquals(0x23BA2C,p.levelIndex().blocks().nativeAddress());
        assertEquals(0x210000,p.levelIndex().chunksNativeAddress()); assertEquals(0x0081,p.levelIndex().uninterpretedTrailing().unsignedWord(0));
        assertEquals(0x0505,p.levelIndex().paletteIds().unsignedWord(0));
        assertEquals("abcb4db263f7966ba4cf1e38e8f0cb147020846b0f82d4922a648bcf838569e2",hash(p.initialPalette().copy()));
        assertEquals(2,p.paletteBindings().size()); var sonic=p.paletteBindings().getFirst(); var level=p.paletteBindings().getLast();
        assertEquals(3,sonic.nativeId()); assertEquals(PAL_SONIC,sonic.source()); assertEquals(16,sonic.colorCount());
        assertEquals(5,level.nativeId()); assertEquals(PAL_LEVEL,level.source()); assertEquals(48,level.colorCount());
        assertEquals(0x200710,a.profile().range(level.source()).nativeAddress());
        assertEquals("91b67f7f",hex(a.view(SPEC_CHUNKS).copy()));
        assertThrows(IndexOutOfBoundsException.class,()->p.colorWord(64));
    }

    @Test void allNineteenPlcSlotsRetainTheirAliasesRowsAndThirtySevenPointerInventoryOnly() throws Exception {
        var p=retail().assets().loadPlan(); assertEquals(19,p.plcs().size());
        int[] counts={1,14,12,1,1,1,11,11,12,10,2,3,3,5,1,12,1,12,3};
        int[] bodies={0xF060,0xF068,0xF0BE,0xF060,0xF276,0xF27E,0xF108,0xF14C,0xF190,0xF1DA,0xF218,0xF226,0xF23A,0xF24E,0xF26E,0xF0BE,0xF286,0xF0BE,0xF28E};
        for(int i=0;i<19;i++) {var list=p.plcs().get(i);assertEquals(i,list.nativeId());assertEquals(counts[i],list.entries().size());assertEquals(bodies[i],list.bodyRange().fileOffset());assertEquals(2+6*counts[i],list.bodyRange().length());}
        assertEquals(p.plcs().get(0).bodyRange(),p.plcs().get(3).bodyRange());
        assertEquals(p.plcs().get(2).bodyRange(),p.plcs().get(15).bodyRange());
        assertEquals(p.plcs().get(2).bodyRange(),p.plcs().get(17).bodyRange());
        assertEquals(List.of(0x23CBA0L,0x237272L,0x2376BAL,0x23718CL,0x23413CL,0x236D90L,0x236EBAL,0x238D7CL,
                0x2354D4L,0x236856L,0x2356F6L,0x235CFAL,0x232180L,0x236B0AL,0x2361E2L,0x238BD4L,0x23707EL,0x237328L,
                0x232C96L,0x237A02L,0x23850CL,0x237F34L,0x238982L,0x236A7CL,0x23B8FAL,0x236982L,0x2351C8L,0x23F784L,
                0x237BE6L,0x23F5D2L,0x238C2AL,0x2381FEL,0x23F936L,0x233432L,0x232E56L,0x23288CL,0x20D7F2L),p.sourcePointers());
        assertEquals(37,p.sourcePointers().size()); assertEquals(0x6700,p.plcs().get(1).entries().getFirst().vramAddress());
        assertThrows(UnsupportedOperationException.class,()->p.plcs().clear());
        assertThrows(UnsupportedOperationException.class,()->p.sourcePointers().clear());
    }

    @Test void fiveSectionRowsAndFiniteBackgroundAndPaletteViewsAreMetadataOnly() throws Exception {
        var a=retail().assets(); var rows=a.loadPlan().sections(); assertEquals(5,rows.size());
        int[] thresholds={0xB00,0x1500,0x1800,0x1C00,0xFFFF};int[] initial={2,6,7,8,9};
        for(int i=0;i<5;i++){assertEquals(thresholds[i],rows.get(i).rightThresholdWord());assertEquals(initial[i],rows.get(i).initialPlcId());assertEquals(10+i,rows.get(i).updatePlcId());}
        var ids=a.view(BG_CAMERA_SECT_IDS);assertEquals(34,ids.length());
        for(int i=0;i<34;i++)assertEquals(i<9 || i>=17?0:i<13?6:4,ids.unsignedByte(i));
        assertEquals("00fff70800fff70800fff71000fff718",hex(a.view(BG_CAMERA_SECTS).copy()));
        assertEquals(84,a.view(PALETTE_CYCLE_DATA).length());
        assertThrows(UnsupportedOperationException.class,()->rows.clear());
        assertThrows(IllegalArgumentException.class,()->a.profile().readerRange(PLC_LISTS));
    }

    @Test void commonPlacementOrderAndAllThreePeriodCountsComeFromThisOriginalR12bImage() throws Exception {
        var a=retail().assets();var placements=a.placements();assertSame(placements,a.placements());
        assertEquals(488,placements.records().size()); assertEquals(281,placements.forPeriod(CdPeriod.PAST).size());
        assertEquals(279,placements.forPeriod(CdPeriod.PRESENT).size());assertEquals(282,placements.forPeriod(CdPeriod.BAD_FUTURE).size());
        var first=placements.records().getFirst();assertEquals(0,first.index());assertEquals(9,first.centreX());assertEquals(0x18D,first.packedY());assertEquals(10,first.objectId());assertEquals(6,first.subtype());assertEquals(0xE0,first.periodFlags());
        var raw=a.view(OBJECT_LAYOUTS);
        for(int i=0;i<488;i++){var row=placements.records().get(i);int at=12+i*8;assertEquals(i,row.index());assertEquals(raw.unsignedWord(at),row.centreX());assertEquals(raw.unsignedWord(at+2),row.packedY());assertEquals(raw.unsignedByte(at+4),row.encodedId());assertEquals(raw.unsignedByte(at+7),row.subtype2());}
        assertTrue(placements.backwardSentinel().isPresent());assertTrue(placements.nullList().isPresent());
        assertThrows(UnsupportedOperationException.class,()->placements.records().clear());
    }

    @Test void actualNativeOperandsSelectR12bTablesAndDoNotBorrowTheR12aAddresses() throws Exception {
        var m=retail().original();
        assertEquals("0323cba00223ba2c0021000000810505",hex(m.readFile(0xF02A,16)));
        assertEquals("45f90020f02a",hex(m.readFile(0x167A,6)));assertEquals("45f90020f02a",hex(m.readFile(0x3416,6)));
        assertEquals("43f90023b5f8",hex(m.readFile(0x3492,6)));assertEquals("41f900202910",hex(m.readFile(0x28CE,6)));
        assertEquals("43f9002029d4",hex(m.readFile(0x295C,6)));assertEquals("41f9002076f8",hex(m.readFile(0x7460,6)));
        assertEquals("43fa0050",hex(m.readFile(0xCB6E,4)));assertEquals("43fa002c",hex(m.readFile(0xCB92,4)));
        assertEquals("21fb0006f796",hex(m.readFile(0x1A4E,6)));assertEquals("0023b274".repeat(8),hex(m.readFile(0x1A56,32)));
        for(int i=0;i<6;i++){int at=0x230+14*i;assertEquals(0x43F9,(m.readFile(at,2)[0]&255)*256+(m.readFile(at,2)[1]&255));assertEquals(0x2002C0+14*i,ByteBufferRead.unsignedLong(m.readFile(at+2,4)));}
    }

    @Test void constructedForeignAndMutatedIngressControlsRejectWithoutFallbackReadsOrClosingCaller() throws Exception {
        var f=retail();var p=f.assets().profile();
        byte[] changed=f.original().readFile(0,262144);changed[0x3BA2D]^=1;
        byte[] header=f.original().readFile(0,262144);header[0]^=1;
        for(var image:new CdMmdImage[]{f.act2Present(),f.act1Past(),CdMmdImage.parse(changed),CdMmdImage.parse(header),CdMmdImage.parse(f.original().readFile(0,262143))}){
            var source=mock(CdProgramAssets.class);when(source.header()).thenReturn(f.header());when(source.identity()).thenReturn(f.identity());
            when(source.catalogue()).thenReturn(Optional.of(f.catalogue()));when(source.mmd(CdNativeFile.R12B)).thenReturn(image);
            assertThrows(IOException.class,()->CdR12bTerrainAssets.fromPrograms(source));verify(source,never()).close();verify(source,never()).mmd(CdNativeFile.R11B);verify(source,never()).mmd(CdNativeFile.R12A);
        }
        var foreign=mock(CdProgramAssets.class);var foreignHeader=mock(CdDiscHeader.class);when(foreignHeader.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.JAPAN));when(foreign.header()).thenReturn(foreignHeader);
        assertThrows(IOException.class,()->CdR12bTerrainAssets.fromPrograms(foreign));verify(foreign,never()).mmd(any());verify(foreign,never()).close();
        var unknown=mock(CdProgramAssets.class);var id=f.identity();when(unknown.header()).thenReturn(f.header());when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(),"0".repeat(64),id.cue(),id.binaryFiles(),id.originalContainer(),id.tracks()));
        assertThrows(IOException.class,()->CdR12bTerrainAssets.fromPrograms(unknown));verify(unknown,never()).mmd(any());
        var catalogueMismatch=mock(CdProgramAssets.class);when(catalogueMismatch.header()).thenReturn(f.header());when(catalogueMismatch.identity()).thenReturn(f.identity());
        when(catalogueMismatch.catalogue()).thenReturn(Optional.of(CdProgramCatalogue.forRelease(CdReleaseVariant.EUROPE)));
        assertThrows(IOException.class,()->CdR12bTerrainAssets.fromPrograms(catalogueMismatch));verify(catalogueMismatch,never()).mmd(any());
        assertThrows(IllegalArgumentException.class,()->p.verifyProgram(CdNativeFile.R11B,p.domain(),f.original()));
    }

    @Test void componentMetadataRejectsChangedReachedTablesButDoesNotAuthenticateUnrelatedPayload() throws Exception {
        var f=retail();var p=f.assets().profile();
        for(int at:new int[]{0xF02A,0xF02F,0x3B5F9,0x1A75,0x5DB,0x5EF,0xF03B,0xF060,0xF06D,0xCBC1}){
            byte[] bytes=f.original().readFile(0,262144);bytes[at]^=1;
            assertThrows(IllegalArgumentException.class,()->CdR12bLoadPlan.decode(p,CdMmdImage.parse(bytes)));
        }
        byte[] trailing=f.original().readFile(0,262144);trailing[0xF036]^=1;
        assertThrows(UnsupportedOperationException.class,()->CdR12bLoadPlan.decode(p,CdMmdImage.parse(trailing)));
        byte[] bounds=f.original().readFile(0,262144);bounds[0x2912]=(byte)0xFF;
        assertThrows(IllegalArgumentException.class,()->CdR12bLoadPlan.decode(p,CdMmdImage.parse(bounds)));
        byte[] component=f.original().readFile(0,262144);component[0x20000]^=1;var image=CdMmdImage.parse(component);
        assertEquals(f.assets().loadPlan().bounds(),CdR12bLoadPlan.decode(p,image).bounds());
        assertThrows(IOException.class,()->p.verifyProgram(p.file(),p.domain(),image));
    }

    @Test void detachedProductsSurviveSourceCloseAndSharedGeometryKeepsOriginalLastBlockWords() throws Exception {
        var f=retail();var a=f.assets();var t=a.terrain();var b=a.loadPlan().bounds();
        var level=new SonicCdLevelGeometry(t,a.loadPlan().initialPalette(),0,b.left(),b.right(),b.top(),b.bottom());
        int[][] words={{0xC1C4,0xC84F},{0xC1C5,0xC04C}};
        for(int y=0;y<2;y++)for(int x=0;x<2;x++){assertEquals(words[y][x],t.blockWord(707,x,y).rawWord());assertEquals(words[y][x],level.getChunk(707).getPatternDesc(x,y).get());}
        byte[] exported=a.view(LEVEL_CHUNKS).copy();Arrays.fill(exported,(byte)0xFF);assertEquals(MANIFEST.lines().findFirst().orElseThrow().split(" ")[3],hash(a.view(LEVEL_CHUNKS).copy()));
        byte[] palette=a.loadPlan().initialPalette().copy();Arrays.fill(palette,(byte)0xFF);assertEquals(0,a.loadPlan().colorWord(0));
        assertSame(t,a.terrain());assertSame(a.loadPlan(),a.loadPlan());assertTrue(level.getObjects().isEmpty());assertTrue(level.getRings().isEmpty());
        assertEquals(MMD,hash(f.original().readFile(0,262144)));
    }

    private Fixture retained;
    private record Fixture(CdR12bTerrainAssets assets,CdMmdImage original,CdMmdImage act2Present,CdMmdImage act1Past,
                           CdObservedMediaIdentity identity,CdDiscHeader header,CdProgramCatalogue catalogue) { }
    private Fixture retail() throws Exception {
        if(retained!=null)return retained;
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure original CD media root; no silent skip");assertFalse(root.isBlank());
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            retained=new Fixture(CdR12bTerrainAssets.fromPrograms(source),source.mmd(CdNativeFile.R12B),source.mmd(CdNativeFile.R12A),source.mmd(CdNativeFile.R11B),source.identity(),source.header(),source.catalogue().orElseThrow());
        }
        return retained;
    }
    private static String hex(byte[] value){return HexFormat.of().formatHex(value);}
    private static String hash(byte[] value){return CdR11aProfile.sha256(value);}
    private static final class ByteBufferRead {
        static long unsignedLong(byte[] value){return Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(value).getInt());}
    }
}

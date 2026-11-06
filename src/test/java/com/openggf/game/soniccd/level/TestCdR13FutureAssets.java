package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.openggf.game.soniccd.level.CdR13FutureAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Actual original-USA data prerequisites only. Tests first; all Java UNRUN. No FULL/Frame/World/current art.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Act3 Data/Load/Scroll and Sonic tables.
 * Fingerprints below came from independent original MMD/directive and bounded Python Nemesis analysis.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR13FutureAssets {
    private static final String MANIFEST = """
LEVEL_DATA_INDEX e310 16 ead4 16 e4dfd4386974cb11060bd1adb6c5b5c643cfe228624fa9215e3c21ce032be5de 21b8e8d806f845646d1e8278c48cec94b59dbdee40dad4f5f5f08b2773b00ac0
PLC_LISTS e320 158 eae4 158 862225f6f74326d414b44a09c86d8b8991c2c38260fe7158b05bc1983a43f9cf 98ceaf6805cc37dde1e8545295339d1753ba0a4f7783910a80bebd01562634cf
LEVEL_CHUNKS 10000 54272 10000 54272 231f8ace8a041ba816866fec7783ff8d78ceac12f3082c81da7bc8b7017bd25d f877093cefa5f10b897c79a72834a06ce0548bd50cfefcab6cd96995e1840915
LEVEL_BLOCKS 38490 4790 38490 4772 db69944068f73987069329c9b2c71058010118d08a22cc2cef60a2ab861a92df 0e61da173fb7bc9e23ad6bffac94433d253296351b9f6e459c017c5462113ba3
ART_LEVEL_TILES 39746 11158 39734 11686 6183109387a85f9885fde847a95e543af57f3726913d6e4a7e2651a5ab7103cb 08c497311f7960a47b7d18f98123f5e74f353be899847bf3d53af9665a52a24e
LEVEL_LAYOUTS 381fa 72 381fa 72 bac4474f832e201cbff8b38cbe3528fe8dbfa0463ccba002fd8b2ba46bb16754 bac4474f832e201cbff8b38cbe3528fe8dbfa0463ccba002fd8b2ba46bb16754
LAYOUT_FG 38242 62 38242 62 4018e60772e87d2c5fcea1ef0f3fa71075525e6bd913ee83b05c03f3d8bc4f8f ed4a0c628ed154b420d1c59586781d3a2a758fd60495986205443e3148a0c9f7
LAYOUT_BG 38280 22 38280 22 117166a3f48d7116f098af950ff923311c4062bc004c6ae33165f2edd75e6963 117166a3f48d7116f098af950ff923311c4062bc004c6ae33165f2edd75e6963
LEVEL_COL_INDEX 1942 32 19ae 32 3b260b12fa8252f59466f90526e0408197203ecde0a88884c695f5ae2eaba93d 3b260b12fa8252f59466f90526e0408197203ecde0a88884c695f5ae2eaba93d
LEVEL_COLLISION 37eda 800 37eda 800 d54105f8a12c728701d6d4821090648fdde4717b73668e410def7310df79e77b 29fe8ba4301103e82dc602a3d909dd3aa0376c18d44635d354a0e16186367816
COL_ANGLE_MAP 35dda 256 35dda 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 35eda 4096 35eda 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 36eda 4096 36eda 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
CAM_BOUNDS 27fc 12 2868 12 f0255cf9a7e4e123b5673279653ac51889e2a46c30a7ee4b7090ebcb3f48c9de f0255cf9a7e4e123b5673279653ac51889e2a46c30a7ee4b7090ebcb3f48c9de
LEVEL_START_LOC 28c0 4 292c 4 0da8566fc246945089e47465274e6af28e8db01e79914be94e9872388c210f2a 0da8566fc246945089e47465274e6af28e8db01e79914be94e9872388c210f2a
SPEC_CHUNKS 28c4 4 2930 4 8aab2355270e419ce7706427b4ab6e260d2c7ab25d66404a2dcc0ca06558204d 8aab2355270e419ce7706427b4ab6e260d2c7ab25d66404a2dcc0ca06558204d
OBJECT_INDEX 3b82 256 3b9a 256 430ff3d7defba4d3c7da091a430c95024d9a58c318b6174d704c9e7386ff8f86 a1e9e48e9cf08f0b2ba01e7d4d781b9a23082702f84305e94f318f990b24fcb2
OBJECT_LAYOUTS 7418 514 7430 514 09c6d1e3e0a86cd64fbc950116868948a81c2a40ea4874dfbf709644efc3df1f 09c6d1e3e0a86cd64fbc950116868948a81c2a40ea4874dfbf709644efc3df1f
MAP_SPR_SONIC 2df20 2443 2df20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
DPLC_SONIC 2e8ac 1338 2e8ac 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
ART_SONIC 20000 57120 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
ANI_SONIC 56e8 578 5700 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
MINI_ANIMATIONS 5698 80 56b0 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
RAMP_CHUNKS 445e 6 4476 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
SINE_TABLE 71a 640 786 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
ARC_TAN_TABLE a00 258 a6c 258 7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f 7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f
PALETTE_TABLE 4ec 48 558 48 f1876d5b0543264ada2250129ec31058d160444292ddf58ccc6d3abbee7e2315 85cafdce8b5782bf0af5b33ff400b6adb56f78c39b544951e8a167f61e8898e9
PAL_SONIC 61c 32 688 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 63c 96 6a8 96 29d86b702295ed28d6d6c4fe4e829333036d0bb2b3c69f4ddb71e38b71a8e8cd 7a34893db95e1d9c0e4ab5acf2a492857d7b660f3e34263b60bd889fb8d03cb2
""";

    @Test void actualPairIsAuthenticatedAgainstItsOwnOriginalProgrammeAndBorrowsTheDisc() throws Exception {
        var f=retail();
        for(var a:f.all()) {
            var id=a.identity();assertEquals(CdReleaseVariant.USA,id.variant());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,id.domain());
            assertEquals(id.file()==CdNativeFile.R13C?CdStagePayload.R13C.stage():CdStagePayload.R13D.stage(),id.stage());
            assertEquals(hash(f.image(id.file()).readFile(0,262144)),id.mmdSha256());assertEquals(f.disc().normalizedSha256(),id.observedWholeDiscSha256());
            assertEquals(167,a.sonic().mappingFrames().size());assertSame(a.sonic(),a.player().sonic());
        }
        try(var p=CdProgramAssets.open(cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage());assertDoesNotThrow(()->p.mmd(CdNativeFile.R13D));
        }
    }
    @Test void all29NamedViewsHaveExactSourceOffsetsDomainsBytesAndReaderBounds() throws Exception {
        var f=retail();assertEquals(29,CdR13FutureAssets.Asset.values().length);var rows=MANIFEST.strip().lines().toList();assertEquals(29,rows.size());
        for(var a:f.all())for(int i=0;i<29;i++) {
            var asset=CdR13FutureAssets.Asset.values()[i];var c=rows.get(i).strip().split("\\s+");boolean good=a.identity().file()==CdNativeFile.R13C;int off=Integer.parseInt(c[good?1:3],16),len=Integer.parseInt(c[good?2:4]);
            assertEquals(c[0],asset.name());assertEquals(off,asset.fileOffset(a.identity().file()));assertEquals(len,asset.length(a.identity().file()));
            var r=a.range(asset);assertEquals(a.identity().file(),r.file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,r.domain());assertEquals(off,r.fileOffset());assertEquals(len,r.length());assertEquals(0x200000+off,r.nativeAddress());assertEquals(off+len,r.endExclusive());
            assertEquals(c[good?5:6],hash(a.view(asset).copy()));assertArrayEquals(f.image(a.identity().file()).readFile(off,len),a.view(asset).copy());
            assertArrayEquals(a.view(asset).copy(),r.read(f.image(a.identity().file())).copy());
        }
    }
    @Test void actualConsumersSelectEachProgrammesLocalLiveRoots() throws Exception {
        var f=retail();for(var a:f.all()) {
            boolean good=a.identity().file()==CdNativeFile.R13C;var m=f.image(a.identity().file());int d=good?0:0x18;
            assertEquals("217c0022df200004",hex(m.readFile(0x3DB4+d,8)));assertEquals("45f90022e8ac",hex(m.readFile(0x5940+d,6)));assertEquals("43f900220000",hex(m.readFile(0x5970+d,6)));
            assertEquals(good?"43f9002056e8":"43f900205700",hex(m.readFile(0x53F8+d,6)));assertEquals("103b0004",hex(m.readFile(0x5692+d,4)));
            assertEquals(good?"45f90020445e":"45f900204476",hex(m.readFile(0x441E+d,6)));
            assertEquals(good?"03239746022384900021000000810404":"03239734022384900021000000810404",hex(a.view(LEVEL_DATA_INDEX).copy()));
            var layout=a.view(LEVEL_LAYOUTS);assertEquals(a.range(LAYOUT_FG).fileOffset(),a.range(LEVEL_LAYOUTS).fileOffset()+(short)layout.unsignedWord(0));assertEquals(a.range(LAYOUT_BG).fileOffset(),a.range(LEVEL_LAYOUTS).fileOffset()+(short)layout.unsignedWord(2));
            for(int i=0;i<8;i++)assertEquals(a.range(LEVEL_COLLISION).nativeAddress(),a.view(LEVEL_COL_INDEX).unsignedLong(i*4));
        }
    }
    @Test void completeNemesisOutputsAndRealReaderCursorsFitTheirDistinctShapes() throws Exception {
        for(var a:retail().all()) {
            boolean g=a.identity().file()==CdNativeFile.R13C;var t=a.terrain();
            assertEquals(g?760:756,t.blockCount());assertEquals(g?788:814,t.patternCount());assertEquals(106,t.chunkCount());
            assertEquals(g?4790:4772,a.blocksDecode().consumedInputBytes());assertEquals(g?11158:11685,a.levelTilesDecode().consumedInputBytes());
            assertEquals(g?"8e63182b1edbba17e823136e62cdcb8cc82d3b789eefb6540c4bbfa42c407f82":"27aa5cbc7e7ad7b3dede400fd19e44dcc8186352b9e9a46ba3e223aa08e37b46",hash(t.blocks().copy()));
            assertEquals(g?"963d8b39d5941ead09033c6b5cb773a56bb848f02f11540b228a221a8db23438":"11cd4655075a61e0a55b553f15e9f9717ee6f08bb6644836b5a5cef1206a9caf",hash(t.patterns().copy()));
            assertArrayEquals(a.blocksDecode().bytes().copy(),t.blocks().copy());assertArrayEquals(a.levelTilesDecode().bytes().copy(),t.patterns().copy());
            int maximum=0;for(int b=0;b<t.blockCount();b++)for(int y=0;y<2;y++)for(int x=0;x<2;x++)maximum=Math.max(maximum,t.blockWord(b,x,y).patternIndex());assertEquals(g?787:813,maximum);
            assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(t.blockCount(),0,0));assertThrows(IndexOutOfBoundsException.class,()->t.pixel(t.patternCount(),0,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().idForBlock(800));
        }
    }
    @Test void nativeLayoutCopyAndAllCollisionTablesStayFinite() throws Exception {
        for(var a:retail().all()) {
            var t=a.terrain();var l=t.layout();assertEquals(15,l.columns(CdStageTerrain.Plane.FOREGROUND));assertEquals(4,l.rows(CdStageTerrain.Plane.FOREGROUND));assertEquals(10,l.columns(CdStageTerrain.Plane.BACKGROUND));assertEquals(2,l.rows(CdStageTerrain.Plane.BACKGROUND));
            assertEquals(a.identity().file()==CdNativeFile.R13C?"9e472c04e21051ef99b4916ae624718756b84ae19b8c3caaf2874601c9a252ad":"dadbcae1c61abaebf14a4fb5e36c452251988f8e096c9b595781c689688334d3",hash(l.ram().copy()));
            assertEquals(0,l.nativeCell(CdStageTerrain.Plane.FOREGROUND,63,15).rawByte());assertEquals(0,l.nativeCell(CdStageTerrain.Plane.BACKGROUND,63,15).rawByte());
            assertArrayEquals(a.view(LEVEL_COLLISION).copy(),t.collision().ids().copy());assertArrayEquals(a.view(COL_ANGLE_MAP).copy(),t.collision().angles().copy());assertArrayEquals(a.view(COL_HEIGHT_MAP).copy(),t.collision().heights().copy());assertArrayEquals(a.view(COL_WIDTH_MAP).copy(),t.collision().widths().copy());
            for(int id=0;id<256;id++)for(int i=0;i<16;i++){assertEquals(a.view(COL_HEIGHT_MAP).signedByte(id*16+i),t.collision().height(id,i));assertEquals(a.view(COL_WIDTH_MAP).signedByte(id*16+i),t.collision().width(id,i));}
        }
    }
    @Test void sourceBoundsStartSpecialChunksAndPaletteBindingsHaveNoDefaultFields() throws Exception {
        for(var a:retail().all()) {
            assertEquals(new CdR11bLoadPlan.Bounds(4,0,0xD97,0,0x310,0x60),a.bounds());assertEquals(new CdR11bLoadPlan.InitialCentre(0x40,0x2A2),a.initialCentre());
            assertEquals("917f1e1e",hex(a.player().specialChunks().copy()));assertEquals(3,a.levelIndex().levelArt().plcId());assertEquals(2,a.levelIndex().blocks().plcId());assertEquals("0081",hex(a.levelIndex().uninterpretedTrailing().copy()));assertEquals("0404",hex(a.levelIndex().paletteIds().copy()));
            assertEquals(2,a.paletteBindings().size());int[] ids={3,4},dest={0xFB00,0xFB20},counter={7,23};
            for(int i=0;i<2;i++){var p=a.paletteBindings().get(i);assertEquals(ids[i],p.nativeId());assertEquals(dest[i],p.destinationWord());assertEquals(counter[i],p.longwordCounter());assertArrayEquals(a.view(i==0?PAL_SONIC:PAL_LEVEL).copy(),p.bytes().copy());}
            assertEquals(a.identity().file()==CdNativeFile.R13C?"1415cb4071b71678f9f48abe6cd97add4d360cde1ea2bc069128cc6509e5266f":"22e42c73827900070d259e615cfa65e413619ac1b06d31e279d47126af8102b8",hash(a.initialPalette().copy()));
        }
    }
    @Test void plcListsRetainAll17IdsFiveBodiesAndOriginalRowsWithoutSubmittingArt() throws Exception {
        for(var a:retail().all()) {
            var raw=a.view(PLC_LISTS);assertEquals(17,a.plcs().size());var unique=new HashSet<Integer>();var pointers=new HashSet<Long>();
            for(int id=0;id<17;id++){var p=a.plcs().get(id);int at=(short)raw.unsignedWord(id*2),count=raw.unsignedWord(at)+1;assertEquals(id,p.nativeId());assertEquals(a.range(PLC_LISTS).fileOffset()+at,p.bodyRange().fileOffset());assertEquals(count,p.entries().size());assertEquals(2+6*count,p.bodyRange().length());unique.add(at);
                for(int i=0;i<count;i++){var e=p.entries().get(i);assertEquals(raw.unsignedLong(at+2+i*6),e.sourceNativeAddress());assertEquals(raw.unsignedWord(at+6+i*6),e.vramAddress());pointers.add(e.sourceNativeAddress());}}
            assertEquals(5,unique.size());assertEquals(19,pointers.size());assertEquals(a.plcs().get(0).bodyRange(),a.plcs().get(3).bodyRange());assertEquals(a.plcs().get(2).bodyRange(),a.plcs().get(15).bodyRange());
        }
    }
    @Test void authenticatedPlacementOwnerRetainsTheActualReversalAndRawObjectIndex() throws Exception {
        for(var a:retail().all()) {
            a.requireAuthenticatedLayout();assertArrayEquals(a.view(OBJECT_LAYOUTS).copy(),a.nativeObjectLayout().copy());assertEquals(256,a.view(OBJECT_INDEX).length());
            var rows=a.placements().records();assertEquals(61,rows.size());assertEquals(0xC10,rows.get(55).centreX());assertEquals(0xBF0,rows.get(56).centreX());assertEquals(0x19,rows.get(56).objectId());
            assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeLayout(a.view(OBJECT_LAYOUTS).copy()));
        }
    }
    @Test void wrongStageReleaseCatalogueAndUnknownDiscFailBeforeSelectedImageReads() throws Exception {
        var f=retail();var p=source(f,CdNativeFile.R13C,f.c());assertThrows(UnsupportedOperationException.class,()->CdR13FutureAssets.fromPrograms(p,CdStagePayload.R12B.stage()));verify(p,never()).mmd(any());
        assertThrows(NullPointerException.class,()->CdR13FutureAssets.fromPrograms(null,CdStagePayload.R13C.stage()));assertThrows(NullPointerException.class,()->CdR13FutureAssets.fromPrograms(p,null));
        var missing=source(f,CdNativeFile.R13C,f.c());when(missing.catalogue()).thenReturn(Optional.empty());assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(missing,CdStagePayload.R13C.stage()));verify(missing,never()).mmd(any());
        var inconsistent=source(f,CdNativeFile.R13C,f.c());var other=mock(CdProgramCatalogue.class);when(other.release()).thenReturn(CdReleaseVariant.EUROPE);when(inconsistent.catalogue()).thenReturn(Optional.of(other));assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(inconsistent,CdStagePayload.R13C.stage()));verify(inconsistent,never()).mmd(any());
        var foreign=source(f,CdNativeFile.R13C,f.c());var header=mock(CdDiscHeader.class);when(header.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.EUROPE));when(foreign.header()).thenReturn(header);assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(foreign,CdStagePayload.R13C.stage()));verify(foreign,never()).mmd(any());
        var unknown=source(f,CdNativeFile.R13C,f.c());var id=f.disc();when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(),"0".repeat(64),id.cue(),id.binaryFiles(),id.originalContainer(),id.tracks()));assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(unknown,CdStagePayload.R13C.stage()));verify(unknown,never()).mmd(any());
        var bad=source(f,CdNativeFile.R13C,f.c());var cat=mock(CdProgramCatalogue.class);when(cat.release()).thenReturn(CdReleaseVariant.USA);when(cat.stage(CdStagePayload.R13C.stage())).thenReturn(CdStagePayload.R13D);when(bad.catalogue()).thenReturn(Optional.of(cat));assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(bad,CdStagePayload.R13C.stage()));verify(bad,never()).mmd(any());
    }
    @Test void everyChangedViewForeignPairAndShortImageFailsWholeSourceAuthentication() throws Exception {
        var f=retail();for(var asset:CdR13FutureAssets.Asset.values()) {byte[] b=f.c().readFile(0,262144);var r=f.good().range(asset);b[r.endExclusive()-1]^=1;var p=source(f,CdNativeFile.R13C,CdMmdImage.parse(b));assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),asset.name());verify(p,never()).close();}
        for(var wrong:List.of(f.d(),f.legacyImage(),CdMmdImage.parse(f.c().readFile(0,262143))))assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(source(f,CdNativeFile.R13C,wrong),CdStagePayload.R13C.stage()));
        byte[] outside=f.c().readFile(0,262144);outside[0x4000]^=1;assertThrows(java.io.IOException.class,()->CdR13FutureAssets.fromPrograms(source(f,CdNativeFile.R13C,CdMmdImage.parse(outside)),CdStagePayload.R13C.stage()));
    }
    @Test void detachedCopiesCannotMutateAnyRetainedSourceOrPublishedList() throws Exception {
        for(var a:retail().all()){for(var asset:CdR13FutureAssets.Asset.values()){byte[] before=a.view(asset).copy(),copy=a.view(asset).copy();Arrays.fill(copy,(byte)99);assertArrayEquals(before,a.view(asset).copy());assertThrows(ReadOnlyBufferException.class,()->a.view(asset).buffer().put(0,(byte)1));}
            assertThrows(UnsupportedOperationException.class,()->a.plcs().clear());assertThrows(UnsupportedOperationException.class,()->a.plcs().getFirst().entries().clear());assertThrows(UnsupportedOperationException.class,()->a.paletteBindings().clear());assertThrows(UnsupportedOperationException.class,()->a.placements().records().clear());assertThrows(NullPointerException.class,()->a.view(null));
            assertThrows(IndexOutOfBoundsException.class,()->a.view(ART_SONIC).unsignedByte(57120));assertThrows(IllegalStateException.class,a.sonic()::alignmentByte);
            assertThrows(IllegalArgumentException.class,()->retail().c().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY,0x220000,32));
        }
    }
    @Test void oldTerrainShapeOrdinalsAndLegacyPlayerOwnerArePreserved() throws Exception {
        assertEquals(List.of("PPZ1_PRESENT","PPZ1_PAST","PPZ1_GOOD_FUTURE","PPZ1_BAD_FUTURE","PPZ2_PRESENT","PPZ2_PAST"),Arrays.stream(CdStageTerrain.Shape.values()).limit(6).map(Enum::name).toList());
        var p=retail().legacy();assertEquals(198,p.sonic().mappingFrames().size());assertEquals(60,p.player().animations().size());assertEquals(69472,p.sonic().artReadView().length());assertEquals(0,p.sonic().alignmentByte());
    }

    record Fixture(CdR13FutureAssets good,CdR13FutureAssets bad,CdMmdImage c,CdMmdImage d,CdMmdImage legacyImage,CdR12bPlayerAssets legacy,CdDiscHeader header,CdObservedMediaIdentity disc,CdProgramCatalogue catalogue){List<CdR13FutureAssets> all(){return List.of(good,bad);}CdMmdImage image(CdNativeFile f){return f==CdNativeFile.R13C?c:d;}}
    private static Fixture retained;
    static synchronized Fixture retail() throws Exception {
        if(retained!=null)return retained;
        try(var p=CdProgramAssets.open(cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var c=p.mmd(CdNativeFile.R13C);var d=p.mmd(CdNativeFile.R13D);
            retained=new Fixture(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()),c,d,p.mmd(CdNativeFile.R12B),CdR12bPlayerAssets.fromPrograms(p),p.header(),p.identity(),p.catalogue().orElseThrow());
        }return retained;
    }
    static CdProgramAssets source(Fixture f,CdNativeFile file,CdMmdImage image)throws Exception {var p=mock(CdProgramAssets.class);when(p.header()).thenReturn(f.header());when(p.identity()).thenReturn(f.disc());when(p.catalogue()).thenReturn(Optional.of(f.catalogue()));when(p.mmd(file)).thenReturn(image);return p;}
    private static Path cue()throws Exception {String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media is absent");try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());return cues.getFirst();}}
    static String hash(byte[] b){return CdR11aProfile.sha256(b);}static String hex(byte[] b){return HexFormat.of().formatHex(b);}
}

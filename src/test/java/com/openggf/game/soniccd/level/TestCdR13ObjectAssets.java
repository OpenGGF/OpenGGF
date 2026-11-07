package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.RawPiece;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.openggf.game.soniccd.level.CdR13ObjectAssets.View.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual configured USA C/D bytes; detached metadata tests, never a World/FULL or natural route claim.
 * Optional absent/blank media is handled by the existing retail fixture; configured bad media hard fails.
 * N1ecc5f3af: Act3 ObjectIndex/TileIDs, native Init operands and exact includes; author Java UNRUN. */
class TestCdR13ObjectAssets {
    private static final String MANIFEST = """
OBJECT_TILE_IDS ABEA AC02 68 41e75f9fbefde560f302406a0e2f7d3ebbc6bad37b05fdfc7b196ae18dc06927 41e75f9fbefde560f302406a0e2f7d3ebbc6bad37b05fdfc7b196ae18dc06927
RING_DELTAS 81AA 81C2 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
MAP_SPR_RING 860E 8626 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
ANI_RING 8606 861E 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
SPRING_SPEEDS 7DFC 7E14 4 5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621 5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621
MAP_SPRING 80B4 80CC 199 f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd
ANI_SPRING 80A6 80BE 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
MAP_SPR_MONITOR_TIME 8DA0 8DB8 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
ANI_MONITOR 8D18 8D30 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
MAP_SPR_HUD 90B8 90D0 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
ART_HUD_NUMBERS 330BC 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
ART_LIFE_ICON 32F3C 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
HUD_RESET_TILES 93EE 9406 12 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c
HUD_DIVISORS 954E 9566 40 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea
ANIMAL_BASE_TILES AF00 AF18 22 74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec 74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec
ANI_FLYING_ANIMAL AE4A AE62 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
ANI_GROUND_ANIMAL AE8C AEA4 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
MAP_SPR_FLYING_ANIMAL AEC8 AEE0 20 00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39 00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39
MAP_SPR_GROUND_ANIMAL AEDC AEF4 36 029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2 029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2
TITLE_CARD_DATA E13C E154 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
MAP_SPR_TITLE_CARD E17C E194 276 a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11 a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11
OBJECT_INDEX 3B82 3B9A 256 430ff3d7defba4d3c7da091a430c95024d9a58c318b6174d704c9e7386ff8f86 a1e9e48e9cf08f0b2ba01e7d4d781b9a23082702f84305e94f318f990b24fcb2
PAL_SONIC 61C 688 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 63C 6A8 96 29d86b702295ed28d6d6c4fe4e829333036d0bb2b3c69f4ddb71e38b71a8e8cd 7a34893db95e1d9c0e4ab5acf2a492857d7b660f3e34263b60bd889fb8d03cb2
DIAGONAL_SPRING_HEADER 31E2E 31E2E 2 3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd 3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd
GAME_OVER_CODE D954 D96C 228 d81476d2deb155dba7420e02b696281166039854d767bfcad8b9b7546f385f61 b59a31ca104128837c4407461ea9452c702037bf0a9a176a643546df2f1f7c06
MAP_SPR_GAME_OVER DA38 DA50 56 ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b
""".strip();
    private static final String INDEX_C = "203CF0 203CF0 206144 203C82 203C82 205D0E 203C82 203C82 203C82 207CC2 2060DE 2060A6 205FCE 205F78 207B76 208192 208476 203C82 203C82 20B194 20AF16 20B2CC 20B450 205DB4 2089B4 208B30 207AFE 209008 203C82 203C82 205E42 203C82 20A55A 209AB6 20A064 20AC2E 203C82 20A46C 203C82 20A134 203C82 20B958 20C1B8 20CE18 20D240 20D47E 20C97C 20CA6A 20CB9C 20CD3A 203C82 206320 203C82 203C82 203C82 203C82 203C82 20DBF6 20D954 20DA70 2098CA 20976C 209606 209DEC";
    private static final String INDEX_D = "203D08 203D08 20615C 203C9A 203C9A 205D26 203C9A 203C9A 203C9A 207CDA 2060F6 2060BE 205FE6 205F90 207B8E 2081AA 20848E 203C9A 203C9A 20B1AC 20AF2E 20B2E4 20B468 205DCC 2089CC 208B48 207B16 209020 203C9A 203C9A 205E5A 203C9A 20A572 209ACE 20A07C 20AC46 203C9A 20A484 203C9A 20A14C 203C9A 20B970 20C1D0 20CE30 20D258 20D496 20C994 20CA82 20CBB4 20CD52 203C9A 206338 203C9A 203C9A 203C9A 203C9A 203C9A 20DC0E 20D96C 20DA88 2098E2 209784 20961E 209E04";
    private static final String OPERANDS_C = """
abc8 43f90020abead040
82de 237c0020860e0004337ca7ae0002137c
7d50 217c002080b40004317c052000020028
7d98 217c002080ba0004
7dba 217c002081040004
7df4 317b00060030
89fa 217c00208da00004317c05a80002117c
9026 217c002090b80004317c85680002317c
ac66 217c0020aec80004
ad76 217c0020aedc0004
ae2c 43fa00d2
acd8 43fa0170
ae08 43fa0082
daae 317c83600002217c0020e17c0004117c00040018
dac6 45f90020e13c
8b80 227c00208da0
93c0 43f9002330bc
1a6e 43f900232f3c
93b8 45fa0034
9494 45f90020954e
9418 45f900209552
9578 45f900209566
""".strip();
    private static final String OPERANDS_D = """
abe0 43f90020ac02d040
82f6 237c002086260004337ca7ae0002137c
7d68 217c002080cc0004317c052000020028
7db0 217c002080d20004
7dd2 217c0020811c0004
7e0c 317b00060030
8a12 217c00208db80004317c05a80002117c
903e 217c002090d00004317c85680002317c
ac7e 217c0020aee00004
ad8e 217c0020aef40004
ae44 43fa00d2
acf0 43fa0170
ae20 43fa0082
dac6 317c83600002217c0020e1940004117c00040018
dade 45f90020e154
8b98 227c00208db8
93d8 43f9002330bc
1ada 43f900232f3c
93d0 45fa0034
94ac 45f900209566
9430 45f90020956a
9590 45f90020957e
""".strip();

    @Test void completeTwentySevenViewManifestPinsBothOriginalImagesAndExactBounds() throws Exception {
        var f=TestCdR13FutureAssets.retail();var lines=MANIFEST.lines().toList();
        assertEquals(27,lines.size());assertEquals(27,CdR13ObjectAssets.View.values().length);
        for(var a:f.all()) {
            var o=CdR13ObjectAssets.fromAssets(a);var image=f.image(a.identity().file());
            assertSame(a.identity(),o.identity());assertSame(a.placements(),o.placements());assertTrue(o.ownsAssets(a));
            assertEquals(CdReleaseVariant.USA,o.identity().variant());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,o.identity().domain());
            for(int i=0;i<lines.size();i++) {
                var p=lines.get(i).split(" ");var v=CdR13ObjectAssets.View.values()[i];assertEquals(p[0],v.name());
                int column=a.identity().file()==CdNativeFile.R13C?1:2;
                var r=v.range(a.identity().file());assertEquals(Integer.parseInt(p[column],16),r.fileOffset());
                assertEquals(Integer.parseInt(p[3]),r.length());assertEquals(0x200000+r.fileOffset(),r.nativeAddress());
                assertEquals(a.identity().file(),r.file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,r.domain());
                var b=o.view(v);assertSame(b,o.view(v));assertEquals(p[column+3],hash(b.copy()));
                assertEquals(p[column+3],v.sha256(a.identity().file()));
                assertArrayEquals(image.readFile(r.fileOffset(),r.length()),b.copy());assertEquals(r.length(),b.length());
                assertThrows(IndexOutOfBoundsException.class,()->b.unsignedByte(b.length()));
            }
        }
    }

    @Test void allSixtyFourDispatchPointersAndSixStartupIdentitiesAreProgrammeLocal() throws Exception {
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var o=CdR13ObjectAssets.fromAssets(a);var raw=o.view(OBJECT_INDEX);
            assertArrayEquals(a.view(CdR13FutureAssets.Asset.OBJECT_INDEX).copy(),raw.copy());
            var expected=(a.identity().file()==CdNativeFile.R13C?INDEX_C:INDEX_D).split(" ");
            assertEquals(64,expected.length);for(int i=0;i<64;i++)assertEquals(Long.parseLong(expected[i],16),raw.unsignedLong(i*4));
            for(int id:new int[]{0x0A,0x10,0x19,0x1C,0x24,0x3C})assertNotEquals(raw.unsignedLong(3*4),raw.unsignedLong((id-1)*4));
            assertEquals(List.of(0x0A,0x10,0x15,0x19,0x22,0x24,0x2A,0x34),
                    o.placements().records().stream().map(CdObjectPlacements.Placement::objectId).distinct().sorted().toList());
            assertEquals(61,o.placements().records().size());assertEquals(0x2A,o.placements().records().get(55).objectId());
            assertEquals(0x15,o.placements().records().get(60).objectId());
        }
    }

    @Test void actualLiteralAndPcRelativeConsumersRetainIndependentCandDOperands() throws Exception {
        var f=TestCdR13FutureAssets.retail();
        for(var a:f.all()) {
            var b=f.image(a.identity().file());var fixture=a.identity().file()==CdNativeFile.R13C?OPERANDS_C:OPERANDS_D;
            for(var row:fixture.lines().toList()) {var p=row.split(" ");assertEquals(p[1],HexFormat.of().formatHex(b.readFile(Integer.parseInt(p[0],16),p[1].length()/2)));}
            var o=CdR13ObjectAssets.fromAssets(a);assertEquals(0xF000,o.view(SPRING_SPEEDS).unsignedWord(0));assertEquals(0xF600,o.view(SPRING_SPEEDS).unsignedWord(2));
            assertEquals(0x3CF,o.view(ANIMAL_BASE_TILES).unsignedWord((2*4+2)*2));
            assertArrayEquals(a.view(CdR13FutureAssets.Asset.PAL_SONIC).copy(),o.view(PAL_SONIC).copy());
            assertArrayEquals(a.view(CdR13FutureAssets.Asset.PAL_LEVEL).copy(),o.view(PAL_LEVEL).copy());
            assertEquals(3,a.paletteBindings().get(0).nativeId());assertEquals(0xFB00,a.paletteBindings().get(0).destinationWord());
            assertEquals(4,a.paletteBindings().get(1).nativeId());assertEquals(0xFB20,a.paletteBindings().get(1).destinationWord());
        }
    }

    @Test void actualSixtyEightByteTileDirectoryHasEighteenSelectorsAndSixteenOneWordBodies() throws Exception {
        int[] offsets={36,38,40,42,44,46,52,50,66,66,48,54,56,58,60,62,64,66};
        int[] tiles={0x2000,0x200C,0x2000,0x2000,0x23E7,0x4000,0,0,0,0,0,0x4FE,0x83AE,0,0,0,0,0};
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var b=CdR13ObjectAssets.fromAssets(a).view(OBJECT_TILE_IDS);assertEquals(68,b.length());
            for(int i=0;i<18;i++){assertEquals(offsets[i],b.unsignedWord(i*2));assertEquals(tiles[i],b.unsignedWord(offsets[i]));assertEquals(offsets[i]+2,CdObjectTileIds.endOfBody(b,i));}
            assertEquals(16,Arrays.stream(offsets).distinct().count());
            assertThrows(IllegalArgumentException.class,()->CdObjectTileIds.endOfBody(b,18));
            assertThrows(IllegalArgumentException.class,()->CdObjectTileIds.endOfBody(b.slice(0,67),0));
        }
    }

    @Test void nineVisualsRetainEveryNativeCountedFrameAndExistingAnimationGrammar() throws Exception {
        int[] frames={9,3,3,3,4,20,3,5,9},scripts={1,1,1,1,0,10,2,1,0};
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var o=CdR13ObjectAssets.fromAssets(a);var v=visuals(o);var maps=mapViews();
            for(int k=0;k<v.size();k++) {
                assertSame(v.get(k),visuals(o).get(k));assertEquals(frames[k],v.get(k).mappingFrames().size());assertEquals(scripts[k],v.get(k).animations().size());
                var bytes=o.view(maps.get(k));assertArrayEquals(bytes.copy(),v.get(k).mappings().copy());
                int root=v.get(k).mappingTableOffset();
                for(int n=0;n<frames[k];n++) {var frame=v.get(k).mappingFrames().get(n);assertEquals((short)bytes.unsignedWord(root+n*2),frame.relativeOffset());int at=root+frame.relativeOffset();assertEquals(frame.pieces().size(),bytes.unsignedByte(at));assertArrayEquals(bytes.slice(at,1+frame.pieces().size()*5).copy(),frame.bytes().copy());}
            }
            assertEquals(16,o.ringDeltas().size());assertSame(o.ringDeltas(),o.ringDeltas());assertSame(o.view(MAP_SPRING),o.springMappings());
            assertThrows(UnsupportedOperationException.class,()->o.ringDeltas().clear());
        }
    }

    @Test void springUsesExactOddIncludeAndIndependentMetadataOnlyDiagonalHeader() throws Exception {
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var o=CdR13ObjectAssets.fromAssets(a);assertEquals(199,o.springMappings().length());
            assertEquals(0,o.springVertical().mappingTableOffset());assertEquals(6,o.springHorizontal().mappingTableOffset());assertEquals(80,o.springDiagonal().mappingTableOffset());
            assertEquals(46,o.view(DIAGONAL_SPRING_HEADER).unsignedWord(0));
            var sources=CdR13SectionSources.fromAssets(a);assertEquals(19,sources.sources().size());
            assertEquals(36,sources.source(CdR13SectionSources.SourceId.ART_SPRINGS).tileCount());
            assertThrows(IllegalArgumentException.class,()->sources.source(0x231E2EL));
            assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.springMappings().copy(),9,o.view(ANI_SPRING).copy(),1,46));
            assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeIndexed(o.springMappings().slice(0,198).copy(),80,3,86,198,o.view(ANI_SPRING).copy(),1,46));
        }
    }

    @Test void titleSelectsLiveRootsAndBoundsCardAndTextWithoutInventingTheThreeTileGap() throws Exception {
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var o=CdR13ObjectAssets.fromAssets(a);var f=a.identity().file();var r=MAP_SPR_TITLE_CARD.range(f);var data=o.titleCardAssets();
            assertEquals(f==CdNativeFile.R13C?0x20E17C:0x20E194,r.nativeAddress());assertNotEquals(0x20F782,r.nativeAddress());
            assertEquals(r.nativeAddress(),data.mappingsAddress());assertSame(data,o.titleCardAssets());assertSame(o.view(TITLE_CARD_DATA),data.initData());assertEquals(64,data.initData().length());
            var sources=CdR13SectionSources.fromAssets(a);assertEquals(109,sources.source(CdR13SectionSources.SourceId.ART_TITLE_CARD).tileCount());assertEquals(52,sources.source(CdR13SectionSources.SourceId.ART_TITLE_CARD_TEXT).tileCount());
            for(int frame=0;frame<9;frame++)for(var p:o.titleCard().mappingFrames().get(frame).pieces()){assertTrue(p.tileOffset()>= (frame>=7?112:0));assertTrue(p.tileOffset()+p.tileCount()<=(frame>=7?164:109));}
            assertEquals(0x130,data.initData().unsignedWord(0));assertEquals(0x85A,data.initData().unsignedWord(62));
        }
    }

    @Test void hudRawFontIconResetAndDivisorViewsRemainImmutableDetachedValues() throws Exception {
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var o=CdR13ObjectAssets.fromAssets(a);var h=o.hudAssets();assertSame(h,o.hudAssets());
            assertSame(o.view(ART_HUD_NUMBERS),h.numbers());assertSame(o.view(HUD_RESET_TILES),h.resetTiles());assertSame(o.view(HUD_DIVISORS),h.divisors());
            assertEquals(1024,h.numbers().length());assertEquals(12,h.resetTiles().length());assertEquals(40,h.divisors().length());assertEquals(384,o.view(ART_LIFE_ICON).length());
            long[] divisors={100000,10000,1000,100,10,1,0x1000,0x100,0x10,1};for(int i=0;i<10;i++)assertEquals(divisors[i],h.divisors().unsignedLong(i*4));
            for(var v:CdR13ObjectAssets.View.values()){var b=o.view(v);byte[] before=b.copy(),copy=b.copy();Arrays.fill(copy,(byte)0x55);assertArrayEquals(before,o.view(v).copy());assertThrows(ReadOnlyBufferException.class,()->b.buffer().put(0,(byte)1));}
            assertThrows(UnsupportedOperationException.class,()->o.titleCard().mappingFrames().clear());assertThrows(UnsupportedOperationException.class,()->o.rings().animations().clear());
        }
    }

    @Test void finiteMapperJoinsTenCountedRootsAndEightActualFirstPiecePointers() throws Exception {
        for(var a:TestCdR13FutureAssets.retail().all()) {
            var o=CdR13ObjectAssets.fromAssets(a);var m=CdObjectMappings.fromAct3FutureAssets(a,o);var vf=visuals(o);var views=mapViews();
            assertEquals(10,privateMap(m,"tables").size());assertEquals(8,privateMap(m,"staticPieces").size());
            for(int k=0;k<vf.size();k++){var visual=vf.get(k);int pointer=views.get(k).range(a.identity().file()).nativeAddress()+visual.mappingTableOffset();
                for(int frame=0;frame<visual.mappingFrames().size();frame++)assertEquals(visual.mappingFrames().get(frame).pieces().stream().map(p->new RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList(),m.resolve(pointer,false,frame).pieces());
                assertThrows(UnsupportedOperationException.class,()->m.resolve(pointer,false,visual.mappingFrames().size()));assertThrows(UnsupportedOperationException.class,()->m.resolve(pointer|0x01000000,false,0));}
            var source=o.view(MAP_SPR_MONITOR_TIME);int root=MAP_SPR_MONITOR_TIME.range(a.identity().file()).nativeAddress();
            for(int frame=0;frame<8;frame++){int at=(short)source.unsignedWord(frame*2)+1;var expected=new RawPiece(source.signedByte(at),source.unsignedByte(at+1),source.unsignedWord(at+2),source.signedByte(at+4));assertEquals(List.of(expected),m.resolve(root+at,true,255).pieces());}
            assertThrows(UnsupportedOperationException.class,()->m.resolve(root,true,0));assertThrows(UnsupportedOperationException.class,()->m.resolve(0x20F782,false,0));
            var sonic=a.sonic();for(int frame=0;frame<167;frame++) {
                var bytes=sonic.mappingFrames().get(frame).bytes();var actual=m.resolve(0x22DF20,false,frame).pieces();
                assertEquals(sonic.mappingFrames().get(frame).pieceCount(),actual.size());
                for(int piece=0;piece<actual.size();piece++){int at=1+piece*5;assertEquals(new RawPiece(bytes.signedByte(at),bytes.unsignedByte(at+1),bytes.unsignedWord(at+2),bytes.signedByte(at+4)),actual.get(piece));}
            }
            var sourceField=CdR13ObjectAssets.class.getDeclaredField("sources");sourceField.setAccessible(true);var retained=(CdR13SectionSources)sourceField.get(o);
            var products=CdR13SectionSources.class.getDeclaredField("products");products.setAccessible(true);for(var product:(Object[])products.get(retained))assertNull(product,"metadata mapper never decodes art");
            var sourceViews=CdR13SectionSources.class.getDeclaredField("views");sourceViews.setAccessible(true);for(var row:(Object[][])sourceViews.get(retained))for(var view:row)assertNull(view,"metadata counts do not open compressed readers");
            assertThrows(UnsupportedOperationException.class,()->m.resolve(0x22DF20,false,167));
        }
    }

    @Test void wrongRetainedImagePairHashAndUnownedRawDomainAreRejected() throws Exception {
        var f=TestCdR13FutureAssets.retail();
        assertThrows(NullPointerException.class,()->CdR13ObjectAssets.fromAssets(null));
        for(var wrong:List.of(f.d(),f.legacyImage(),CdMmdImage.parse(f.c().readFile(0,262143)))) {
            var owner=spy(f.good());doReturn(wrong).when(owner).originalImage();assertThrows(IllegalArgumentException.class,()->CdR13ObjectAssets.fromAssets(owner));
        }
        for(var view:CdR13ObjectAssets.View.values()){byte[] bytes=f.c().readFile(0,262144);bytes[view.range(CdNativeFile.R13C).endExclusive()-1]^=1;var owner=spy(f.good());doReturn(CdMmdImage.parse(bytes)).when(owner).originalImage();assertThrows(IllegalArgumentException.class,()->CdR13ObjectAssets.fromAssets(owner),view.name());}
        var good=CdR13ObjectAssets.fromAssets(f.good());var bad=CdR13ObjectAssets.fromAssets(f.bad());
        assertFalse(good.ownsAssets(f.bad()));assertThrows(IllegalArgumentException.class,()->CdObjectMappings.fromAct3FutureAssets(f.good(),bad));
        assertThrows(NullPointerException.class,()->CdObjectMappings.fromAct3FutureAssets(f.good(),null));assertThrows(NullPointerException.class,()->good.view(null));
        assertThrows(IllegalArgumentException.class,()->MAP_SPR_RING.range(CdNativeFile.R12B));
        assertThrows(IllegalArgumentException.class,()->new CdR13FutureAssets.FileRange(CdNativeFile.R13C,CdMmdImage.Domain.RELOCATED_COPY,0,2));
        assertThrows(IllegalArgumentException.class,()->f.c().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY,0x20860E,2));
    }

    @Test void originalSourceDecoderPoliciesAndLegacyTwoHundredByteSpringViewRemainExact() throws Exception {
        var f=TestCdR13FutureAssets.retail();var old=CdR12bTerrainAssets.fromPrograms(TestCdR13FutureAssets.source(f,CdNativeFile.R12B,f.legacyImage())).objects();
        assertEquals(200,old.springMappings().length());assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c",hash(old.springMappings().copy()));
        assertEquals(72,old.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS).length());assertEquals(3,old.springDiagonal().mappingFrames().size());
        byte[] invalid={0,2,(byte)129};assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(invalid,1,1));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(new byte[]{0,2,1,0,0,0,1,0},1,1));
        assertThrows(NullPointerException.class,()->CdObjectMappings.fromAct2PastAssets(null,f.legacy()));
    }

    @Test void metadataSurfaceAddsNoPublicRawFactoryOrResourceCaptureOwner() throws Exception {
        assertEquals(List.of("flyingAnimal/0","fromAssets/1","groundAnimal/0","hudAssets/0","hudVisuals/0","identity/0","monitorTime/0","ownsAssets/1","placements/0","ringDeltas/0","rings/0","springDiagonal/0","springHorizontal/0","springMappings/0","springVertical/0","titleCard/0","titleCardAssets/0","view/1"),
                Arrays.stream(CdR13ObjectAssets.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        assertEquals(0,CdR13ObjectAssets.class.getConstructors().length);assertEquals(1,CdR13ObjectAssets.class.getDeclaredConstructors().length);
        assertEquals(List.of("act2MonitorItemStaticPieces/1","act2PastMonitorItemStaticPieces/1","declaredSourceViews/2","fromAct2PastAssets/2","fromAct3FutureAssets/2", "fromAct3FutureAssets/3", "fromAct3FutureAssets/4", "fromAct3FutureAssets/6","fromOpeningAssets/2","fromR31aAssets/2","resolve/3","withAct3FutureGameOver/3"),
                Arrays.stream(CdObjectMappings.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        assertEquals(19,CdR13SectionSources.SourceId.values().length);assertEquals(29,CdR13FutureAssets.Asset.values().length);
        assertEquals(16,Arrays.stream(CdR13FutureAssets.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).count());
    }

    private static String hash(byte[] bytes){return CdR11aProfile.sha256(bytes);}
    private static List<CdObjectVisualAssets> visuals(CdR13ObjectAssets o){return List.of(o.rings(),o.springVertical(),o.springHorizontal(),o.springDiagonal(),o.hudVisuals(),o.monitorTime(),o.flyingAnimal(),o.groundAnimal(),o.titleCard());}
    private static List<CdR13ObjectAssets.View> mapViews(){return List.of(MAP_SPR_RING,MAP_SPRING,MAP_SPRING,MAP_SPRING,MAP_SPR_HUD,MAP_SPR_MONITOR_TIME,MAP_SPR_FLYING_ANIMAL,MAP_SPR_GROUND_ANIMAL,MAP_SPR_TITLE_CARD);}
    private static Map<?,?> privateMap(CdObjectMappings owner,String name)throws Exception{var f=CdObjectMappings.class.getDeclaredField(name);f.setAccessible(true);return (Map<?,?>)f.get(owner);}
}

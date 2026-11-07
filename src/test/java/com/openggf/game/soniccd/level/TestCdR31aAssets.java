package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;import java.util.*;import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
/** Tests authored first. Actual original USA data; no Body/Frame/spawn/World/loading invocation. Java UNRUN. */
class TestCdR31aAssets {
 private static final String MANIFEST="""
LEVEL_CHUNKS 10000 54784 5501b636432a99dfce17a10c1357b497b06a8d48716fbd325536ac82d9ca0d44
COL_ANGLE_MAP 1d600 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 1d700 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 1e700 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
LEVEL_COLLISION 1f700 700 7c606a40992f3bab8e995c4e2a343a0c7ee0583de53b3c10039e09e903e537d6
LAYOUT_FG 1fa04 290 874d951032d030e7ae0df432811465b3b7fecedcaff01d7f8a4d19022bc7f13b
LAYOUT_BG 1fb26 24 878fac8156943fdc600fc75e058a55819a7839df1925d084da4dff9ced8253f2
LEVEL_BLOCKS 36c76 3960 51c6a77d51fc3817ff0935c4095cd2ad6c3e809e9348507299b03f313dff09a3
ART_LEVEL_TILES 37bee 10162 f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37
ART_SONIC 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
MAP_SPR_SONIC 2df20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
DPLC_SONIC 2e8ac 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
ANI_SONIC 590a 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
MINI_ANIMATIONS 58ba 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
SINE_TABLE 79a 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
ARC_TAN_TABLE a80 258 7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f
TOUCH_DIMENSIONS 754a 126 5337f6beae28fe2b8458277610f92d358020a78e947d68b09470b38829e46575
ENEMY_POINTS 7340 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
CAM_BOUNDS 287c 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
LEVEL_START_LOC 2940 4 f2084622a23e63902510eea2a083587f297c6f521575b9943592127adbe41df0
SPEC_CHUNKS 2944 4 365475d471c68745e3881c3743e1669911103607f18d88d9db7292e29177c0bf
PALETTE_CYCLES 200 100 f1c83c9d9f2454d129b2811a0dc138994130ad7754a59cf6935a90ab071a3f72
PAL_SONIC 65c 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 67c 96 2af2050f1eb57a5183cb90be48b6e5e7ffbbf7cd3cc5764d466eaf64a185bd15
PAL_LEVEL_END 6dc 96 4277057e64bf28760727361b2b5ecda2795d57e05c49511236c4532e1b03e8c4
SECTIONS bf48 24 fd7724d154bea8193d2e18a092e570dddca627c749715f20a4582f092c13a9a1
BG_SECTIONS 3504 50 2a2a2a30fe4a460e0fdd45a0d941fe7b0e1cee3ee4fd2a9aacbee6ba6c517e2a
SCROLL_REPEAT_COUNTS 2c02 16 6dcfe3a302eac5f3c0efe96d992b050b7657c04d7906dcaa6bb49db6a1f5ac90
SCROLL_WATER_BOUNDS 2c12 6 677bb2193c2749569097a8311af9320d7269d37ad55c06dac13626006a2d5104
WOBBLE 3a746 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
HUD_MAPPINGS a850 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
POINTS_MAPPINGS a74e 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
TITLE_CHARACTERS f966 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
TITLE_MAPPINGS f9a6 275 b02c6847c24061150b4b815cfd6610fd79ed114af5afd4d5ebd736d2b0ff6e42
MONITOR_ANIMATIONS a4b0 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
MONITOR_MAPPINGS a538 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
ART_LIFE_ICONS 32f3c 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
ART_HUD_NUMBERS 330bc 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
PAL_S1_TITLE 55c 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
PAL_S1_LEVEL_SELECT 5dc 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
ART_SHIELD 3114e 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
ART_INVINCIBILITY 315ce 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
ART_TIME_STARS 31a4e 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
LEVEL_COL_INDEX 19c2 32 cc50e40a8b5ce071fe95bd39ba479d65c4e9a985c9e4ac3c9f66b7f8fac8fa1c
LEVEL_DATA_INDEX fac0 16 48bec27bc20f2c10196ae78631fe65d902aeaf3d96a9c52168513c335ce37152
PLC_LISTS fad0 534 abac5333eb26053a3373f20f4f87a4e8a0e363c2a0c5e2402bb421fe31e1338c
LEVEL_LAYOUTS 1f9bc 72 31fd506d49908b9e734b6b7792cb731e8c4990c37225d4006913800130bee113
OBJECT_INDEX 3db4 240 91cadd2958133a5d66c363a7c1233e2a86be70db860dd911df3a98e4a87d4ccb
OBJECT_LAYOUTS 7bae 4162 cd825a16352a1f385b0f5cc3dec98ec7d8b05c9a0f4621506f0e3011042cbce5
PALETTE_TABLE 52c 48 8dd2f06d81432e838ae90f04765214f0229b3d2e44b401a6f0feac170007fdd4
BG_POINTERS 3536 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
SONIC_MAPPING_READ_VIEW 2df20 2444 9836b535d15ee481a9a663bf651377791569f3ea9d91c6cd657ddc83cca3ae77
TITLE_MAPPING_READ_VIEW f9a6 276 d8818ede6de8c23469e94b36c8445439969b36b120ec7b69855d1f8ef7134a11
PLAYER_BLOCKS 6858 8 fa316848863cb1723eff5b464a05b21e48b87da9444792decf31c760cf992478
PLAYER_BLOCKS_A 6860 34 d05eb8d9f7ab1c19d8dc26425c9d922c8accb09b9c74da0f652e2a89d270f7a9
PLAYER_BLOCKS_B 6882 6 7b054c17b49ee53fe432f8700be73ba7d337e56cc0aa4aeced43ef662a7f2ebe
PLAYER_BLOCKS_C 6888 34 4919cb2975fad8d7ce977c58c4235b139abbfe707a382ad68cb302e081521805
PLAYER_BLOCKS_D 68aa 34 a0e3da04701e194ebf10531da38153f9c058051ca7340833cc068da408c9dc30
PLAYER_BLOCK_ROUTINES 6922 32 234e4a3bb1b19f9237548700a94e9a515571e11284f2ec9d333825c5ad4a9003
PLAYER_BUMPER_COLLIDE 6b2e 6 b310628db3d435d16937fc75ab8b04097658ac0fcf15530843d3c0aa591cae74
PLAYER_BUMPER_COLLIDE_1 6b34 16 46fd7d2954ec5a94f0ab18ed3ce82ef3738b9e9a5f444e952caccba97ec43cd8
PLAYER_BUMPER_COLLIDE_2 6b44 16 7039c80bf965bd800e6e814ae50b42d9316184aa79c63a41075fa1945bf7dd47
PLAYER_BUMPER_COLLIDE_3 6b54 16 55863b4d00c3326f9acf1d393883e0964cd7598563e7f4548d12a1eea4fdf745
RING_BODY 9768 740 2fe09f8bef2a5da08bf7995bbe9a7f5bcf4493f825a9d40a6caa5e8db85731f9
RING_DELTAS 9780 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
RING_ANIMATIONS 9bdc 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
RING_MAPPINGS 9be4 68 306667dca113be405fd1d78fd69b09152b853236db8ad8fbeb6a73bbde3be7e8
 """;
 @Test void wholeSourceIdentityAndCompleteLiteralViewManifestAreAuthenticated()throws Exception{
  var a=actual().assets();assertEquals(CdReleaseVariant.USA,a.identity().variant());assertEquals(CdStagePayload.R31A.stage(),a.identity().stage());assertEquals(CdNativeFile.R31A,a.identity().file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,a.identity().domain());assertEquals("30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e",a.identity().mmdSha256());assertEquals(67,CdR31aAssets.Asset.values().length);
  var lines=MANIFEST.strip().lines().toList();assertEquals(lines.size(),a.ranges().size());for(int i=0;i<lines.size();i++){var p=lines.get(i).split(" ");var id=CdR31aAssets.Asset.valueOf(p[0]);assertEquals(i,id.ordinal());var r=a.range(id);assertEquals(Integer.parseInt(p[1],16),r.fileOffset());assertEquals(Integer.parseInt(p[2]),r.length());assertEquals(p[3],hash(a.view(id).copy()));assertEquals(0x200000+r.fileOffset(),r.nativeAddress());assertSame(a.view(id),a.view(id));assertThrows(IndexOutOfBoundsException.class,()->a.view(id).unsignedByte(r.length()));}
 }
 @Test void actualTerrainUsesExistingNemesisAndExactNewFiniteShape()throws Exception{
  var a=actual().assets();var t=a.terrain();assertSame(t,a.terrain());assertEquals(CdStageTerrain.Shape.CCZ1_PRESENT,t.shape());assertEquals(656,t.blockCount());assertEquals(694,t.patternCount());assertEquals(107,t.chunkCount());assertEquals(48,t.layout().columns(CdStageTerrain.Plane.FOREGROUND));assertEquals(6,t.layout().rows(CdStageTerrain.Plane.FOREGROUND));assertEquals(7,t.layout().columns(CdStageTerrain.Plane.BACKGROUND));assertEquals(3,t.layout().rows(CdStageTerrain.Plane.BACKGROUND));assertEquals("108e1dc9e6fb4c14578ca3073fbd9099f9fe5028d10fed98dad102ce56076964",hash(a.blocksDecode().bytes().copy()));assertEquals(3959,a.blocksDecode().consumedInputBytes());assertEquals("db0664cea0d185e7817e63918fc20350f09afda60e8caed64d0ce19707df8591",hash(a.levelTilesDecode().bytes().copy()));assertEquals(10161,a.levelTilesDecode().consumedInputBytes());assertArrayEquals(a.levelTilesDecode().bytes().copy(),t.patterns().copy());
 }
 @Test void rawPlayerRetains167ArtAndAllScriptsWithoutInventingARampOwner()throws Exception{
  var a=actual().assets();assertEquals(167,a.sonic().mappingFrames().size());assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,a.sonic().sourceFormat());assertEquals(57120,a.sonic().rawArt().length());assertEquals(57120,a.sonic().artReadView().length());assertThrows(IllegalStateException.class,a.sonic()::alignmentByte);assertEquals(578,a.view(CdR31aAssets.Asset.ANI_SONIC).length());assertEquals(80,a.view(CdR31aAssets.Asset.MINI_ANIMATIONS).length());assertTrue(Arrays.stream(CdR31aAssets.Asset.values()).noneMatch(x->x.name().contains("RAMP")));assertTrue(Arrays.stream(CdR31aAssets.class.getDeclaredMethods()).noneMatch(x->x.getName().equals("player")));assertEquals(126,a.view(CdR31aAssets.Asset.TOUCH_DIMENSIONS).length());assertEquals("5337f6beae28fe2b8458277610f92d358020a78e947d68b09470b38829e46575",hash(a.view(CdR31aAssets.Asset.TOUCH_DIMENSIONS).copy()));
 }
 @Test void sourceOrderRetains517RowsWithoutWideningTheOldPlacementContract()throws Exception{
  var a=actual().assets();var rows=a.placementRows();assertEquals(517,rows.size());int last=-1,present=0;var raw=a.view(CdR31aAssets.Asset.OBJECT_LAYOUTS);for(int i=0;i<rows.size();i++){assertArrayEquals(raw.slice(12+i*8,8).copy(),rows.get(i).copy());int x=rows.get(i).unsignedWord(0);assertTrue(x>=last);last=x;if((rows.get(i).unsignedByte(6)&0x40)!=0)present++;}assertEquals(409,present);assertEquals(0x1A4,rows.getFirst().unsignedWord(0));assertEquals(0x99,rows.getFirst().unsignedByte(4));assertEquals(0x2F90,rows.getLast().unsignedWord(0));assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeLayout(raw.copy()));assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(497,0,0,1,0,0x40,0));assertThrows(UnsupportedOperationException.class,()->rows.clear());
 }
 @Test void exactNativeLoadOperandsAndBoundsAreRetainedMetadataOnly()throws Exception{
  var a=actual().assets();assertEquals(new CdR11bLoadPlan.Bounds(4,0,0x2E97,0,0x510,0x60),a.bounds());assertEquals(new CdR11bLoadPlan.InitialCentre(0x60,0x3EC),a.initialCentre());assertEquals(3,a.levelIndex().levelArt().plcId());assertEquals(2,a.levelIndex().blocks().plcId());assertEquals(0x237BEE,a.levelIndex().levelArt().nativeAddress());assertEquals(0x236C76,a.levelIndex().blocks().nativeAddress());assertEquals(0x210000,a.levelIndex().chunksNativeAddress());assertEquals("0081",HexFormat.of().formatHex(a.levelIndex().uninterpretedTrailing().copy()));assertEquals("0404",HexFormat.of().formatHex(a.levelIndex().paletteIds().copy()));
 }
 @Test void wrongStageReleaseCatalogueAndUnknownWholeDiscFailBeforeMmdRead()throws Exception{
  var f=actual();var p=source(f,f.image());assertThrows(UnsupportedOperationException.class,()->CdR31aAssets.fromPrograms(p,CdStagePayload.R13D.stage()));verify(p,never()).mmd(any());assertThrows(NullPointerException.class,()->CdR31aAssets.fromPrograms(null,CdStagePayload.R31A.stage()));assertThrows(NullPointerException.class,()->CdR31aAssets.fromPrograms(p,null));
  var bad=source(f,f.image());var h=mock(CdDiscHeader.class);when(h.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.EUROPE));when(bad.header()).thenReturn(h);assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(bad,CdStagePayload.R31A.stage()));verify(bad,never()).mmd(any());
  var missing=source(f,f.image());when(missing.catalogue()).thenReturn(Optional.empty());assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(missing,CdStagePayload.R31A.stage()));verify(missing,never()).mmd(any());var unknown=source(f,f.image());var id=f.disc();when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(),"0".repeat(64),id.cue(),id.binaryFiles(),id.originalContainer(),id.tracks()));assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(unknown,CdStagePayload.R31A.stage()));verify(unknown,never()).mmd(any());
  var mismatch=source(f,f.image());var cat=mock(CdProgramCatalogue.class);when(cat.release()).thenReturn(CdReleaseVariant.USA);when(cat.stage(CdStagePayload.R31A.stage())).thenReturn(CdStagePayload.R13D);when(mismatch.catalogue()).thenReturn(Optional.of(cat));assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(mismatch,CdStagePayload.R31A.stage()));verify(mismatch,never()).mmd(any());
 }
 @Test void everyViewMutationForeignOriginalAndShortImageFailAuthentication()throws Exception{
  var f=actual();for(var view:CdR31aAssets.Asset.values()){byte[] b=f.image().readFile(0,262144);b[f.assets().range(view).endExclusive()-1]^=1;assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(source(f,CdMmdImage.parse(b)),CdStagePayload.R31A.stage()),view.name());}for(var image:List.of(f.foreign(),CdMmdImage.parse(f.image().readFile(0,262143))))assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(source(f,image),CdStagePayload.R31A.stage()));
 }
 @Test void detachedBytesAndListsAreImmutableWithoutKeepingTheDiscOpen()throws Exception{
  var a=actual().assets();for(var id:CdR31aAssets.Asset.values()){var old=a.view(id).copy();var copy=a.view(id).copy();Arrays.fill(copy,(byte)99);assertArrayEquals(old,a.view(id).copy());assertThrows(java.nio.ReadOnlyBufferException.class,()->a.view(id).buffer().put(0,(byte)1));}assertThrows(UnsupportedOperationException.class,()->a.ranges().clear());assertThrows(NullPointerException.class,()->a.view(null));assertThrows(IllegalArgumentException.class,()->actual().image().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY,0x220000,32));
 }
 @Test void allOldShapeOrdinalsAndBothR13InventoriesRemainUnchanged()throws Exception{
  assertEquals(List.of("PPZ1_PRESENT","PPZ1_PAST","PPZ1_GOOD_FUTURE","PPZ1_BAD_FUTURE","PPZ2_PRESENT","PPZ2_PAST","PPZ3_GOOD_FUTURE","PPZ3_BAD_FUTURE","CCZ1_PRESENT","CCZ2_PRESENT"),Arrays.stream(CdStageTerrain.Shape.values()).map(Enum::name).toList());assertEquals(29,CdR13FutureAssets.Asset.values().length);assertEquals(19,CdR13SectionSources.SourceId.values().length);for(var c:CdR31aAssets.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));
 }
 record Fixture(CdR31aAssets assets,CdMmdImage image,CdMmdImage foreign,CdDiscHeader header,CdObservedMediaIdentity disc,CdProgramCatalogue catalogue){}
 private static Fixture retained;
 static synchronized Fixture actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media absent");Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(x->x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Fixture(CdR31aAssets.fromPrograms(p,CdStagePayload.R31A.stage()),p.mmd(CdNativeFile.R31A),p.mmd(CdNativeFile.R13D),p.header(),p.identity(),p.catalogue().orElseThrow());}return retained;}
 static CdProgramAssets source(Fixture f,CdMmdImage image)throws Exception{var p=mock(CdProgramAssets.class);when(p.header()).thenReturn(f.header());when(p.identity()).thenReturn(f.disc());when(p.catalogue()).thenReturn(Optional.of(f.catalogue()));when(p.mmd(CdNativeFile.R31A)).thenReturn(image);return p;}
 static String hash(byte[] b){return CdR11aProfile.sha256(b);}
}

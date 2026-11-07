package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Focused source/contract controls. Java execution intentionally UNRUN in this packet. */
class TestCdProgrammeReader {
 @Test void closedReaderIsPackagePrivateAndHasNoPublicConstructor() {
  assertFalse(Modifier.isPublic(CdProgrammeReader.class.getModifiers()));
  assertEquals(0,CdProgrammeReader.class.getConstructors().length);
 }
 @Test void unsupportedStageDoesNotTouchProgrammeOwner() throws Exception {
  var actual=mock(CdProgramAssets.class);
  assertThrows(UnsupportedOperationException.class,()->CdProgrammeReader.open(actual,CdStagePayload.R31B.stage()));
  verifyNoInteractions(actual);
 }
 @Test void explicitStageAndActualOwnerAreRequiredBeforeReads() {
  var actual=mock(CdProgramAssets.class);
  assertThrows(NullPointerException.class,()->CdProgrammeReader.open(actual,null));
  assertThrows(NullPointerException.class,()->CdProgrammeReader.open(null,CdStagePayload.R32A.stage()));
  verifyNoInteractions(actual);
 }
 @Test void rawAndCompressedInventoriesRemainIndependent() {
  assertEquals(63,CdR31aAssets.Asset.values().length);
  assertEquals(62,CdR32aDataLayout.Asset.values().length);
  assertEquals(37,CdR31aLoadMetadata.SourceId.values().length);
  assertEquals(41,CdR32aLoadLayout.SourceId.values().length);
  assertNotSame(CdR31aAssets.Asset.ANI_SONIC,CdR32aDataLayout.Asset.ANI_SONIC);
 }
 @Test void terrainShapeAppendsWithoutChangingExistingOrdinals() {
  assertEquals(8,CdStageTerrain.Shape.CCZ1_PRESENT.ordinal());
  assertEquals(9,CdStageTerrain.Shape.CCZ2_PRESENT.ordinal());
 }
 @Test void r31FacadeDoesNotPublishRawImageOrPlayerFactory() {
  assertFalse(Arrays.stream(CdR31aAssets.class.getDeclaredMethods()).anyMatch(x->x.getName().equals("originalImage")));
  assertFalse(Arrays.stream(CdR31aAssets.class.getMethods()).anyMatch(x->x.getName().equals("player")));
 }

 @Test void actualR31FacadeAndMetadataConsumeTheSameReaderCaches()throws Exception {
  var a=TestCdR31aAssets.actual().assets();var reader=a.internalReader();
  for(var id:CdR31aAssets.Asset.values())assertSame(a.view(id),reader.view(id));
  for(var id:CdR31aLoadMetadata.SourceId.values()) {
   assertSame(a.loadMetadata().canonicalView(id),reader.canonicalInput(id));
   assertSame(a.loadMetadata().javaView(id),reader.javaInput(id));
   assertSame(a.loadMetadata().decoded(id),reader.decoded(id));
  }
  assertSame(a.blocksDecode(),reader.decoded(CdProgrammeReader.Blocks.R31));
  assertSame(a.terrain(),reader.terrain());assertSame(a.sonic(),reader.sonic());
 }
 @Test void introUsesOnlyItsFixedOriginalViewsThroughTheSameReader()throws Exception {
  var fixture=TestCdR31aAssets.actual();var a=fixture.assets();var reader=a.internalReader();
  var intro=CdR31IntroAssets.fromAssets(a);
  assertEquals(15,CdR31IntroAssets.View.values().length);
  for(var view:CdR31IntroAssets.View.values()) {
   var actual=reader.view(view);
   assertArrayEquals(fixture.image().readFile(view.offset,view.length),actual.copy(),view.name());
   assertSame(actual,reader.view(view));
  }
  assertSame(reader.view(CdR31IntroAssets.View.INTRO_MAPPINGS),intro.introMappingBytes());
  assertSame(reader.view(CdR31IntroAssets.View.METAL_MAPPINGS),intro.metalMappingBytes());
  assertSame(reader.view(CdR31IntroAssets.View.AMY_MAPPINGS),intro.amyMappingBytes());
  assertSame(reader.view(CdR31IntroAssets.View.ROLL_MAPPINGS),intro.rollMappingBytes());
  assertEquals(63,a.ranges().size());
 }
 @Test void independentEqualOriginalsHaveIndependentCustodyAndProducts()throws Exception {
  var f=TestCdR31aAssets.actual();var p=TestCdR31aAssets.source(f,f.image());
  var first=CdProgrammeReader.open(p,CdStagePayload.R31A.stage());
  var second=CdProgrammeReader.open(p,CdStagePayload.R31A.stage());
  assertNotSame(first,second);assertNotSame(first.sonic(),second.sonic());
  var player=CdR11aPlayerAssets.fromR3Programme(first);
  assertTrue(player.ownsProgramme(first));assertFalse(player.ownsProgramme(second));
  assertNotSame(player,CdR11aPlayerAssets.fromR3Programme(first));
  verify(p,times(2)).mmd(CdNativeFile.R31A);
 }
 @Test void sameNamedForeignEnumsCannotSelectViewsOrProducts()throws Exception {
  var reader=TestCdR31aAssets.actual().assets().internalReader();
  assertThrows(IllegalArgumentException.class,()->reader.view(CdR32aDataLayout.Asset.ANI_SONIC));
  assertThrows(IllegalArgumentException.class,()->reader.javaInput(CdR32aLoadLayout.SourceId.STAGE_GFX));
  assertThrows(IllegalArgumentException.class,()->reader.decoded(CdR32aLoadLayout.SourceId.STAGE_GFX));
 }
 @Test void includeJavaAndNativeProfilesRetainAliasesAndRealNeighbourBytes()throws Exception {
  var a=TestCdR31aAssets.actual().assets();var reader=a.internalReader();var plan=CdR31aLoadPlan.fromAssets(a);
  for(var id:CdR31aLoadMetadata.SourceId.values()) {
   var fact=a.loadMetadata().source(id);var include=reader.canonicalInput(id);var java=reader.javaInput(id);var nativeBytes=reader.nativeInput(id);
   assertEquals(fact.includeBytes(),include.length());assertEquals(fact.javaReaderBytes(),java.length());
   assertSame(nativeBytes,plan.nativeView(id));
   if(include.length()==java.length())assertSame(include,java);
   if(nativeBytes.length()==include.length())assertSame(include,nativeBytes);
   else if(nativeBytes.length()==java.length())assertSame(java,nativeBytes);
  }
  assertEquals(532,reader.canonicalInput(CdR31aLoadMetadata.SourceId.BUMPER_GFX).length());
  assertEquals(533,reader.javaInput(CdR31aLoadMetadata.SourceId.BUMPER_GFX).length());
 }
 @Test void failedSourceReadPublishesNoInputAndCanBeRetried()throws Exception {
  var f=TestCdR31aAssets.actual();var image=spy(f.image());
  var p=TestCdR31aAssets.source(f,image);var reader=CdProgrammeReader.open(p,CdStagePayload.R31A.stage());
  var id=CdR31aLoadMetadata.SourceId.BUMPER_GFX;var fact=new CdR31aLoadMetadata.Source(id);
  byte[] good=f.image().readFile(fact.fileOffset(),fact.javaReaderBytes());byte[] bad=good.clone();bad[bad.length-1]^=1;
  doReturn(bad).when(image).readFile(fact.fileOffset(),fact.javaReaderBytes());
  assertThrows(IllegalArgumentException.class,()->reader.javaInput(id));
  doReturn(good).when(image).readFile(fact.fileOffset(),fact.javaReaderBytes());
  assertArrayEquals(good,reader.javaInput(id).copy());assertSame(reader.javaInput(id),reader.javaInput(id));
 }
 @Test void failedDecodeWrongCursorAndWrongOutputAreNotCached()throws Exception {
  var f=TestCdR31aAssets.actual();var reader=CdProgrammeReader.open(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R31A.stage());
  var id=CdR31aLoadMetadata.SourceId.STAGE_GFX;
  var real=TestCdR31aAssets.actual().assets().loadMetadata().decoded(id);
  try(var decoder=mockStatic(CdNemesisAsset.class)) {
   decoder.when(()->CdNemesisAsset.decode(any(byte[].class),eq(22208),eq(65536)))
    .thenThrow(new java.io.IOException("controlled decoder failure"))
    .thenReturn(new CdNemesisAsset.Decoded(real.bytes(),real.consumedInputBytes()+1))
    .thenReturn(new CdNemesisAsset.Decoded(CdAssetBytes.copyOf(new byte[22208]),real.consumedInputBytes())).thenReturn(real);
   assertThrows(java.io.IOException.class,()->reader.decoded(id));
   assertThrows(java.io.IOException.class,()->reader.decoded(id));
   assertThrows(java.io.IOException.class,()->reader.decoded(id));
   assertSame(real,reader.decoded(id));assertSame(real,reader.decoded(id));
   decoder.verify(()->CdNemesisAsset.decode(any(byte[].class),eq(22208),eq(65536)),times(4));
  }
 }
 @Test void r32DirectConsumerUsesOwnOriginalAndCommonParserWithNoNativeFallback()throws Exception {
  var f=TestCdR31aAssets.actual();var image=TestCdR32aDataLayout.actual().image();
  var p=TestCdR31aAssets.source(f,f.image());when(p.mmd(CdNativeFile.R32A)).thenReturn(image);
  var reader=CdProgrammeReader.open(p,CdStagePayload.R32A.stage());
  assertThrows(IllegalArgumentException.class,()->reader.view(CdR31aAssets.Asset.ANI_SONIC));
  for(var id:CdR32aDataLayout.Asset.values())assertEquals(id.sha256(),CdR11aProfile.sha256(reader.view(id).copy()));
  for(var id:CdR32aLoadLayout.SourceId.values()) {
   assertThrows(UnsupportedOperationException.class,()->reader.nativeInput(id));
   assertEquals(id.outputSha256(),CdR11aProfile.sha256(reader.decoded(id).bytes().copy()));
   assertSame(reader.decoded(id),reader.decoded(id));
  }
  var source=CdStagePlayerSource.fromProgramme(reader);
  assertEquals(0x101,source.ordinaryZoneActWord());assertEquals(1,source.ordinaryPeriod());
  assertEquals(CdStageTerrain.Shape.CCZ2_PRESENT,reader.terrain().shape());
  assertEquals(40,reader.terrain().layout().columns(CdStageTerrain.Plane.FOREGROUND));
  assertEquals(454,CdObjectPlacements.decodeLayout(reader.role(CdProgrammeReader.Role.PLACEMENTS).copy()).records().size());
  var player=CdR11aPlayerAssets.fromR3Programme(reader);
  assertEquals(80,reader.role(CdProgrammeReader.Role.MINI).length());assertEquals(60,player.miniAnimations().length());
  assertEquals(258,reader.role(CdProgrammeReader.Role.ATAN).length());assertEquals(257,player.arcTanTable().length());
  assertSame(reader.sonic(),player.sonic());assertEquals(167,player.sonic().mappingFrames().size());
  assertThrows(CdR11aPlayerAssets.UnownedPpzRamp.class,player::rampChunks);
  assertEquals(0x101,CdR32aDataLayout.readPlayerSource(p).ordinaryZoneActWord());
 }
 @Test void r32MutatedEndpointsAndForeignOriginalNeverAuthenticate()throws Exception {
  var f=TestCdR31aAssets.actual();var image=TestCdR32aDataLayout.actual().image();
  var p=TestCdR31aAssets.source(f,f.image());
  when(p.mmd(CdNativeFile.R32A)).thenReturn(f.image());
  assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(p,CdStagePayload.R32A.stage()));
  for(var id:CdR32aDataLayout.Asset.values()) {
   byte[] bad=image.readFile(0,image.fileBytes());bad[id.endExclusive()-1]^=1;
   when(p.mmd(CdNativeFile.R32A)).thenReturn(com.openggf.game.soniccd.media.CdMmdImage.parse(bad));
   assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(p,CdStagePayload.R32A.stage()),id.name());
  }
 }
 @Test void r32RetainsAllSixHighFrameScriptsAndReachedAnimationBoundary()throws Exception {
  var f=TestCdR31aAssets.actual();var p=TestCdR31aAssets.source(f,f.image());
  when(p.mmd(CdNativeFile.R32A)).thenReturn(TestCdR32aDataLayout.actual().image());
  var player=CdR11aPlayerAssets.fromR3Programme(CdProgrammeReader.open(p,CdStagePayload.R32A.stage()));
  var high=new ArrayList<Integer>();for(int id=0;id<60;id++){var bytes=player.animation(id).bytes();
   for(int at=1;at<bytes.length();at++){int b=bytes.unsignedByte(at);if(b>=253)break;if(b>=167){high.add(id);break;}}}
  assertEquals(List.of(15,51,52,53,56,57),high);
  var animation=new com.openggf.game.soniccd.player.CdPlayerAnimation(player);
  var selected=new com.openggf.game.soniccd.player.CdPlayerAnimation.State(15,2,0,0x62);
  var input=new com.openggf.game.soniccd.player.CdPlayerAnimation.Input(15,0,0,0,0xA4,0,0);
  assertThrows(UnsupportedOperationException.class,()->animation.step(selected,input));
 }
 @Test void r32StaticGeometryKeepsItsOwnDiagnosticWithoutRuntimeAdmission()throws Exception {
  var f=TestCdR31aAssets.actual();var p=TestCdR31aAssets.source(f,f.image());
  when(p.mmd(CdNativeFile.R32A)).thenReturn(TestCdR32aDataLayout.actual().image());
  var source=CdStagePlayerSource.fromProgramme(CdProgrammeReader.open(p,CdStagePayload.R32A.stage()));
  // Actual source terrain plus an explicit constructed palette; this is a static data facade, not a World/pixel claim.
  var geometry=new SonicCdLevelGeometry(source.terrain(),CdAssetBytes.copyOf(new byte[128]),0,0,0x2697,0,0x510);
  assertEquals("R32A static view cannot invent unloaded pattern/resident data",assertThrows(UnsupportedOperationException.class,()->geometry.ensurePatternCapacity(source.terrain().patternCount()+1)).getMessage());
  assertTrue(geometry.getObjects().isEmpty());assertTrue(geometry.getRings().isEmpty());
 }

}

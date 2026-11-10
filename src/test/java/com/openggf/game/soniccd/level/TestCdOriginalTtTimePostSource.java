package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE/tests-first, allJavaUNRUN. Actual original DATA relations, not a World or Monitor grant. */
class TestCdOriginalTtTimePostSource {
 private static CdProgrammeData programme,foreign,past;private static CdProgrammeObjectSource source;
 @BeforeAll static void actualOriginalReader()throws Exception{
  String root=System.getProperty("soniccd.media.root","");assertFalse(root.isBlank(),"Original USA media is required");var cue=Path.of(root,"USA","Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
  try(var actual=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){assertEquals(CdR4DataLayout.USA_DISC_SHA256,actual.identity().normalizedSha256());programme=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);foreign=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);past=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41B);}source=programme.objectSource();
 }
 @Test void completeOriginalMonitorAnimationAndMappingHaveAuthenticatedSeparateRoots(){
  var a=source.view(CdProgrammeObjectSource.Table.MONITOR_ANIMATIONS);assertEquals(0x209C56,a.nativeAddress());assertEquals(136,a.bytes().length());assertEquals("c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36",CdR11aProfile.sha256(a.bytes().copy()));
  var m=source.view(CdProgrammeObjectSource.Table.MONITOR_MAPPINGS);assertEquals(0x209CDE,m.nativeAddress());assertEquals(450,m.bytes().length());assertEquals("00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca",CdR11aProfile.sha256(m.bytes().copy()));
  int[] offsets={20,32,44,56,68,80,92,104,116,126};for(int n=0;n<offsets.length;n++)assertEquals(offsets[n],a.bytes().unsignedWord(n*2));assertEquals(0xA,a.bytes().unsignedByte(117));assertEquals(0xC,a.bytes().unsignedByte(127));
 }
 @Test void cachedTimePostAssetsUseTheSameTwentyFrameTenScriptReaderValues(){
  var a=source.timePostAssets();assertSame(a,source.timePostAssets());assertEquals(0x209CDE,a.mappingsAddress());assertEquals(20,a.visual().mappingFrames().size());assertEquals(10,a.visual().animations().size());assertArrayEquals(source.view(CdProgrammeObjectSource.Table.MONITOR_MAPPINGS).bytes().copy(),a.visual().mappings().copy());assertArrayEquals(source.view(CdProgrammeObjectSource.Table.MONITOR_ANIMATIONS).bytes().copy(),a.visual().animationTable().copy());assertEquals(5,a.visual().mappingFrames().get(10).pieces().size());
 }
 @Test void actualDispatchNineteenSelectsMonitorTimeWhileInitialFamilyInventoryIsUnchanged(){
  assertEquals(0x2098F2,source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes().unsignedLong((0x19-1)*4));assertFalse(source.isNullHandler(0x19));assertEquals(List.of(4,0x0B,0x10,0x19,0x1C,0x20,0x21,0x22,0x2B,0x2F,0x32,0x34,0x3C),source.initialFamilyIds());assertEquals(31,CdProgrammeObjectSource.Table.COLLAPSE_MAPPINGS.ordinal());assertTrue(CdProgrammeObjectSource.Table.MONITOR_ANIMATIONS.ordinal()>CdProgrammeObjectSource.Table.COLLAPSE_MAPPINGS.ordinal());
 }
 @Test void existingMapperResolvesAllTwentyActualFramesAndRejectsForeignRoots(){
  var mapper=source.mappings();assertSame(mapper,source.mappings());for(int n=0;n<20;n++)assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,mapper.resolve(source.timePostAssets().mappingsAddress(),false,n));assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x209CDE,false,20));assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x209CE0,false,0));
 }
 @Test void anotherEqualReaderCannotBorrowTheCurrentPlacementAndPastRemainsUnadmitted(){
  var placement=new CdObjectPlacementRuntime(programme.placements());source.requirePlacement(placement);assertNotSame(source.timePostAssets(),foreign.objectSource().timePostAssets());assertThrows(IllegalArgumentException.class,()->foreign.objectSource().requirePlacement(placement));assertThrows(UnsupportedOperationException.class,()->past.objectSource());
 }
}

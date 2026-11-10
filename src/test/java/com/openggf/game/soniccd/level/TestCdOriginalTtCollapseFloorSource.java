package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Authentic original DATA only; equal bytes grant no World/Frame invocation. */
class TestCdOriginalTtCollapseFloorSource {
 private static CdProgrammeData actual,other;private static CdProgrammeObjectSource source;
 @BeforeAll static void source()throws Exception {
  var root=System.getProperty("soniccd.media.root","");var cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual USA source");
  try(var disc=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
   assertEquals(CdR4DataLayout.USA_DISC_SHA256,disc.identity().normalizedSha256());actual=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A);other=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A);
  }source=actual.objectSource();
 }
 @Test void dispatchThirtyNineAndMappingOperandBelongToActualOriginalR41A(){
  assertEquals(0x20F238,source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes().unsignedLong((0x39-1)*4));assertFalse(source.isNullHandler(0x39));
  var mapping=source.view(CdProgrammeObjectSource.Table.COLLAPSE_MAPPINGS);assertEquals(0x20F3F2,mapping.nativeAddress());assertEquals(392,mapping.bytes().length());assertEquals("f8883a2759b1b3d6c8ceaa311e96e488c1efcaa9451d1982cdac1c113d2bc6ac",CdR11aProfile.sha256(mapping.bytes().copy()));
 }
 @Test void everyNativeFrameOffsetAndPieceCountIsRetained(){
  var bytes=source.view(CdProgrammeObjectSource.Table.COLLAPSE_MAPPINGS).bytes();int[] offsets={18,29,50,81,122,173,234,305,386};int[] pieces={2,4,6,8,10,12,14,16,1};
  for(int frame=0;frame<9;frame++){assertEquals(offsets[frame],bytes.unsignedWord(frame*2));assertEquals(pieces[frame],bytes.unsignedByte(offsets[frame]));assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(0x20F3F2,false,frame));}
  assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(0x20F3F2,false,9));
 }
 @Test void finiteCollapseDataIsCachedAndSuppliesNoInventedSizeOffsetOrOtherFamilyOperand(){
  var data=source.collapseFloorData();assertSame(data,source.collapseFloorData());assertEquals(0x20F3F2,data.mapping(CdR4SolidMover.Mapping.COLLAPSE));
  assertThrows(UnsupportedOperationException.class,()->data.mapping(CdR4SolidMover.Mapping.BLOCK));assertThrows(UnsupportedOperationException.class,()->data.table(CdR4SolidMover.Table.BLOCK_SIZES));assertThrows(UnsupportedOperationException.class,()->data.address(CdR4SolidMover.Address.SAVED_FLAGS));
 }
 @Test void equalForeignReaderDoesNotAcquireThisPlacementOrCachedDataIdentity(){
  var placement=new CdObjectPlacementRuntime(actual.placements());source.requirePlacement(placement);assertThrows(IllegalArgumentException.class,()->other.objectSource().requirePlacement(placement));assertNotSame(source.collapseFloorData(),other.objectSource().collapseFloorData());
 }
}

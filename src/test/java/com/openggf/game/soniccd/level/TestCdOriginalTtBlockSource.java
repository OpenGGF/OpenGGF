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
/** Actual source data only. No World, Frame, native clear or gameplay grant. */
class TestCdOriginalTtBlockSource {
 private static CdProgrammeData actual,other;private static CdProgrammeObjectSource source;
 @BeforeAll static void source()throws Exception {
  var root=System.getProperty("soniccd.media.root","");var cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual USA source");
  try(var disc=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
   assertEquals(CdR4DataLayout.USA_DISC_SHA256,disc.identity().normalizedSha256());actual=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A);other=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A);
  }source=actual.objectSource();
 }
 @Test void actualDispatchThirtyFourIsHexadecimalBlockWithItsOwnOriginalOperand() {
  assertEquals(0x20ED62,source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes().unsignedLong((0x34-1)*4));
  assertFalse(source.isNullHandler(0x34));assertTrue(source.initialFamilyIds().contains(0x34));
 }
 @Test void blockSizesAndLogicalMappingRetainExactSourceBytesAndOddTail() {
  var sizes=source.view(CdProgrammeObjectSource.Table.BLOCK_SIZES);assertEquals(0x20EDBC,sizes.nativeAddress());assertArrayEquals(new byte[]{16,16,16,16,32,16,16,32,32,32,64,16,16,64},sizes.bytes().copy());
  var map=source.view(CdProgrammeObjectSource.Table.BLOCK_MAPPINGS);assertEquals(0x20EDCA,map.nativeAddress());assertEquals(111,map.bytes().length());assertEquals("908a5ceb031420fe4c0ac0dc6fdfbdab8f2fa40de546da7b6fc710fe4b616fd3",CdR11aProfile.sha256(map.bytes().copy()));
 }
 @Test void blockDataCachesOnlyActualFiniteBytesAndRejectsOtherFamilyOperands() {
  var data=source.blockData();assertSame(data,source.blockData());assertEquals(0x20EDCA,data.mapping(CdR4SolidMover.Mapping.BLOCK));assertSame(source.view(CdProgrammeObjectSource.Table.BLOCK_SIZES).bytes(),data.table(CdR4SolidMover.Table.BLOCK_SIZES));
  assertThrows(UnsupportedOperationException.class,()->data.mapping(CdR4SolidMover.Mapping.WOOD));assertThrows(UnsupportedOperationException.class,()->data.table(CdR4SolidMover.Table.TWIN_MOTION));assertThrows(UnsupportedOperationException.class,()->data.address(CdR4SolidMover.Address.SAVED_FLAGS));
 }
 @Test void resolverAdmitsExactlySevenBlockFramesWithoutPpzSubstitution() {
  for(int n=0;n<7;n++)assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(0x20EDCA,false,n));
  assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(0x20EDCA,false,7));assertSame(source.mappings(),source.mappings());
 }
 @Test void equalOtherReaderCannotBorrowThisSourcePlacementOrDataIdentity() {
  var p=new CdObjectPlacementRuntime(actual.placements());source.requirePlacement(p);assertThrows(IllegalArgumentException.class,()->other.objectSource().requirePlacement(p));
  assertNotSame(source.blockData(),other.objectSource().blockData());
 }
}

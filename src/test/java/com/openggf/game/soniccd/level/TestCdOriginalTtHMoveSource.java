package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Source/data checks only; component fixtures do not claim original gameplay. */
class TestCdOriginalTtHMoveSource {
 private static CdProgrammeData actual,other;private static CdProgrammeObjectSource source;
 @BeforeAll static void source()throws Exception {var root=System.getProperty("soniccd.media.root","");var cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual USA source");try(var disc=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){assertEquals(CdR4DataLayout.USA_DISC_SHA256,disc.identity().normalizedSha256());actual=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A);other=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A);}source=actual.objectSource();}
 @Test void actualDispatchAndPresentPlacementBindHMoveInsteadOfAbsentWoodWall(){assertEquals(0x20C1EA,source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes().unsignedLong((0x24-1)*4));var row=actual.placements().records().get(79);assertEquals(0x24,row.objectId());assertEquals(1552,row.centreX());assertEquals(528,row.centreY());assertEquals(1,row.subtype());assertEquals(0x20,row.periodFlags());}
 @Test void allSixDistinctMappingRootsRetainTheirOriginalBytesAndFrameCounts(){
  check(CdProgrammeObjectSource.Table.HMOVE_MAPPING_1,0x20C376,42,"c613d2f99028b5732d7b41c779bf027e3406d79cdb94484bfc2154826165533a",2);
  check(CdProgrammeObjectSource.Table.HMOVE_MAPPING_2,0x20C472,13,"6deba6caaa5273dfe76821a0d2ae697fdaa673bbee28632e0469373a9fa580b1",1);
  check(CdProgrammeObjectSource.Table.HMOVE_MAPPING_3,0x20C5D0,23,"2486593b2c8e2f314977a38c241d757fbb43998f05ad52b33b49c4d36407968e",1);
  check(CdProgrammeObjectSource.Table.HMOVE_MAPPING_4,0x20C6CC,3,"dbbc7025e89eb9b1ec9e8e2c7a2db6869dbb50fba21bf374c86529dc311cede9",1);
  check(CdProgrammeObjectSource.Table.HMOVE_MAPPING_5,0x20C7EC,23,"2486593b2c8e2f314977a38c241d757fbb43998f05ad52b33b49c4d36407968e",1);
  check(CdProgrammeObjectSource.Table.HMOVE_MAPPING_6,0x20C90E,63,"2a3f5226c346a89f4e85a006cc31e2a6482cd31be4450dcd3dea93783fa12d29",4);
 }
 @Test void hMoveDataIsCachedAndDoesNotGrantUnrelatedMappingOrAddressOperands(){var d=source.hMoveData();assertSame(d,source.hMoveData());assertEquals(0x20C376,d.mapping(CdR4SolidMover.Mapping.H1));assertEquals(0x20C7EC,d.mapping(CdR4SolidMover.Mapping.H5));assertThrows(UnsupportedOperationException.class,()->d.mapping(CdR4SolidMover.Mapping.WOOD));assertThrows(UnsupportedOperationException.class,()->d.table(CdR4SolidMover.Table.BLOCK_SIZES));assertThrows(UnsupportedOperationException.class,()->d.address(CdR4SolidMover.Address.SAVED_FLAGS));}
 @Test void finiteNativeTablesAndFailedAllocatorSpanAreAuthenticatedWithoutPadding(){
  var vHMOVE_H3_OFFSETS=source.view(CdProgrammeObjectSource.Table.HMOVE_H3_OFFSETS);assertEquals(36,vHMOVE_H3_OFFSETS.bytes().length());assertEquals("6312f63248697ff77ff39a90c6d85c865bd29857ccdc6ff0b67431a176f92884",CdR11aProfile.sha256(vHMOVE_H3_OFFSETS.bytes().copy()));
  var vHMOVE_H5_START=source.view(CdProgrammeObjectSource.Table.HMOVE_H5_START);assertEquals(14,vHMOVE_H5_START.bytes().length());assertEquals("9a30f5e3cdcd64c005d3cc5610f8d039f6c8a00078950b8d6e43b6aecb355e24",CdR11aProfile.sha256(vHMOVE_H5_START.bytes().copy()));
  var vHMOVE_H5_MOTION=source.view(CdProgrammeObjectSource.Table.HMOVE_H5_MOTION);assertEquals(24,vHMOVE_H5_MOTION.bytes().length());assertEquals("b0bd1cca88767bdaa0776b476746be3f1ddab437e629da3f225b56c9e3be1f9d",CdR11aProfile.sha256(vHMOVE_H5_MOTION.bytes().copy()));
  var vHMOVE_H3_FAILED_OFFSETS=source.view(CdProgrammeObjectSource.Table.HMOVE_H3_FAILED_OFFSETS);assertEquals(6,vHMOVE_H3_FAILED_OFFSETS.bytes().length());assertEquals("39301389039035eadf6c7a79f3c364be18b0d3c9611f6a76f4a47d72f2883110",CdR11aProfile.sha256(vHMOVE_H3_FAILED_OFFSETS.bytes().copy()));
  assertThrows(IndexOutOfBoundsException.class,()->source.hMoveData().table(CdR4SolidMover.Table.H5_START).unsignedByte(14));
 }
 @Test void equalForeignReaderCannotSupplyThisPlacementOrCachedFamilyData(){var p=new CdObjectPlacementRuntime(actual.placements());source.requirePlacement(p);assertThrows(IllegalArgumentException.class,()->other.objectSource().requirePlacement(p));assertNotSame(source.hMoveData(),other.objectSource().hMoveData());}
 private static void check(CdProgrammeObjectSource.Table t,int address,int size,String sha,int frames){var v=source.view(t);assertEquals(address,v.nativeAddress());assertEquals(size,v.bytes().length());assertEquals(sha,CdR11aProfile.sha256(v.bytes().copy()));for(int n=0;n<frames;n++)assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(address,false,n));assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(address,false,frames));}
}

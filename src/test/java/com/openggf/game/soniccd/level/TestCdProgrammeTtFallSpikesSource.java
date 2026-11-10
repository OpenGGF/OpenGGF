package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Written before the family binding. Actual original DATA; no World authority from these values. */
class TestCdProgrammeTtFallSpikesSource {
 private static CdProgrammeData programme;private static CdProgrammeObjectSource source;private static CdMmdImage image;private static Path cue;
 @BeforeAll static void actual()throws Exception {
  String root=System.getProperty("soniccd.media.root","");cue=Path.of(root,"USA","Sonic CD (US).cue");
  assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual original USA source");
  try(var actual=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
   assertEquals(CdR4DataLayout.USA_DISC_SHA256,actual.identity().normalizedSha256());
   programme=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);image=actual.mmd(CdNativeFile.R41A);
  }source=programme.objectSource();
 }
 @Test void literalHex32SelectsActualFallSpikesAndNeverDecimal32BubbleSpawner() {
  var dispatch=source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes();
  assertEquals(0x20E928L,dispatch.unsignedLong((0x32-1)*4));
  assertNotEquals(dispatch.unsignedLong((32-1)*4),dispatch.unsignedLong((0x32-1)*4));
  assertEquals(0x20EB08,source.ecologyPointer(CdR4ObjectPorts.Data.FALL_SPIKES_MAP));
 }
 @Test void mappingAndReachedParentOperandBorrowExactOriginalBytesAndCache() {
  for(var table:List.of(CdProgrammeObjectSource.Table.FALL_SPIKES_MAPPINGS,CdProgrammeObjectSource.Table.FALL_SPIKES_PARENT_MAP_STORE)) {
   var view=source.view(table);assertSame(view,source.view(table));
   assertArrayEquals(image.readFile(table.fileOffset(),table.length()),view.bytes().copy());
   assertEquals(table.sha256(),CdR11aProfile.sha256(view.bytes().copy()));
  }
  var store=source.view(CdProgrammeObjectSource.Table.FALL_SPIKES_PARENT_MAP_STORE).bytes();
  assertEquals(0x217C,store.unsignedWord(0));assertEquals(0x20EB08L,store.unsignedLong(2));assertEquals(4,store.unsignedWord(6));
 }
 @Test void bothMappingFramesResolveIncludingNativeEmptyChildAndNoThirdFrame() {
  var mappings=source.mappings();assertSame(mappings,source.mappings());
  for(int frame=0;frame<2;frame++)assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,mappings.resolve(0x20EB08,false,frame));
  assertThrows(UnsupportedOperationException.class,()->mappings.resolve(0x20EB08,false,2));
  assertEquals(0,source.view(CdProgrammeObjectSource.Table.FALL_SPIKES_MAPPINGS).bytes().unsignedByte(25));
  assertThrows(UnsupportedOperationException.class,()->source.ecologyAnimation(CdR4ObjectPorts.Data.FALL_SPIKES_MAP));
 }
 @Test void originalPlacementOrderAndFiveFallSpikesRecordsAreAuthenticated() {
  var found=programme.placements().records().stream().filter(p->p.objectId()==0x32).toList();
  assertEquals(List.of(29,164,167,238,246),found.stream().map(CdObjectPlacements.Placement::index).toList());
  assertEquals(List.of(0x340,0xB70,0xBD0,0x1050,0x10B0),found.stream().map(CdObjectPlacements.Placement::centreX).toList());
  assertEquals(List.of(0x510,0x60,0x60,0x500,0x500),found.stream().map(CdObjectPlacements.Placement::packedY).toList());
  for(var row:found){assertEquals(0,row.subtype());assertEquals(0,row.subtype2());assertEquals(0x40,row.periodFlags());}
 }
 @Test void equalIndependentReaderCannotAuthenticateForeignPlacement()throws Exception {
  var original=new CdObjectPlacementRuntime(programme.placements());assertDoesNotThrow(()->source.requirePlacement(original));
  try(var actual=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
   var other=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);
   assertThrows(IllegalArgumentException.class,()->source.requirePlacement(new CdObjectPlacementRuntime(other.placements())));
  }
 }
 @Test void oldTableOrdinalsRemainAnExactPrefixWithOnlyTwoAppendedFacts() {
  // Historical61 = original59 plus two FallSpikes facts; the five water facts follow it.
  assertEquals(73,CdProgrammeObjectSource.Table.values().length);
  assertEquals(List.of("RING_MAPPINGS","RING_ANIMATIONS","WATER_MAPPINGS","WATER_ANIMATIONS","TITLE_MAPPINGS","HUD_MAPPINGS","MONITOR_MAPPINGS","TITLE_CHARACTERS","RING_DELTAS","HUD_RESET","HUD_DIVISORS","WATER_OFFSETS","DISPATCH","BUBBLE_MAPPINGS","BUBBLE_ANIMATIONS","BUBBLE_PATTERNS","BUBBLE_PATTERN_READS","NULL_HANDLER","HUD_RENDER_CODE","TOUCH_DIMENSIONS","ENEMY_POINTS","BLOCK_SIZES","BLOCK_MAPPINGS","SPIKES_MAPPINGS","TONBO_MAPPINGS1","TONBO_MAPPINGS2","TONBO_ANIMATION","ANIMAL_MAPPINGS1","ANIMAL_MAPPINGS2","ANIMAL_ANIMATION","ANIMAL_TILES","COLLAPSE_MAPPINGS","HMOVE_MAPPING_1","HMOVE_MAPPING_2","HMOVE_MAPPING_3","HMOVE_MAPPING_4","HMOVE_MAPPING_5","HMOVE_MAPPING_6","HMOVE_H3_OFFSETS","HMOVE_H5_START","HMOVE_H5_MOTION","HMOVE_H3_FAILED_OFFSETS","MONITOR_ANIMATIONS","SPRING_MAPPINGS","SPRING_ANIMATIONS","SPRING_SPEEDS","MOVING_SPRING_MAPPINGS","MOVING_SPRING_ANIMATIONS","SPRING_TILE_HELPER","POWERUP_ANIMATIONS","POWERUP_MAPPINGS","POWERUP_ART_INDEX","POWERUP_COPY_0","POWERUP_COPY_1","POWERUP_COPY_2","POWERUP_COPY_3","POWERUP_COPY_4","EXPLOSION_ANIMATIONS","EXPLOSION_MAPPINGS","FALL_SPIKES_MAPPINGS","FALL_SPIKES_PARENT_MAP_STORE"),Arrays.stream(CdProgrammeObjectSource.Table.values()).limit(61).map(Enum::name).toList());
  assertEquals(59,CdProgrammeObjectSource.Table.FALL_SPIKES_MAPPINGS.ordinal());
  assertEquals(60,CdProgrammeObjectSource.Table.FALL_SPIKES_PARENT_MAP_STORE.ordinal());
  assertEquals(58,CdProgrammeObjectSource.Table.EXPLOSION_MAPPINGS.ordinal());
 }
}

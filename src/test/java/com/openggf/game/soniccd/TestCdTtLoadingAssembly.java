package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.resources.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual retained USA DATA controls. These are not World, Ready or deferred-service positives. */
class TestCdTtLoadingAssembly {
 private CdLevelLoadingSource source() throws Exception {
  return TestCdOriginalBootPrefix.source().programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
 }
 @Test void loadingFacadeBorrowsOneReaderAndRejectsForeignProgramme() throws Exception {
  var a=source();var b=CdLevelLoadingSource.fromOriginalR11(TestCdOriginalBootPrefix.source().assets());var player=a.playerSource();
  a.requireRetained();assertSame(player,a.retainedPlayerSource());assertTrue(a.ownsPlayerSource(player));
  assertFalse(b.ownsPlayerSource(player));assertThrows(IllegalArgumentException.class,()->b.requirePlayerSource(player));
  assertEquals(0x200,a.nativeZoneActWord());assertEquals(CdLevelLoadingSource.Completion.WATER_PALETTE_AFTER_FADE,a.completion());
 }
 @Test void returnedWaterPaletteUsesActualPaletteZeroLongCounterAndDestination() throws Exception {
  var a=source();var pal=a.loadTables().palettes().getFirst();var water=a.waterPalette(0);
  assertSame(pal.bytes(),water.bytes());assertEquals(32,water.longCount());assertEquals(0,water.byteOffset());
  assertEquals(128,water.bytes().length());assertArrayEquals(a.playerPalette().copy(),a.loadTables().palettes().get(3).bytes().copy());
 }
 @Test void stageTileWriterPublishesOnlyReachedSourceWindowAndPreservesOtherKnowledge() throws Exception {
  var a=source();var art=CdR11aSectionArt.fromLoadingSource(a);
  // Explicit component values, not a native clear/World or ready certificate.
  var state=art.fromInactiveValues(CdR11aCameraArtResidency.unknownForLoading(a),CdAssetBytes.copyOf(new byte[512]),
   0,CdR11aPlcBuffer.cleared(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
  byte[] raw=new byte[32];Arrays.fill(raw,(byte)0xA5);var tile=CdAssetBytes.copyOf(raw);
  var after=art.writeCurrentStageAnimationTile(state,0x4500,tile);
  assertNotSame(state,after);assertSame(state.queue(),after.queue());assertSame(state.codeTable(),after.codeTable());
  assertArrayEquals(raw,CdR11aCameraArtResidency.readTile(after.residency(),0x4500/32).copy());
  assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(state.residency(),0x4500/32));
  assertThrows(IllegalArgumentException.class,()->art.writeCurrentStageAnimationTile(state,0x44E0,tile));
  assertThrows(IllegalArgumentException.class,()->art.writeCurrentStageAnimationTile(state,0x4B00,tile));
  assertThrows(IllegalArgumentException.class,()->art.writeCurrentStageAnimationTile(state,0x4501,tile));
  art.validateState(after);assertTrue(after.residency().ownsLoadingSource(a));
 }
 @Test void placementsPreserveEveryCarriedByteAndRejectShortBanks() throws Exception {
  var a=source();byte[] dirty=new byte[0x2FE];for(int i=0;i<dirty.length;i++)dirty[i]=(byte)(i*17+3);
  var bank=CdAssetBytes.copyOf(dirty);var placement=CdObjectPlacementRuntime.fromPlacements(a.placements(),bank);
  assertSame(a.placements(),placement.placements());assertArrayEquals(dirty,placement.capture().savedFlags().copy());
  assertThrows(IllegalArgumentException.class,()->CdObjectPlacementRuntime.fromPlacements(a.placements(),bank.slice(0,bank.length()-1)));
 }
 @Test void honestPlaneBindingAndAnimationBorrowStayOnSelectedOriginal() throws Exception {
  var a=source();assertNotNull(a.backgroundPlane());assertEquals(130,a.backgroundSectionIds().length());
  assertEquals(0x510,a.initialEventTargetBottomWord());assertEquals(0x7F7F7F7F,a.loopChunksLong());
  for(int i=0;i<3;i++){long address=a.stageAnimationScript(i);assertEquals(24,a.stageAnimationByte(address));}
  assertThrows(IndexOutOfBoundsException.class,()->a.stageAnimationScript(3));
  assertThrows(IllegalArgumentException.class,()->a.stageAnimationLong(0x200000));
 }
}

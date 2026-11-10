package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Tests FIRST, SOURCE/UNRUN. Actual supplied original assets; component values confer no World authority. */
class TestCdOriginalR11LoadingArt {
 @Test void fullProfileStartsUnknownAndLegacyProfileKeepsItsBoundary()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var inputs=a.loadingArtInputs();var full=CdR11aCameraArtResidency.unknownForLoading(inputs);
  assertTrue(full.ownsLoadingAssets(a));assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(full,0));
  var legacy=CdR11aCameraArtResidency.unknown();var stop=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(legacy,0));assertEquals(CdR11aCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,stop.boundary());
  var cleared=CdR11aCameraArtResidency.clear(full);assertArrayEquals(new byte[32],CdR11aCameraArtResidency.readTile(cleared,0).copy());assertTrue(cleared.ownsLoadingAssets(a));
 }
 @Test void levelThreeThenStandardOneReallyDecodeIntoOneCurrentBank()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var art=CdR11aSectionArt.fromAssetsForLoading(a);var before=entry(a,art);
  var level=art.loadImmediateList(before,3);assertArrayEquals(a.decoded(CdR11aAsset.ART_LEVEL_TILES).slice(0,32).copy(),CdR11aCameraArtResidency.readTile(level.residency(),0).copy());
  var standard=art.loadImmediateList(level,1);assertTrue(standard.residency().ownsLoadingAssets(a));
  for(int address:new int[]{0x6400,0x6500,0x6680,0x6C00,0x7A00,0xA400,0xAD00,0xB500,0xD000,0xD8C0,0xDAE0,0xF5C0})assertEquals(32,CdR11aCameraArtResidency.readTile(standard.residency(),address>>>5).length());
  assertEquals(before.queue(),standard.queue());assertEquals(0,standard.remainingTilesWord());assertEquals(before.residency(),entry(a,art).residency());
 }
 @Test void blocksAreRamOnlyAndLifeUsesTheActualRawPeriodSlice()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var art=CdR11aSectionArt.fromAssetsForLoading(a);var before=entry(a,art);var blocks=art.decodeBlocksToRam(before);
  assertArrayEquals(a.decoded(CdR11aAsset.LEVEL_BLOCKS).copy(),blocks.blocks().copy());assertSame(before.residency(),blocks.state().residency());
  var life=art.loadLifeIcon(blocks.state(),1);assertArrayEquals(a.view(CdR11aAsset.ART_LIFE_ICON).bytes().slice(128,32).copy(),CdR11aCameraArtResidency.readTile(life.residency(),0xB420>>>5).copy());
  assertArrayEquals(blocks.state().codeTable().copy(),life.codeTable().copy());
 }
 @Test void failedLifeReadRetainsTerminalOriginalStateAndCannotBeReopened()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var art=CdR11aSectionArt.fromAssetsForLoading(a);var before=entry(a,art);
  var stop=assertThrows(CdR11aSectionArt.SectionArtException.class,()->art.loadLifeIcon(before,3));assertEquals(CdR11aSectionArt.Operation.LIFE_ICON,stop.operation());assertTrue(stop.state().stopped());assertSame(before.residency(),stop.state().residency());assertThrows(IllegalStateException.class,()->art.loadImmediateList(stop.state(),1));
 }
 @Test void actualBlockRowsAreKnownButComponentAndForeignOwnersCannotSupplyOriginalHistory()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var art=CdR11aSectionArt.fromAssetsForLoading(a);
  var before=entry(a,art);var result=art.decodeBlocksToRam(before);var knowledge=result.blockDefinitions();
  for(int i=0;i<result.blocks().length();i++)assertEquals(result.blocks().unsignedByte(i),knowledge.unsignedByte(i));
  assertThrows(CdBlockDefinitionStaging.UnknownRead.class,()->knowledge.unsignedByte(result.blocks().length()));
  var foreign=CdR11aSectionArt.fromAssetsForLoading(a);
  assertThrows(IllegalArgumentException.class,()->foreign.decodeBlocksToRam(before));
  assertSame(before.residency(),result.state().residency());
  assertArrayEquals(a.decoded(CdR11aAsset.LEVEL_BLOCKS).copy(),result.blocks().copy());
 }
 static CdR11aSectionArt.State entry(CdR11aAssets a,CdR11aSectionArt art)throws Exception{
  var current=CdR11aCameraArtResidency.clear(CdR11aCameraArtResidency.unknownForLoading(a.loadingArtInputs()));
  return art.fromInactiveValues(current,CdAssetBytes.copyOf(new byte[512]),0,CdR11aPlcBuffer.cleared(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.CLEARED,0,0,0,0,0),0,0,0,0);
 }
}

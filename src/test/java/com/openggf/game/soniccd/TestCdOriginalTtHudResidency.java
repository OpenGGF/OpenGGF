package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
/** Constructed UNKNOWN pattern values on actual source geometry; no clear/Frame/runtime admission. */
class TestCdOriginalTtHudResidency {
 @Test void actualFontSourceExtendsOnlyOwnedGeometryAndNeverInventsInitialHudPixels()throws Exception{
  var actual=TestCdOriginalBootPrefix.source().programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
  assertSame(actual.objectSource(),actual.ordinaryObjectSource().orElseThrow());
  var unknown=CdR11aCameraArtResidency.unknownForLoading(actual);
  for(int dest:new int[]{0xB060,0xB220,0xB260,0xB2E0,0xB360,0xB400,0xB4A0,0xB4C0}){
   var fault=assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(unknown,dest>>>5));
   assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,fault.boundary());
  }
 }
 @Test void oneExplicitHudTileCommitKeepsAdjacentFontUnknownAndInitialValueUnchanged()throws Exception{
  var actual=TestCdOriginalBootPrefix.source().programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
  var before=CdR11aCameraArtResidency.unknownForLoading(actual);byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)0x61);
  var after=CdR11aCameraArtResidency.writeTile(before,0xB4A0,CdAssetBytes.copyOf(bytes));
  assertArrayEquals(bytes,CdR11aCameraArtResidency.readTile(after,0xB4A0>>>5).copy());
  for(var value:new CdR11aCameraArtResidency.State[]{before,after})assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,
   assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(value,0xB4C0>>>5)).boundary());
  assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class,()->CdR11aCameraArtResidency.readTile(before,0xB4A0>>>5)).boundary());
 }
}

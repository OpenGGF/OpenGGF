package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests authored first; actual reader DATA and constructed component adversaries only. All Java UNRUN. */
class TestCdOriginalPastStartupData {
 private static CdLevelLoadingSource source()throws Exception {try(var media=TestCdCczProgrammeData.open()){return CdProgrammeReader.open(media,CdStagePayload.R11B.stage()).levelLoadingSource();}}
 private static CdObjectPlacementRuntime placed(CdLevelLoadingSource s){return CdObjectPlacementRuntime.fromPlacements(s.placements(),CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));}
 @Test void fourAppendedRolesKeepEveryOldOrdinalOffsetWidthAndHash()throws Exception {
  var all=CdR11bObjectData.Table.values();assertEquals(98,all.length);var text=new StringBuilder();
  for(int i=0;i<94;i++){var r=all[i];assertEquals(i,r.ordinal());text.append(r.name()).append(':').append(r.fileOffset()).append(':').append(r.bytes()).append(':').append(r.sha256()).append('\n');}
  assertEquals("2fccb94dd97dd4e9085c36b838971b39f87dd526e4d6e1056405191a75464ae6",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))));
  assertEquals(List.of("TITLE_CHARACTERS","HUD_NUMBERS","HUD_RESET","HUD_DIVISORS"),Arrays.stream(all).skip(94).map(Enum::name).toList());
 }
 @Test void startupDataBytesAreBorrowedFromOneAuthenticPastFacade()throws Exception {
  var s=source();var d=s.standardPastObjectSource();assertSame(d,s.standardPastObjectSource());
  for(var role:List.of(CdR11bObjectData.Table.TITLE_CHARACTERS,CdR11bObjectData.Table.HUD_NUMBERS,CdR11bObjectData.Table.HUD_RESET,CdR11bObjectData.Table.HUD_DIVISORS)){
   var v=d.view(role);assertSame(v,d.view(role));assertEquals(role.sha256(),CdR11aProfile.sha256(v.bytes().copy()));assertEquals(role.bytes(),v.bytes().length());
  }
  assertEquals(0x20E4EE,d.view(CdR11bObjectData.Table.TITLE_CHARACTERS).nativeAddress());assertEquals(64,d.view(CdR11bObjectData.Table.TITLE_CHARACTERS).bytes().length());
  assertEquals(0x236456,d.view(CdR11bObjectData.Table.HUD_NUMBERS).nativeAddress());assertEquals(1024,d.font().numbers().length());
 }
 @Test void cachedCommonFamilyDataRetainsActualPastMappingRoots()throws Exception {
  var d=source().standardPastObjectSource();assertSame(d.ringData(),d.ringData());assertSame(d.font(),d.font());assertSame(d.titleAssets(),d.titleAssets());assertSame(d.hudAssets(),d.hudAssets());assertSame(d.touchScanner(),d.touchScanner());assertSame(d.touchResponse(),d.touchResponse());
  assertEquals(0x209F52,d.ringData().mappingAddress());assertEquals(0xA7AE,d.ringData().baseTileWord());assertEquals(16,d.ringData().deltas().size());
  assertEquals(0x20E52E,d.titleAssets().mappingsAddress());assertEquals(60,CdR11bObjectData.Mapping.values().length);assertEquals(34,CdR11bObjectData.Animation.values().length);
 }
 @Test void sourcePlacementRejectsEqualForeignReaderBeforeAnyMutableStore()throws Exception {
  var a=source();var b=source();var actual=placed(a);var foreign=placed(b);var before=foreign.capture();a.standardPastObjectSource().requirePlacement(actual);
  assertThrows(IllegalArgumentException.class,()->a.standardPastObjectSource().requirePlacement(foreign));assertThrows(IllegalArgumentException.class,()->CdRing.fromStandardPastSource(foreign,a.standardPastObjectSource()));
  assertEquals(before.status(),foreign.capture().status());assertEquals(before.pool().slots(),foreign.capture().pool().slots());assertArrayEquals(before.savedFlags().copy(),foreign.capture().savedFlags().copy());
 }
 @Test void authenticatedGenericPastPlacementCannotUseTheRawRingConstructor()throws Exception {
  var s=source();var p=placed(s);assertThrows(IllegalArgumentException.class,()->new CdRing(p,s.standardPastObjectSource().ringData()));assertNotNull(CdRing.fromStandardPastSource(p,s.standardPastObjectSource()));
 }
 @Test void pastBoundRingRejectsPresentOrWrongZoneInputsBeforeCallbacksOrPoolWrites()throws Exception {
  var s=source();var p=placed(s);var ring=CdRing.fromStandardPastSource(p,s.standardPastObjectSource());var before=p.capture();
  var calls=new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound x){fail("unexpected sound");}public void subCpuCommand(CdRing.SubCpuCommand x){fail("unexpected sub CPU");}public void drawObject(CdObjectPool x,int w){fail("unexpected Draw");}};
  var globals=new CdRing.GlobalsPort(){public CdRingGlobals read(){throw new AssertionError("unexpected global read");}public void write(CdRingGlobals x){fail("unexpected global store");}};
  assertThrows(IllegalArgumentException.class,()->ring.executeSelected(32,new CdRing.Inputs(CdReleaseVariant.USA,CdStagePayload.R11A.stage(),0,0,1,0),globals,calls));
  assertThrows(RuntimeException.class,()->ring.executeSelected(32,new CdRing.Inputs(CdReleaseVariant.USA,CdStagePayload.R11B.stage(),1,0,0,0),globals,calls));
  assertEquals(before.pool().slots(),p.capture().pool().slots());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());
 }
 @Test void sourceFactoryWidthNullAndUnownedDispatchControlsKeepDataInert()throws Exception {
  var d=source().standardPastObjectSource();assertThrows(NullPointerException.class,()->d.requirePlacement(null));assertThrows(NullPointerException.class,()->CdRing.fromStandardPastSource(null,d));
  assertThrows(IllegalArgumentException.class,()->d.dispatch(256));assertThrows(CdR11bObjectData.UnownedDispatchRead.class,()->d.dispatch(0x80));assertTrue(d.dispatch(0).isEmpty());
  assertEquals(13,d.families().stream().filter(CdR11bObjectData.Family::nativeNull).count());
 }
}

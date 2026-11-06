package com.openggf.game.soniccd.resources;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Source-backed generic HUD adapter; explicit component inputs, all Java UNRUN. */
class TestCdR13NativeHud {
 @Test void actualCAndDWriteOriginalGlyphsThroughTheSharedHudCursor()throws Exception{
  for(var a:TestCdR13PlaneSources.actual()){
   var art=CdR13SectionArt.fromAssets(a);var s=state(a,art);var before=s.section();
   var result=new CdLevelHud.Act3Future(CdR13ObjectAssets.fromAssets(a).hudAssets(),art).run(s,inputs());
   assertTrue(result.calls().contains(CdLevelHud.NativeCall.RESET_SCORE));assertTrue(result.calls().contains(CdLevelHud.NativeCall.DRAW_LIVES));
   assertEquals(0,result.state().score().updateHudScore());assertEquals(0,result.state().ring().updateHudRings());
   assertEquals(1,CdCheckpointLiveAux.read(result.state().liveTime(),CdCheckpointLiveAux.Field.TIME_LONG));
   assertSame(before.queue(),result.state().section().queue());assertSame(before.codeTable(),result.state().section().codeTable());assertEquals(before.savedDecoder(),result.state().section().savedDecoder());
   assertTrue(CdR13ArtResidency.readTile(result.state().section().residency(),0xB060/32).length()==32);
   assertThrows(CdR13ArtResidency.OwnershipBoundary.class,()->CdR13ArtResidency.readTile(before.residency(),0xB060/32));
  }
 }
 @Test void actualUnknownTimeRetainsCompletedNativeLivesScoreAndRingsPrefix()throws Exception{
  var a=TestCdR13PlaneSources.actual().getFirst();var art=CdR13SectionArt.fromAssets(a);var s=state(a,art);
  var fault=assertThrows(CdLevelHud.UnsupportedAct3FutureHud.class,()->new CdLevelHud.Act3Future(CdR13ObjectAssets.fromAssets(a).hudAssets(),art).run(new CdLevelHud.Act3FutureState(s.section(),s.ring(),s.level(),s.score(),CdCheckpointLiveAux.unknown(),s.bonus()),inputs()));
  assertEquals(CdLevelHud.Boundary.LIVE_TIME,fault.boundary());assertTrue(fault.calls().contains(CdLevelHud.NativeCall.DRAW_LIVES));assertEquals(0,fault.state().ring().updateHudRings());assertEquals(0,fault.state().score().updateHudScore());assertSame(s.section().queue(),fault.state().section().queue());
 }
 @Test void originalArtFaultIsAvailableWithExternalPrefixAndNoSynthesizedHudSuccess()throws Exception{
  var a=TestCdR13PlaneSources.actual().getFirst();var art=CdR13SectionArt.fromAssets(a);var s=state(a,art);var q=s.section();var old=art.fromValues(q.codeTable(),CdR13ArtResidency.unknown(a),q.sectionWord(),q.queue(),q.decoder(),q.savedDecoder(),q.remainingTilesWord(),q.processBudgetWord(),q.hintWord(),q.padWord());
  var fault=assertThrows(CdLevelHud.Act3FutureArtFailure.class,()->new CdLevelHud.Act3Future(CdR13ObjectAssets.fromAssets(a).hudAssets(),art).run(new CdLevelHud.Act3FutureState(old,s.ring(),s.level(),s.score(),s.liveTime(),s.bonus()),inputs()));
  assertSame(fault.original().state(),fault.state().section());assertEquals(CdR13SectionArt.Reason.RESIDENCY,fault.original().reason());assertEquals(0xB4A0,fault.original().destinationByteOffset());assertSame(s.ring(),fault.state().ring());
 }
 private static CdLevelHud.Act3FutureState state(CdR13FutureAssets a,CdR13SectionArt art){var s=art.fromValues(TestCdR13PlaneSources.fill(512,0x31),CdR13ArtResidency.unknownForNativeHud(a),0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.NORMAL,0,0,0,0,0),0,3,4,5);var live=CdCheckpointLiveAux.clearTimeAtSpawnZero(CdCheckpointLiveAux.unknown());return new CdLevelHud.Act3FutureState(s,new CdRingGlobals(16,3,0,0x80,1,0,0,0),new CdLevelGlobals(0,1,0,0,3,0,2,0,0),new CdOpeningObjectRuntime.ScoreGlobals(1234,5000,0x80),live,Optional.of(new CdLevelHud.BonusValues(0,0,0)));}
 private static CdLevelHud.Inputs inputs(){return new CdLevelHud.Inputs(new CdLevelHud.Globals(0,0,0,2,0x40,0x2A2,2),new CdLevelHud.DebugData(OptionalInt.empty(),Optional.empty(),OptionalInt.empty()));}
}

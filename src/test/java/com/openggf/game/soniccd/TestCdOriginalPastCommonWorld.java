package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-selected shared-World requirements, written before production. All Java UNRUN. */
@Isolated class TestCdOriginalPastCommonWorld extends TestCdOriginalR11LoadingWorld {
 private static CdLevelLoadingSource source(CdStagePayload p)throws Exception {
  var root=System.getProperty("soniccd.media.root");assertNotNull(root);
  try(var media=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())){return p==CdStagePayload.R41A ? CdProgrammeData.open(media,CdR4DataLayout.Programme.R41A).levelLoadingSource() : CdPpzProgrammeData.open(media,CdPpzDataLayout.Programme.valueOf(p.name())).levelLoadingSource();}
 }
 private static CdR11OriginalWorldBindings binding(CdLevelLoadingSource s)throws Exception {return new CdR11OriginalWorldBindings(s.playerSource(),s,CdR11aSectionArt.fromLoadingSource(s),s.placements());}
 @Test void actualPastUsesTheOneStandardSceneStrategyAndExactSourcePair()throws Exception {
  var s=source(CdStagePayload.R11B);var b=binding(s);assertSame(s.playerSource(),b.playerSource());assertSame(s.placements(),b.placements());assertEquals(0,b.nativeZoneActWord());assertEquals(CdLevelLoadingSource.StandardBackgroundProgram.PARALLAX_CLOUD_BANDS,s.standardBackgroundProgram());
 }
 @Test void equalForeignPlayerOrArtCannotRelabelThePastStrategy()throws Exception {
  var a=source(CdStagePayload.R11B);var b=source(CdStagePayload.R11B);var art=CdR11aSectionArt.fromLoadingSource(a);
  assertThrows(IllegalArgumentException.class,()->new CdR11OriginalWorldBindings(b.playerSource(),a,art,a.placements()));
  assertThrows(IllegalArgumentException.class,()->new CdR11OriginalWorldBindings(a.playerSource(),a,CdR11aSectionArt.fromLoadingSource(b),a.placements()));
 }
 @Test void foreignPlacementAndPresentOrTtSourceDoNotGainPastConstruction()throws Exception {
  var a=source(CdStagePayload.R11B);var b=source(CdStagePayload.R11B);var art=CdR11aSectionArt.fromLoadingSource(a);
  assertThrows(IllegalArgumentException.class,()->new CdR11OriginalWorldBindings(a.playerSource(),a,art,b.placements()));
  for(var p:List.of(CdStagePayload.R11A,CdStagePayload.R41A)){var other=source(p);assertThrows(RuntimeException.class,()->binding(other));}
  assertThrows(NullPointerException.class,()->new CdR11OriginalWorldBindings(a.playerSource(),(CdLevelLoadingSource)null,art,a.placements()));
 }
 @Test void nativePastScrollReusesItsExistingKernelAndRetainsAllNonwrites()throws Exception {
  var b=binding(source(CdStagePayload.R11B));var fg=new CdR11aForegroundScroll.State(0x120,0x00A01234,16,16,0,0,0,0x310,0,0,0);
  var bg=new CdR11aBackgroundScroll.State(0x11223344,0x10203040,0x55667788,0x00123456,0x99AABBCC,0x00789ABC,bytes(8,0xA5),0x1234,0x5678,0x9ABC,bytes(512,0x5A),bytes(512,0x6B),bytes(1024,0x7C),0x12345678);
  var copies=new SonicCdR31aLoadingVisit.Copies(0x11223344,0x55667788,new CdR11aBackgroundPlane.CopiedCameras(new CdR11aBackgroundPlane.Camera(1,2),new CdR11aBackgroundPlane.Camera(3,4),new CdR11aBackgroundPlane.Camera(5,6)),0x4567,new CdR11aBackgroundPlane.FlagWords(1,2,3));
  var before=new SonicCdR31aLoadingVisit.Scroll(fg,bg,copies,0x7788,0x20,0x1122);
  var in=new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(0x190,0xB0,0,0,0,0),new CdPlayerMovement.Bounds(0,0x4000,0x1000,0),0x20,160,96,CdReleaseVariant.USA,0,320,224);
  var next=b.scroll(before,in,0,0,0x190,0);var f=next.foreground().state();var expected=CdR11bBackgroundScroll.step(bg,new CdR11bBackgroundScroll.Input(f.cameraX(),f.cameraYWord(),(short)f.scrollXDiff(),(short)f.scrollYDiff(),f.scrollLock()>>>8));
  CdCapturedValueAssertions.assertCapturedValueEquals(expected,next.scroll().background());assertSame(copies,next.scroll().copies());assertEquals(0x7788,next.scroll().foregroundXFractionWord());assertEquals(0x1122,next.scroll().waterDeformWord());assertSame(bg.cloudRam(),next.scroll().background().cloudRam());
 }
 @Test void pastPaletteConsumesItsOwnSixScriptsAndKeepsTheSeventhCounters()throws Exception {
  var s=source(CdStagePayload.R11B);var b=binding(s);var pal=bytes(128,0x44);var t=CdAssetBytes.copyOf(new byte[]{1,2,3,4,5,6,0x71});var st=CdAssetBytes.copyOf(new byte[]{0,0,0,0,0,0,0x72});
  var expected=CdLevelPaletteCycle.pass(s.cycles().stream().map(c->new CdLevelPaletteCycle.Script(c.script(),c.colors())).toList(),new CdLevelPaletteCycle.State(pal,t.slice(0,6),st.slice(0,6)));var actual=b.paletteCycle(pal,t,st);
  assertEquals(expected,actual.nativeResult());assertEquals(0x71,actual.timers7().unsignedByte(6));assertEquals(0x72,actual.steps7().unsignedByte(6));
 }
 @Test void sourceSelectedPaletteReadFailureKeepsTheOriginalExceptionAndCompleteInputs()throws Exception {
  var b=binding(source(CdStagePayload.R11B));var expected=new IllegalStateException("actual palette owner fault");var calls=new java.util.concurrent.atomic.AtomicInteger();
  var actual=assertThrows(IllegalStateException.class,()->b.paletteCycle(()->{calls.incrementAndGet();throw expected;}));assertSame(expected,actual);assertEquals(1,calls.get());assertEquals(0,actual.getSuppressed().length);
 }
 @Test void genuineWarpDestinationMustReachPastReadyAndOneOrdinaryThenReplayCompleteOwners()throws Exception {
  var original=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(original,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=dispose()){
   var loop=boot.bootSonicCdSelectedProfile(original,attempt);for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());
   for(int n=0;n<6351;n++)step(loop,((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8);
   for(int n=0;n<22;n++)step(loop,8);step(loop,0);step(loop,0);for(int n=0;n<90;n++)step(loop,0);step(loop,0);for(int n=0;n<24;n++)step(loop,0);step(loop,0x80);for(int n=0;n<24;n++)step(loop,0);step(loop,0);step(loop,0);
   assertEquals(CdCommand.R11B,m.originalWarpDestination().orElseThrow().command());var loading=m.originalWarpDestination().orElseThrow().loadingSource().orElseThrow();var old=m.originalR11EntryFrame();step(loop,0);
   for(int n=0;n<256&&m.originalR11LoadingImage().phase()!=SonicCdR31aLoadingVisit.Phase.READY;n++)step(loop,0);
   var frame=m.originalR11EntryFrame();assertNotSame(old,frame);assertTrue(old.closed());assertSame(loading.playerSource(),frame.stageSource());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());assertEquals(0,frame.environment().movement().gates().timeZone());assertFalse(frame.closed());
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();step(loop,0);var image=image(m);
   var callbacks=new java.util.concurrent.atomic.AtomicInteger();com.openggf.game.GameServices.graphics().registerCommand((x,y,width,height)->{
    assertThrows(IllegalStateException.class,()->m.serviceOriginalR11Loading(0));assertThrows(RuntimeException.class,registry::capture);assertThrows(RuntimeException.class,()->registry.restore(full));assertThrows(IllegalStateException.class,()->m.gameplayFrameController().drawOverlay());callbacks.incrementAndGet();
   });m.gameplayFrameController().drawOverlay();assertEquals(1,callbacks.get());assertFalse(frame.closed());CdCapturedValueAssertions.assertCapturedValueEquals(image,image(m));
   registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(image,image(m));registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(image,image(m));assertFalse(frame.closed());assertTrue(m.originalR11WorldImage().orElseThrow().cpu().isPresent());
  }
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();return List.of(f.captureActor(),f.captureShared(),m.originalR11PlacementImage(),m.originalR11LoadingImage(),m.originalR11LoadingPrefix(),m.originalR11WorldImage(),m.originalR11TimeWarpSavedImage());}
 private static CdAssetBytes bytes(int n,int fill){var b=new byte[n];Arrays.fill(b,(byte)fill);return CdAssetBytes.copyOf(b);}
 private static AutoCloseable dispose(){return ()->{try{SessionManager.clear();}finally{com.openggf.game.GameServices.graphics().cleanup();}};}
}

package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Strict public source requirements. No unsupported edge is accepted before the actual white RTS. */
@Isolated class TestCdOriginalR11MarkedLevelStartReturn extends TestCdOriginalR11LoadingWorld {
 @Test void genuine6351AndTwentyTwoWhiteWaitsReturnThenReplayFullAndCourse()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileContextCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());assertEquals(1,m.originalR11LoadingPrefix().main().palFadeFlagsByte()&1,"Initial Main86 old0 BSET skips black fade and retains bit1 at Ready");
   assertTrue(m.getLevelInitProfile().isLevelMusicPublicationPending(),"Native profile suppresses host music permanently; it is not queued host work");
   var frame=m.originalR11EntryFrame();
   for(int n=0;n<6350;n++){int held=((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8;step(loop,held);assertFalse(frame.closed());assertTrue(frame.state().motion().routine()<6);}
   assertEquals(6350,frame.environment().globals().levelFrames());assertEquals(229,frame.environment().globals().timeWarpTimer());assertEquals(1,m.originalR11LoadingPrefix().main().palFadeFlagsByte()&1,"The genuine pre6351 cut retains the initial palette flag; native230 does not write it");assertEquals(0x80,frame.environment().movement().gates().timeZone());
   var bank=m.originalR11TimeWarpSavedImage();for(byte known:bank.knownMask().copy())assertEquals(1,known);assertEquals(2,m.originalR11LoadingPrefix().main().level().spawnMode());
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var beforeFull=registry.capture();var beforeCourse=registry.captureCourse();
   step(loop,8);assertFalse(frame.closed());assertEquals(6351,frame.environment().globals().levelFrames());assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE,m.originalR11LoadingImage().phase());assertEquals(0,m.originalR11LoadingImage().fadeSteps());assertEquals(0x003F,m.originalR11LoadingImage().globals().tail().orElseThrow().paletteFadeInfoWord());assertEquals(0x0100,m.originalR11LoadingPrefix().main().level().levelRestart());assertEquals(1,m.originalR11LoadingPrefix().main().palFadeFlagsByte()&1);assertSame(bank,m.originalR11TimeWarpSavedImage());
   var entry=image(m);var entryFull=registry.capture();var entryCourse=registry.captureCourse();
   for(int n=0;n<11;n++)step(loop,8);assertEquals(11,m.originalR11LoadingImage().fadeSteps());assertEquals(SonicCdR31aLoadingVisit.Phase.INITIAL_TO_WHITE,m.originalR11LoadingImage().phase());var middle=image(m);var midFull=registry.capture();var midCourse=registry.captureCourse();
   assertSealedPreludeRejectsRecombinedValues(m);
   for(int n=11;n<22;n++)step(loop,8);assertWhiteRts(m,bank);var returned=image(m);var returnFull=registry.capture();var returnCourse=registry.captureCourse();
   for(boolean course:new boolean[]{false,true}){
    if(course)registry.restoreCourse(beforeCourse);else registry.restore(beforeFull);step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(entry,image(m));
    if(course)registry.restoreCourse(entryCourse);else registry.restore(entryFull);for(int n=0;n<11;n++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(middle,image(m));
    if(course)registry.restoreCourse(midCourse);else registry.restore(midFull);for(int n=11;n<22;n++)step(loop,8);assertWhiteRts(m,bank);CdCapturedValueAssertions.assertCapturedValueEquals(returned,image(m));
    if(course)registry.restoreCourse(returnCourse);else registry.restore(returnFull);assertWhiteRts(m,bank);CdCapturedValueAssertions.assertCapturedValueEquals(returned,image(m));
   }
   // The genuine returned occurrence now owns the actual return blank and zero-pulse WARP setup.
   step(loop,8);assertTrue(m.warpSceneState().isEmpty());step(loop,8);
   assertEquals(SonicCdGameModule.OriginalWarpPhase.WARP_ACTIVE,m.originalWarpPhase());
   assertEquals(com.openggf.game.soniccd.scene.CdWarpScene.Phase.DELAY,m.warpSceneState().orElseThrow().phase());assertEquals(90,m.warpSceneState().orElseThrow().timerWord());assertEquals(0,m.warpSceneState().orElseThrow().vIntCounterWord());assertFalse(frame.closed());
  }
 }
 private static void assertWhiteRts(SonicCdGameModule m,CdTimeWarpSaveData.State bank)throws Exception{
  var frame=m.originalR11EntryFrame();assertFalse(frame.closed());assertEquals(SonicCdR31aLoadingVisit.Phase.RETURNED,m.originalR11LoadingImage().phase());assertEquals(22,m.originalR11LoadingImage().fadeSteps());assertEquals(6351,frame.environment().globals().levelFrames());assertEquals(0x80,frame.environment().movement().gates().timeZone());assertEquals(0,frame.environment().movement().gates().timeWarpDirection());assertEquals(0,m.originalR11LoadingPrefix().main().level().levelRestart());assertEquals(2,m.originalR11LoadingPrefix().main().level().spawnMode());assertEquals(0,m.originalR11LoadingPrefix().main().palFadeFlagsByte()&1);assertEquals(0,m.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(128,m.originalR11LoadingPrefix().gameMode()&128);assertSame(bank,m.originalR11TimeWarpSavedImage());var f=SonicCdGameModule.class.getDeclaredField("r11ReturnKind");f.setAccessible(true);assertEquals(SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART,f.get(m));
 }
 private static void assertSealedPreludeRejectsRecombinedValues(SonicCdGameModule m)throws Exception{
  var capture=SonicCdGameModule.class.getDeclaredMethod("captureOriginalR11EntryImage");capture.setAccessible(true);Object issued=capture.invoke(m);var before=image(m);var type=issued.getClass();var validate=SonicCdGameModule.class.getDeclaredMethod("validateOriginalR11Restore",type);validate.setAccessible(true);
  for(String component:new String[]{"loading","prefix","returnKind"}){
   Object wrong=switch(component){case "loading"->m.originalR11LoadingImage().phase(SonicCdR31aLoadingVisit.Phase.READY);case "prefix"->m.originalR11LoadingPrefix().palette(m.originalR11LoadingPrefix().normalPalette());default->SonicCdR31aLoadingVisit.ReturnKind.WHITE_RESTART;};
   var forged=copyRecord(issued,component,wrong);var error=assertThrows(InvocationTargetException.class,()->validate.invoke(m,forged));assertInstanceOf(IllegalArgumentException.class,error.getCause());CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 private static Object copyRecord(Object original,String name,Object replacement)throws Exception{
  var components=original.getClass().getRecordComponents();var types=new Class<?>[components.length];var values=new Object[components.length];for(int i=0;i<components.length;i++){types[i]=components[i].getType();var read=components[i].getAccessor();read.setAccessible(true);values[i]=components[i].getName().equals(name)?replacement:read.invoke(original);}var ctor=original.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(values);
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.pool().slots(),p.savedFlags(),p.status(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11WorldImage(),m.originalR11FirstLoad(),m.originalR11TimeWarpSavedImage(),m.programServicesState(),m.selectedProfileState());}
 // Exact existing Root289b saved-bank fixture contract, owned here rather than falsely inherited.
 private static AutoCloseable disposeWhileContextCurrent(){return ()->{Throwable failure=null;try{SessionManager.clear();}catch(RuntimeException|Error original){failure=original;}try{com.openggf.game.GameServices.graphics().cleanup();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}if(failure instanceof RuntimeException original)throw original;if(failure instanceof Error original)throw original;};}
}

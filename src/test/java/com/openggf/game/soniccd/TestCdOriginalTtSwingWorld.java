package com.openggf.game.soniccd;
import com.openggf.GameLoop;import com.openggf.game.save.*;import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.parallel.Isolated;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Strict real-public-controller obligations. All Java UNRUN; no positive endpoint is inferred. */
@Isolated class TestCdOriginalTtSwingWorld extends TestCdOriginalR11LoadingWorld {
 @Test void original425ThenWholeConnectedSwingAndSwitchThrough432ReplaysFullAndCourse()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);for(int n=1;n<=425;n++){step(loop,8);living(m,n);}
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var expected=new ArrayList<Object>();
   for(int n=426;n<=432;n++){step(loop,8);living(m,n);expected.add(image(m));}
   var slots=m.originalR11PlacementImage().pool().slots();assertTrue(slots.stream().anyMatch(v->v.id()==0x29));assertTrue(slots.stream().anyMatch(v->v.id()==0x27));assertEquals(-1,m.originalR11SwitchesImage().orElseThrow().knownMask());
   for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);for(int n=426;n<=432;n++){step(loop,8);living(m,n);CdCapturedValueAssertions.assertCapturedValueEquals(expected.get(n-426),image(m));}}
  }
 }
 @Test void primaryWordPreparationPreservesFractionEveryOtherMotionAndCompleteStateWithoutAdoption()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var f=m.originalR11EntryFrame();var s=f.state();var e=f.environment();var motion=s.motion();int word=((motion.centreY16_16()>>>16)+7)&65535;var out=f.prepareObjectYWord(s,e,motion.centreY16_16()>>>16,word);
   assertSame(s,out.expectedState());assertSame(e,out.expectedEnvironment());assertSame(e,out.finalEnvironment());assertSame(s,f.state());assertSame(e,f.environment());assertEquals((word<<16)|(motion.centreY16_16()&65535),out.finalState().motion().centreY16_16());
   for(var c:s.getClass().getRecordComponents())if(!c.getName().equals("motion"))assertEquals(c.getAccessor().invoke(s),c.getAccessor().invoke(out.finalState()));
   for(var c:motion.getClass().getRecordComponents())if(!c.getName().equals("centreY16_16"))assertEquals(c.getAccessor().invoke(motion),c.getAccessor().invoke(out.finalState().motion()));
   assertThrows(IllegalArgumentException.class,()->f.prepareObjectYWord(s,e,motion.centreY16_16()>>>16,65536));step(loop,8);assertThrows(IllegalArgumentException.class,()->f.prepareObjectYWord(s,e,motion.centreY16_16()>>>16,word));
  }
 }
 @Test void idleForeignPoolAndOffThreadSwitchPortsCannotAlterBankActorOrPool()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);step(loop,8);living(m,1);var binding=field(field(m,"r11World"),"binding");var owner=(CdR4Switch.Owner)field(binding,"ttSwitchOwner");var before=image(m);
   var foreign=new CdObjectPool();foreign.write(foreign.state(32).moveByte(CdObjectState.ByteField.ID,0x27));var data=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().objectSource().switchData();assertThrows(IllegalStateException.class,()->CdR4Switch.run(new CdR4Switch.Inputs(foreign,32,data),owner));assertThrows(IllegalStateException.class,()->owner.primaryY(null));
   var thrown=new java.util.concurrent.atomic.AtomicReference<Throwable>();var thread=new Thread(()->{try{m.originalR11SwitchesImage();}catch(Throwable failure){thrown.set(failure);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,thrown.get());CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 private SonicCdGameModule.SelectedProfileAttempt tt(CdGameDataSource source)throws Exception{var saves=new SaveManager(root);var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));saves.writeSlot("scd",1,profile.encode());return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);}
 private static void ready(GameLoop loop,SonicCdGameModule m){for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());}
 private static void living(SonicCdGameModule m,int n){var f=m.originalR11EntryFrame();assertFalse(f.closed());assertTrue(f.state().motion().routine()==2||f.state().motion().routine()==4,"Actual returned caller "+n);assertEquals(n,f.environment().globals().levelFrames());assertEquals(8,f.environment().controls().rawHeld());}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var b=m.originalR11SwitchesImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.originalR11WorldImage(),m.programServicesState(),m.selectedProfileState(),b.map(x->List.of(x.knownMask(),x.bytes())));}
 private static AutoCloseable retireContext(){return ()->{SessionManager.clear();com.openggf.game.GameServices.graphics().cleanup();};}
 private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 @Test void boundedRightContinuationAfterOwnedSwing432ReportsFirstBoundaryOrDeath()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();
  try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);int returned=0;String lastHealthy="Ready; no ordinary caller returned";
   for(int n=1;n<=3000;n++){
    try{step(loop,8);}catch(UnsupportedOperationException stop){
     if(n<=432)throw stop;
     System.out.println("TT_AFTER_SWING boundary attempted="+n+" returned="+returned+" type="+stop.getClass().getName()+" message="+stop.getMessage()+" lastHealthy="+lastHealthy);return;
    }
    returned=n;var f=m.originalR11EntryFrame();assertFalse(f.closed(),"A returned call must retain its active Frame");
    int routine=f.state().motion().routine();
    if(routine!=2&&routine!=4){
     assertTrue(n>432,"Owned432 prefix must remain healthy");
     System.out.println("TT_AFTER_SWING returned-death-or-nonnormal returned="+returned+" "+afterSwingObservation(m));return;
    }
    living(m,n);lastHealthy=afterSwingObservation(m);
   }
   System.out.println("TT_AFTER_SWING bounded returned="+returned+" "+afterSwingObservation(m));
  }
 }
 /** Read-only observations, never expected gameplay values or a continuation source. */
 private static String afterSwingObservation(SonicCdGameModule m){
  var f=m.originalR11EntryFrame();var p=f.state().motion();
  var actors=m.originalR11PlacementImage().pool().slots().stream().filter(v->v.id()!=0).limit(20)
    .map(v->v.identity().wholeIndex()+":"+Integer.toHexString(v.id())+"/"+v.routine()).toList();
  return "centre="+(p.centreX16_16()>>>16)+","+(p.centreY16_16()>>>16)+" routine="+p.routine()
    +" rawPeriod="+f.environment().movement().gates().timeZone()+" actors(first20)="+actors;
 }
}

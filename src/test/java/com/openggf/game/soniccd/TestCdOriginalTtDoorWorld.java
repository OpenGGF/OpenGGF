package com.openggf.game.soniccd;
import com.openggf.GameLoop;
import com.openggf.game.save.*;import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Tests FIRST: real public R41A route. No object/pose/input authority hydration. AllJavaUNRUN. */
@Isolated class TestCdOriginalTtDoorWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actual425PrefixThenDoor426Through432ReturnsAndReplaysFullAndCourse()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);for(int n=1;n<=425;n++){step(loop,8);living(m,n);}
   var old=m.originalR11PlacementImage().pool().slots().get(35-32);assertEquals(0,old.id(),"The actual spawner creates Door35 during caller426; caller425 still owns a free slot");assertEquals(0,old.routine());
   var switches=m.originalR11SwitchesImage().orElseThrow();assertEquals(-1,switches.knownMask());assertArrayEquals(new byte[32],switches.bytes(),"Actual camera clear, not a supplied zero bank");
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var expected=new ArrayList<Object>();
   for(int n=426;n<=432;n++){step(loop,8);living(m,n);var door=m.originalR11PlacementImage().pool().slots().get(3);assertEquals(0x28,door.id());assertEquals(0x20CF46,door.longBits(CdObjectState.LongField.MAPPINGS));assertTrue(door.routine()==2||door.routine()==4||door.routine()==6);assertEquals(switches.knownMask(),m.originalR11SwitchesImage().orElseThrow().knownMask(),"Actual linked Switch27 may publish a replacement bank; every byte remains known and the complete values replay below");expected.add(image(m));}
   for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);for(int n=426;n<=432;n++){step(loop,8);living(m,n);CdCapturedValueAssertions.assertCapturedValueEquals(expected.get(n-426),image(m));}}
  }
 }
 @Test void idleForeignPoolAndOffThreadDoorPortsRejectWithoutAnyWorldWrite()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var m=attempt.module().orElseThrow();try(var boot=boot();var retire=retireContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);step(loop,8);living(m,1);var binding=field(field(m,"r11World"),"binding");var owner=(CdR4Door.Owner)field(binding,"ttDoorOwner");var before=image(m);
   var foreign=new CdObjectPool();foreign.write(foreign.state(32).moveByte(CdObjectState.ByteField.ID,0x28));var data=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().objectSource().doorData();
   assertThrows(IllegalStateException.class,()->CdR4Door.run(new CdR4Door.Inputs(foreign,32,data),owner));assertThrows(IllegalStateException.class,()->owner.primary(null));
   var result=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{m.originalR11SwitchesImage();}catch(Throwable x){result.set(x);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,result.get());CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));
  }
 }
 private SonicCdGameModule.SelectedProfileAttempt tt(CdGameDataSource source)throws Exception{var saves=new SaveManager(root);var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));saves.writeSlot("scd",1,profile.encode());return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);}
 private static void ready(GameLoop loop,SonicCdGameModule m){for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());}
 private static void living(SonicCdGameModule m,int n){var f=m.originalR11EntryFrame();assertFalse(f.closed());assertTrue(f.state().motion().routine()==2||f.state().motion().routine()==4,"Actual returned caller "+n);assertEquals(n,f.environment().globals().levelFrames());assertEquals(8,f.environment().controls().rawHeld());}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var b=m.originalR11SwitchesImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11FirstLoad(),m.originalLoadingWaterImage(),m.originalLoadingWaterPalette(),m.originalR11WorldImage(),m.programServicesState(),m.selectedProfileState(),b.map(x->List.of(x.knownMask(),x.bytes())));}
 private static AutoCloseable retireContext(){return ()->{SessionManager.clear();com.openggf.game.GameServices.graphics().cleanup();};}
 private static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}

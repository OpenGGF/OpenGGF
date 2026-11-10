package com.openggf.game.soniccd;
import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
/** Tests FIRST; SOURCE/allJavaUNRUN. Labelled constructed pool Seeds are component fixtures;
 * the separate public-controller requirement alone claims a natural Flower occurrence. */
@Isolated class TestCdOriginalR11FlowerWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actualR11FlowerViewsHaveIndependentOriginalRootsAndBytes()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var o=a.openingObjects();var data=CdFlower.Assets.fromObjectAssets(o);
  assertEquals(CdNativeFile.R11A,o.identity().file());assertEquals(0x235EF4,data.mappingsAddress());assertEquals(8,data.flower().mappingFrames().size());assertEquals(4,data.flower().animations().size());
  assertEquals(0x235ED8,o.view(CdR11aObjectAssets.Table.FLOWER_ANIMATIONS).range().nativeAddress());assertEquals(28,o.view(CdR11aObjectAssets.Table.FLOWER_ANIMATIONS).bytes().length());assertEquals(90,o.view(CdR11aObjectAssets.Table.FLOWER_MAPPINGS).bytes().length());
  assertEquals("74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9",sha(o.view(CdR11aObjectAssets.Table.FLOWER_ANIMATIONS).bytes().copy()));
  assertEquals("48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41",sha(o.view(CdR11aObjectAssets.Table.FLOWER_MAPPINGS).bytes().copy()));
 }
 @Test void genuineOriginalLoadingClearOwnsAll768PositionBytes()throws Exception{try(var f=start()){
  var p=f.module.originalR11LoadingImage().flowers();assertTrue(p.clearedByOriginal());assertEquals(768,p.knownBytes());for(int n=0;n<768;n+=2)assertEquals(0,p.word(n));assertThrows(CdFlowerPositions.Unavailable.class,()->p.word(768));
  assertSame(f.body,kernel(f.module));assertEquals(0x235EF4,f.body.assets().mappingsAddress());
 }}
 @Test void actualPublicInputMustReturnTheFirstFlowerAndReplayFullAndCourse()throws Exception{try(var f=start()){
  var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var loads=f.module.programServicesState().loads();int rows=0;
  while(rows<4192&&!returned(f.module)){step(f.loop,input(rows));rows++;assertFalse(f.module.originalR11EntryFrame().closed());assertTrue(f.module.originalR11EntryFrame().state().motion().routine()<6,"Early death cannot certify Flower");}
  assertTrue(returned(f.module),"Required actual returned original R11 Flower body after the boulder/ledge input course");assertTrue(rows>=4168);assertTrue(f.module.originalR11LoadingPrefix().score().score()>=10);var expected=image(f.module);
  for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);for(int n=0;n<rows;n++)step(f.loop,input(n));assertTrue(returned(f.module));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(f.module));}
  assertSame(loads,f.module.programServicesState().loads());assertSame(f.body,kernel(f.module));
 }}
 @Test void constructedPersistentLandingUsesCurrentFloorCountAndPositionAndReplays()throws Exception{try(var f=start()){
  var seed=landing(f);f.placement.pool().write(seed);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();step(f.loop,0);
  var row=f.placement.pool().state(127);assertEquals(4,row.routine());assertEquals(1,row.unsignedByte(ANIMATION));assertEquals(0x100,f.module.originalR11LoadingPrefix().main().level().flowerCount());var positions=f.module.originalR11LoadingImage().flowers();assertEquals(seed.centreX(),positions.word(0x100));assertEquals(row.centreY(),positions.word(0x102));var expected=image(f.module);
  for(boolean c:new boolean[]{false,true}){if(c)registry.restoreCourse(course);else registry.restore(full);step(f.loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(f.module));}
 }}
 @Test void idleAndOffThreadDispatchRejectWithoutPoolOrFrameWrites()throws Exception{try(var f=start()){
  f.placement.pool().write(seed(f,0,1));var before=f.placement.capture();var actor=f.module.originalR11EntryFrame().captureActor();var shared=f.module.originalR11EntryFrame().captureShared();assertThrows(IllegalStateException.class,()->f.body.run(127));assertThrows(IllegalStateException.class,()->observer(f.body).beforeCall(new CdFlower.DrawCall(1,before,f.placement.pool().state(127))));
  var failure=new AtomicReference<Throwable>();var t=new Thread(()->{try{f.body.run(127);}catch(Throwable e){failure.set(e);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,failure.get());assertPool(before,f.placement.capture());assertEquals(actor,f.module.originalR11EntryFrame().captureActor());assertEquals(shared,f.module.originalR11EntryFrame().captureShared());
 }}
 @Test void refusedFirstPublicationPreservesExactSourceAndFault()throws Exception{publicationFault(false);}
 @Test void landedFirstPublicationPreservesExactNativePrefixAndFault()throws Exception{publicationFault(true);}
 @Test void landedPrimaryAngleFaultRetainsTheActualPrezero()throws Exception{try(var f=start()){
  f.placement.pool().write(seed(f,2,1));var original=observer(f.body);var call=new AtomicReference<CdFlower.PrimaryAngleCall>();var fault=new Fault("original Flower primary angle");replaceObserver(f.body,new CdFlower.DispatchObserver(){public void beforePublication(CdFlower.Publication c){original.beforePublication(c);}public void afterPublication(CdFlower.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);if(c instanceof CdFlower.PrimaryAngleCall a){call.set(a);throw fault;}}});
  var frame=f.module.originalR11EntryFrame();assertSame(fault,assertThrows(Fault.class,()->step(f.loop,0)));assertNotNull(call.get());assertEquals(0,call.get().value());assertEquals(call.get().value(),frame.environment().movement().globals().primaryAngle());assertEquals(0,fault.getSuppressed().length);assertFalse(f.body.isCurrentCall(call.get()));
 }}
 @Test void landedPersistentXFaultKeepsCountAndXWithoutInventingY()throws Exception{try(var f=start()){
  var seed=landing(f);f.placement.pool().write(seed);var original=observer(f.body);var call=new AtomicReference<CdFlower.SavedCall>();var fault=new Fault("original Flower position X");
  replaceObserver(f.body,new CdFlower.DispatchObserver(){public void beforePublication(CdFlower.Publication c){original.beforePublication(c);}public void afterPublication(CdFlower.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);if(c instanceof CdFlower.SavedCall s&&s.site()==CdFlower.SavedSite.POSITION_X){call.set(s);throw fault;}}});
  assertSame(fault,assertThrows(Fault.class,()->step(f.loop,0)));assertNotNull(call.get());var raw=(SonicCdGameModule.R11LoadingState)field(f.module,"r11LoadingState");var prefix=(SonicCdGameModule.R11LoadingPrefix)field(f.module,"r11LoadingPrefix");assertEquals(0x100,prefix.main().level().flowerCount());assertEquals(seed.centreX(),raw.flowers().word(0x100));assertEquals(0,raw.flowers().word(0x102));assertEquals(0,fault.getSuppressed().length);assertFalse(f.body.isCurrentCall(call.get()));
 }}
 private void publicationFault(boolean landed)throws Exception{try(var f=start()){
  f.placement.pool().write(seed(f,0,1));var original=observer(f.body);var call=new AtomicReference<CdFlower.Publication>();var fault=new Fault("original Flower publication "+landed);
  replaceObserver(f.body,new CdFlower.DispatchObserver(){public void beforePublication(CdFlower.Publication c){if(!landed){call.set(c);throw fault;}original.beforePublication(c);}public void afterPublication(CdFlower.Publication c){original.afterPublication(c);call.set(c);throw fault;}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
  assertSame(fault,assertThrows(Fault.class,()->step(f.loop,0)));var c=Objects.requireNonNull(call.get());assertEquals(landed?c.output():c.source(),f.placement.pool().state(127));var rows=new ArrayList<>(c.cut().pool().slots());rows.set(95,landed?c.output():c.source());assertEquals(rows,f.placement.capture().pool().slots());assertEquals(c.cut().status(),f.placement.capture().status());assertArrayEquals(c.cut().savedFlags().copy(),f.placement.capture().savedFlags().copy());assertEquals(0,fault.getSuppressed().length);assertFalse(f.body.isCurrentCall(c));
 }}
 private static CdObjectState seed(Fixture f,int routine,int subtype){var motion=f.module.originalR11EntryFrame().state().motion();return f.placement.pool().state(127).moveByte(ID,0x1F).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SPRITE_FLAGS,4).moveByte(Y_RADIUS,0).moveLong(CdObjectState.LongField.MAPPINGS,f.body.assets().mappingsAddress()).moveWord(TILE,CdFlower.SEED_TILE_WORD).moveWord(ANIMATION_WORD,2).moveWord(X,motion.centreX16_16()>>>16).moveWord(Y,((motion.centreY16_16()>>>16)-64)&65535);}
 private static CdObjectState landing(Fixture f){var base=seed(f,2,0);var terrain=new CdPlayerTerrain(f.assets.terrain());for(int offset=0;offset<256;offset+=8){var row=base.moveWord(Y,(base.centreY()+offset)&65535);if(CdObjectCollisionCheck.objGetFloorDist(terrain,row).distance()<0)return row;}throw new AssertionError("Actual retained terrain did not supply the labelled constructed landing precondition within256 pixels");}
 private Fixture start()throws Exception{var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();var b=boot();try{var loop=b.bootSonicCdSelectedProfile(source,attempt);for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;n++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());return new Fixture(b,loop,m,source.assets(),kernel(m),(CdObjectPlacementRuntime)field(field(field(m,"r11World"),"binding"),"placement"));}catch(Exception|Error e){b.close();throw e;}}
 private record Fixture(com.openggf.tools.HeadlessGameBoot boot,GameLoop loop,SonicCdGameModule module,CdR11aAssets assets,CdFlower body,CdObjectPlacementRuntime placement)implements AutoCloseable{public void close(){try{SessionManager.clear();}finally{try{com.openggf.game.GameServices.graphics().cleanup();}finally{boot.close();}}}}
 private static int input(int row){return(row>=4000&&row<4032)||(row>=4096&&row<4128)?0x18:8;}
 private static boolean returned(SonicCdGameModule m){return m.originalR11PlacementImage().pool().slots().stream().anyMatch(s->s.id()==0x1F&&s.routine()!=0);}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();var first=m.originalR11FirstLoad();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.selectedProfileState(),first.selection().inputs(),first.foreground(),first.background(),first.copies(),w.cpu(),w.sounds(),w.tunnelServices(),w.mechanismServices(),w.closureServices(),m.programServicesState());}
 private static String sha(byte[] b)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));}
 private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 private static CdFlower kernel(SonicCdGameModule m)throws Exception{return(CdFlower)field(field(field(m,"r11World"),"binding"),"flower");}
 private static CdFlower.DispatchObserver observer(CdFlower f)throws Exception{return(CdFlower.DispatchObserver)field(f,"observer");}
 private static void replaceObserver(CdFlower owner,CdFlower.DispatchObserver value)throws Exception{var f=CdFlower.class.getDeclaredField("observer");f.setAccessible(true);f.set(owner,value);}
 private static void assertPool(CdObjectPlacementRuntime.Snapshot a,CdObjectPlacementRuntime.Snapshot b){assertEquals(a.status(),b.status());assertEquals(a.pool().slots(),b.pool().slots());assertArrayEquals(a.savedFlags().copy(),b.savedFlags().copy());}
 private static final class Fault extends RuntimeException{Fault(String m){super(m);}}
}

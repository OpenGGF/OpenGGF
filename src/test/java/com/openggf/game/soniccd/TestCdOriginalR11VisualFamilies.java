package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/** SOURCE-FIRST / ALL JAVA UNRUN. The first case is genuine public original R11 Boot/World
 * and real FULL/course replay. Remaining cases are explicitly CONSTRUCTED placement controls
 * over authenticated original R11 assets and existing body/queue kernels. They do not inject
 * Module/Frame state or establish an encounter, natural route, current art, pixels or parity. */
class TestCdOriginalR11VisualFamilies extends TestCdOriginalR11LoadingWorld {
 @Test void realOriginalOrdinaryWorldRetainsItsClosedCapturesAndCurrentSelectionOnBothReplayForms()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeInContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);
   for(int n=0;n<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;n++)step(loop,0);
   assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());
   var frame=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   step(loop,0);step(loop,8);var complete=closed(module);var owner=frame.stageSource();
   assertFalse(frame.closed());assertEquals(2,frame.state().motion().routine());assertTrue(module.originalR11FirstLoad().selection().inputs().selectedCpu().isPresent());
   registry.restore(full);step(loop,0);step(loop,8);assertEquals(complete,closed(module));assertSame(frame,module.originalR11EntryFrame());assertSame(owner,frame.stageSource());
   registry.restoreCourse(course);step(loop,0);step(loop,8);assertEquals(complete,closed(module));assertSame(frame,module.originalR11EntryFrame());assertSame(owner,frame.stageSource());
  }
 }
 @Test void actualOriginalMetadataKeepsTheThreeNativePointersAndCachedVisuals()throws Exception{
  try(var h=new Constructed()){
   assertEquals(0x2089EE,CdLogShadow.Assets.fromObjectAssets(h.metadata).mappingsAddressBits());
   assertEquals(0x23F088,CdExplosion.Assets.fromObjectAssets(h.metadata).mappingsAddress());
   assertEquals(0x20AC64,CdPoints.Assets.fromObjectAssets(h.metadata).mappingsAddress());
   assertSame(h.metadata.logShadow(),CdLogShadow.Assets.fromObjectAssets(h.metadata).visual());
   assertSame(h.metadata.explosion(),h.explosion.assets().explosion());assertSame(h.metadata.points(),h.points.assets().points());
   assertTrue(h.draws.isEmpty());assertTrue(h.placement.pool().capture().slots().stream().allMatch(s->s.id()==0));
  }
 }
 @Test void constructedDirtyLogInitPreservesUnwrittenBytesAndOneOriginalDraw()throws Exception{
  try(var h=new Constructed()){
   var dirty=h.pool().state(40);for(var f:CdObjectState.LongField.values())dirty=dirty.moveLong(f,0xA5A5A5A5);for(var f:CdObjectState.WordField.values())dirty=dirty.moveWord(f,0xA5A5);for(var f:CdObjectState.ByteField.values())dirty=dirty.moveByte(f,0xA5);
   h.pool().write(dirty.moveByte(ID,0x33).moveByte(SPRITE_FLAGS,3).moveByte(ROUTINE,0).moveByte(SUBTYPE,1).moveByte(SAVED_FLAGS_ID,0).moveWord(X,100).moveWord(Y,100));var before=h.pool().state(40).copyBytes();
   h.log.run(40,h.logGlobals());var after=h.pool().state(40);assertEquals(2,after.routine());assertEquals(0x4418,after.word(TILE));assertEquals(0x2089EE,after.longBits(MAPPINGS));assertEquals(1,after.unsignedByte(MAP_FRAME));assertEquals(List.of(40),h.draws);
   var written=Set.of(1,2,3,4,5,6,7,0x16,0x18,0x19,0x1A,0x24);for(int i=0;i<64;i++)if(!written.contains(i))assertEquals(before[i],after.copyBytes()[i],"unwritten original byte "+i);
   assertTrue(h.finish().spriteCount()>0);
  }
 }
 @Test void constructedLogBadFrameIsDeferredUntilTheActualQueueSelectsItsMapping()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x33,0,100,100);h.pool().write(h.pool().state(40).moveByte(SUBTYPE,0xFF));
   h.log.run(40,h.logGlobals());assertEquals(2,h.pool().state(40).routine());assertEquals(255,h.pool().state(40).unsignedByte(MAP_FRAME));assertEquals(0x4418,h.pool().state(40).word(TILE));assertEquals(List.of(40),h.draws);
   var completed=h.pool().capture();assertThrows(UnsupportedOperationException.class,h::finish);assertEquals(completed.slots(),h.pool().capture().slots());
  }
 }
 @Test void constructedCulledLogStillRunsDespawnAndClearsOnlyItsLoadedSavedBit()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x33,2,0x1000,100);h.pool().write(h.pool().state(40).moveByte(SPRITE_FLAGS,0x84).moveByte(SAVED_FLAGS_ID,3));
   h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.LOADED);h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.MEMBER_7);var before=h.placement.capture().savedFlags().copy();
   h.log.run(40,h.logGlobals());assertArrayEquals(new byte[64],h.pool().state(40).copyBytes());assertEquals(List.of(40),h.draws);var after=h.placement.capture().savedFlags().copy();before[2+3*3+1]&=0x7F;assertArrayEquals(before,after);assertEquals(0,h.finish().spriteCount());
  }
 }
 @Test void constructedLaterPointsChildExecutesOnceAtItsFreshPhysicalVisit()throws Exception{
  try(var h=new Constructed()){
   h.seed(32,0x18,0,100,100);h.pool().write(h.pool().state(32).moveWord(EXPLOSION_POINTS_FRAME,6));h.walk();
   var child=h.pool().state(33);assertEquals(0x1C,child.id());assertEquals(2,child.routine());assertEquals(0x83,child.subtype());assertEquals(3,child.unsignedByte(MAP_FRAME));assertEquals(23,child.unsignedByte(POINTS_TIMER));assertEquals(98,child.centreY());assertEquals(List.of(32,33),h.draws);assertTrue(h.finish().spriteCount()>0);
  }
 }
 @Test void constructedEarlierPointsChildWaitsAndTheSameCachedBodiesReplayAfterPlacementRestore()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x18,0,100,100);var before=h.placement.capture();h.walk();assertEquals(0x1C,h.pool().state(32).id());assertEquals(0,h.pool().state(32).routine());assertEquals(List.of(40),h.draws);var expected=h.placement.capture();var cpu=h.finish();
   h.placement.restore(before);h.nextPass();h.walk();assertEquals(expected.pool().slots(),h.placement.capture().pool().slots());assertArrayEquals(expected.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertEquals(expected.status(),h.placement.status());assertArrayEquals(cpu.bytes(),h.finish().bytes());
   h.nextPass();h.walk();assertEquals(2,h.pool().state(32).routine());assertEquals(23,h.pool().state(32).unsignedByte(POINTS_TIMER));assertEquals(List.of(32,40),h.draws);h.finish();
  }
 }
 @Test void constructedFullPoolRetainsExplosionInitWithoutAllocatingOrChangingOtherImages()throws Exception{
  try(var h=new Constructed()){
   for(int w=32;w<128;w++)h.seed(w,0x7F,0,100,100);h.pool().write(h.pool().state(40).moveByte(ID,0x18));var before=h.placement.capture();
   h.explosion.run(40);assertEquals(2,h.pool().state(40).routine());assertEquals(List.of(40),h.draws);for(int w=32;w<128;w++)if(w!=40)assertEquals(before.pool().slots().get(w-32),h.pool().state(w));assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());h.finish();
  }
 }
 @Test void constructedPointsTimerDeletionStillQueuesTheNowZeroPhysicalReference()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x1C,2,100,100);h.pool().write(h.pool().state(40).moveByte(SUBTYPE,0x80).moveByte(POINTS_TIMER,1));var saved=h.placement.capture().savedFlags();
   h.points.run(40);assertArrayEquals(new byte[64],h.pool().state(40).copyBytes());assertEquals(List.of(40),h.draws);assertEquals(0,h.finish().spriteCount());assertArrayEquals(saved.copy(),h.placement.capture().savedFlags().copy());
  }
 }
 @Test void constructedExplosionDoneConvertsToTheStillUnownedFlowerOrDeletesWithoutDrawing()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x18,4,100,100);h.explosion.run(40);assertEquals(0x1F,h.pool().state(40).id());assertEquals(0,h.pool().state(40).routine());assertTrue(h.draws.isEmpty());
   // No Flower call is made: that next physical family remains an explicit World boundary.
   h.seed(41,0x18,4,100,100);h.pool().write(h.pool().state(41).moveByte(ROUTINE_2,1));h.explosion.run(41);assertArrayEquals(new byte[64],h.pool().state(41).copyBytes());assertTrue(h.draws.isEmpty());
  }
 }
 @Test void constructedUnsupportedRoutinesAndDynamicHudSelectionFailBeforeAnyWrite()throws Exception{
  try(var h=new Constructed()){
   for(int id:new int[]{0x33,0x18,0x1C}){h.seed(40,id,8,100,100);h.pool().write(h.pool().state(40).moveByte(SUBTYPE,0x80));var before=h.placement.capture();assertThrows(UnsupportedOperationException.class,()->h.dispatch(40));assertEquals(before.pool().slots(),h.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());}
   h.seed(40,0x1C,0,100,100);h.pool().write(h.pool().state(40).moveByte(SUBTYPE,0));var before=h.pool().capture();var failure=assertThrows(CdPoints.UnsupportedPoints.class,()->h.points.run(40));assertEquals(CdPoints.UnsupportedBoundary.HUD_OWNER,failure.boundary());assertEquals(before.slots(),h.pool().capture().slots());assertTrue(h.draws.isEmpty());
  }
 }
 @Test void originalAExplosionSharedAnimationRetainsResetAndTimerBeforeDirectoryFailure()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x18,2,100,100);h.pool().write(h.pool().state(40).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,9).moveByte(ANIMATION_TIME,12));var saved=h.placement.capture().savedFlags();
   var failure=assertThrows(UnsupportedOperationException.class,()->h.explosion.run(40));var s=h.pool().state(40);assertEquals(255,s.unsignedByte(PREVIOUS_ANIMATION));assertEquals(0,s.unsignedByte(ANIMATION_FRAME));assertEquals(255,s.unsignedByte(ANIMATION_TIME));assertTrue(h.draws.isEmpty());assertArrayEquals(saved.copy(),h.placement.capture().savedFlags().copy());assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,assertInstanceOf(CdExplosion.UnsupportedExplosion.class,failure).boundary());
  }
 }
 @Test void originalAExplosionSharedAnimationRetainsTheReachedNativeSpeedBeforeLaterFrameFailure()throws Exception{
  try(var h=new Constructed()){
   h.seed(40,0x18,2,100,100);h.pool().write(h.pool().state(40).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,4));var table=h.metadata.explosion().animationTable();int script=(short)table.unsignedWord(0);int speed=table.unsignedByte(script);
   var failure=assertThrows(UnsupportedOperationException.class,()->h.explosion.run(40));assertEquals(speed,h.pool().state(40).unsignedByte(ANIMATION_TIME));assertEquals(4,h.pool().state(40).unsignedByte(MAP_FRAME));assertTrue(h.draws.isEmpty());assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_FRAME_READ,assertInstanceOf(CdExplosion.UnsupportedExplosion.class,failure).boundary());
  }
 }
 @Test void originalAExplosionDrawFailureKeepsTheOriginalThrowableAndCompletedParentChildPrefix()throws Exception{
  try(var h=new Constructed()){
   h.seed(32,0x18,0,100,100);var original=new IllegalArgumentException("constructed original Draw fault");h.afterDraw=whole->{throw original;};
   assertSame(original,assertThrows(IllegalArgumentException.class,()->h.explosion.run(32)));assertEquals(2,h.pool().state(32).routine());assertEquals(0x23F088,h.pool().state(32).longBits(MAPPINGS));assertEquals(0x1C,h.pool().state(33).id());assertEquals(0,h.pool().state(33).routine());assertEquals(List.of(32),h.draws);
  }
 }

 private static AutoCloseable disposeInContext(){return ()->{try{SessionManager.clear();}finally{GameServices.graphics().cleanup();}};}
 private static Object closed(SonicCdGameModule m){return material(Arrays.asList(m.originalR11EntryFrame().captureActor(),m.originalR11EntryFrame().captureShared(),m.selectedProfileState(),m.programServicesState(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),m.originalR11PlacementImage().status(),m.originalR11PlacementImage().pool().slots(),m.originalR11PlacementImage().savedFlags(),m.originalR11FirstLoad().selection().inputs(),m.originalR11FirstLoad().selection().projection().getClass(),m.originalR11FirstLoad().foreground(),m.originalR11FirstLoad().background(),m.originalR11FirstLoad().copies(),m.originalR11FirstLoad().levelLoaded(),m.originalR11FirstLoad().standardLoaded(),m.originalR11FirstLoad().completedWaits(),m.originalR11FirstLoad().music(),m.originalR11WorldImage().orElseThrow().cpu(),m.originalR11WorldImage().orElseThrow().sounds()));}
 private static Object material(Object value){
  if(value==null)return List.of();if(value instanceof CdAssetBytes b)return List.of(CdAssetBytes.class,HexFormat.of().formatHex(b.copy()));
  if(value instanceof CdObjectDrawQueue.CpuSat c)return List.of(CdObjectDrawQueue.CpuSat.class,HexFormat.of().formatHex(c.bytes()),c.spriteCount(),material(c.provenance()));
  if(value instanceof Optional<?> o)return o.isPresent()?List.of(Optional.class,material(o.orElseThrow())):List.of(Optional.class);
  if(value instanceof List<?> xs){var result=new ArrayList<Object>();for(var x:xs)result.add(material(x));return List.copyOf(result);}
  if(value.getClass().isArray()){var result=new ArrayList<Object>();for(int i=0;i<java.lang.reflect.Array.getLength(value);i++)result.add(material(java.lang.reflect.Array.get(value,i)));return List.copyOf(result);}
  if(value.getClass().isRecord()){var result=new ArrayList<Object>();result.add(value.getClass());for(var c:value.getClass().getRecordComponents()){var a=c.getAccessor();if(!a.trySetAccessible())throw new AssertionError(c);try{result.add(material(a.invoke(value)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}return List.copyOf(result);}return value;
 }
 /** Explicitly constructed same-source placement, not the Module's private live bank. */
 private static final class Constructed implements AutoCloseable{
  final CdR11aAssets assets;final CdR11aObjectAssets metadata;final CdObjectPlacementRuntime placement;final CdObjectMappings mappings;final CdLogShadow log;final CdExplosion explosion;final CdPoints points;
  final List<Integer> draws=new ArrayList<>();final CdObjectDrawQueue.CameraWords camera=new CdObjectDrawQueue.CameraWords(0,0);CdObjectDrawQueue queue;java.util.function.IntConsumer afterDraw=whole->{};
  Constructed()throws Exception{assets=TestCdOriginalBootPrefix.source().assets();metadata=assets.openingObjects();placement=CdObjectPlacementRuntime.fromAssets(assets,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));mappings=CdObjectMappings.fromOpeningAssets(assets,metadata);nextPass();log=new CdLogShadow(placement,CdLogShadow.Assets.fromObjectAssets(metadata),this::draw);explosion=new CdExplosion(placement,CdExplosion.Assets.fromObjectAssets(metadata),this::draw);points=new CdPoints(placement,CdPoints.Assets.fromObjectAssets(metadata),this::draw);}
  CdObjectPool pool(){return placement.pool();}
  CdObjectState seed(int whole,int id,int routine,int x,int y){var s=pool().state(whole).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(SPRITE_FLAGS,4).moveWord(X,x).moveWord(Y,y);pool().write(s);return s;}
  CdLogShadow.Globals logGlobals(){return new CdLogShadow.Globals(1,0,0,0,camera.xWord());}
  void dispatch(int whole){switch(pool().state(whole).id()){case 0x33->log.run(whole,logGlobals());case 0x18->explosion.run(whole);case 0x1C->points.run(whole);case 0->{}default->throw new UnsupportedOperationException("constructed selector owns these three families only");}}
  void walk(){CdPhysicalObjectWalk.run(32,128,whole->{if(pool().state(whole).id()!=0)dispatch(whole);});}
  void draw(CdObjectPool actual,CdObjectState.Identity ref){assertSame(pool(),actual);var s=pool().state(ref.wholeIndex());draws.add(ref.wholeIndex());queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(ref.wholeIndex()),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)),camera);afterDraw.accept(ref.wholeIndex());}
  void nextPass(){if(queue!=null)queue.close();draws.clear();queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots(){public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){var s=pool().state(ref.wholeIndex());return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));}public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){var s=pool().state(ref.wholeIndex());pool().write(s.moveByte(SPRITE_FLAGS,(s.spriteFlags()&127)|(value?128:0)));}},mappings,new byte[640]);queue.beginPass();}
  CdObjectDrawQueue.CpuSat finish(){return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera,camera,camera));}
  public void close(){if(queue!=null)queue.close();}
 }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

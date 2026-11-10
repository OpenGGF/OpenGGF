package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/** SOURCE-FIRST / ALL JAVA UNRUN. First case: genuine public selected original R11 entry,
 * ordinary service and FULL/course replay. Other vectors explicitly construct same-original-source
 * component/placement data and mock reward completion ports, not live World/Frame authority.
 * No private bank access, Snapshot construction, actor/clock injection or natural encounter claim. */
class TestCdOriginalR11MonitorFamilies extends TestCdOriginalR11LoadingWorld {
 @Test void originalPublicWorldBirthAndBothOrdinaryReplayFormsKeepTheSameCompleteOwners()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeInContext()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int n=0;n<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;n++)step(loop,0);assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());
   var frame=module.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();step(loop,0);step(loop,8);var expected=closed(module);assertFalse(frame.closed());
   registry.restore(full);step(loop,0);step(loop,8);assertSame(frame,module.originalR11EntryFrame());assertEquals(expected,closed(module));
   registry.restoreCourse(course);step(loop,0);step(loop,8);assertSame(frame,module.originalR11EntryFrame());assertEquals(expected,closed(module));
  }
 }
 @Test void originalMetadataKeepsAllEightItemStaticPiecesAndTheExistingPowerupRoot()throws Exception{
  try(var h=new Constructed()){
   var data=CdMonitor.Assets.fromObjectAssets(h.metadata);assertSame(h.metadata.monitorTime(),data.visual());var map=data.visual().mappings();
   for(int kind=0;kind<8;kind++){int first=(short)map.unsignedWord(kind*2)+1;var piece=h.mappings.resolve(data.mappingsAddress()+first,true,kind);assertEquals(1,piece.pieces().size());assertEquals(map.unsignedByte(first+1),piece.pieces().getFirst().sizeByte());}
   var powerup=CdTimeStars.Assets.fromObjectAssets(h.metadata);assertEquals(9,powerup.animations().scriptCount());assertDoesNotThrow(()->h.mappings.resolve(powerup.mappingAddressBits(),false,0));assertTrue(h.draws.isEmpty());assertEquals(0,h.powerup);
  }
 }
 @Test void constructedMonitorInitMarksOnlyTheNativeSavedByteAndDoesNotCallSolidOffscreen()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,0,2);h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.LOADED);var before=h.placement.capture().savedFlags().copy();h.monitorRun(40);var s=h.pool().state(40);
   assertEquals(2,s.routine());assertEquals(0x46,s.unsignedByte(COLLISION_TYPE));assertEquals(2,s.unsignedByte(ANIMATION));assertEquals(0,h.solids);assertEquals(List.of(40),h.draws);before[12]&=127;assertArrayEquals(before,h.placement.capture().savedFlags().copy());h.finish();
  }
 }
 @Test void constructedAlreadyBrokenMonitorRetainsInitAndReturnsBeforeAnyDraw()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,0,2);h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);h.monitorRun(40);assertEquals(8,h.pool().state(40).routine());assertEquals(17,h.pool().state(40).unsignedByte(MAP_FRAME));assertTrue(h.draws.isEmpty());assertEquals(0,h.solids);
  }
 }
 @Test void constructedMonitorBreakCallsDestroyBeforeCreatingItsTwoNativeChildren()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,4,2);h.monitorRun(40);assertEquals(List.of("FM_DESTROY","DRAW40"),h.order);var item=h.pool().state(32);var explosion=h.pool().state(33);assertEquals(0x1A,item.id());assertEquals(2,item.unsignedByte(ANIMATION));assertEquals(0x18,explosion.id());assertEquals(1,explosion.unsignedByte(ROUTINE_2));assertEquals(1,explosion.subtype());assertEquals(0,item.routine());assertEquals(0,explosion.routine());assertEquals(8,h.pool().state(40).routine());assertEquals(0,h.pool().state(40).unsignedByte(COLLISION_TYPE));assertTrue((h.placement.capture().savedFlags().unsignedByte(12)&1)!=0);h.finish();
  }
 }
 @Test void constructedDestroyFailureKeepsItsOriginalThrowableBeforeAllocationOrSavedMarking()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,4,2);var before=h.placement.capture();var failure=new IllegalArgumentException("constructed original destroy fault");h.fmFailure=failure;
   assertSame(failure,assertThrows(IllegalArgumentException.class,()->h.monitorRun(40)));assertEquals(before.pool().slots(),h.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertTrue(h.draws.isEmpty());
  }
 }
 @Test void constructedFullPoolStillCompletesBreakWithoutAllocatingOrReplacingAnyOtherImage()throws Exception{
  try(var h=new Constructed()){
   for(int w=32;w<128;w++)h.pool().write(h.pool().state(w).moveByte(ID,0x7F));h.seedMonitor(40,4,2);var before=h.pool().capture();h.monitorRun(40);for(int w=32;w<128;w++)if(w!=40)assertEquals(before.slots().get(w-32),h.pool().state(w));assertEquals(8,h.pool().state(40).routine());assertEquals(List.of(40),h.draws);
  }
 }
 @Test void sourceOwnerMonitorAnimationRetainsResetAndDecrementBeforeItsDirectoryFault()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,6,2);h.pool().write(h.pool().state(40).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,9).moveByte(ANIMATION_TIME,4));var saved=h.placement.capture().savedFlags();
   assertThrows(UnsupportedOperationException.class,()->h.monitorRun(40));var s=h.pool().state(40);assertEquals(255,s.unsignedByte(PREVIOUS_ANIMATION));assertEquals(0,s.unsignedByte(ANIMATION_FRAME));assertEquals(255,s.unsignedByte(ANIMATION_TIME));assertArrayEquals(saved.copy(),h.placement.capture().savedFlags().copy());assertTrue(h.draws.isEmpty());
  }
 }
 @Test void sourceOwnerMonitorAnimationRetainsTheRealSpeedBeforeALaterFrameFault()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,6,2);h.pool().write(h.pool().state(40).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0));var a=h.metadata.monitorTime().animationTable();int speed=a.unsignedByte((short)a.unsignedWord(0));
   assertThrows(UnsupportedOperationException.class,()->h.monitorRun(40));assertEquals(speed,h.pool().state(40).unsignedByte(ANIMATION_TIME));assertTrue(h.draws.isEmpty());
  }
 }
 @Test void constructedOnscreenMonitorUsesOneGenuineSolidOutcomeAndCurrentReadback()throws Exception{
  try(var h=new Constructed()){
   h.seedMonitor(40,2,2);h.pool().write(h.pool().state(40).moveByte(SPRITE_FLAGS,0x84));var player=h.player;h.monitorRun(40);assertEquals(1,h.solids);assertEquals(player,h.player);assertEquals(List.of(40),h.draws);h.finish();
  }
 }
 @Test void constructedItemInitUsesItsActualOddStaticPieceAndRetainsNativeMotion()throws Exception{
  try(var h=new Constructed()){
   h.seedItem(40,0,2);h.items.run(40,Optional.of(new CdMonitorItem.PrimarySupport(1,2)));var s=h.pool().state(40);var data=CdMonitor.Assets.fromObjectAssets(h.metadata);int piece=(short)data.visual().mappings().unsignedWord(4)+1;
   assertEquals(data.mappingsAddress()+piece,s.longBits(MAPPINGS));assertEquals(2,s.routine());assertEquals(-0x2E8,s.signedWord(Y_VELOCITY));assertEquals(97,s.centreY());assertTrue(h.rewardRequests.isEmpty());assertEquals(List.of(40),h.draws);h.finish();
  }
 }
 @Test void constructedTypedRingShieldAndShoesRequestsOccurOnlyAfterTheSameNativeRewardPrefix()throws Exception{
  try(var h=new Constructed()){
   for(int kind:new int[]{1,2,4}){h.seedItem(40,2,kind);h.items.run(40,Optional.of(new CdMonitorItem.PrimarySupport(1,2)));assertEquals(4,h.pool().state(40).routine());assertEquals(29,h.pool().state(40).word(MONITOR_ITEM_DELETE));}
   assertEquals(List.of(1,2,4),h.rewardRequests);assertEquals(0,h.reserved.state(6).unsignedByte(ID));assertEquals(0,h.powerup);
   // Mock completed ports deliberately do not claim any private World/global or Frame adoption.
  }
 }
 @Test void constructedUnownedAwardsRetainRoutineFourWord29WithoutAnyOtherRewardOrShieldWork()throws Exception{
  try(var h=new Constructed()){
   for(int kind:new int[]{0,3,5,6,7}){h.seedItem(40,2,kind);var raw=h.rawReserved.clone();var failure=assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,()->h.items.run(40,Optional.empty()));assertEquals(kind,failure.value());assertEquals(4,h.pool().state(40).routine());assertEquals(29,h.pool().state(40).word(MONITOR_ITEM_DELETE));assertArrayEquals(raw,h.rawReserved);}
   assertTrue(h.rewardRequests.isEmpty());assertEquals(0,h.powerup);
  }
 }
 @Test void constructedShieldInitDoesNotDrawAndItsLaterMainOnlyIssuesTheActualPowerupLatchStores()throws Exception{
  try(var h=new Constructed()){
   h.reserved.write(h.reserved.state(6).moveByte(ID,3));h.shield.run(6);assertEquals(2,h.reserved.state(6).unsignedByte(ROUTINE));assertTrue(h.reads.isEmpty());assertTrue(h.draws.isEmpty());assertTrue(h.powerupWrites.isEmpty());
   h.shield.run(6);assertEquals(List.of(2,0x82),h.powerupWrites);assertEquals(List.of(6),h.draws);assertEquals(200,h.reserved.state(6).word(X));assertEquals(150,h.reserved.state(6).word(Y));assertEquals(640,h.finish().bytes().length);
   // The component has no art uploader. Actual World VInt ownership remains Root-qualified work.
  }
 }
 @Test void constructedShieldConditionalReadsStopBeforeUnreachedOwnersAndZeroShieldDeletesAll64()throws Exception{
  try(var h=new Constructed()){
   h.reserved.write(h.reserved.state(6).moveByte(ID,3).moveByte(ROUTINE,2));h.warp=1;h.shield.run(6);assertEquals(List.of(CdShieldPowerup.ReadField.SHIELD,CdShieldPowerup.ReadField.TIME_WARP),h.reads);assertTrue(h.draws.isEmpty());
   h.reads.clear();h.contactShield=0;h.shield.run(6);assertEquals(List.of(CdShieldPowerup.ReadField.SHIELD),h.reads);assertArrayEquals(new byte[64],h.reserved.state(6).copyBytes());assertTrue(h.powerupWrites.isEmpty());
  }
 }
 @Test void constructedRawReservedCaptureHasOneBackingAndRestoresEveryOtherByte()throws Exception{
  try(var h=new Constructed()){
   for(int i=0;i<h.rawReserved.length;i++)h.rawReserved[i]=(byte)(i*17);var raw=h.rawReserved.clone();var capture=h.reserved.capture();h.reserved.write(h.reserved.state(6).moveByte(ID,3));for(int i=0;i<raw.length;i++)if(i!=5*64)assertEquals(raw[i],h.rawReserved[i]);h.reserved.restore(capture);assertArrayEquals(raw,h.rawReserved);
  }
 }
 private static AutoCloseable disposeInContext(){return ()->{try{SessionManager.clear();}finally{GameServices.graphics().cleanup();}};}
 private static Object closed(SonicCdGameModule m){var f=m.originalR11EntryFrame();var first=m.originalR11FirstLoad();var placed=m.originalR11PlacementImage();var world=m.originalR11WorldImage().orElseThrow();return material(Arrays.asList(f.captureActor(),f.captureShared(),m.selectedProfileState(),m.programServicesState(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),placed.status(),placed.pool().slots(),placed.savedFlags(),first.selection().inputs(),first.selection().projection().getClass(),first.foreground(),first.background(),first.copies(),first.levelLoaded(),first.standardLoaded(),first.completedWaits(),first.music(),world.cpu(),world.sounds()));}
 private static Object material(Object value){if(value==null)return List.of();if(value instanceof CdAssetBytes b)return List.of(CdAssetBytes.class,HexFormat.of().formatHex(b.copy()));if(value instanceof CdObjectDrawQueue.CpuSat c)return List.of(CdObjectDrawQueue.CpuSat.class,HexFormat.of().formatHex(c.bytes()),c.spriteCount(),material(c.provenance()));if(value instanceof Optional<?> o)return o.isPresent()?List.of(Optional.class,material(o.orElseThrow())):List.of(Optional.class);if(value instanceof List<?> xs){var out=new ArrayList<Object>();for(var x:xs)out.add(material(x));return List.copyOf(out);}if(value.getClass().isArray()){var out=new ArrayList<Object>();for(int i=0;i<java.lang.reflect.Array.getLength(value);i++)out.add(material(java.lang.reflect.Array.get(value,i)));return List.copyOf(out);}if(value.getClass().isRecord()){var out=new ArrayList<Object>();out.add(value.getClass());for(var c:value.getClass().getRecordComponents()){var a=c.getAccessor();if(!a.trySetAccessible())throw new AssertionError(c);try{out.add(material(a.invoke(value)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}return List.copyOf(out);}return value;}
 /** Constructed component DATA and mock completion ports; never the private Module banks. */
 private static final class Constructed implements AutoCloseable{
  final CdR11aAssets assets;final CdR11aObjectAssets metadata;final CdObjectPlacementRuntime placement;final CdObjectMappings mappings;final CdReservedObjectBank reserved;final CdMonitor monitor;final CdMonitorItem items;final CdShieldPowerup shield;
  final byte[] rawReserved=new byte[31*64];final List<Integer> draws=new ArrayList<>(),rewardRequests=new ArrayList<>(),powerupWrites=new ArrayList<>();final List<String> order=new ArrayList<>();final List<CdShieldPowerup.ReadField> reads=new ArrayList<>();final CdObjectDrawQueue queue;
  CdSolidObject.Player player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(0x20000000,0x1000000,0,0,0,2,0,2,4,9,19,0,0,0,0,0,0,0,0,0),0,2,0,0),1,0,0);CdSolidObject.Globals solidGlobals=new CdSolidObject.Globals(0,0,0,0);final CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);int solids,powerup,contactShield=1,warp,invincible;RuntimeException fmFailure;
  Constructed()throws Exception{
   assets=TestCdOriginalBootPrefix.source().assets();metadata=assets.openingObjects();placement=CdObjectPlacementRuntime.fromAssets(assets,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));mappings=CdObjectMappings.fromOpeningAssets(assets,metadata);
   reserved=CdReservedObjectBank.backedBy(new CdReservedObjectBank.RawBacking(){public CdAssetBytes read(){return CdAssetBytes.copyOf(rawReserved);}public boolean compareAndSet(CdAssetBytes expected,CdAssetBytes next){if(!Arrays.equals(rawReserved,expected.copy()))return false;System.arraycopy(next.copy(),0,rawReserved,0,rawReserved.length);return true;}});
   queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots(){public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){byte[] b=ref.wholeIndex()<32?reserved.state(ref.wholeIndex()).copyBytes():pool().state(ref.wholeIndex()).copyBytes();var v=CdAssetBytes.copyOf(b);return new CdObjectDrawQueue.LiveDrawSlot(v.unsignedByte(0),v.unsignedByte(1),v.unsignedWord(8),v.unsignedWord(12),v.unsignedWord(10),(int)v.unsignedLong(4),v.unsignedByte(26),v.unsignedWord(2));}public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){if(ref.wholeIndex()<32){var s=reserved.state(ref.wholeIndex());reserved.write(s.moveByte(SPRITE_FLAGS,(s.unsignedByte(SPRITE_FLAGS)&127)|(value?128:0)));}else{var s=pool().state(ref.wholeIndex());pool().write(s.moveByte(SPRITE_FLAGS,(s.spriteFlags()&127)|(value?128:0)));}}},mappings,new byte[640],List.of(new CdObjectDrawQueue.CdSlotRef(6)));queue.beginPass();
   monitor=CdMonitor.withPastOwner(placement,CdMonitor.Assets.fromObjectAssets(metadata),CdSolidObject.fromAssets(assets),new CdPlayerTerrain(assets.terrain()),new CdMonitor.PrefixSolidOwner(){public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut c){assertEquals(player,c.expectedPlayer());return live();}public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall c){solids++;var o=c.outcome();CdObjectState object;List<CdObjectState> other;if(o instanceof CdSolidObject.Completed done){var r=done.completedResult();object=r.object();other=r.otherSlots();player=r.player();solidGlobals=r.globals();}else{var r=((CdSolidObject.NativeStopped)o).prefix();object=r.object();other=r.otherSlots();player=r.player();solidGlobals=r.globals();}var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(other);pool().restore(CdObjectPool.overlay(c.cut().callCut(),images));return live();}},(expected,value)->solidGlobals=new CdSolidObject.Globals(solidGlobals.invincible(),solidGlobals.scoreChain(),value,solidGlobals.secondaryAngle()),this::draw,s->{order.add("FM_DESTROY");if(fmFailure!=null)throw fmFailure;});
   items=CdMonitorItem.withRingAndShieldAward(placement,CdMonitor.Assets.fromObjectAssets(metadata),c->{reward(c.identity(),4);return new CdMonitorItem.AwardResult(List.of(CdMonitorItem.AwardSound.SCMD_SHOESMUS));},c->{reward(c.identity(),2);return new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);},c->{reward(c.identity(),1);return new CdMonitorItem.RingAwardResult(CdMonitorItem.RingAwardSound.FM_RING);},this::draw);
   shield=new CdShieldPowerup(reserved,CdTimeStars.Assets.fromObjectAssets(metadata),f->{reads.add(f);return switch(f){case SHIELD->contactShield;case TIME_WARP->warp;case INVINCIBLE->invincible;case PLAYER_X_WORD->200;case PLAYER_Y_WORD->150;case PLAYER_FLAGS->2;case ZONE,LAYER->0;};},new CdShieldPowerup.PowerupPort(){public int read(){return powerup;}public void write(int v){powerupWrites.add(v);powerup=v;}},(bank,ref)->{assertSame(reserved,bank);drawReserved(ref.wholeIndex());});
  }
  CdMonitor.LiveSolid live(){return new CdMonitor.LiveSolid(player,gates,solidGlobals);}CdObjectPool pool(){return placement.pool();}
  void reward(CdObjectState.Identity ref,int kind){assertEquals(4,pool().state(ref.wholeIndex()).routine());assertEquals(29,pool().state(ref.wholeIndex()).word(MONITOR_ITEM_DELETE));rewardRequests.add(kind);}
  void seedMonitor(int w,int routine,int subtype){pool().write(pool().state(w).moveByte(ID,0x19).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(ANIMATION,subtype).moveByte(SAVED_FLAGS_ID,3).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,15).moveByte(PRIORITY,3).moveByte(COLLISION_TYPE,0x46).moveWord(X,100).moveWord(Y,100).moveLong(MAPPINGS,CdMonitor.Assets.fromObjectAssets(metadata).mappingsAddress()));}
  void seedItem(int w,int routine,int kind){var a=CdMonitor.Assets.fromObjectAssets(metadata);int pointer=a.mappingsAddress()+(short)a.visual().mappings().unsignedWord(2*kind)+1;pool().write(pool().state(w).moveByte(ID,0x1A).moveByte(ROUTINE,routine).moveByte(ANIMATION,kind).moveByte(SPRITE_FLAGS,0x24).moveByte(WIDTH,8).moveByte(PRIORITY,3).moveWord(X,100).moveWord(Y,100).moveWord(Y_VELOCITY,0).moveLong(MAPPINGS,pointer));}
  void monitorRun(int whole){monitor.runPast(whole,new CdMonitor.PastGlobals(){public int rawTimeZone(){return 1;}public int rawTimeWarpDirection(){return 0;}public int cameraXWord(){return 0;}public int timeAttackMode(){return 0;}public int zone(){return 0;}public int layer(){return 0;}public int timeStopTimer(){return 0;}},new CdMonitor.SolidInputs(player,gates,solidGlobals));}
  void draw(CdObjectPool p,CdObjectState.Identity ref){assertSame(pool(),p);var s=p.state(ref.wholeIndex());draws.add(ref.wholeIndex());order.add("DRAW"+ref.wholeIndex());queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(ref.wholeIndex()),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)),new CdObjectDrawQueue.CameraWords(0,0));}
  void drawReserved(int w){var s=reserved.state(w);draws.add(w);queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(w),s.unsignedByte(SPRITE_FLAGS),s.word(X),s.word(Y),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)),new CdObjectDrawQueue.CameraWords(0,0));}
  CdObjectDrawQueue.CpuSat finish(){var c=new CdObjectDrawQueue.CameraWords(0,0);return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(c,c,c));}public void close(){queue.close();}
 }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

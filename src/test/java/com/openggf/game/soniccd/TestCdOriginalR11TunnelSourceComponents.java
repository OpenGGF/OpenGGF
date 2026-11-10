package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
/** Actual reader-backed metadata with explicit component vectors. These are not natural-route witnesses.
 * No capture construction or reflection. All Java/discovery/runtime UNRUN. */
class TestCdOriginalR11TunnelSourceComponents {
 @Test void originalSplashPublishesItsDistinctOperandBeforeFirstSoundFaultAndRetiresReceipt()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);int w=43;
  p.pool().write(p.pool().state(w).moveByte(ID,0x0B).moveByte(SUBTYPE,2).moveByte(SPRITE_FLAGS,0xBB).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF));
  var before=p.capture();var holder=new CdTunnelDoorSplash[1];var last=new Object[1];var original=new AssertionError("first actual sound");var failed=new int[1];
  holder[0]=CdTunnelDoorSplash.fromOriginalPresentAssets(p,a,new CdTunnelDoorSplash.DispatchObserver(){
   public void beforePublication(CdTunnelDoorSplash.Publication c){assertTrue(holder[0].isCurrentCall(c));assertFalse(holder[0].isCurrentCall(new CdTunnelDoorSplash.Publication(c.ordinal(),c.kind(),c.cut(),c.source(),c.before(),c.output())));}
   public void afterPublication(CdTunnelDoorSplash.Publication c){assertTrue(holder[0].isCurrentCallCompleted(c));}
   public void beforeCall(Object c){last[0]=c;assertTrue(holder[0].isCurrentCall(c));}
   public void afterCall(Object c){fail("Sound must stop");}
   public void failed(Object c,Throwable fault){assertSame(original,fault);assertTrue(holder[0].isCurrentCall(c));failed[0]++;}
  },c->fail("No draw after first sound fault"),c->{assertEquals(CdTunnelDoorSplash.FmSound.FM_A1,c.sound());assertEquals(0x23EFC6,p.pool().state(w).longBits(CdObjectState.LongField.MAPPINGS));assertEquals(4,p.pool().state(w).spriteFlags());throw original;},
  new CdTunnelDoorSplash.ReceiptGuard(){public void prepared(Object c){assertTrue(holder[0].isCurrentCall(c));}public void completed(Object c){assertTrue(holder[0].isCurrentCallCompleted(c));}});
  assertSame(original,assertThrows(AssertionError.class,()->holder[0].run(w)));assertEquals(1,failed[0]);assertFalse(holder[0].isCurrentCall(last[0]));
  var live=p.pool().state(w);assertEquals(2,live.routine());assertEquals(2,live.unsignedByte(ANIMATION));assertEquals(0xCAFE,live.word(X_FRACTION));assertEquals(0xBEEF,live.word(Y_FRACTION));assertEquals(before.status(),p.capture().status());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());
  for(int i=32;i<128;i++)if(i!=w)assertEquals(before.pool().slots().get(i-32),p.pool().state(i));
 }
 @Test void originalVerticalResetUsesHighByteCarryNoSolidAndFreshDrawFlags()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);int w=44;
  var old=p.pool().state(w).moveByte(ID,0x35).moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x84).moveWord(TUNNEL_DOOR_TIMER,0xF87B).moveByte(MAP_FRAME,1).moveWord(X,0x1000).moveWord(Y,0x2000);p.pool().write(old);var baseline=p.capture();
  var holder=new CdTunnelDoorVertical[1];var last=new Object[1];var kinds=new ArrayList<CdTunnelDoorVertical.OriginalSite>();
  var observer=new CdTunnelDoorVertical.OriginalObserver(){public void before(CdTunnelDoorVertical.OriginalCall c){last[0]=c;assertTrue(holder[0].isCurrentOriginalCall(c));kinds.add(c.site());}public void after(CdTunnelDoorVertical.OriginalCall c){assertTrue(c.completed());}public void failed(CdTunnelDoorVertical.OriginalCall c,Throwable t){fail(t);}};
  try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
   holder[0]=CdTunnelDoorVertical.fromOriginalPresentAssets(p,a,CdSolidObject.fromAssets(a),c->fail("Reset has no Solid"),(pool,id)->{assertTrue(holder[0].isCurrentDrawCall(pool,id));pool.write(pool.state(w).moveByte(SPRITE_FLAGS,4));},sound->fail("Reset has no sound"),observer);
   var input=solidInputs(frame);holder[0].run(w,new CdTunnelDoorVertical.Globals(1,0,0x1080),new CdTunnelDoorVertical.SolidInputs(input.player(),input.gates(),input.globals()));
  }
  assertEquals(old.moveByte(ROUTINE,2).moveByte(MAP_FRAME,0).moveWord(TUNNEL_DOOR_TIMER,0x007B).moveByte(SPRITE_FLAGS,4),p.pool().state(w));assertEquals(List.of(CdTunnelDoorVertical.OriginalSite.SOURCE,CdTunnelDoorVertical.OriginalSite.DRAW),kinds);assertFalse(holder[0].isCurrentOriginalCall(last[0]));
  for(int i=32;i<128;i++)if(i!=w)assertEquals(baseline.pool().slots().get(i-32),p.pool().state(i));assertArrayEquals(baseline.savedFlags().copy(),p.capture().savedFlags().copy());
 }
 @Test void originalVerticalSoundFaultRetainsOpenPrefixBeforeFrameOneAndSolid()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);int w=45;var fault=new AssertionError("A4");var holder=new CdTunnelDoorVertical[1];var pending=new Object[1];
  try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
   var m=frame.state().motion();p.pool().write(p.pool().state(w).moveByte(ID,0x35).moveByte(SPRITE_FLAGS,0x84).moveByte(MAP_FRAME,3).moveWord(TUNNEL_DOOR_TIMER,0xBEEF).moveWord(X,((m.centreX16_16()>>>16)+16)&65535).moveWord(Y,m.centreY16_16()>>>16));
   holder[0]=CdTunnelDoorVertical.fromOriginalPresentAssets(p,a,CdSolidObject.fromAssets(a),c->fail("No Solid after sound failure"),(pool,id)->fail("No Draw after sound failure"),sound->{assertEquals(4,p.pool().state(w).routine());assertEquals(0,p.pool().state(w).word(TUNNEL_DOOR_TIMER));assertEquals(3,p.pool().state(w).unsignedByte(MAP_FRAME));throw fault;},new CdTunnelDoorVertical.OriginalObserver(){public void before(CdTunnelDoorVertical.OriginalCall c){pending[0]=c;assertTrue(holder[0].isCurrentOriginalCall(c));}public void after(CdTunnelDoorVertical.OriginalCall c){}public void failed(CdTunnelDoorVertical.OriginalCall c,Throwable t){assertSame(fault,t);assertFalse(c.completed());}});
   var input=solidInputs(frame);assertSame(fault,assertThrows(AssertionError.class,()->holder[0].run(w,new CdTunnelDoorVertical.Globals(1,0,0),new CdTunnelDoorVertical.SolidInputs(input.player(),input.gates(),input.globals()))));assertFalse(holder[0].isCurrentOriginalCall(pending[0]));
  }
 }
 @Test void originalDoorRejectsRealNonclearedSecondaryAfterPrimaryAndKeepsExactPrefix()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);int w=46;
  p.pool().write(p.pool().state(w).moveByte(ID,0x0D).moveWord(X,0x1000).moveWord(Y,0x300).moveWord(X_FRACTION,0x1234));var before=p.capture();
  var holder=new CdTunnelDoor[1];var order=new ArrayList<String>();var last=new Object[1];var raw=new byte[64];raw[0x27]=1;
  try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
   holder[0]=CdTunnelDoor.fromOriginalPresentAssets(p,a,()->{assertTrue(holder[0].isCurrentCall(last[0]));order.add("secondary-read");return CdAssetBytes.copyOf(raw);},CdSolidObject.fromAssets(a),new CdTunnelDoor.PrefixSolidOwner(){
    public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut c){assertEquals(CdTunnelDoor.Participant.PRIMARY,c.participant());assertTrue(holder[0].isCurrentCall(c));order.add("primary-preflight");var i=solidInputs(frame);return new CdTunnelDoor.LiveSolid(i.player(),i.gates(),i.globals());}
    public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){assertTrue(holder[0].isCurrentCall(c));order.add("primary-commit");var done=assertInstanceOf(CdSolidObject.Completed.class,c.outcome()).completedResult();var images=new ArrayList<CdObjectState>();images.add(done.object());images.addAll(done.otherSlots());var fp=frame.prepareObjectSolidResult(c.cut().expectedPrimary(),c.cut().expectedGlobals(),done);frame.validatePreparedObjectSolidPublication(fp);p.pool().restore(CdObjectPool.overlay(c.cut().callCut(),images));frame.applyPreparedObjectSolidPublication(fp);var i=solidInputs(frame);return new CdTunnelDoor.LiveSolid(i.player(),i.gates(),i.globals());}
   },(pool,id)->fail("Rejected secondary cannot reach Draw"),sound->fail("No opening at this rectangle"),new CdTunnelDoor.DispatchObserver(){
    public void beforePublication(CdTunnelDoor.Publication c){assertTrue(holder[0].isCurrentCall(c));assertFalse(holder[0].isCurrentCall(new CdTunnelDoor.Publication(c.ordinal(),c.kind(),c.cut(),c.source(),c.output(),c.savedIndex())));}
    public void afterPublication(CdTunnelDoor.Publication c){}public void beforeCall(Object c){last[0]=c;}public void afterCall(Object c,Object value){}
   });var i=solidInputs(frame);
   var stopped=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,()->holder[0].run(w,new CdTunnelDoor.Globals(1,0,0x1080),new CdTunnelDoor.SolidInputs(i.player(),i.gates(),i.globals())));
   assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_PLAYER_OWNER,stopped.boundary());assertEquals(0x27,stopped.value());assertEquals(List.of("primary-preflight","primary-commit","secondary-read"),order);assertFalse(holder[0].isCurrentCall(last[0]));
  }
  assertEquals(2,p.pool().state(w).routine());assertEquals(0x1234,p.pool().state(w).word(X_FRACTION));assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());for(int i=32;i<128;i++)if(i!=w)assertEquals(before.pool().slots().get(i-32),p.pool().state(i));
 }
 @Test void originalPathReadsItsActualTablesAndOriginalReceiptsCannotBeCopied()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);int w=47;var holder=new CdTunnelPath[1];var last=new Object[1];
  p.pool().write(p.pool().state(w).moveByte(ID,7).moveWord(X,0x1000).moveWord(Y,0x300).moveWord(X_FRACTION,0xBEEF));var before=p.capture();
  try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
   holder[0]=CdTunnelPath.fromOriginalPresentAssets(p,a,new CdTunnelPath.PlayerOwner(){public CdTunnelPath.PlayerCut read(){assertTrue(holder[0].isCurrentCall(last[0]));return new CdTunnelPath.PlayerCut(frame.state(),frame.environment());}public void commit(CdTunnelPath.PlayerCall c){fail("Far component rectangle cannot acquire player");}},c->fail("No sound outside rectangle"),new CdTunnelPath.DispatchObserver(){
    public void beforeOperation(Object c){last[0]=c;assertTrue(holder[0].isCurrentCall(c));if(c instanceof CdTunnelPath.WordReadCall d)assertFalse(holder[0].isCurrentCall(new CdTunnelPath.WordReadCall(d.cut(),d.source(),d.site(),d.addressBits())));}
    public void afterOperation(Object c){holder[0].currentResult(c);}public void failedOperation(Object c,Throwable t){fail(t);}
   },new CdTunnelPath.ReceiptGuard(){public void prepared(Object c){assertTrue(holder[0].isCurrentCall(c));}public void completed(Object c){holder[0].currentResult(c);}});
   holder[0].run(w,new CdTunnelPath.Globals(1,0,0x1080));assertFalse(holder[0].isCurrentCall(last[0]));
  }
  var result=p.pool().state(w);assertEquals(2,result.routine());assertEquals(0x88,result.word(TUNNEL_PATH_INDEX_END));assertEquals(0x208DA0,result.longBits(CdObjectState.LongField.TUNNEL_PATH_DATA));assertEquals(0x1440,result.word(TUNNEL_PATH_TARGET_X));assertEquals(0xF0,result.word(TUNNEL_PATH_TARGET_Y));assertEquals(0xBEEF,result.word(X_FRACTION));assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());for(int i=32;i<128;i++)if(i!=w)assertEquals(before.pool().slots().get(i-32),p.pool().state(i));
 }
 private static CdFloatBlock.SolidInputs solidInputs(CdPlayerFrameRuntime f){
  var s=f.state();var e=f.environment();var h=s.header();var g=e.movement().globals();
  return new CdFloatBlock.SolidInputs(new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject()),new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle()));
 }
}

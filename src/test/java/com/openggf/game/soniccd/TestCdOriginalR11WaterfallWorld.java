package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdSpinTunnelSplash;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;

/** SOURCE/UNRUN. Actual public original New Game/Frame/physical pass, never a DEV Frame,
 * seeded pool, reconstructed occurrence or effect replay. The fixed caller recipe is an
 * unqualified positive obligation: any earlier unsupported edge/death fails it. Reflection
 * observes the private cached binding or substitutes a fault-only wrapper around its real
 * synchronous guard; it never grants a native call or changes gameplay/capture data. */
class TestCdOriginalR11WaterfallWorld extends TestCdOriginalR11LoadingWorld {
 @Test void cachedBodyBorrowsTheActualOriginalEightByteAnimationAndMappingRoot()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var b=binding(m);var kernel=kernel(b);
   var assets=(CdR11aAssets)field(b,"assets");var metadata=(CdR11aObjectAssets)field(b,"metadata");
   var data=(CdSpinTunnelSplash.Data)field(kernel,"data");
   assertSame(assets,source.assets());var reloaded=assets.openingObjects();assertEquals(metadata.identity(),reloaded.identity());
   for(var table:List.of(CdR11aObjectAssets.Table.TUNNEL_WATERFALL_ANIMATIONS,CdR11aObjectAssets.Table.TUNNEL_WATERFALL_MAPPINGS)){assertEquals(metadata.view(table).range(),reloaded.view(table).range());assertArrayEquals(metadata.view(table).bytes().copy(),reloaded.view(table).bytes().copy());}
   assertSame(data,field(b,"waterfallData"));
   assertSame(metadata.view(CdR11aObjectAssets.Table.TUNNEL_WATERFALL_ANIMATIONS).bytes(),data.animation().bytes());
   assertEquals(8,data.animation().bytes().length());assertEquals(0,data.animation().tableOffset());assertEquals(1,data.animation().scriptCount());
   assertEquals(0x23F05C,data.mappingsAddress());assertEquals(24,metadata.view(CdR11aObjectAssets.Table.TUNNEL_WATERFALL_MAPPINGS).bytes().length());
   assertTrue(m.originalR11EntryFrame().stageSource().ownsPresent(assets));
   assertFalse((boolean)field(b,"waterfallDispatch"));assertNull(field(b,"waterfallOperation"));
   var placed=(CdObjectPlacementRuntime)field(b,"placement");var before=placed.capture();var actor=m.originalR11EntryFrame().captureActor();var shared=m.originalR11EntryFrame().captureShared();
   var dispatch=b.getClass().getDeclaredMethod("waterfall",int.class);dispatch.setAccessible(true);
   var denied=assertThrows(java.lang.reflect.InvocationTargetException.class,()->dispatch.invoke(b,32));assertInstanceOf(IllegalStateException.class,denied.getCause());assertEquals(0,denied.getCause().getSuppressed().length);
   assertPool(before,placed.capture());assertEquals(actor,m.originalR11EntryFrame().captureActor());assertEquals(shared,m.originalR11EntryFrame().captureShared());
   SessionManager.clear();var retired=assertThrows(java.lang.reflect.InvocationTargetException.class,()->dispatch.invoke(b,32));assertInstanceOf(IllegalStateException.class,retired.getCause());assertPool(before,placed.capture());
  }
 }
 @Test void naturalPublicWaterfallReturnsAndFullCourseReplayUsesTheSameOwner()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var frame=m.originalR11EntryFrame();var b=binding(m);var body=kernel(b);
   var guard=(CdSpinTunnelSplash.Guard)field(body,"guard");var last=new AtomicReference<CdSpinTunnelSplash.Operation>();
   replace(body,"guard",new CdSpinTunnelSplash.Guard(){public void before(CdSpinTunnelSplash.Operation c){last.set(c);guard.before(c);}public void after(CdSpinTunnelSplash.Operation c){guard.after(c);}});
   var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   var loads=m.programServicesState().loads();var services=m.programServicesState().completedCalls();int rows=reachReturnedWaterfall(loop,m);var expected=image(m);
   assertNotNull(last.get());assertTrue(last.get().completed());assertFalse(body.isCurrentCall(last.get()));
   assertFalse((boolean)field(b,"waterfallDispatch"));assertNull(field(b,"waterfallOperation"));
   var placed=(CdObjectPlacementRuntime)field(b,"placement");var before=placed.capture();
   assertThrows(IllegalStateException.class,()->guard.before(last.get()));assertPool(before,placed.capture());
   int whole=last.get().wholeIndex();if(placed.pool().state(whole).id()==CdSpinTunnelSplash.OBJECT_ID){
    var denied=assertThrows(IllegalStateException.class,()->body.run(whole));assertEquals(0,denied.getSuppressed().length,"A refused idle before must not manufacture a source-changed unwind fault");assertPool(before,placed.capture());
    var threadFailure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{body.run(whole);}catch(Throwable fault){threadFailure.set(fault);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,threadFailure.get());assertPool(before,placed.capture());
   }
   registry.restore(full);for(int n=0;n<rows;n++)returned(loop,m,n);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);for(int n=0;n<rows;n++)returned(loop,m,n);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   assertSame(frame,m.originalR11EntryFrame());assertSame(body,kernel(binding(m)));assertSame(loads,m.programServicesState().loads());assertEquals(services,m.programServicesState().completedCalls());
  }
 }
 @Test void refusedOriginalBeforeKeepsTheExactFaultWithoutFalseUnwindSuppression()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var b=binding(m);var body=kernel(b);var placed=(CdObjectPlacementRuntime)field(b,"placement");var guard=(CdSpinTunnelSplash.Guard)field(body,"guard");
   var fault=new Fault("refused genuine waterfall dispatch");var call=new AtomicReference<CdSpinTunnelSplash.Operation>();
   replace(body,"guard",new CdSpinTunnelSplash.Guard(){
    public void before(CdSpinTunnelSplash.Operation c){if(c.kind()==CdSpinTunnelSplash.Kind.DISPATCH){call.set(c);throw fault;}guard.before(c);}
    public void after(CdSpinTunnelSplash.Operation c){guard.after(c);}
   });
   assertSame(fault,assertThrows(Fault.class,()->driveToFault(loop,m)));assertNotNull(call.get());assertFalse(call.get().completed());
   assertPool(call.get().cut(),placed.capture());assertFalse(body.isCurrentCall(call.get()));assertEquals(0,fault.getSuppressed().length);
   assertNull(field(b,"waterfallOperation"));assertFalse((boolean)field(b,"waterfallDispatch"));
  }
 }
 @Test void landedMappingFaultRetainsTheNativePrefixAndRunsTheRealAfterGuard()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot();var cleanup=disposeWhileCurrent()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var b=binding(m);var body=kernel(b);var placed=(CdObjectPlacementRuntime)field(b,"placement");var guard=(CdSpinTunnelSplash.Guard)field(body,"guard");
   var fault=new Fault("landed genuine mapping store");var call=new AtomicReference<CdSpinTunnelSplash.Operation>();var after=new int[1];
   replace(body,"guard",new CdSpinTunnelSplash.Guard(){
    public void before(CdSpinTunnelSplash.Operation c){guard.before(c);}
    public void after(CdSpinTunnelSplash.Operation c){
     if(c.kind()!=CdSpinTunnelSplash.Kind.INIT_MAPPING||!c.completed()){guard.after(c);return;}
     call.set(c);try{guard.after(c);after[0]++;}catch(RuntimeException|Error verification){if(verification!=fault)fault.addSuppressed(verification);}throw fault;
    }
   });
   assertSame(fault,assertThrows(Fault.class,()->driveToFault(loop,m)));var c=Objects.requireNonNull(call.get());assertTrue(c.completed());assertEquals(1,after[0]);assertEquals(0,fault.getSuppressed().length,"Landed store must not be compared with stale pre-store source identity");
   var live=placed.pool().state(c.wholeIndex());assertEquals(c.output(),live);assertEquals(2,live.routine());assertEquals(4,c.before().spriteFlags()&4);assertEquals(0x23F05C,live.longBits(MAPPINGS));
   var expected=new ArrayList<>(c.cut().pool().slots());expected.set(c.wholeIndex()-32,c.output());assertEquals(expected,placed.capture().pool().slots());assertEquals(c.cut().status(),placed.capture().status());assertArrayEquals(c.cut().savedFlags().copy(),placed.capture().savedFlags().copy());
   assertFalse(body.isCurrentCall(c));assertNull(field(b,"waterfallOperation"));assertFalse((boolean)field(b,"waterfallDispatch"));
  }
 }
 @Test void refusedDrawKeepsTheCurrentPoolAndNeverInventsAQueueCall()throws Exception{drawFault(false);}
 @Test void landedDrawKeepsOnlyTheCurrentQueuePrefixAndNeverInventsASecondDraw()throws Exception{drawFault(true);}
 private void drawFault(boolean land)throws Exception{
   var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root.resolve(land?"land":"refuse")),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
   try(var boot=boot();var cleanup=disposeWhileCurrent()){
    var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,m);var b=binding(m);var body=kernel(b);var placed=(CdObjectPlacementRuntime)field(b,"placement");var draw=(CdSpinTunnelSplash.DrawObjectCall)field(body,"draw");
    var fault=new Fault("actual Draw "+land);var call=new AtomicReference<CdSpinTunnelSplash.Operation>();var calls=new int[1];
    replace(body,"draw",(CdSpinTunnelSplash.DrawObjectCall)c->{call.set(c);if(land){draw.drawObject(c);calls[0]++;}throw fault;});
    assertSame(fault,assertThrows(Fault.class,()->driveToFault(loop,m)));var c=Objects.requireNonNull(call.get());assertFalse(c.completed());assertEquals(land?1:0,calls[0]);assertEquals(0,fault.getSuppressed().length);
    var own=land?c.before().moveByte(SPRITE_FLAGS,c.before().spriteFlags()&127):c.before();var expected=new ArrayList<>(c.cut().pool().slots());expected.set(c.wholeIndex()-32,own);
    assertEquals(expected,placed.capture().pool().slots());assertEquals(c.cut().status(),placed.capture().status());assertArrayEquals(c.cut().savedFlags().copy(),placed.capture().savedFlags().copy());assertFalse(body.isCurrentCall(c));
    assertNull(field(b,"waterfallOperation"));assertFalse((boolean)field(b,"waterfallDispatch"));
   }
 }
 private static final class Fault extends RuntimeException{Fault(String message){super(message);}}
 private static void ready(GameLoop loop,SonicCdGameModule m){for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;n++)step(loop,0);assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());}
 private static int input(int row){return row>=4000&&row<4012?0x18:8;}
 private static void returned(GameLoop loop,SonicCdGameModule m,int row){step(loop,input(row));var f=m.originalR11EntryFrame();assertFalse(f.closed());assertTrue(f.state().motion().routine()<6,"Returned death precedes positive waterfall at caller row "+(row+1));}
 private static int reachReturnedWaterfall(GameLoop loop,SonicCdGameModule m){for(int n=0;n<8192;n++){returned(loop,m,n);if(m.originalR11WorldImage().orElseThrow().tunnelServices().stream().anyMatch(s->s.id()==0x0E&&s.operations()>0))return n+1;}throw new AssertionError("The fixed genuine caller recipe never returned the waterfall body within8192 rows");}
 private static void driveToFault(GameLoop loop,SonicCdGameModule m){for(int n=0;n<8192;n++)returned(loop,m,n);throw new AssertionError("Expected genuine waterfall fault was never reached");}
 private static void assertPool(CdObjectPlacementRuntime.Snapshot expected,CdObjectPlacementRuntime.Snapshot actual){assertEquals(expected.status(),actual.status());assertEquals(expected.pool().slots(),actual.pool().slots());assertArrayEquals(expected.savedFlags().copy(),actual.savedFlags().copy());}
 private static Object binding(SonicCdGameModule m)throws Exception{return field(field(m,"r11World"),"binding");}
 private static CdSpinTunnelSplash kernel(Object binding)throws Exception{return (CdSpinTunnelSplash)field(binding,"waterfallSplash");}
 private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 private static void replace(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
 private static List<Object> image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();var first=m.originalR11FirstLoad();return List.of(f.captureActor(),f.captureShared(),m.selectedProfileState(),m.programServicesState(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),p.status(),p.pool().slots(),p.savedFlags(),first.selection().inputs(),w.cpu(),w.sounds(),w.tunnelServices());}
 private static AutoCloseable disposeWhileCurrent(){return ()->{Throwable fault=null;try{SessionManager.clear();}catch(RuntimeException|Error original){fault=original;}try{GameServices.graphics().cleanup();}catch(RuntimeException|Error check){if(fault==null)fault=check;else if(check!=fault)fault.addSuppressed(check);}if(fault instanceof RuntimeException x)throw x;if(fault instanceof Error x)throw x;};}
}

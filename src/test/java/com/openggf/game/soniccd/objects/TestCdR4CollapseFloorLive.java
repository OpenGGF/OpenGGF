package com.openggf.game.soniccd.objects;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
/** Constructed private-occurrence component controls; no original World/source admission. */
class TestCdR4CollapseFloorLive {
 @Test void nativeInitUsesSharedLazyTopAndMatchesDetachedValuesWithoutBorrowingASecondary(){
  var h=new C();h.h.set(0x28,1,3);var pure=CdR4CollapseFloor.run(h.h.pure());assertTrue(pure.stopped().isEmpty());h.run();assertEquals(pure.prefix().pool().slots(),h.h.place.capture().pool().slots());assertEquals(pure.prefix().primary(),h.h.primary);assertEquals(Tail.DRAW_DESPAWN_X,h.tail);assertEquals(1,h.solids);
 }
 @Test void twoSameIdChildrenCopyOnlyTheNativeHeaderAndKeepDirtyRoutineDelayAndDelta(){
  var h=new C();h.h.set(0x24,1,6);h.h.set(0x28,1,1);h.h.set(0x2c,2,8);
  h.child(32,0x24,1,4);h.child(32,0x2c,4,0x12345678);h.child(33,0x24,1,2);h.child(33,0x2a,2,0x5678);h.child(33,0x2c,4,0x23456789);
  h.run();assertEquals(0,h.h.place.pool().state(40).id());assertEquals(List.of(32,33),h.found);assertEquals(Tail.NONE,h.tail);
  var a=h.h.place.pool().state(32);var b=h.h.place.pool().state(33);assertEquals(0x39,a.id());assertEquals(0x39,b.id());assertEquals(255,u8(a,0x29));assertEquals(255,u8(b,0x29));assertEquals(4,a.routine());assertEquals(2,b.routine());assertEquals(4,u16(a,0x2a));assertEquals(0x5678,u16(b,0x2a));assertEquals(0x12345678,i32(a,0x2c));assertEquals(0x23456789,i32(b,0x2c));assertEquals(0x1F8,a.centreY());assertEquals(0x208,b.centreY());
 }
 @Test void exhaustedAllocationDoesNotInventAChildOrFailedAllocationCrossWrite(){
  var h=new C();for(int w=32;w<128;w++)if(w!=40)h.child(w,0,1,1);h.h.set(0x24,1,6);h.h.set(0x28,1,1);var before=h.h.place.pool().capture();h.run();assertEquals(1,h.searches);assertTrue(h.found.isEmpty());assertEquals(0,h.childStores);for(int w=32;w<128;w++)if(w!=40)assertEquals(before.slots().get(w-32),h.h.place.pool().state(w));
 }
 @Test void mappingFaultRetainsOnlyTheEarlierNativeInitStores(){
  var h=new C();h.h.data.mapFault=Mapping.COLLAPSE;assertThrows(IllegalStateException.class,h::run);var row=h.h.place.pool().state(40);assertEquals(2,row.routine());assertEquals(4,row.spriteFlags());assertEquals(0x4364,u16(row,2));assertEquals(0,i32(row,4));assertEquals(0,h.solids);assertEquals(0,h.tails);
 }
 @Test void refusedChildStoreKeepsTheActualAllocatorResultAndNoHeaderWrite(){
  var h=new C();h.h.set(0x24,1,6);h.childBefore=new Fault("child refused");assertSame(h.childBefore,assertThrows(Fault.class,h::run));assertEquals(List.of(32),h.found);assertEquals(0,h.h.place.pool().state(32).id());assertEquals(0,h.childStores);assertEquals(0,h.solids);
 }
 @Test void landedChildStoreThenGuardFaultKeepsItsOneNativeLongAndOriginalThrowable(){
  var h=new C();h.h.set(0x24,1,6);h.childAfter=new Fault("child landed");assertSame(h.childAfter,assertThrows(Fault.class,h::run));assertEquals(1,h.childStores);assertEquals(i32(h.h.place.pool().state(40),0),i32(h.h.place.pool().state(32),0));assertEquals(0,u8(h.h.place.pool().state(32),0x29));assertEquals(0,h.solids);
 }
 @Test void foreignPoolWriteDuringSearchIsRejectedBeforeAnyChildPublication(){
  var h=new C();h.h.set(0x24,1,6);h.searchBefore=()->h.child(127,0x28,1,0x77);assertThrows(IllegalStateException.class,h::run);assertEquals(0,h.childStores);assertEquals(0x77,h.h.place.pool().state(127).subtype());
 }
 @Test void reachedPhysicalOneReleaseKeepsTheSharedNativePrefixAndThrowable(){
  var h=new C();h.h.set(0x24,1,2);h.h.set(1,1,128);h.h.set(0x16,1,17);h.h.set(0x19,1,16);h.h.primary=TestCdR4SolidMovers.player(1,0x1001234,0x1E05678,0x123,0x100,10,1);
  var first=assertThrows(RuntimeException.class,h::run);assertSame(h.nativeFirst,first);assertEquals(0,u8(h.h.place.pool().state(40),0x25));assertEquals(8,u8(h.h.place.pool().state(40),0x22)&8);assertEquals(1,h.h.primary.standObject());assertEquals(0,h.tails);
 }
 @Test void foreignAndRetiredReceiptsCannotReopenTheSameIdGraph(){
  var h=new C();var other=new C();h.beforeHook=()->assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(other));h.run();assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h));
 }
 @Test void wrongIdIsRejectedBeforeAnySourceOperation(){var h=new C();h.h.set(0,1,0x34);var before=h.h.place.capture();assertThrows(IllegalArgumentException.class,h::run);assertEquals(0,h.operations);assertEquals(before.pool().slots(),h.h.place.capture().pool().slots());}
 @Test void pieceHeaderIsOneNativeLongAndPostStoreFaultRetainsAllFourBytes(){
  var h=new C();h.h.set(0x29,1,255);h.h.set(0x16,4,0x11223344);h.heightAfter=new Fault("native LONG landed");
  assertSame(h.heightAfter,assertThrows(Fault.class,h::run));var row=h.h.place.pool().state(40);
  assertEquals(2,row.routine());assertEquals(8,u8(row,0x1a));assertEquals(0x08080308,i32(row,0x16));
  assertEquals(1,h.heightStores);assertEquals(4,h.heightStoreBytes);assertEquals(0,h.tails);
 }
 static final class Fault extends RuntimeException{Fault(String s){super(s);}}
 static final class C implements LiveOwner {
  final TestCdR4SolidMoverLive.H h=new TestCdR4SolidMoverLive.H();final List<Integer> found=new ArrayList<>();int operations,solids,tails,searches,childStores,heightStores,heightStoreBytes;boolean running;Call saved;Tail tail;Throwable nativeFirst;Fault childBefore,childAfter,heightAfter;Runnable searchBefore=()->{},beforeHook=()->{};
  C(){h.set(0,1,0x39);}void child(int slot,int at,int size,int value){h.place.pool().write(put(h.place.pool().state(slot),at,size,value));}
  void run(){if(running)throw new IllegalStateException("fixture reentry");running=true;try{CdR4CollapseFloor.run(h.inputs(),this);}finally{running=false;}}
  public long generation(){return 9;}public void requireCurrent(Call c){c.requireCurrent(this);assertTrue(running);assertSame(h.place,c.placement());assertEquals(40,c.wholeIndex());}
  public void before(Operation c){requireCurrent(c);saved=c;operations++;beforeHook.run();if(c instanceof SearchCall)searchBefore.run();if(c instanceof ChildStoreCall&&childBefore!=null)throw childBefore;}
  public void after(Operation c){requireCurrent(c);if(c instanceof StoreCall store&&c.completed()&&store.offset()==0x16){heightStores++;heightStoreBytes=store.bytes();if(heightAfter!=null)throw heightAfter;}if(c instanceof SearchCall s&&c.completed()){searches++;s.found().ifPresent(found::add);}if(c instanceof ChildStoreCall&&c.completed()){childStores++;if(childAfter!=null)throw childAfter;}}
  public void sound(SoundCall c){requireCurrent(c);assertEquals(0xa3,c.nativeId());}
  public void solid(SolidCall c){requireCurrent(c);solids++;var outcome=c.outcome();if(outcome instanceof CdSolidObject.Completed done){var r=done.completedResult();h.place.pool().write(r.object());for(var o:r.otherSlots())h.place.pool().write(o);h.primary=r.player();h.globals=r.globals();}else{var stop=(CdSolidObject.NativeStopped)outcome;nativeFirst=stop.stop().originalException();var r=stop.prefix();h.place.pool().write(r.object());for(var o:r.otherSlots())h.place.pool().write(o);h.primary=r.player();h.globals=r.globals();}}
  public void tail(TailCall c){requireCurrent(c);tails++;tail=c.tail();}
 }
}

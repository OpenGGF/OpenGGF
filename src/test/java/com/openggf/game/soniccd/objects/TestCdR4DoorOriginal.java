package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Explicit constructed original-port controls, not live World/native/route evidence. */
class TestCdR4DoorOriginal {
 static class Fault extends RuntimeException{}
 static final class Scene implements CdR4Door.Owner {
  final CdObjectPool pool=new CdObjectPool();final List<CdR4Door.Kind> calls=new ArrayList<>();long generation=1;CdR4Door.Call captured;int solids,draws,despawns,switchByte,after;CdR4Door.Kind fail;Fault fault=new Fault();boolean refuse;
  final CdR4Door.Data data=new CdR4Door.Data(){public int mapping(){return 0x20CF46;}public int shape(int r,int c){return new int[][]{{64,8,128,2},{64,8,128,2},{8,32,64,1},{8,64,128,0},{8,64,128,0}}[r][c];}};
  Scene(){set(0,1,0x28);set(8,2,0x100);set(12,2,0x200);}
  void set(int at,int size,int v){pool.write(CdR4SolidMover.put(pool.state(32),at,size,v));}
  CdR4Door.Inputs in(){return new CdR4Door.Inputs(pool,32,data);}
  public long generation(){return generation;}
  public void before(CdR4Door.Call c){c.requireCurrent(this);if(refuse)throw fault;calls.add(c.kind());captured=c;if(c.kind()==fail)throw fault;}
  public void after(CdR4Door.Call c,Throwable failure){c.requireCurrent(this);after++;}
  public int primary(CdR4Door.ReadCall c){return c.offset()==8?0x100:c.offset()==12?0x200:c.offset()==0x16?19:9;}
  public int switches(CdR4Door.ReadCall c){return switchByte;}
  public void solid(CdR4Door.Call c){c.requireCurrent(this);solids++;}
  public void draw(CdR4Door.Call c){c.requireCurrent(this);draws++;}
  public void despawn(CdR4Door.ReadCall c){c.requireCurrent(this);despawns++;}
 }
 @Test void dirtyInitUsesOneSharedBodyWithoutAnotherSlotOrSecondary(){var s=new Scene();s.set(0x28,1,0x42);s.set(0x3A,1,0x78);var before=s.pool.capture();CdR4Door.run(s.in(),s);var a=s.pool.state(32);assertEquals(2,a.routine());assertEquals(0x4440,CdR4SolidMover.u16(a,2));assertEquals(0x20CF46,a.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(8,CdR4SolidMover.u8(a,0x19));assertEquals(0x78,CdR4SolidMover.u8(a,0x3A));assertEquals(0,s.solids);assertEquals(1,s.draws);for(int i=1;i<96;i++)assertSame(before.slots().get(i),s.pool.capture().slots().get(i));}
 @Test void refusalBeforeFirstOperationDoesNotCallAfterOrWrite(){var s=new Scene();var old=s.pool.capture();s.refuse=true;assertSame(s.fault,assertThrows(Fault.class,()->CdR4Door.run(s.in(),s)));assertEquals(old.slots(),s.pool.capture().slots());assertEquals(0,s.after);}
 @Test void sourceMappingFaultRetainsOnlyEarlierNativeHeaderStores(){var s=new Scene();s.fail=CdR4Door.Kind.MAPPING_READ;assertSame(s.fault,assertThrows(Fault.class,()->CdR4Door.run(s.in(),s)));assertEquals(2,s.pool.state(32).routine());assertEquals(0x4440,CdR4SolidMover.u16(s.pool.state(32),2));assertEquals(0,CdR4SolidMover.i32(s.pool.state(32),4));assertEquals(0,s.draws);}
 @Test void switchReadRefusalDoesNotStoreMoveSolidOrDraw(){var s=new Scene();CdR4Door.run(s.in(),s);s.calls.clear();s.fail=CdR4Door.Kind.SWITCH_READ;var old=s.pool.capture();assertSame(s.fault,assertThrows(Fault.class,()->CdR4Door.run(s.in(),s)));assertEquals(old.slots(),s.pool.capture().slots());assertEquals(0,s.solids);assertEquals(1,s.draws);}
 @Test void nativeTypeFourOpensFromItsRealSwitchHighBitAndInvokesPlainSolid(){var s=new Scene();s.set(0x28,1,0x40);CdR4Door.run(s.in(),s);s.switchByte=0x80;CdR4Door.run(s.in(),s);assertEquals(8,CdR4SolidMover.u8(s.pool.state(32),0x3A));assertEquals(0x1F8,s.pool.state(32).centreY());assertEquals(1,s.solids);}
 @Test void closingUnsignedUnderflowClampsBeforePositionAndSolid(){var s=new Scene();s.set(0x28,1,0x40);CdR4Door.run(s.in(),s);s.set(0x3A,1,3);CdR4Door.run(s.in(),s);assertEquals(0,CdR4SolidMover.u8(s.pool.state(32),0x3A));assertEquals(0x200,s.pool.state(32).centreY());assertEquals(1,s.solids);}
 @Test void expiredAndForeignOriginalOccurrencesCannotBeReused()throws Exception{var s=new Scene();CdR4Door.run(s.in(),s);var old=s.captured;assertThrows(IllegalStateException.class,()->old.requireCurrent(s));var other=new Scene();assertThrows(IllegalStateException.class,()->old.requireCurrent(other));var thrown=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{old.requireCurrent(s);}catch(Throwable x){thrown.set(x);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,thrown.get());}
 @Test void drawFaultRetainsInitAndNeverRunsDespawn(){var s=new Scene();s.fail=CdR4Door.Kind.DRAW;assertSame(s.fault,assertThrows(Fault.class,()->CdR4Door.run(s.in(),s)));assertEquals(2,s.pool.state(32).routine());assertEquals(0,s.despawns);assertEquals(0,s.draws);}
}

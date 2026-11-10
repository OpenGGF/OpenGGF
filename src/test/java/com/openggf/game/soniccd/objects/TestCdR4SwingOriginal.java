package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.*;import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.Test;import java.nio.ByteBuffer;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
/** Constructed native-prefix controls only. No live World or native-execution claim. */
class TestCdR4SwingOriginal {
 static final class Fault extends RuntimeException{}
 static final class H implements LiveOwner {
  final CdObjectPlacementRuntime p=placement();final TestCdR4SolidMovers.Data data=new TestCdR4SolidMovers.Data();final CdSolidObject solid=TestCdR4SolidMovers.solid();
  CdSolidObject.Player player=TestCdR4SolidMovers.player(1,0x3001234,0x3005678,0,0,0,0);CdSolidObject.Globals globals=new CdSolidObject.Globals(0,0,0x12,0x34);
  final CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);Operation saved;Kind fail;Fault fault=new Fault();int yWrites,draws,sines,searches;boolean running,failSine;long generation=7;
  H(){set(0,1,0x29);set(8,4,0x100BEEF);set(12,4,0x200ABCD);}
  void set(int a,int n,int v){p.pool().write(put(p.pool().state(40),a,n,v));}
  PrimaryInputs in(){return new PrimaryInputs(p,40,player,gates,globals,solid,data);}
  void run(){if(running)throw new IllegalStateException("reentry");running=true;try{CdR4Swing.run(in(),this);}finally{running=false;}}
  public long generation(){return generation;}public void requireCurrent(Call c){c.requireCurrent(this);assertTrue(running);assertSame(p,c.placement());assertEquals(40,c.wholeIndex());}
  public void before(Operation c){requireCurrent(c);saved=c;if(c.kind()==fail)throw fault;}
  public void after(Operation c){requireCurrent(c);}
  public CdPlayerMath.SineCosine sine(SineCall c){requireCurrent(c);sines++;if(failSine)throw fault;return new CdPlayerMath.SineCosine(0,256);}
  public void solid(SolidCall c){requireCurrent(c);assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,c.entry());var r=assertInstanceOf(CdSolidObject.Completed.class,c.outcome()).completedResult();p.pool().write(r.object());for(var s:r.otherSlots())p.pool().write(s);player=r.player();globals=r.globals();}
  public void primaryY(PrimaryYCall c){requireCurrent(c);assertEquals(player,c.expectedPlayer());player=c.output();yWrites++;}
  public void tail(TailCall c){requireCurrent(c);assertEquals(Tail.DRAW_SWING_WINDOW,c.tail());draws++;}
  static CdObjectPlacementRuntime placement(){var b=ByteBuffer.allocate(26);b.putShort((short)12).putShort((short)20).putShort((short)-1).putInt(0).putShort((short)0).putShort((short)-1).putInt(0).putShort((short)0).putShort((short)-1).putInt(0);return new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(b.array()));}
 }
 @Test void originalParentAllocatesSixDirtyChildrenInNativeDescendingPartOrder(){var h=new H();h.run();for(int i=0;i<6;i++){var c=h.p.pool().state(32+i);assertEquals(0x29,c.id());assertEquals(6-i,u8(c,0x3C));assertEquals(i==0?1:2,u8(c,0x1A));assertEquals(0x100,u16(c,0x36));assertEquals(0x200,u16(c,0x32));assertEquals(0,c.routine());}assertEquals(1,h.draws);assertEquals(1,h.sines);assertEquals(0xBEEF,u16(h.p.pool().state(40),10));assertEquals(0xABCD,u16(h.p.pool().state(40),14));}
 @Test void actualWordPhasePreservesTheLowDirtyByteAndBothSubtypeDirections(){var a=new H();a.set(0x3B,1,0x22);a.run();assertEquals(0xBFA2,u16(a.p.pool().state(40),0x3A));var b=new H();b.set(0x28,1,16);b.set(0x3B,1,0x22);b.run();assertEquals(0xC122,u16(b.p.pool().state(40),0x3A));}
 @Test void outerPartCreatesOnlyTheActualFollowingSwitchWithLinkedParentWord(){var h=new H();h.set(0x3C,1,6);h.set(0x28,1,0xBA);h.run();var c=h.p.pool().state(41);assertEquals(0x27,c.id());assertEquals(0xDA00,u16(c,0x34));assertEquals(0xF0,u8(c,0x39));assertEquals(0x10,u8(c,0x38));assertEquals(0xA,c.subtype());assertEquals(0,h.yWrites);}
 @Test void failedGlobalAllocationContinuesTheSixSearchesWithoutInventedChildren(){var h=new H();for(int n=32;n<128;n++)if(n!=40)h.p.pool().write(put(h.p.pool().state(n),0,1,0x55));h.run();assertEquals(95,h.p.pool().capture().slots().stream().filter(v->v.id()==0x55).count());assertEquals(1,h.draws);}
 @Test void mappingReadFailureRetainsOnlyEarlierNativeHeaderStores(){var h=new H();h.data.mapFault=Mapping.SWING;assertThrows(IllegalStateException.class,h::run);var a=h.p.pool().state(40);assertEquals(2,a.routine());assertEquals(4,a.spriteFlags());assertEquals(4,u8(a,0x18));assertEquals(0,u16(a,2));assertEquals(0,i32(a,4));assertEquals(0,h.draws);}
 @Test void sineRefusalPreservesHeaderChildrenAndPhaseButDoesNotMoveOrDraw(){var h=new H();h.failSine=true;assertSame(h.fault,assertThrows(Fault.class,h::run));assertEquals(2,h.p.pool().state(40).routine());assertEquals(6,h.p.pool().capture().slots().stream().filter(v->v.id()==0x29&&v.identity().wholeIndex()!=40).count());assertEquals(0xBF80,u16(h.p.pool().state(40),0x3A));assertEquals(0x100,h.p.pool().state(40).centreX());assertEquals(0x200,h.p.pool().state(40).centreY());assertEquals(0,h.draws);}
 @Test void wrongIdRejectsBeforeAnyOccurrenceOrMutation(){var h=new H();h.set(0,1,0x28);var old=h.p.capture();assertThrows(IllegalArgumentException.class,h::run);assertNull(h.saved);assertEquals(old.pool().slots(),h.p.capture().pool().slots());}
 @Test void issuedReceiptsExpireAndRejectForeignOrOffThreadOwners()throws Exception{var h=new H();h.run();var c=h.saved;assertThrows(IllegalStateException.class,()->c.requireCurrent(h));assertThrows(IllegalStateException.class,()->c.requireCurrent(new H()));var ref=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{c.requireCurrent(h);}catch(Throwable e){ref.set(e);}});t.start();t.join();assertInstanceOf(IllegalStateException.class,ref.get());}
 @Test void primaryOnlyIngressStillHasNoSyntheticCounterpartWaterOrWidths(){assertEquals(List.of("placement","wholeIndex","primary","gates","globals","solid","data"),Arrays.stream(PrimaryInputs.class.getRecordComponents()).map(v->v.getName()).toList());assertEquals(0,PrimaryYCall.class.getConstructors().length);}
}

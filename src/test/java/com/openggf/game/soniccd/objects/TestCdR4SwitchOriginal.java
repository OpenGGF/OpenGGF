package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Explicit constructed child/switch publication controls, not original World or native execution. */
class TestCdR4SwitchOriginal {
 static final class Fault extends RuntimeException{}
 static final class H implements CdR4Switch.Owner {
  final CdObjectPool p=new CdObjectPool();int y=0x201,height=19,bank=0x1B,solids,draws,deleted,sounds;boolean pressed;CdR4Switch.Kind fail;Fault fault=new Fault();CdR4Switch.Call saved;
  final CdR4Switch.Data data=new CdR4Switch.Data(){public int mapping(){return 0x20CCE4;}public int switchBaseWord(){return 0xF7E0;}};
  H(){set(0,1,0x27);set(8,2,0x100);set(12,2,0x200);}
  void set(int at,int n,int v){p.write(CdR4SolidMover.put(p.state(32),at,n,v));}void run(){CdR4Switch.run(new CdR4Switch.Inputs(p,32,data),this);}
  public long generation(){return 9;}public void before(CdR4Switch.Call c){c.requireCurrent(this);saved=c;if(c.kind()==fail)throw fault;}public void after(CdR4Switch.Call c,Throwable first){c.requireCurrent(this);}
  public int primary(CdR4Switch.ReadCall c){return c.offset()==12?y:c.offset()==0x16?height:0x100;}
  public void primaryY(CdR4Switch.PrimaryYCall c){y=c.word();}
  public int parent(CdR4Switch.ReadCall c){return c.offset()==8?0x100:c.offset()==12?0x200:0x100;}
  public int switchByte(CdR4Switch.ReadCall c){assertEquals(0,c.index());return bank;}
  public void switchByte(CdR4Switch.SwitchStoreCall c){assertEquals(0,c.index());bank=c.value();}
  public boolean solid(CdR4Switch.Call c){solids++;return pressed;}public void sound(CdR4Switch.ReadCall c){assertEquals(0xBF,c.index());sounds++;}
  public void draw(CdR4Switch.Call c){draws++;}public void despawn(CdR4Switch.ReadCall c){deleted++;}
 }
 @Test void actualUnpressedChildInitializesThenClearsOnlyTheCurrentPressBit(){var h=new H();h.bank=0xFF;h.run();assertEquals(2,h.p.state(32).routine());assertEquals(0x50A,CdR4SolidMover.u16(h.p.state(32),2));assertEquals(0x20CCE4,CdR4SolidMover.i32(h.p.state(32),4));assertEquals(0xF7E0,CdR4SolidMover.u16(h.p.state(32),0x3C));assertEquals(0x7F,h.bank);assertEquals(1,h.solids);assertEquals(1,h.draws);}
 @Test void firstPressAlignsTwiceLatchesAndTogglesWithoutDroppingLowBits(){var h=new H();h.pressed=true;h.run();assertEquals(0xFB,h.bank);assertEquals(0x200-10-19+1+8,h.y);assertEquals(1,CdR4SolidMover.u8(h.p.state(32),0x1A));assertEquals(6,CdR4SolidMover.u8(h.p.state(32),0x16));}
 @Test void heldPressDoesNotRepeatTheToggleOrTransitionSound(){var h=new H();h.pressed=true;h.run();int first=h.bank;h.run();assertEquals(first,h.bank);assertEquals(0,h.sounds);}
 @Test void releaseClearsOnlyBitSevenAndRestoresHeightAndFrame(){var h=new H();h.pressed=true;h.run();h.pressed=false;h.run();assertEquals(0x7B,h.bank);assertEquals(10,CdR4SolidMover.u8(h.p.state(32),0x16));assertEquals(0,CdR4SolidMover.u8(h.p.state(32),0x1A));}
 @Test void mappingFaultKeepsEarlierHeaderButNeverRunsSolidBankOrDraw(){var h=new H();h.fail=CdR4Switch.Kind.MAPPING;assertSame(h.fault,assertThrows(Fault.class,h::run));assertEquals(2,h.p.state(32).routine());assertEquals(0x50A,CdR4SolidMover.u16(h.p.state(32),2));assertEquals(0,h.solids);assertEquals(0,h.draws);assertEquals(0x1B,h.bank);}
 @Test void refusedBankStoreKeepsAlignedPrimaryAndTheOriginalEarlierObjectPrefix(){var h=new H();h.pressed=true;h.fail=CdR4Switch.Kind.SWITCH_STORE;assertSame(h.fault,assertThrows(Fault.class,h::run));assertEquals(0x200-10-19+1,h.y);assertEquals(0x1B,h.bank);assertEquals(255,CdR4SolidMover.u8(h.p.state(32),0x3F));assertEquals(0,h.draws);}
 @Test void linkedSwitchFollowsParentBeforeSolidAndUsesParentAnchorAfterDraw(){var h=new H();h.set(0x34,2,0xDA00);h.set(0x38,1,16);h.set(0x39,1,0xF0);h.run();assertEquals(0x110,h.p.state(32).centreX());assertEquals(0x1F0,h.p.state(32).centreY());assertEquals(0x1000,CdR4SolidMover.u16(h.p.state(32),0x10));assertEquals(1,h.draws);assertEquals(1,h.deleted);}
 @Test void zeroAlignmentRetainsNativeSneZeroInsteadOfTreatingSolidContactAsPressed(){var h=new H();h.set(12,2,28);h.pressed=true;h.run();assertEquals(0,h.y);assertEquals(0x1B,h.bank);assertEquals(0,CdR4SolidMover.u8(h.p.state(32),0x3F));}
 @Test void expiredReceiptAndForeignOwnerCannotMutateSwitchOrPrimary(){var h=new H();h.run();var c=h.saved;assertThrows(IllegalStateException.class,()->c.requireCurrent(h));assertThrows(IllegalStateException.class,()->c.requireCurrent(new H()));assertEquals(0,CdR4Switch.Call.class.getConstructors().length);}
}

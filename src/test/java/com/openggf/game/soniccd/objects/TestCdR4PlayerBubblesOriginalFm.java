package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdR4WaterAirPorts;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed component controls only. N1ecc5f3 player_bubbles.asm:181-237.
 * Tests authored before the owner bridge. All Java UNRUN; no World admission from these values. */
class TestCdR4PlayerBubblesOriginalFm {
 static final class Fault extends RuntimeException { Fault(String text){super(text);} }
 static final class Cell implements Slot {
  final byte[] bytes=new byte[64];final List<String> trace;final String name;
  Cell(String name,List<String> trace){this.name=name;this.trace=trace;}
  int get(int at,int size){int v=0;for(int n=0;n<size;n++)v=(v<<8)|(bytes[at+n]&255);return v;}
  void put(int at,int size,int value){for(int n=0;n<size;n++)bytes[at+n]=(byte)(value>>>(8*(size-1-n)));}
  public int read(B f){return get(f.offset,1);}public int read(W f){return get(f.offset,2);}public int read(L f){return get(f.offset,4);}
  public void write(B f,int v){put(f.offset,1,v);trace.add(name+":"+f+"="+v);}
  public void write(W f,int v){put(f.offset,2,v);trace.add(name+":"+f+"="+v);}
  public void write(L f,int v){put(f.offset,4,v);trace.add(name+":"+f+"="+v);}
 }
 static final class H implements Calls {
  final List<String> trace=new ArrayList<>();final Cell object=new Cell("o",trace),player=new Cell("p",trace),reserved=new Cell("r",trace);
  final EnumMap<Value,Integer> values=new EnumMap<>(Value.class);
  final BubbleData data=new BubbleData(0x21E9B4,CdAssetBytes.copyOf(new byte[128]),(p,n)->{throw new AssertionError("No pattern read");},0);
  H(){object.put(B.ROUTINE.offset,1,10);object.put(B.COUNTDOWN.offset,1,1);player.put(B.ROUTINE.offset,1,2);player.put(B.FLAGS.offset,1,0x40);values.put(Value.DROWN_TIMER,12);values.put(Value.STAGE_FRAMES_LOW,0);}
  public int read(Value f){Integer v=values.get(f);if(v==null)throw new AssertionError("Unconfigured reached read "+f);return v;}
  public void write(Value f,int v){values.put(f,v);trace.add("v:"+f+"="+v);}
  public Slot player(){return player;}public Slot reservedBubbles(){return reserved;}
  public Optional<Slot> spawn(){trace.add("spawn");return Optional.empty();}public int random(){trace.add("random");return 0;}
  public void sound(int id){throw new AssertionError("Original FM must use the current typed owner, not raw Calls.sound");}
  public void music(int id){throw new AssertionError("Music remains a separate reached owner");}
  public CdPlayerMath.SineCosine sineCosine(int a){throw new AssertionError("No sine");}
  public void setPlayerGround(){trace.add("ground");throw new UnsupportedOperationException("constructed ground boundary");}
  public void move(Slot s){throw new AssertionError("No move");}public void animate(Slot s,Animation a){throw new AssertionError("No animation");}public void draw(Slot s){throw new AssertionError("No draw");}public void delete(Slot s){throw new AssertionError("No delete");}
 }
 static final class Owner implements CdR4PlayerBubbles.FmOwner {
  final H h;long generation=1;int ordinal;CdR4PlayerBubbles.FmCall issued;boolean failBefore,failAfter,probe,changeGeneration;
  final Fault original=new Fault("original FM fault"),verification=new Fault("after guard");boolean failGuard,selfGuard;
  Owner(H h){this.h=h;}
  public long generation(){return generation;}
  public void before(CdR4PlayerBubbles.FmCall c){
   c.requireCurrent(this);assertSame(h.object,c.slot());assertSame(h,c.calls());assertSame(h.data,c.data());assertEquals(++ordinal,c.ordinal());assertFalse(c.consumed());issued=c;
   if(probe){var rejected=new AtomicReference<Throwable>();var t=new Thread(()->{try{c.requireCurrent(this);}catch(Throwable x){rejected.set(x);}});t.start();try{t.join();}catch(InterruptedException x){throw new AssertionError(x);}assertInstanceOf(IllegalStateException.class,rejected.get());assertThrows(IllegalStateException.class,()->c.consume(new Owner(h)));}
   if(failBefore)throw original;if(changeGeneration)generation++;
  }
  public void sound(CdR4PlayerBubbles.FmCall c){c.requireCurrent(this);c.consume(this);assertThrows(IllegalStateException.class,()->c.consume(this));h.trace.add("fm:"+Integer.toHexString(c.sound().nativeId()));if(failAfter)throw original;}
  public void after(CdR4PlayerBubbles.FmCall c){if(failGuard)throw selfGuard?original:verification;c.requireCurrent(this);}
  void run(){CdR4PlayerBubbles.runOriginal(h.object,h,h.data,this);}
 }
 @Test void typedNativeFamilyContainsOnlyWarningAndDrowningCommands(){assertEquals(2,CdR4PlayerBubbles.FmSound.values().length);assertEquals(0xDF,CdR4PlayerBubbles.FmSound.AIR_WARNING.nativeId());assertEquals(0xB6,CdR4PlayerBubbles.FmSound.DROWNING.nativeId());}
 @Test void warningPublishesExactlyAtNativeCutBeforeCountdownAndAirWord(){var h=new H();var o=new Owner(h);o.run();assertEquals(List.of("o:TIMER=65535","o:TIMER=59","o:BURST=1","random","o:REMAINING=0","fm:df","o:COUNTDOWN=0","v:DROWN_TIMER=11","random","o:EMIT_TIMER=0","spawn"),h.trace);assertEquals(1,o.ordinal);assertTrue(o.issued.completed());assertTrue(o.issued.consumed());}
 @Test void beforeSoundFaultKeepsEarlierStoresAndLeavesCountdownAndAirUnwritten(){var h=new H();var o=new Owner(h);o.failBefore=true;assertSame(o.original,assertThrows(Fault.class,o::run));assertEquals(List.of("o:TIMER=65535","o:TIMER=59","o:BURST=1","random","o:REMAINING=0"),h.trace);assertEquals(1,h.object.read(B.COUNTDOWN));assertEquals(12,h.read(Value.DROWN_TIMER));assertFalse(o.issued.completed());assertFalse(o.issued.consumed());}
 @Test void soundFaultAfterLandingKeepsOneCommandAndOriginalThrowable(){var h=new H();var o=new Owner(h);o.failAfter=true;assertSame(o.original,assertThrows(Fault.class,o::run));assertEquals("fm:df",h.trace.get(h.trace.size()-1));assertEquals(1,h.trace.stream().filter(s->s.startsWith("fm:")).count());assertEquals(1,h.object.read(B.COUNTDOWN));assertEquals(12,h.read(Value.DROWN_TIMER));assertTrue(o.issued.consumed());assertFalse(o.issued.completed());}
 @Test void laterGuardIsSuppressedWithoutReplacingOriginalSoundFault(){var h=new H();var o=new Owner(h);o.failAfter=true;o.failGuard=true;assertSame(o.original,assertThrows(Fault.class,o::run));assertArrayEquals(new Throwable[]{o.verification},o.original.getSuppressed());}
 @Test void identicalAfterThrowableIsNeverSelfSuppressed(){var h=new H();var o=new Owner(h);o.failAfter=true;o.failGuard=true;o.selfGuard=true;assertSame(o.original,assertThrows(Fault.class,o::run));assertEquals(0,o.original.getSuppressed().length);}
 @Test void currentReceiptRejectsWrongOwnerOffThreadDoubleConsumeAndRetires(){var h=new H();var o=new Owner(h);o.probe=true;o.run();assertThrows(IllegalStateException.class,()->o.issued.requireCurrent(o));assertThrows(IllegalStateException.class,()->o.issued.consume(o));assertThrows(IllegalStateException.class,()->o.issued.sound());}
 @Test void changedOwnerGenerationRejectsBeforeCommandPublication(){var h=new H();var o=new Owner(h);o.changeGeneration=true;assertThrows(IllegalStateException.class,o::run);assertTrue(h.trace.stream().noneMatch(s->s.startsWith("fm:")));assertEquals(12,h.read(Value.DROWN_TIMER));}
 @Test void stageBitAndAirThresholdBranchesDoNotSpeculateAWarning(){for(int air:new int[]{25,20,15,13}){var h=new H();h.values.put(Value.DROWN_TIMER,air);var o=new Owner(h);o.run();assertEquals(0,o.ordinal);}var h=new H();h.values.put(Value.STAGE_FRAMES_LOW,0x20);var o=new Owner(h);o.run();assertEquals(0,o.ordinal);assertEquals(11,h.read(Value.DROWN_TIMER));}
 @Test void dryAndDeadPlayersDoNotIssueSoundOrTimerStores(){for(boolean dry:new boolean[]{false,true}){var h=new H();if(dry)h.player.put(B.FLAGS.offset,1,0);else h.player.put(B.ROUTINE.offset,1,6);var o=new Owner(h);o.run();assertTrue(h.trace.isEmpty());assertEquals(0,o.ordinal);}}
 @Test void zeroAirUsesTwoOrderedCommandsThenRetainsTheDistinctGroundBoundary(){var h=new H();h.values.put(Value.DROWN_TIMER,0);var o=new Owner(h);var stop=assertThrows(UnsupportedOperationException.class,o::run);assertEquals("constructed ground boundary",stop.getMessage());assertEquals(2,o.ordinal);int df=h.trace.indexOf("fm:df"),air=h.trace.indexOf("v:DROWN_TIMER=65535"),reset=h.trace.indexOf("v:DROWN_TIMER=30"),control=h.trace.indexOf("p:CONTROL=129"),b6=h.trace.indexOf("fm:b6");assertTrue(df<air&&air<reset&&reset<control&&control<b6);assertEquals(120,h.object.read(W.DEATH_DELAY));assertEquals("ground",h.trace.get(h.trace.size()-1));}
}

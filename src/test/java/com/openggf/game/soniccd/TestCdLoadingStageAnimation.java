package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded source-level primitive requirements, not actual Module invocation or discovery. UNRUN. */
class TestCdLoadingStageAnimation {
 private static final class Port implements CdLoadedLevelSetup.LoadingAnimation {
  int timer,frame;final Map<Long,Integer> bytes=new HashMap<>(),longs=new HashMap<>();
  final Map<Integer,Integer> cpu=new TreeMap<>();final List<String> trace=new ArrayList<>();
  RuntimeException fault;long faultAt=Long.MIN_VALUE;boolean publishFault;
  public int timer(int ordinal){trace.add("read timer"+ordinal);return timer;}
  public void timer(int ordinal,int value){trace.add("timer="+value);timer=value;}
  public int frame(int ordinal){trace.add("read frame"+ordinal);return frame;}
  public void frame(int ordinal,int value){trace.add("frame="+value);frame=value;}
  public long script(int ordinal){trace.add("script"+ordinal);return 0x200000;}
  public int sourceByte(long address){trace.add("byte:"+Long.toHexString(address));if(address==faultAt)throw fault;return Objects.requireNonNull(bytes.get(address),"unbacked byte");}
  public int sourceLong(long address){trace.add("long:"+Long.toHexString(address));if(address==faultAt)throw fault;return Objects.requireNonNull(longs.get(address),"unbacked LONG");}
  public void cpuLong(int at,int bits){trace.add("cpu:"+at);cpu.put(at,bits);}
  public void publishCpu(int destination,int size){trace.add("publish:"+destination+":"+size);if(publishFault)throw fault;}
 }
 private Port source(){var p=new Port();p.bytes.put(0x200000L,2);p.bytes.put(0x200004L,8);p.bytes.put(0x200005L,0);p.longs.put(0x200006L,0x210000);p.longs.put(0x210000L,0x11223344);p.longs.put(0x210004L,0x55667788);p.longs.put(0x210008L,0x12345678);return p;}
 @Test void nonnegativeTimerReturnsBeforeFrameScriptOrSourceReads(){
  var p=new Port();p.timer=1;CdLoadedLevelSetup.animateStage(p,2,0x4900,512);
  assertEquals(List.of("read timer2","timer=0"),p.trace);assertTrue(p.cpu.isEmpty());
 }
 @Test void realUnderflowStoresFrameAndDurationBeforeLongPairsAndOnePublication(){
  var p=source();CdLoadedLevelSetup.animateStage(p,0,0x4500,12);
  assertEquals(1,p.frame);assertEquals(8,p.timer);assertEquals(Map.of(0,0x11223344,4,0x55667788,8,0x12345678),p.cpu);
  assertTrue(p.trace.indexOf("frame=1")<p.trace.indexOf("byte:200004"));assertTrue(p.trace.indexOf("timer=8")<p.trace.indexOf("long:200006"));
  assertEquals("publish:17664:12",p.trace.getLast());
 }
 @Test void sourceLongFaultKeepsAllEarlierStoresAndDoesNotPublishOrRetry(){
  var p=source();p.fault=new IllegalArgumentException("actual read fault");p.faultAt=0x210008;
  assertSame(p.fault,assertThrows(IllegalArgumentException.class,()->CdLoadedLevelSetup.animateStage(p,0,0x4500,12)));
  assertEquals(1,p.frame);assertEquals(8,p.timer);assertEquals(Map.of(0,0x11223344,4,0x55667788),p.cpu);
  assertFalse(p.trace.stream().anyMatch(v->v.startsWith("publish")));assertEquals(0,p.fault.getSuppressed().length);
 }
 @Test void signedFrameIndexReadsTheActualEarlierPointerWithoutPadding(){
  var p=source();p.bytes.put(0x200005L,255);p.longs.put(0x200002L,0x210000);p.publishFault=true;p.fault=new IllegalStateException("actual publication fault");
  assertSame(p.fault,assertThrows(IllegalStateException.class,()->CdLoadedLevelSetup.animateStage(p,0,0x4500,4)));
  assertTrue(p.trace.contains("long:200002"));assertEquals(Map.of(0,0x11223344),p.cpu);assertEquals(0,p.fault.getSuppressed().length);
 }
}

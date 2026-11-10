package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Isolated callback-lifetime controls only; no native route, Frame, capture or invocation claim.
 * All Java/discovery/runtime UNRUN. Synthetic Call values are never natural-course evidence. */
class TestCdStageClosureInvocation {
 @Test void currentIsIdentityScopedAndExpiresAfterReturn(){
  var calls=new ArrayList<CdStageClosureInvocation.Call>();var holder=new CdStageClosureInvocation.Issuer[1];
  holder[0]=new CdStageClosureInvocation.Issuer(new CdStageClosureInvocation.Observer(){
   public void before(CdStageClosureInvocation.Call c){assertTrue(holder[0].current(c));assertFalse(c.completed());calls.add(c);}
   public void after(CdStageClosureInvocation.Call c,boolean returned){assertTrue(returned);assertTrue(c.completed());assertTrue(holder[0].current(c));}
  });
  var argument=new Object();int value=holder[0].invoke(argument,null,null,()->7);assertEquals(7,value);assertFalse(holder[0].current(calls.getFirst()));
  holder[0].invoke(argument,null,null,()->{assertFalse(holder[0].current(calls.getFirst()));return 8;});assertNotSame(calls.get(0),calls.get(1));
 }
 @Test void originalFailureRetainsPrimitivePrefixAndDistinctVerificationFault(){
  var original=new IllegalArgumentException("first primitive");var verification=new IllegalStateException("after prefix");var prefix=new ArrayList<Integer>();
  var issuer=new CdStageClosureInvocation.Issuer(new CdStageClosureInvocation.Observer(){public void before(CdStageClosureInvocation.Call c){}public void after(CdStageClosureInvocation.Call c,boolean returned){assertFalse(returned);assertFalse(c.completed());throw verification;}});
  assertSame(original,assertThrows(IllegalArgumentException.class,()->issuer.invoke(new Object(),null,null,()->{prefix.add(1);throw original;})));
  assertEquals(List.of(1),prefix);assertArrayEquals(new Throwable[]{verification},original.getSuppressed());
 }
 @Test void failedBeforeDoesNotInvokePrimitiveOrAfter(){
  var original=new IllegalArgumentException("before");var count=new int[1];
  var issuer=new CdStageClosureInvocation.Issuer(new CdStageClosureInvocation.Observer(){public void before(CdStageClosureInvocation.Call c){throw original;}public void after(CdStageClosureInvocation.Call c,boolean returned){count[0]+=100;}});
  assertSame(original,assertThrows(IllegalArgumentException.class,()->issuer.invoke(new Object(),null,null,()->{count[0]++;return 1;})));assertEquals(0,count[0]);assertEquals(0,original.getSuppressed().length);
 }
 @Test void afterFaultIsNotRetriedOrSelfSuppressed(){
  var original=new IllegalStateException("after");var count=new int[1];
  var issuer=new CdStageClosureInvocation.Issuer(new CdStageClosureInvocation.Observer(){public void before(CdStageClosureInvocation.Call c){}public void after(CdStageClosureInvocation.Call c,boolean returned){count[0]++;throw original;}});
  assertSame(original,assertThrows(IllegalStateException.class,()->issuer.invoke(new Object(),null,null,()->1)));assertEquals(1,count[0]);assertEquals(0,original.getSuppressed().length);
 }
 @Test void nestedIssueRejectsAndLeavesOuterCurrent(){
  var holder=new CdStageClosureInvocation.Issuer[1];var call=new CdStageClosureInvocation.Call[1];holder[0]=new CdStageClosureInvocation.Issuer(new CdStageClosureInvocation.Observer(){public void before(CdStageClosureInvocation.Call c){call[0]=c;}public void after(CdStageClosureInvocation.Call c,boolean returned){assertTrue(returned);}});
  holder[0].invoke(new Object(),null,null,()->{assertThrows(IllegalStateException.class,()->holder[0].invoke(new Object(),null,null,()->1));assertTrue(holder[0].current(call[0]));return 0;});assertFalse(holder[0].current(call[0]));
 }
}

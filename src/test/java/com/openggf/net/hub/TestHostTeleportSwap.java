package com.openggf.net.hub;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestHostTeleportSwap {
 HostTeleportSwap host = new HostTeleportSwap();
 List<ControlMessage> sent = new ArrayList<>();
 HostTeleportSwap.Observation player(int slot,double progress,int frame) {
  return new HostTeleportSwap.Observation(slot,frame,1000,false,progress,
   new ControlMessage.SwapTarget(slot*100,50,0,0,false,false,0,false,0,0,(int)progress));
 }
 ControlMessage request(int slot,int frame,List<HostTeleportSwap.Observation> players) {
  return host.request(slot,1,frame,1,1000,12,players,(s,m)->sent.add(m));
 }
 @Test void nearestAheadThenBehindTiesAndLog() {
  var command=assertInstanceOf(ControlMessage.TeleportSwap.class,request(0,10,List.of(player(0,1,10),player(3,1.1,10),player(2,1.1,10),player(1,.99,10))));
  assertEquals(2,command.slotB()); assertEquals(2,sent.size()); assertEquals(3,host.log().getFirst().candidates().size());
  host.outcome(0,new ControlMessage.TeleportOutcome(command.swapId(),11,"APPLIED"));
  assertEquals("APPLIED",host.log().getFirst().outcomes().get(0).outcome());
  host.taint(2); assertTrue(host.log().getFirst().tainted());
 }
 @Test void noPartnerFinishedDisconnectedAndStaleExcluded() {
  assertInstanceOf(ControlMessage.TeleportNoPartner.class,request(0,10,List.of(player(0,1,10))));
  var decision = host.log().getFirst();
  assertNull(decision.command());
  var fallback = (ControlMessage.TeleportNoPartner) sent.getFirst();
  assertEquals(fallback.swapId(), decision.swapId());
  host.outcome(0,new ControlMessage.TeleportOutcome(fallback.swapId(),11,"FALLBACK"));
  assertEquals(fallback.swapId(),host.log().getFirst().outcomes().get(0).swapId());
  var stale=new HostTeleportSwap.Observation(1,10,0,false,2,player(1,2,10).target());
  assertInstanceOf(ControlMessage.TeleportNoPartner.class,request(0,10,List.of(player(0,1,10),stale)));
 }
 @Test void capRefusalCannotBypassRequestFrameBounds() {
  assertThrows(IllegalArgumentException.class,()->host.request(0,1,Integer.MAX_VALUE,1,1000,0,
   List.of(player(0,1,10)),(slot,m)->fail("malformed request must not create a receipt")));
  assertTrue(host.log().isEmpty());
 }
 @Test void waitsForRequestStreamAndEnforcesCap() {
  assertNull(request(0,11,List.of(player(0,1,10),player(1,2,10))));
  host.drain(1100,List.of(player(0,1,11),player(1,2,11)),(slot,m)->sent.add(m),(slot,d)->fail(d));
  assertEquals(1,host.log().size());
  assertEquals(11,host.log().getFirst().command().sentAtFrameA());
  assertEquals("SOURCE_CAP",assertInstanceOf(ControlMessage.TeleportNoPartner.class,host.request(2,1,10,1,1000,0,List.of(player(2,1,10)),(slot,m)->{})).reason());
 }
 @Test void hurtAndFinishedPeersAreNotPartners() {
  var self = player(0,1,10);
  var hurt = new HostTeleportSwap.Observation(1,10,1000,true,2,player(1,2,10).target());
  assertInstanceOf(ControlMessage.TeleportNoPartner.class,request(0,10,List.of(self,hurt)));
 }
 @Test void firstFrameRequestWaitsForItsFirstValidatedBatch() {
  assertNull(request(0,0,List.of(player(1,2,0))));
  host.drain(1100,List.of(player(0,1,0),player(1,2,0)),(slot,m)->sent.add(m),(slot,d)->fail(d));
  assertEquals(1,host.log().size()); assertEquals(2,sent.size());
  assertEquals(0,host.log().getFirst().command().sentAtFrameA());
 }
 @Test void missingStartupFramesExpireAndDisconnectedWaiterIsRemoved() {
  var violations=new ArrayList<Integer>();
  assertNull(request(0,0,List.of()));
  host.drain(1251,List.of(),(slot,m)->sent.add(m),(slot,d)->violations.add(slot));
  assertTrue(violations.isEmpty()); assertEquals("DEFERRAL_EXPIRED",((ControlMessage.TeleportNoPartner)sent.getFirst()).reason());
  assertNull(request(1,0,List.of())); host.onPlayerLeft(1);
  host.drain(1251,List.of(),(slot,m)->sent.add(m),(slot,d)->violations.add(slot));
  assertTrue(violations.isEmpty());
 }
 @Test void requesterHurtWhileAwaitingFramesGetsLoggedFallback() {
  assertNull(request(0,10,List.of(player(0,1,9),player(1,2,9))));
  var hurt = new HostTeleportSwap.Observation(0,10,1000,true,1,player(0,1,10).target());
  host.drain(1050,List.of(hurt,player(1,2,10)),(slot,m)->sent.add(m),(slot,d)->fail(d));
  assertInstanceOf(ControlMessage.TeleportNoPartner.class,sent.getFirst());
  assertNull(host.log().getFirst().command());
 }
 HostTeleportSwap.Observation fresh(int slot,double progress,int frame,long now) {
  var o=player(slot,progress,frame);
  return new HostTeleportSwap.Observation(slot,frame,now,o.hurt(),progress,o.target());
 }
 @Test void incomingSwapsReserveTheRecipientsRemainingRecordingBudget() {
  for (int i=1;i<=52;i++) {
   int f=10+(i-1)*65; long now=1000L+f*1000L/60;
   var c=assertInstanceOf(ControlMessage.TeleportSwap.class,host.request(i,1,f,1,now,12,
    List.of(fresh(0,1,f,now),fresh(i,0,f,now)),(slot,m)->sent.add(m)));
   assertEquals(0,c.slotB());
   host.outcome(i,new ControlMessage.TeleportOutcome(c.swapId(),f+1,"APPLIED"));
   host.outcome(0,new ControlMessage.TeleportOutcome(c.swapId(),f+1,"APPLIED"));
  }
  int f=3390; long now=1000L+f*1000L/60;
  assertInstanceOf(ControlMessage.TeleportNoPartner.class,host.request(53,1,f,1,now,12,
   List.of(fresh(0,1,f,now),fresh(53,0,f,now)),(slot,m)->sent.add(m)));
  int next=f+65; long later=1000L+next*1000L/60;
  assertInstanceOf(ControlMessage.TeleportSwap.class,host.request(0,1,next,1,later,12,
   List.of(fresh(0,1,next,later),fresh(54,2,next,later)),(slot,m)->sent.add(m)));
 }
 @Test void concurrentCooldownChainAndImplausibleFrame() {
  var players=List.of(player(0,1,10),player(1,2,10),player(2,3,10));
  var c=assertInstanceOf(ControlMessage.TeleportSwap.class,request(0,10,players));
  assertNull(request(1,10,players));
  assertThrows(IllegalArgumentException.class,()->host.outcome(0,new ControlMessage.TeleportOutcome(c.swapId(),191,"APPLIED")));
  host.outcome(0,new ControlMessage.TeleportOutcome(c.swapId(),11,"APPLIED"));
  host.outcome(1,new ControlMessage.TeleportOutcome(c.swapId(),11,"FALLBACK"));
  var refusal=assertInstanceOf(ControlMessage.TeleportNoPartner.class,request(1,10,players));
  host.outcome(1,new ControlMessage.TeleportOutcome(refusal.swapId(),12,"REFUSED"));
  host.drain(1000,List.of(player(0,1,75),player(1,2,75),player(2,3,75)),(slot,m)->sent.add(m),(slot,d)->fail(d));
  assertEquals(3,host.log().size());
  assertInstanceOf(ControlMessage.TeleportSwap.class,host.log().getLast().command());
  assertThrows(IllegalArgumentException.class,()->request(0,500,players));
 }
}

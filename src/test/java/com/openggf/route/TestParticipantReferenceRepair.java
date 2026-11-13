package com.openggf.route;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestParticipantReferenceRepair {
 @Test void p2PresenceAndDistanceSurviveWindowAndCopiedInputs() {
  var factory=SyntheticRouteWorlds.flat();var launch=SyntheticRouteWorlds.launch();
  try(var world=factory.openColdReplay(launch)) {
   var sample=world.observe();var tape=InputTape.of(List.of(PadRow.NEUTRAL));
   var ref=ReferencePath.recorded(launch,tape,new int[]{sample.p1().centreX()},new int[]{sample.p1().centreY()},new long[]{sample.epoch()},"test");
   assertEquals(0,new ReferenceProgress(ref.withParticipantPositions(new int[]{0},new int[]{0},new boolean[]{false}).window(0,0)).distance(sample,0));
   assertTrue(new ReferenceProgress(ref.withParticipantPositions(new int[]{0},new int[]{0}).window(0,0)).distance(sample,0)>1000);
   int[] x={sample.p1().centreX()+10};
   var both=new RouteSample(0,sample.epoch(),sample.level(),sample.clocks(),sample.p1(),Optional.of(sample.p1()),0,0,0);
   var target=ref.withParticipantPositions(x,new int[]{sample.p1().centreY()}).window(0,0);x[0]=0;
   assertEquals(10,new ReferenceProgress(target).distance(both,0));
  }
 }
 @Test void requiredSeamChangesOneRowAndKeepsEveryOtherRow() {
  var hosts=SyntheticRouteWorlds.flat();var tape=SyntheticRouteWorlds.right(40);
  var original=RouteArtifact.of(SyntheticRouteWorlds.launch(),tape,new Provenance("test","","",List.of()));
  var ref=ReferencePath.extract(hosts,original.launch(),tape,40);
  var result=new RoutePlanner(hosts).plan(new RouteTask.Repair(original,ref,new RepairScope(10,1,1,new RepairTrigger.RequiredEdit(10,Set.of(tape.hash()))),AuthorHints.referenceFollowing(),new SearchBudget(20,10000,20,0)),Cancellation.NONE);
  var solved=assertInstanceOf(PlanResult.Solved.class,result);
  assertEquals(tape.slice(0,10),solved.route().tape().slice(0,10));
  assertNotEquals(tape.row(10),solved.route().tape().row(10));
  assertEquals(tape.slice(11,40),solved.route().tape().slice(11,40));
  var window=new RoutePlanner(hosts).plan(new RouteTask.Repair(original,ref.window(0,24),
          new RepairScope(10,1,1,new RepairTrigger.RequiredEdit(10,Set.of(tape.hash()))),
          AuthorHints.referenceFollowing(),new SearchBudget(20,10000,20,0)),Cancellation.NONE);
  var bounded=assertInstanceOf(PlanResult.Solved.class,window);
  assertEquals(25,bounded.route().tape().size(),"do not copy source rows outside the declared repair window");
  assertEquals(tape.slice(11,25),bounded.route().tape().slice(11,25));
 }
}

package com.openggf.route;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestGoalFactCounter {
 static class World extends TestGoalNativeCharge.World {
  final boolean missing,menu;
  World(boolean missing,boolean menu){this.missing=missing;this.menu=menu;}
  @Override public RouteSample observe(){var s=super.observe();return new RouteSample(s.rowsOffered(),s.epoch(),s.level(),s.clocks(),s.p1(),s.p2(),0,0,0,List.of(),List.of(),
      missing?Map.of("input.menu",menu?1:0,"mode",1):Map.of("input.menu",menu?1:0,"mode",1,"presentation",rows));}
 }
 RouteWorldFactory hosts(boolean missing){return hosts(missing,false);}
 RouteWorldFactory hosts(boolean missing,boolean menu){return new RouteWorldFactory(){
  public RouteWorld openSearch(LaunchContract l){return new World(missing,menu);}
  public ReplayWorld openColdReplay(LaunchContract l){return new World(missing,menu);}
  public String environment(){return "counter-observation";}
 };}
 GoalProgram goals(int counter){return GoalProgram.of(Guard.NONE,
   new Leg("observe",new RouteGoal.Achieve(new SamplePredicate.FactEquals("presentation",counter)),
       Guard.NONE.holding(new SamplePredicate.FactEquals("mode",1)),1100));}
 @Test void copiedCounterRanksAnObservationHoldAndQualifiesOnlyExactFrozenRows(){
  var r=new RoutePlanner(hosts(false)).plan(new RouteTask.Author(SyntheticRouteWorlds.launch(),InputTape.empty(),goals(1000),new SearchBudget(100,60000,20,0)),Cancellation.NONE);
  var solved=assertInstanceOf(PlanResult.Solved.class,r);assertEquals(1000,solved.route().tape().size());
  assertTrue(solved.route().tape().rows().stream().noneMatch(row->row.held(PlayerSlot.P1,PhysicalButton.START)));
  assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts(false)).replay(solved.route().withChecks(goals(1001)),Cancellation.NONE));
  assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts(true)).replay(solved.route(),Cancellation.NONE));
 }
 @Test void guardedMenuObservationIsOneBoundedNeutralTrialWithoutCheckpointChurn(){
  var result=new RoutePlanner(hosts(false,true)).plan(new RouteTask.Author(SyntheticRouteWorlds.launch(),InputTape.empty(),goals(1000),new SearchBudget(3,1200,1,0)),Cancellation.NONE);
  var solved=assertInstanceOf(PlanResult.Solved.class,result);assertEquals(1000,solved.route().tape().size());
  assertEquals(1,solved.work().trials());assertTrue(solved.route().tape().rows().stream().allMatch(PadRow.NEUTRAL::equals));
  assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts(false,true)).replay(solved.route().withChecks(goals(1001)),Cancellation.NONE));
 }

}

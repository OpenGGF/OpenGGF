package com.openggf.route;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestGoalNativeCharge {
 static class World implements RouteWorld {
  int rows,x,speed;boolean duck,spin;
  public RecordedStep step(PadRow row) {
   boolean down=row.held(PlayerSlot.P2,PhysicalButton.DOWN),jump=row.held(PlayerSlot.P2,PhysicalButton.A);
   if(down) {if(duck && jump)spin=true;duck=true;}
   else {if(spin){x=10;speed=2048;}duck=false;spin=false;}
   rows++;return new RecordedStep(row,observe());
  }
  public RouteSample observe() {
   var one=new PlayerView(0,0,0,0,0,0,0,0,false,false,false,false,false,false,0,false,false,false,PlayerView.Motion.NONE);
   var two=new PlayerView(x,0,0,0,speed,0,speed,0,false,false,spin,duck,false,false,0,false,false,false,
       new PlayerView.Motion(1536,12,128,12,0,false));
   return new RouteSample(rows,1,new RouteSample.Level(0,0,0,"test"),new RouteSample.Clocks(rows,rows),one,Optional.of(two),0,0,0);
  }
  record Saved(World owner,int rows,int x,int speed,boolean duck,boolean spin) implements WorldCheckpoint {}
  public WorldCheckpoint capture(){return new Saved(this,rows,x,speed,duck,spin);}
  public void restore(WorldCheckpoint cp){var s=(Saved)cp;assertSame(this,s.owner());rows=s.rows();x=s.x();speed=s.speed();duck=s.duck();spin=s.spin();}
  public int rowsOffered(){return rows;}
  public void close(){}
 }
 @Test void nativeStateGatedChargeUsesOnlyTheTargetHumanPadAndQualifiesFrozenRows() {
  var hosts=new RouteWorldFactory(){
   public RouteWorld openSearch(LaunchContract l){return new World();}
   public ReplayWorld openColdReplay(LaunchContract l){return new World();}
   public String environment(){return "native-charge-contract";}
  };
  var goals=GoalProgram.of(Guard.NONE,Leg.of("p2",new RouteGoal.Achieve(new SamplePredicate.PlayerIn(PlayerSlot.P2,new Region(10,0,20,10))),200));
  var result=new RoutePlanner(hosts).plan(new RouteTask.Author(SyntheticRouteWorlds.launch(),InputTape.empty(),goals,new SearchBudget(30,10000,20,0)),Cancellation.NONE);
  var solved=assertInstanceOf(PlanResult.Solved.class,result);
  assertTrue(solved.route().tape().rows().stream().allMatch(r->r.p1()==0));
  assertTrue(solved.route().tape().rows().stream().anyMatch(r->r.held(PlayerSlot.P2,PhysicalButton.DOWN)&&r.held(PlayerSlot.P2,PhysicalButton.A)));
  var broken=InputTape.of(solved.route().tape().rows().stream().map(r->r.with(PlayerSlot.P2,r.p2()&~PhysicalButton.DOWN.bit())).toList());
  assertInstanceOf(ReplayResult.Failed.class,new RouteReplay(hosts).replay(RouteArtifact.of(solved.route().launch(),goals,broken,solved.route().manifest().provenance()),Cancellation.NONE));
 }
}

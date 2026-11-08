// Purpose: observe the production object-ID counter across planner checkpoint restore, without writes.
// Inputs: frozen P1-finish route plus engine-authored goal programme; no native/trace input.
// Origin: lane P EHZ checkpoint frontier, 2026-11-08. Delegates the one existing planner.
import com.openggf.route.*;
import com.openggf.stageplay.route.*;
import com.openggf.game.rewind.*;
import com.openggf.game.rewind.snapshot.*;
import com.openggf.game.session.*;
import java.nio.file.*;
class CompetitionCheckpointProbe {
 static int liveCounter() {
  try {var manager=SessionManager.getCurrentGameplayMode().getObjectManager();var f=manager.getClass().getDeclaredField("dynamicObjectIdCounter");f.setAccessible(true);return f.getInt(manager);}
  catch(Exception e){throw new RuntimeException(e);}
 }
 static ObjectManagerSnapshot snapshot(WorldCheckpoint cp) {
  try {var m=cp.getClass().getDeclaredMethod("state");m.setAccessible(true);var state=(CompositeSnapshot)m.invoke(cp);
   return state.entries().values().stream().filter(v->v instanceof ObjectManagerSnapshot).map(v->(ObjectManagerSnapshot)v).findFirst().orElseThrow();}
  catch(Exception e){throw new RuntimeException(e);}
 }
 public static void main(String[] args) throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("ROUTE.route.json GOALS.json S2_ROM");
  var original=RouteFiles.read(Path.of(args[0]));var goals=RouteCodec.parseGoals(Files.readString(Path.of(args[1])));
  var base=new StageplayCompetitionRouteHosts(Path.of(args[2]));
  var hosts=new RouteWorldFactory(){
   public RouteWorld openSearch(LaunchContract launch) {
    var world=base.openSearch(launch);
    return new RouteWorld(){
     boolean reported;
     public RecordedStep step(PadRow row){return world.step(row);}
     public RouteSample observe(){return world.observe();}
     public int rowsOffered(){return world.rowsOffered();}
     public WorldCheckpoint capture(){return world.capture();}
     public void restore(WorldCheckpoint cp){
      var s=snapshot(cp);int before=liveCounter();world.restore(cp);int after=liveCounter();
      if(!reported && s.dynamicObjectIdCounter()!=after){reported=true;
       System.out.println("RESTORE_COUNTER rows="+world.rowsOffered()+" captured="+s.dynamicObjectIdCounter()+" liveBefore="+before+" liveAfter="+after+" active="+s.slots().size()+" dynamics="+s.dynamicObjects().size());
       for(var o:s.slots())System.out.println("ACTIVE "+o.spawn()+" id="+o.objectId());
       for(var o:s.dynamicObjects())System.out.println("DYNAMIC "+o);
      }
     }
     public void close(){world.close();}
    };
   }
   public ReplayWorld openColdReplay(LaunchContract launch){return base.openColdReplay(launch);}
   public String environment(){return base.environment();}
  };
  System.out.println(StageplayRoutePlanner.planNative(hosts,original.launch(),original.tape(),goals,
       new SearchBudget(5000,800000,256,64),()->false,p->{if(p.expansions()%100==0)System.out.println(p);}));
 }
}

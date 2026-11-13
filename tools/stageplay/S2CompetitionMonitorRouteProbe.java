// Purpose: replay a frozen competition route and record monitor/award witnesses without authoring input.
// Inputs: route manifest, verified S2 ROM, new output CSV. Origin: eight question-mark outcomes, 2026-11-09.
import com.openggf.route.*;
import com.openggf.tools.StageplayCompetitionRouteHosts;
import java.nio.file.*;
public class S2CompetitionMonitorRouteProbe {
    public static void main(String[] args) throws Exception {
        var route=RouteFiles.read(Path.of(args[0]));
        try(var out=Files.newBufferedWriter(Path.of(args[2]),StandardOpenOption.CREATE_NEW)) {
            out.write("row,lfc,content,init,breaker,p1x,p2x,p1lives,p2lives,p1rings,p2rings,p1monitors,p2monitors,hurt,dead\n");
            var result=new RouteReplay(new StageplayCompetitionRouteHosts(Path.of(args[1]))).replay(route,Cancellation.NONE,(row,s)->{
                var f=s.facts();
                try {
                    out.write(row+","+s.clocks().levelFrameCounter()+","+f.getOrDefault("competition.monitor.content",0)+","+f.getOrDefault("competition.monitor.initFrame",-1)+","+f.getOrDefault("competition.monitor.breaker",0)+","+s.p1().centreX()+","+s.p2().map(PlayerView::centreX).orElse(0));
                    for(String key:java.util.List.of("p1.lives","p2.lives","p1.rings","p2.rings","p1.monitors","p2.monitors"))out.write(","+f.getOrDefault("competition."+key,-1));
                    out.write(","+(s.p1().hurt()?1:0)+","+(s.p1().dead()?1:0)+"\n");
                } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
            });
            if(!result.passed())throw new IllegalStateException(result.toString());
            System.out.println(result);
        }
    }
}

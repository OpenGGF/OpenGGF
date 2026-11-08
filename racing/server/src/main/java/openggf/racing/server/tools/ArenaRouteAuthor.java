package openggf.racing.server.tools;

import com.openggf.control.*;
import com.openggf.data.Rom;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.*;
import com.openggf.game.arena.*;
import com.openggf.level.LevelArtInternalAccess;
import com.openggf.mods.run.*;
import com.openggf.game.arena.*;
import com.openggf.route.*;
import openggf.timeattack.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Purpose: bounded arena input authoring through Stageplay's shared RoutePlanner backend.
 * Inputs: a user-supplied locked-on ROM and a frozen arena attempt descriptor.
 * Origin: lane F ROM replay qualification; no positioned player writes or trace state.
 */
public final class ArenaRouteAuthor {
    private ArenaRouteAuthor() { }
    public static PlanResult plan(Path rom, AttemptStartDescriptor start, int attempt) {
        var factory = new RouteWorldFactory() {
            public RouteWorld openSearch(LaunchContract launch) { return new World(rom,start); }
            public ReplayWorld openColdReplay(LaunchContract launch) { return new World(rom,start); }
            public String environment() { return "hosted-arena/native-input/v1"; }
        };
        var launch=LaunchContract.coldLevel(RomIdentity.standard("s3k"),new LevelId("s3k",S3kCompetitionSession.GRAND_PRIX_ORDER.get(start.zone()).zoneId(),0),
                new Roster(start.character(),""),320,0);
        // The native countdown is a semantic wait, not an authored movement route.
        var prefix=InputTape.of(Collections.nCopies(MassStartRelease.ARENA_LEAD_FRAMES+1,PadRow.NEUTRAL));
        int line,finishLine;
        try(var entry=new World(rom,start)){line=entry.arena.geometry().lapLineX();
            finishLine=line+entry.arena.geometry().lapCount()*entry.arena.geometry().wrapWidthPx();}
        var goals=GoalProgram.of(Guard.NONE,
                Leg.of("start-crossing",new RouteGoal.Achieve(new SamplePredicate.PlayerIn(PlayerSlot.P1,
                        new Region(line+16,0,line+64,1024))),600),
                Leg.of("backward-crossing",new RouteGoal.Achieve(new SamplePredicate.PlayerIn(PlayerSlot.P1,
                        new Region(line-64,0,line-16,1024))),600),
                Leg.of("five-forward-wraps",new RouteGoal.Achieve(
                new SamplePredicate.PlayerIn(PlayerSlot.P1,new Region(finishLine,0,finishLine+64,1024))),2400));
        var task=new RouteTask.Author(launch,prefix,goals,new SearchBudget(attempt==0?128:256,attempt==0?20_000:40_000,8,4));
        return new RoutePlanner(factory).plan(task,Cancellation.NONE);
    }

    public record Sample(AttemptInputRecording recording,String ghostHash,int clockStart,List<Integer> laps,
            boolean finished,boolean startCrossing,boolean backwardCrossing,boolean death,int currentLap) { }

    public static Sample sample(Path rom,AttemptStartDescriptor start,InputTape tape) throws Exception {
        var recording=new AttemptInputRecording(start);
        var hash=java.security.MessageDigest.getInstance("SHA-256");
        var attempt=new TimeAttackAttempt(TimeAttackAttempt.TimingRule.RACE_CLOCK);
        boolean crossing=false,backwards=false,death=false;int previousLap=0;
        try(var world=new World(rom,start)) {
            for(var row:tape.rows()) {
                world.step(row);var step=world.last;
                int lap=step.race().currentLap();
                crossing|=lap>0;backwards|=lap<previousLap && !world.arena.respawned();
                death|=world.arena.respawned();previousLap=lap;
                attempt.onFrame(row.toEngine(recording.frameCount()).p1InputMask(),world.arena.trackFinished(),-1,step.race());
                var f=com.openggf.mods.run.NativePlayerPose.fromArena(step.pose()).toGhostFrame(false,Math.clamp(lap,0,15));
                var ghost=new com.openggf.net.ghost.GhostFrame(f.x(),f.y(),f.mappingFrame(),f.hFlip(),f.vFlip(),false,
                        f.priorityBucket(),f.highPriority(),f.hurt(),f.progress(),f.secondaryPath(),f.shoeKind(),world.arena.respawned());
                byte[] bytes=new byte[com.openggf.net.ghost.GhostFrameCodec.BYTES];
                com.openggf.net.ghost.GhostFrameCodec.encode(ghost,bytes,0);hash.update(bytes);
                recording.appendFrame(row.toEngine(recording.frameCount()).p1InputMask(),false);
                if(attempt.phase()==TimeAttackAttempt.Phase.FINISHED || attempt.phase()==TimeAttackAttempt.Phase.VOID)break;
            }
        }
        return new Sample(recording,java.util.HexFormat.of().formatHex(hash.digest()),attempt.clockStartFrame(),
                Arrays.stream(attempt.lapFrames()).boxed().toList(),attempt.phase()==TimeAttackAttempt.Phase.FINISHED,
                crossing,backwards,death,previousLap);
    }

    private static final class World implements RouteWorld {
        final Rom rom=new Rom();
        final S3kCompetitionNativeHost host;
        final S3kHostedArenaSession arena;
        int rows,previous,lastX,unwrappedX;
        ArenaStep last;
        World(Path path,AttemptStartDescriptor start) {
            try {
                SessionManager.clear();
                if(!rom.open(path.toString()))throw new IllegalArgumentException("ROM open failed");
                var engine=EngineServices.current();
                engine.roms().setRom(rom);engine.audio().setRom(rom);
                engine.audio().setBackend(new com.openggf.audio.NullAudioBackend());engine.graphics().initHeadless();
                var root=new Sonic3kGameModule();
                var resources=S3kCompetitionResources.identify(rom);
                host=new S3kCompetitionNativeHost(engine,root,new InputHandler(),ignored->{},
                        new S3kCompetitionRecords(rom.readBytes(resources.defaultRecords(),80)),ignored->{},
                        new S3kSoloArenaViews(engine.graphics()),LevelArtInternalAccess::initializeObjectArt);
                arena=new S3kHostedArenaSession(host,new HostedArenaRequest(start.zone(),start.character(),false),()->{});
                host.world().context().beginGameplayRun(TimeAttackRuntime.competitionPolicy(MonitorRule.OFF),start.zone(),0);
                lastX=host.session().player(0).centreX();unwrappedX=lastX;
            } catch(Exception failure) { rom.close();throw new IllegalStateException(failure); }
        }
        public RouteSample observe() {
            var p=host.session().player(0);var s=p.capture();var speeds=p.speeds();
            var view=new PlayerView(unwrappedX,p.centreY(),p.xFraction(),p.yFraction(),p.xVelocity(),p.yVelocity(),
                    s.groundVelocity(),s.angle(),(s.status()&2)!=0,(s.status()&4)!=0,s.spinDashFlag()!=0,false,
                    s.routine()==4,s.routine()>=6,0,s.standingObject()!=0,s.objectControl()!=0,(s.status()&1)!=0,
                    new PlayerView.Motion(speeds.maxSpeed(),speeds.acceleration(),speeds.deceleration(),speeds.acceleration(),0,false));
            return new RouteSample(rows,0,new RouteSample.Level(host.session().arena().zoneId(),0,0,"COMPETITION"),
                    new RouteSample.Clocks(host.world().context().getObjectManager().getVblaCounter(),host.session().levelFrameCounter()),view,Optional.empty(),
                    host.camera(0).getX(),host.camera(0).getY(),arena.geometry().wrapHeightPx());
        }
        public RecordedStep step(PadRow row) {
            var engine=row.toEngine(rows);int mask=engine.p1InputMask();int pressed=mask&~previous;
            var input=PlayerInputState.of(mask,pressed,engine.p1ActionMask(),
                    (pressed&16)!=0?InputActionMasks.ACTION_A:0,false,false);
            host.world().context().plcFrameLifecycle().runLogicalIteration(()->{},frame->{
                NativeCompetitionIterationAccess.runAdmitted(frame,()->last=arena.step(input));return null;
            });
            int x=host.session().player(0).centreX();
            unwrappedX+=RaceProgressEstimate.wrapDelta(x-lastX,arena.geometry().wrapWidthPx());
            lastX=x;previous=mask;rows++;
            return new RecordedStep(row,observe());
        }
        record Saved(World owner,CompositeSnapshot state,int rows,int previous,int lastX,int unwrappedX) implements WorldCheckpoint { }
        public WorldCheckpoint capture() {
            return new Saved(this,host.world().context().getRewindRegistry().capture(),rows,previous,lastX,unwrappedX);
        }
        public void restore(WorldCheckpoint checkpoint) {
            var s=(Saved)checkpoint;if(s.owner()!=this)throw new IllegalArgumentException("foreign route checkpoint");
            host.world().context().getRewindRegistry().restore(s.state());
            rows=s.rows();previous=s.previous();lastX=s.lastX();unwrappedX=s.unwrappedX();
        }
        public int rowsOffered() {return rows;}
        public void close() {try{arena.close();}finally{SessionManager.clear();rom.close();}}
    }
}

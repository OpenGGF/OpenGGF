package openggf.racing.server.verifier;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.*;
import com.openggf.game.arena.*;
import com.openggf.route.*;
import com.openggf.tests.rules.*;
import openggf.timeattack.*;
import openggf.racing.server.tools.ArenaRouteAuthor;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAttemptReplayHarnessPlannedArena {
    @Test void twoBoundedPlannerAttemptsThenInputOnlyReplayFallback() throws Exception {
        Files.createDirectories(Path.of("target/lane-f"));
        var rom=Path.of(System.getProperty("s3k.rom.path","s3k.gen"));
        var start=new AttemptStartDescriptor("s3k",1,0,"sonic",AttemptReplayHarness.fingerprintForRom(rom),
                TrackKind.ARENA,CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.OFF,
                GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,false);
        Path saved=Path.of("src/test/resources/racing/competition/bpz-planned-tape.gz");
        if(!Files.exists(saved))saved=Path.of("target/lane-f/planned-arena-tape.gz");
        InputTape best=InputTape.empty();boolean solved=false;int tries=0;
        if(Files.exists(saved)) {
            var rows=new java.util.ArrayList<PadRow>();
            try(var in=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(Files.newInputStream(saved)))) {
                int size=in.readInt();for(int i=0;i<size;i++)rows.add(PadRow.unpack(in.readUnsignedShort()));
            }
            best=InputTape.of(rows);
        } else {
            Path counter=Path.of("target/lane-f/planner-attempt-count.txt");
            int previous=Files.exists(counter)?Integer.parseInt(Files.readString(counter)):0;
            assertEquals(0,previous,"two planner attempts are a session-wide limit; preserve the generated tape");
            for(int attempt=0;attempt<2;attempt++) {
                tries++;Files.writeString(counter,Integer.toString(tries));
                var result=ArenaRouteAuthor.plan(rom,start,attempt);
                var tape=result instanceof PlanResult.Solved r?r.route().tape():((PlanResult.Stopped)result).bestDraft();
                System.out.println("Arena planner attempt "+tries+": "+result.getClass().getSimpleName()+" rows="+tape.size());
                if(tape.size()>best.size())best=tape;
                if(result instanceof PlanResult.Solved){solved=true;break;}
            }
            if(!solved)assertEquals(2,tries,"route-authoring kill condition");
            try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(saved)))) {
                out.writeInt(best.size());for(var row:best.rows())out.writeShort(row.packed());
            }
        }
        assertTrue(best.size()>240,"fallback includes native GO");
        var live=ArenaRouteAuthor.sample(rom,start,best);
        var ghosts=new java.util.ArrayList<com.openggf.net.ghost.GhostFrame>();
        var replay=AttemptReplayHarness.replay(live.recording(),rom,ghosts::add);
        assertNull(replay.failureReason());
        assertEquals(live.ghostHash(),replay.ghostStreamHashHex());
        assertEquals(live.clockStart(),replay.firstInputFrame());
        assertEquals(live.laps(),replay.lapFrames());
        assertEquals(live.currentLap(),ghosts.getLast().progress());
        assertEquals(live.finished(),replay.finished());
        assertTrue(live.startCrossing()); assertTrue(live.backwardCrossing());
        Files.writeString(Path.of("target/lane-f/planner-result.txt"),"attemptsThisRun="+tries+" solved="+solved
                +" finished="+live.finished()+" frames="+live.recording().frameCount()+" laps="+live.laps()
                +" go="+live.clockStart()+" startCrossing="+live.startCrossing()+" backwards="+live.backwardCrossing()+" death="+live.death());
        try(var out=new java.util.zip.GZIPOutputStream(Files.newOutputStream(Path.of("target/lane-f/planned-arena.input.gz")))) {
            out.write(live.recording().encode());
        }
    }
}

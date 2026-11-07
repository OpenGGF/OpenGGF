package openggf.timeattack;
import com.openggf.mods.run.*;
import com.openggf.control.*;
import com.openggf.net.ghost.GhostFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestCompetitionIntegrationRuntime {
    @TempDir Path root;
    TimeAttackRuntime runtime(TrackKind kind) {
        var r = new TimeAttackRuntime(new GhostStore(root),root.resolve("identity"),()->false);
        r.armForLaunch(new TimeAttackLaunchRequest("s3k",0,0,"sonic",List.of(),kind,
                CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.COMPETITION_ITEMS,true));
        r.beginAttemptForTest("fp"); return r;
    }
    @Test void liveActProfileCarriesTheEngineSourceInventory() {
        var r=runtime(TrackKind.ACT);
        var start=new RunLevelStart(r.runSpec(),"fp",false,4096,1024,LevelStartCause.LAUNCH,TrackGeometry.ACT,31);
        assertEquals(31,openggf.timeattack.mp.LiveLevelProfileFactory.fromLevelStart(start).maxSwapSources());
    }
    @Test void arenaZeroAndAizOneHaveDifferentRunKindsAndSwapReachesEngine() {
        var arena = runtime(TrackKind.ARENA);
        assertEquals(TrackKind.ARENA,arena.runSpec().kind()); assertTrue(arena.runSpec().swapEnabled()); assertTrue(arena.runSpec().policy().swapEnabled());
        var act = runtime(TrackKind.ACT);
        assertEquals(TrackKind.ACT,act.runSpec().kind());
        assertFalse(new RunSpec("s3k",0,0,"sonic",TimeAttackRuntime.runPolicy()).swapEnabled());
    }
    RunInput input(boolean start, boolean down, boolean accept) {
        return new RunInput() {
            public LogicalInputSnapshot input() { return new LogicalInputSnapshot(PlayerInputState.neutral(),PlayerInputState.neutral(),false,down,false,false,accept,false,start,false,false,false,false,false,false); }
            public boolean keyDown(int key) { return false; }
            public boolean keyPressed(int key) { return false; }
        };
    }
    @Test void liveMenuChatAndConfirmedForfeitNeverPauseAndSendLeave() {
        var r = runtime(TrackKind.ARENA); var events = new ArrayList<String>();
        r.attachHandle(new RunHandle() {
            public void retry() { fail("Competition cannot retry"); }
            public void leave() { events.add("LEAVE"); }
            public void spectate(boolean active,int dx,int dy) {}
            public boolean isActive() { return true; }
        });
        r.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
            public void onAttemptBegan(int id) {}
            public void onFrameSampled(int id,GhostFrame f) {}
            public void onAttemptFinished(int id,int t,int first,int finish,byte[] hash,AttemptInputRecording rec) {}
            public void onAttemptVoided(int id) {}
            public void onAttemptVoided(int id,String reason) { events.add(reason); }
        });
        r.setFrameCompanion(new TimeAttackRuntime.FrameCompanion() {
            public boolean admit(RunInput i) { return true; }
            public void afterStep() {}
            public void openRaceChat() { events.add("CHAT"); }
        });
        assertTrue(r.admitStep(input(true,false,false)));
        assertTrue(r.admitStep(input(false,false,true)));
        assertEquals(List.of("CHAT"),events);
        assertTrue(r.admitStep(input(true,false,false)));
        r.admitStep(input(false,true,false)); r.admitStep(input(false,false,true));
        assertEquals(List.of("CHAT"),events); // safe confirm defaults to Keep racing
        r.admitStep(input(false,true,false));
        assertTrue(r.admitStep(input(false,false,true)));
        assertEquals(List.of("CHAT","LEFT","LEAVE"),events);
    }
    @Test void arenaSuppressesRestartDigitWhileActShowsIt() {
        for (var kind : TrackKind.values()) {
            var r = runtime(kind);
            r.onLevelReady(new RunLevelStart(r.runSpec(),"fp",false,8192,1024,LevelStartCause.FAST_DEATH_RESTART,TrackGeometry.ACT));
            assertTrue(r.admitStep(input(false,false,false)));
            r.afterStep(new RunStep(0,0,false,new PlayerPose(100,100,0,false,false,0,false),false,-1,false));
            assertFalse(r.admitStep(input(false,false,false)));
            assertTrue(r.restartCountdownFrames() > 0);
            assertEquals(kind == TrackKind.ARENA ? 0 : 3, r.countdownDigit());
        }
    }

    @Test void checkpointAndArenaSignalEmitExactlyOneResetBit() {
        for(var kind:TrackKind.values()) {
            var r=runtime(kind); var frames=new ArrayList<GhostFrame>();
            r.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
                public void onAttemptBegan(int id) {}
                public void onFrameSampled(int id,GhostFrame f) { frames.add(f); }
                public void onAttemptFinished(int id,int t,int first,int finish,byte[] h,AttemptInputRecording rec) {}
                public void onAttemptVoided(int id) {}
            });
            r.onLevelReady(new RunLevelStart(r.runSpec(),"fp",false,8192,1024,LevelStartCause.CHECKPOINT_RESPAWN,TrackGeometry.ACT));
            var pose=new PlayerPose(100,100,0,false,false,0,false);
            r.afterStep(new RunStep(0,8,false,pose,false,0,false,RaceClock.ACT,
                    kind==TrackKind.ARENA?RaceSignal.ARENA_DEATH_RESPAWN:RaceSignal.NONE,RaceEventResult.NONE));
            r.afterStep(new RunStep(1,8,false,pose,false,0,false));
            assertTrue(frames.get(0).respawnReset()); assertFalse(frames.get(1).respawnReset());
            assertEquals(0,r.countdownDigit());
        }
    }
}

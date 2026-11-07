package com.openggf.game.sonic3k.competition;

import com.openggf.control.PlayerInputState;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.mods.run.*;
import com.openggf.net.hub.*;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.tests.rules.*;
import openggf.timeattack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestHostedCompetitionEndToEnd {
    @TempDir Path root;
    @Test void liveArenaFallbackAndInputRecordingReplayToTheSameGhostHash() throws Exception {
        var rom = Path.of(System.getProperty("s3k.rom.path","s3k.gen"));
        var fingerprint = openggf.racing.server.verifier.AttemptReplayHarness.fingerprintForRom(rom);
        var recording = new AttemptInputRecording(new AttemptStartDescriptor("s3k",1,0,"sonic",fingerprint,
                TrackKind.ARENA,CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.COMPETITION_ITEMS,
                com.openggf.game.session.GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,true));
        var hash = java.security.MessageDigest.getInstance("SHA-256");
        var attempt = new TimeAttackAttempt(TimeAttackAttempt.TimingRule.RACE_CLOCK);
        try (var fixture = new HostedArenaFixture();
             var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(1,"sonic",true),()->{})) {
            var policy = new RunSpec("s3k",1,0,"sonic",TimeAttackRuntime.competitionPolicy(MonitorRule.COMPETITION_ITEMS),
                    null,TrackKind.ARENA,true).policy();
            fixture.host.world().context().beginGameplayRun(policy,1,0);
            for(int i=0;i<300;i++) {
                if(i==260 || i==280) {
                    var player=fixture.host.session().player(0);
                    RaceEvent event = i==260 ? new RaceEvent.SwapApply(41,new SwapTarget(player.centreX(),player.centreY(),
                            0,0,false,false,2,false,1,23,0)) : new RaceEvent.SwapFallback(42);
                    recording.appendEvent(i,event); arena.raceSwaps().queueForStep(event);
                }
                fixture.iteration(()-> {
                    var step = arena.step(PlayerInputState.neutral());
                    attempt.onFrame(0,arena.trackFinished(),-1,step.race());
                    var f = step.pose().toGhostFrame(false,Math.clamp(step.race().currentLap(),0,15));
                    byte[] bytes = new byte[com.openggf.net.ghost.GhostFrameCodec.BYTES];
                    com.openggf.net.ghost.GhostFrameCodec.encode(f,bytes,0); hash.update(bytes);
                });
                recording.appendFrame(0,false);
                arena.raceSwaps().consumeSignal();
                var result=arena.raceSwaps().consumeApplied();
                if(i==260) assertEquals(RaceEventResult.Outcome.APPLIED,result.outcome());
                if(i==280) assertEquals(RaceEventResult.Outcome.FALLBACK,result.outcome());
            }
        }
        var replay = openggf.racing.server.verifier.AttemptReplayHarness.replay(recording,rom);
        assertNull(replay.failureReason());
        assertEquals(java.util.HexFormat.of().formatHex(hash.digest()),replay.ghostStreamHashHex());
        assertEquals(attempt.clockStartFrame(),replay.firstInputFrame());
        assertEquals(Arrays.stream(attempt.lapFrames()).boxed().toList(),replay.lapFrames());
    }

    @Test void positionedBpzFinishReachesRuntimeHostAndHud() throws Exception {
        try (var fixture = new HostedArenaFixture();
             var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(1,"sonic",false),()->{})) {
            var runtime = new TimeAttackRuntime(new GhostStore(root),root.resolve("identity"),()->false);
            runtime.armForLaunch(new TimeAttackLaunchRequest("s3k",1,0,"sonic",List.of(),TrackKind.ARENA,
                    CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.OFF,false));
            fixture.host.world().context().beginGameplayRun(runtime.runSpec().policy(),1,0);
            var level = fixture.host.world().context().getLevelManager().getCurrentLevel();
            runtime.onLevelReady(new RunLevelStart(runtime.runSpec(),"fp",false,
                    level.getLayerWidthBlocks(0)*level.getBlockPixelSize(),level.getLayerHeightBlocks(0)*level.getBlockPixelSize(),
                    LevelStartCause.LAUNCH,arena.geometry()));
            assertEquals(512,arena.geometry().wrapWidthPx());
            var attempt = new TimeAttackAttempt(TimeAttackAttempt.TimingRule.RACE_CLOCK);
            var ordinal = new int[1];
            Runnable step = () -> fixture.iteration(() -> {
                var observation = arena.step(PlayerInputState.neutral());
                attempt.onFrame(0,arena.trackFinished(),-1,observation.race());
                runtime.afterStep(new RunStep(ordinal[0]++,0,false,observation.pose(),arena.trackFinished(),-1,false,
                        observation.race(),RaceSignal.NONE,RaceEventResult.NONE));
            });
            while (!fixture.host.session().started()) { assertTrue(ordinal[0]<400); step.run(); }
            assertEquals(MassStartRelease.ARENA_LEAD_FRAMES,attempt.clockStartFrame());
            var player = fixture.host.session().player(0);
            player.setObjectControl(1); player.setSolidBits(0xC,0xD);
            var goal = fixture.host.world().context().getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                    .findFirst().orElseThrow();
            var boundaries = new ArrayList<Integer>();
            for (int crossing=0;crossing<6;crossing++) {
                player.setCentre(goal.getX()-64,goal.getY()); step.run();
                for(int wait=0;wait<5+crossing*3;wait++) step.run();
                player.setCentre(goal.getX()+1,goal.getY()); step.run();
                assertEquals(crossing,arena.observation().race().completedLaps());
                if(crossing>0) boundaries.add(ordinal[0]-1);
            }
            assertEquals(5,boundaries.size());
            assertEquals(TimeAttackAttempt.Phase.FINISHED,attempt.phase());
            assertArrayEquals(attempt.lapFrames(),runtime.lapFrames());
            int boundary=attempt.clockStartFrame();
            for(int i=0;i<5;i++) { assertEquals(boundaries.get(i)-boundary,attempt.lapFrames()[i]); boundary=boundaries.get(i); }
            assertEquals(attempt.bestLapFrames(),runtime.competitionHudState().bestLapFrames());
            assertEquals(5,runtime.competitionHudState().completedLapFrames().size());
            var finish = new ControlMessage.AttemptFinish(1,attempt.finalTimeFrames(),attempt.clockStartFrame(),
                    attempt.finishFrame(),Arrays.stream(attempt.lapFrames()).boxed().toList(),List.of(),
                    "ab".repeat(32),"cd".repeat(32),null);
            CompetitionHostAssertions.acceptsBpzFinish(finish);
            runtime.deactivate();
        }
    }
}

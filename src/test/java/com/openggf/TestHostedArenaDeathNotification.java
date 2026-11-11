package com.openggf;

import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.sonic3k.competition.*;
import com.openggf.mods.run.*;
import com.openggf.game.arena.*;
import com.openggf.net.ghost.GhostFrame;
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
class TestHostedArenaDeathNotification {
    @TempDir Path root;
    @Test void nativeRespawnContinuesTheClockAndMarksOnlyItsOwnGhostFrame() throws Exception {
        try(var fixture=new HostedArenaFixture();
            var arena=new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(1,"sonic",false),()->{})) {
            var runtime=new TimeAttackRuntime(new GhostStore(root),root.resolve("id"),()->false);
            runtime.armForLaunch(new TimeAttackLaunchRequest("s3k",1,0,"sonic",List.of(),TrackKind.ARENA,
                    CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.OFF,false));
            var frames=new ArrayList<GhostFrame>();var starts=new ArrayList<LevelStartCause>();
            var ordinals=new ArrayList<Long>();
            int[] began={0},voided={0};
            runtime.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
                public void onAttemptBegan(int n){began[0]++;}
                public void onFrameSampled(int n,GhostFrame f){frames.add(f);}
                public void onAttemptVoided(int n){voided[0]++;}
                public void onAttemptFinished(int n,int time,int first,int finish,byte[] h,AttemptInputRecording r){}
            });
            var controller=new HostedRunController(GameServices.configuration());
            controller.begin(runtime.runSpec(),new RunHost() {
                public void onLevelReady(RunLevelStart s){starts.add(s.cause());runtime.onLevelReady(s);}
                public void afterStep(RunStep s){ordinals.add(s.ordinal());runtime.afterStep(s);}
            });
            assertTrue(controller.ownsStart(com.openggf.game.GameMode.COMPETITION));
            controller.bindArena(arena);controller.onArenaReady(arena.geometry());
            Runnable step=()->fixture.iteration(()->controller.afterArenaStep(arena.step(PlayerInputState.neutral()),arena.trackFinished()));
            for(int i=0;i<=MassStartRelease.ARENA_LEAD_FRAMES;i++)step.run();
            assertTrue(runtime.isAttemptRunning());
            var player=fixture.host.session().player(0);
            // Positioned live setup only; no positioned state is supplied to production replay.
            player.kill();player.setCentre(player.centreX(),fixture.host.camera(0).getY()+0x200);
            step.run();step.run();
            assertEquals(List.of(LevelStartCause.LAUNCH,LevelStartCause.ARENA_DEATH_RESPAWN),starts);
            assertTrue(runtime.isAttemptRunning());
            assertEquals(1,began[0]);assertEquals(0,voided[0]);
            assertEquals(243,frames.size());
            assertTrue(frames.get(241).respawnReset());assertFalse(frames.get(242).respawnReset());
            assertEquals(242L,ordinals.getLast());
            controller.end(RunEndReason.LEFT);runtime.deactivate();
        }
    }
}

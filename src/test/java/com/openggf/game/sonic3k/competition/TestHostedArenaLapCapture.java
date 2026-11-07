package com.openggf.game.sonic3k.competition;

import com.openggf.control.PlayerInputState;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.mods.run.RaceClock;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestHostedArenaLapCapture {
    @Test void clockUsesHighestLapAndNativePerLapTruncation() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(0,"sonic",false), () -> {});
            var session = fixture.host.session();
            var player = session.player(0);
            session.signalGo();
            player.setObjectControl(1); player.setSolidBits(0xC,0xD);
            var goal = fixture.host.world().context().getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                    .findFirst().orElseThrow();
            int executed = 0;
            int previousBoundary = 0;
            for (int crossing=0; crossing<6; crossing++) {
                player.setCentre(goal.getX()-0x40,goal.getY());
                fixture.iteration(() -> arena.step(PlayerInputState.neutral()));
                executed++;
                for (int wait=0;wait<5+crossing*3;wait++) {
                    fixture.iteration(() -> arena.step(PlayerInputState.neutral()));
                    executed++;
                }
                player.setCentre(goal.getX()+1,goal.getY());
                fixture.iteration(() -> arena.step(PlayerInputState.neutral()));
                assertEquals(crossing,arena.observation().race().completedLaps());
                executed++;
                if (crossing > 0) {
                    int frames = executed-1-previousBoundary;
                    assertEquals(frames/60*100 + frames%60*100/60,
                            session.capture().players().getFirst().lapTimes().get(crossing-1));
                    previousBoundary = executed-1;
                }
            }
            assertEquals(RaceClock.Failure.NONE,arena.observation().race().failure());
            assertTrue(session.finished(0));
        }
    }
    @Test void backwardCrossingAndDeathRollbackDoNotBookAnotherLap() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(0,"sonic",false), () -> {});
            var session = fixture.host.session();
            var player = session.player(0);
            var goal = fixture.host.world().context().getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                    .findFirst().orElseThrow();
            session.signalGo();
            player.setSolidBits(0xC,0xD);
            for(int i=0;i<2;i++) {
                player.setCentre(goal.getX()-64,goal.getY()); session.observeGoal(goal.getX(),goal.getY(),goal.getSpawn().subtype());
                player.setCentre(goal.getX()+1,goal.getY()); session.observeGoal(goal.getX(),goal.getY(),goal.getSpawn().subtype());
            }
            assertEquals(1,arena.observation().race().completedLaps());
            int lap = session.lap(0);
            player.setCentre(goal.getX()-1,goal.getY()); session.observeGoal(goal.getX(),goal.getY(),goal.getSpawn().subtype());
            assertEquals(lap-1,session.lap(0));
            assertEquals(1,arena.observation().race().completedLaps());
            session.notifyRespawn(0);
            session.observeGoal(goal.getX(),goal.getY(),goal.getSpawn().subtype());
            assertEquals(lap-2,session.lap(0));
            assertEquals(1,arena.observation().race().completedLaps());
        }
    }

    @Test void capIsTimeOverAndStartCannotPauseHostedPlay() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(2,"tails",false), () -> {});
            assertEquals(S3kCompetitionSession.Arena.CHROME_GADGET,fixture.host.session().arena());
            assertEquals(256,arena.geometry().wrapHeightPx());
            fixture.host.session().signalGo();
            fixture.host.session().setNativeLapTimerFrames(0,35999);
            fixture.host.session().tickGoalTimer(0);
            assertEquals(RaceClock.Failure.TIME_OVER,arena.observation().race().failure());
            var start = PlayerInputState.of(0,0,0,0,true,true);
            fixture.iteration(() -> arena.step(start));
            assertFalse(fixture.host.pause().gamePaused());
        }
    }
}

package com.openggf;

import com.openggf.game.sonic3k.competition.*;
import com.openggf.mods.run.*;
import com.openggf.game.session.*;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.sonic3k.Sonic3kGameModule;

import com.openggf.control.PlayerInputState;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestHostedArenaEquivalence {
    @Test void nativeMenuAndHostedSoloMatchRaceHaveIdenticalStateAcrossFalseStartAndDeath() throws Exception {
        for (boolean items : new boolean[]{false,true}) {
            var expected = new ArrayList<S3kCompetitionSession.State>();
            try (var fixture = new HostedArenaFixture()) {
                fixture.menuLaunch(items);
                for (int frame=0; frame<700; frame++) {
                    prepare(fixture,frame);
                    fixture.nativeStep(PlayerInputState.neutral());
                    expected.add(fixture.host.session().capture());
                }
            }
            try (var fixture = new HostedArenaFixture()) {
                var policy = GameplayRunPolicy.competition();
                policy = new GameplayRunPolicy(false,false,policy.actCompletion(),false,false,policy.deathRestart(),
                        false,false,items ? MonitorRule.COMPETITION_ITEMS : MonitorRule.OFF);
                var spec = new RunSpec("s3k",0,0,"sonic",policy,null,TrackKind.ARENA);
                var steps = new ArrayList<RunStep>();
                var loop = new GameLoop(fixture.input);
                loop.beginHostedRun(spec,new RunHost() {
                    @Override public void afterStep(RunStep step) { steps.add(step); }
                },ignored -> {});
                HostedArenaLaunch.open(loop,spec,new NativeMenuHostContext(EngineServices.current(),
                        new Sonic3kGameModule(),fixture.input,loop::setGameplayMode));
                fixture.host = (S3kCompetitionNativeHost) NativeCompetitionRoster.from(
                        SessionManager.getCurrentGameplayMode().getWorldSession());
                for (int frame=0; frame<700; frame++) {
                    prepare(fixture,frame);
                    fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
                    assertEquals(expected.get(frame), fixture.host.session().capture(), "frame " + frame + ", items=" + items);
                    if (frame == 0) {
                        var player = fixture.host.session().player(0);
                        var saved = player.capture();
                        player.setCentre(player.centreX()+1,player.centreY());
                        assertThrows(org.opentest4j.AssertionFailedError.class,
                                () -> assertEquals(expected.getFirst(),fixture.host.session().capture()),
                                "negative control: one changed centre pixel must fail the session comparison");
                        player.restore(saved);
                    }
                    assertEquals(expected.get(frame).players().getFirst().body().xFixed() >> 16,
                            steps.getLast().player().centreX());
                }
                assertEquals(1, fixture.host.players().size());
                assertEquals(700,steps.size());
                assertEquals(699,steps.getLast().ordinal());
            }
        }
    }
    static void prepare(HostedArenaFixture fixture, int frame) {
        var session = fixture.host.session();
        var player = session.player(0);
        var goal = fixture.host.world().context().getObjectManager().getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                .findFirst().orElseThrow();
        if (frame == 1 || frame == 2) {
            player.setObjectControl(1);
            player.setSolidBits(0xC,0xD);
            player.setCentre(goal.getX() + (frame == 1 ? -0x40 : 1),goal.getY());
        }
        if (frame == 500) player.kill();
        if (frame == 501) player.setCentre(player.centreX(),fixture.host.camera(0).getY() + 400);
    }
}

package com.openggf;

import com.openggf.game.sonic3k.competition.*;
import com.openggf.mods.run.*;
import com.openggf.game.arena.*;
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
    @Test void resultsHoldNativeStateWhileHostInputAndLeaveRemainLive() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var base = GameplayRunPolicy.competition();
            var policy = new GameplayRunPolicy(false,false,base.actCompletion(),false,false,base.deathRestart(),
                    false,false,MonitorRule.OFF);
            var spec = new RunSpec("s3k",0,0,"sonic",policy,null,TrackKind.ARENA);
            var steps = new ArrayList<RunStep>();
            var admissions = new java.util.concurrent.atomic.AtomicInteger();
            var ended = new ArrayList<RunEndReason>();
            var loop = new GameLoop(fixture.input);
            var handle = loop.beginHostedRun(spec,new RunHost() {
                @Override public boolean admitStep(RunInput input) { admissions.incrementAndGet(); return true; }
                @Override public void afterStep(RunStep step) { steps.add(step); }
            },ended::add);
            HostedArenaLaunch.open(loop,spec,new NativeMenuHostContext(EngineServices.current(),
                    new Sonic3kGameModule(),fixture.input,loop::setGameplayMode));
            fixture.host = (S3kCompetitionNativeHost) NativeCompetitionRoster.from(
                    SessionManager.getCurrentGameplayMode().getWorldSession());
            assertThrows(UnsupportedOperationException.class,handle::retry);
            var session = fixture.host.session();
            var player = session.player(0);
            session.signalGo();
            player.setObjectControl(1); player.setSolidBits(0xC,0xD);
            var goal = fixture.host.world().context().getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                    .findFirst().orElseThrow();
            for (int crossing=0;crossing<6;crossing++) {
                player.setCentre(goal.getX()-64,goal.getY());
                session.observeGoal(goal.getX(),goal.getY(),goal.getSpawn().subtype());
                player.setCentre(goal.getX()+1,goal.getY());
                session.observeGoal(goal.getX(),goal.getY(),goal.getSpawn().subtype());
            }
            session.finishFromNeon();
            fixture.input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
            for (int frame=0;frame<160;frame++)
                fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
            var held = session.capture();
            assertTrue(held.resultSent(),"script must exhaust the native results wait");
            int stepCount = steps.size();
            int admissionCount = admissions.get();
            for (int frame=0;frame<10;frame++)
                fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
            assertEquals(held,session.capture(),"results hold must not advance native state");
            assertEquals(stepCount,steps.size(),"held presentations are not executed run steps");
            assertEquals(admissionCount+10,admissions.get(),"host input remains live above the held view");
            handle.leave();
            fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
            assertEquals(java.util.List.of(RunEndReason.LEFT),ended);
        }
    }

    @Test void aHeldArenaPresentsNothingUntilItsFirstStep() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var base = GameplayRunPolicy.competition();
            var policy = new GameplayRunPolicy(false,false,base.actCompletion(),false,false,base.deathRestart(),
                    false,false,MonitorRule.OFF);
            var spec = new RunSpec("s3k",0,0,"sonic",policy,null,TrackKind.ARENA);
            var released = new java.util.concurrent.atomic.AtomicBoolean();
            var steps = new ArrayList<RunStep>();
            var loop = new GameLoop(fixture.input);
            loop.beginHostedRun(spec,new RunHost() {
                @Override public boolean admitStep(RunInput input) { return released.get(); }
                @Override public void afterStep(RunStep step) { steps.add(step); }
            },ignored -> {});
            HostedArenaLaunch.open(loop,spec,new NativeMenuHostContext(EngineServices.current(),
                    new Sonic3kGameModule(),fixture.input,loop::setGameplayMode));
            var launch = (HostedArenaLaunch) loop.getNativeMenuScreen();
            fixture.host = (S3kCompetitionNativeHost) NativeCompetitionRoster.from(
                    SessionManager.getCurrentGameplayMode().getWorldSession());
            var loaded = fixture.host.session().capture();
            fixture.input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
            for (int frame=0;frame<30;frame++)
                fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
            assertEquals(loaded,fixture.host.session().capture(),"the mass start holds the native session");
            assertTrue(steps.isEmpty());
            assertFalse(launch.presentsNativeFrame(),"loc_665A: black until the level loop's first frame");
            released.set(true);
            fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
            assertEquals(1,steps.size());
            assertTrue(launch.presentsNativeFrame());
        }
    }

    @Test void nativeMenuAndHostedSoloMatchRaceHaveIdenticalStateAcrossFalseStartAndDeath() throws Exception {
        for (boolean items : new boolean[]{false,true}) {
            var expected = new ArrayList<S3kCompetitionSession.State>();
            var expectedPoses = new ArrayList<PlayerPose>();
            try (var fixture = new HostedArenaFixture()) {
                fixture.menuLaunch(items);
                for (int frame=0; frame<700; frame++) {
                    prepare(fixture,frame);
                    fixture.nativeStep(buttons(frame));
                    expected.add(fixture.host.session().capture());
                    var sprite = fixture.host.player(0);
                    var nativePlayer = fixture.host.session().player(0);
                    int shoeKind = !sprite.hasSpeedShoes() ? 0 : nativePlayer.speeds().maxSpeed()==0x300 ? 2 : 1;
                    expectedPoses.add(new PlayerPose(sprite.getCentreX(),sprite.getCentreY(),sprite.getMappingFrame(),
                            sprite.getRenderHFlip(),sprite.getRenderVFlip(),sprite.getPriorityBucket(),
                            sprite.isHighPriority(),sprite.isHurt(),sprite.getTopSolidBit()==0xE,shoeKind));
                    if (frame == 2) assertTrue(fixture.host.session().controlsLocked(),"script must enter native false start");
                    if (frame == 500) assertTrue(fixture.host.session().player(0).dead(),"script must enter native death");
                    if (frame == 501) assertFalse(fixture.host.session().player(0).dead(),"script must cross native respawn row");
                }
                assertNotEquals(expected.get(520).players().getFirst().body().xFixed(),
                        expected.get(600).players().getFirst().body().xFixed(),"script must execute real movement after respawn");
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
                    fixture.input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                            buttons(frame),PlayerInputState.neutral()));
                    fixture.iteration(() -> loop.getNativeMenuScreen().update(fixture.input));
                    assertEquals(buttons(frame).heldMask(),steps.getLast().heldMask());
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
                    assertEquals(expectedPoses.get(frame),steps.getLast().player(),"native pose at frame " + frame);
                }
                assertEquals(1, fixture.host.players().size());
                assertEquals(700,steps.size());
                assertEquals(699,steps.getLast().ordinal());
            }
        }
    }
    private static PlayerInputState buttons(int frame) {
        if (frame < 520) return PlayerInputState.neutral();
        int action = frame >= 550 && frame < 556 ? com.openggf.control.InputActionMasks.ACTION_A : 0;
        return PlayerInputState.of(8,frame == 520 ? 8 : 0,action,
                frame == 550 ? action : 0,false,false);
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

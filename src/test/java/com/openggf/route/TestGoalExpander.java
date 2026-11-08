package com.openggf.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Goal programs on the shared search: object legs, a prefix, replayed checks and the encounter scoring. */
class TestGoalExpander {
    private static final ObjectTarget BOSS = ObjectTarget.typed("SyntheticBoss", Capability.HIT_POINTS);
    private static final ObjectTarget PLATFORM = ObjectTarget.placement(0, 0, 3, 0x51, 0);

    private static RouteArtifact plan(SyntheticRouteWorlds world, InputTape prefix, GoalProgram goals) {
        var task = new RouteTask.Author(SyntheticRouteWorlds.launch(), prefix, goals, SearchBudget.encounter());
        var result = new RoutePlanner(world).plan(task, Cancellation.NONE);
        return assertInstanceOf(PlanResult.Solved.class, result, result.toString()).route();
    }

    @Test
    void aDefeatLegJumpsOnTheBossUntilItsOwnerReportsDefeat() {
        var world = SyntheticRouteWorlds.flat().withBoss(80, 3);
        var goals = GoalProgram.of(new Guard(true, true, false, false), RouteGoals.defeat("boss", BOSS, 1500));
        var route = plan(world, InputTape.empty(), goals);
        assertEquals(goals, route.checks(), "the goal program is the route's replayed checks");
        try (var playback = new RouteReplay(world).open(route)) {
            RouteSample end = playback.stepTo(playback.size());
            ObjectView boss = BOSS.resolve(end, PlayerSlot.P1).orElseThrow();
            assertTrue(boss.defeated());
            assertEquals(0, boss.hitsRemaining());
        }
    }

    @Test
    void legsCompleteInOrderAfterAVerbatimPrefix() {
        var world = SyntheticRouteWorlds.flat().withPhasePlatform(60);
        var prefix = SyntheticRouteWorlds.right(20);
        var goals = new GoalProgram(Guard.safe(), List.of(
                RouteGoals.reach("near-platform", PlayerSlot.P1, new Region(55, 0, 65, 2000), 300),
                RouteGoals.awaitPhase("platform-gone", PLATFORM, "gone", 200),
                RouteGoals.reach("past", PlayerSlot.P1, new Region(100, 0, 110, 2000), 300)), prefix.size());
        var route = plan(world, prefix, goals);
        assertEquals(prefix, route.tape().slice(0, prefix.size()), "the prefix rows are kept verbatim");
        var replay = assertInstanceOf(ReplayResult.Passed.class, new RouteReplay(world).replay(route, Cancellation.NONE));
        assertTrue(replay.evidence().coverage().contains("goals[near-platform,platform-gone,past]"),
                replay.evidence().coverage().toString());
    }

    @Test
    void goalProgrammeCommitsBoundaryBeforeTryingAnotherPolicyOnTheOldEpoch() {
        var world = new SyntheticRouteWorlds(List.of(), 20, Integer.MIN_VALUE, false);
        var goals = GoalProgram.of(Guard.NONE,
                RouteGoals.reach("new-epoch",PlayerSlot.P1,new Region(30,990,40,1010),100),
                RouteGoals.reach("after",PlayerSlot.P1,new Region(50,990,70,1010),100));
        assertTrue(plan(world,InputTape.empty(),goals).tape().size()>0);
    }

    @Test
    void completedPrefixLegsAreNotReauthored() {
        var prefix=SyntheticRouteWorlds.right(100);
        var goals=GoalProgram.of(Guard.safe(),
                RouteGoals.reach("earlier",PlayerSlot.P1,new Region(20,990,30,1010),150),
                RouteGoals.reach("later",PlayerSlot.P1,new Region(200,990,210,1010),500));
        var route=plan(SyntheticRouteWorlds.flat(),prefix,goals);
        assertEquals(prefix,route.tape().slice(0,prefix.size()));
        assertTrue(route.tape().slice(prefix.size(),route.tape().size()).rows().stream()
                .noneMatch(row->row.held(PlayerSlot.P1,PhysicalButton.LEFT)),
                "the completed earlier leg must not send the actor back through the prefix");
    }

    @Test
    void prefixAndTrialsRespectTheirExactStepBudget() {
        for(int prefixRows:new int[]{0,100}) {
            var task=new RouteTask.Author(SyntheticRouteWorlds.launch(),SyntheticRouteWorlds.right(prefixRows),
                    GoalProgram.of(Guard.safe(),RouteGoals.reach("far",PlayerSlot.P1,new Region(200,990,210,1010),500)),
                    new SearchBudget(10,20,4,0));
            var stopped=assertInstanceOf(PlanResult.Stopped.class,new RoutePlanner(SyntheticRouteWorlds.flat()).plan(task,Cancellation.NONE));
            assertEquals(StopReason.BUDGET_EXHAUSTED,stopped.reason());
            assertEquals(20,stopped.work().searchSteps());
        }
    }

    @Test
    void objectGoalsSurviveTheCanonicalCodec() throws Exception {
        var goals = new GoalProgram(Guard.safe().holding(new SamplePredicate.Riding(PlayerSlot.P1, PLATFORM)), List.of(
                RouteGoals.board("board", PlayerSlot.P1, PLATFORM, 100),
                RouteGoals.ride("ride", PlayerSlot.P1, PLATFORM, new SamplePredicate.PhaseIs(PLATFORM, "solid"), 100),
                RouteGoals.attach("updraft", PlayerSlot.P1, new ObjectTarget.Typed("UpdraftObjectInstance",
                        Capability.ATTACHMENT, Optional.of(new Region(0, 0, 100, 100))), InteractionKind.AIRFLOW, 100),
                RouteGoals.hold("vine", PlayerSlot.P1, new ObjectTarget.Instance(new InstanceKey(2, 5, 1, 7, -1, "LAYOUT")),
                        InteractionKind.HANG, new SamplePredicate.LevelIs(1, 0), 100),
                RouteGoals.defeat("boss", BOSS, 100),
                RouteGoals.cross("load", 100)), 12);
        assertEquals(goals, RouteCodec.parseGoals(RouteCodec.formatGoals(goals)));
    }

    @Test
    void missingBossAndDeathCannotOutscoreObservedDamage() {
        double hit = GoalExpander.encounterScore(false, 8, 7, 1, false, 20);
        assertTrue(hit > GoalExpander.encounterScore(false, 8, null, 99, false, 0),
                "losing the target is not proof of defeating it");
        assertTrue(hit > GoalExpander.encounterScore(true, 8, 0, 99, false, 0));
        assertTrue(GoalExpander.encounterScore(false, 1, 0, 0, false, 0) > hit);
    }
}

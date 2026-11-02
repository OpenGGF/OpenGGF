package com.openggf.route;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Replay evaluates the manifest's goal program, reports every row, and names the first diverging row. */
class TestRouteReplayChecks {
    private static final Provenance PROVENANCE = new Provenance("test", "", "", List.of());

    private static RouteArtifact route(GoalProgram goals, int rows) {
        return RouteArtifact.of(SyntheticRouteWorlds.launch(), goals, SyntheticRouteWorlds.right(rows), PROVENANCE);
    }

    @Test
    void theObserverSeesEveryPostRowSampleOfTheEvidenceMintingRun() {
        var rows = new ArrayList<Integer>();
        var xs = new ArrayList<Integer>();
        var result = new RouteReplay(SyntheticRouteWorlds.flat()).replay(route(GoalProgram.NONE, 30), Cancellation.NONE,
                (row, sample) -> {
                    rows.add(row);
                    xs.add(sample.p1().centreX());
                });
        var passed = assertInstanceOf(ReplayResult.Passed.class, result);
        assertEquals(30, rows.size());
        assertEquals(0, rows.getFirst());
        assertEquals(29, rows.getLast());
        assertEquals(30, xs.getLast());
        assertEquals(30, passed.end().p1().centreX());
    }

    @Test
    void legsCompleteInOrderWithinTheirBounds() {
        var goals = GoalProgram.of(Guard.safe(),
                Leg.of("first", new RouteGoal.Achieve(new SamplePredicate.PlayerIn(PlayerSlot.P1,
                        new Region(20, 0, 25, 2000))), 30),
                Leg.of("second", new RouteGoal.Achieve(new SamplePredicate.PlayerIn(PlayerSlot.P1,
                        new Region(40, 0, 45, 2000))), 30));
        var passed = assertInstanceOf(ReplayResult.Passed.class,
                new RouteReplay(SyntheticRouteWorlds.flat()).replay(route(goals, 50), Cancellation.NONE));
        assertTrue(passed.evidence().coverage().contains("goals[first,second]"), passed.evidence().coverage().toString());

        var tooSlow = GoalProgram.of(Guard.NONE, Leg.of("far", new RouteGoal.Achieve(new SamplePredicate.PlayerIn(
                PlayerSlot.P1, new Region(40, 0, 45, 2000))), 10));
        var failed = assertInstanceOf(ReplayResult.Failed.class,
                new RouteReplay(SyntheticRouteWorlds.flat()).replay(route(tooSlow, 50), Cancellation.NONE));
        assertEquals(9, failed.row(), failed.reason());
        assertTrue(failed.reason().contains("leg far not complete within 10 rows"), failed.reason());

        var neverReached = GoalProgram.of(Guard.NONE, Leg.of("never", new RouteGoal.Achieve(
                new SamplePredicate.PlayerIn(PlayerSlot.P1, new Region(900, 0, 905, 2000))), 1000));
        var unfinished = assertInstanceOf(ReplayResult.Failed.class,
                new RouteReplay(SyntheticRouteWorlds.flat()).replay(route(neverReached, 50), Cancellation.NONE));
        assertEquals(50, unfinished.row(), "an end condition fails at the tape length");
    }

    @Test
    void guardsFailOnTheFirstDivergingRow() {
        var pits = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(12, 14)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, false);
        var failed = assertInstanceOf(ReplayResult.Failed.class,
                new RouteReplay(pits).replay(route(GoalProgram.of(Guard.safe()), 40), Cancellation.NONE));
        assertEquals(11, failed.row(), "row 11 walks into x=12: " + failed.reason());
        assertTrue(failed.reason().contains("P1 died"), failed.reason());
        var unguarded = new RouteReplay(pits).replay(route(GoalProgram.NONE, 40), Cancellation.NONE);
        assertTrue(unguarded.passed(), "an empty program checks rows only");

        var boundary = new SyntheticRouteWorlds(List.of(), 10, Integer.MIN_VALUE, false);
        var crossed = assertInstanceOf(ReplayResult.Failed.class,
                new RouteReplay(boundary).replay(route(GoalProgram.of(Guard.safe()), 20), Cancellation.NONE));
        assertEquals(9, crossed.row());
        var expected = GoalProgram.of(Guard.safe(), Leg.of("load", new RouteGoal.EnterBoundary(), 20));
        assertTrue(new RouteReplay(boundary).replay(route(expected, 20), Cancellation.NONE).passed(),
                "a boundary a leg asks for is not a guard violation");
    }
}

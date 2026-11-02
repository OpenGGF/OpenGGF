package com.openggf.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exact re-synchronising repair on a synthetic world: an engine change (a one-pixel nudge) moves the
 * player off the recorded run, one edited row brings every tracked state back, and the original
 * rows after it reproduce the recorded run exactly.
 */
class TestExactResyncRepair {
    private static final Provenance PROVENANCE = new Provenance("test", "", "", List.of());

    private static RouteArtifact original() {
        return RouteArtifact.of(SyntheticRouteWorlds.launch(), SyntheticRouteWorlds.right(200), PROVENANCE);
    }

    private static ReferencePath recordedOnFlat() {
        RouteArtifact route = original();
        return ReferencePath.extract(SyntheticRouteWorlds.flat(), route.launch(), route.tape(), route.tape().size());
    }

    private static PlanResult repair(SyntheticRouteWorlds now, ReferencePath reference, int firstEditable) {
        return new RoutePlanner(now).plan(new RouteTask.Repair(original(), reference,
                new RepairScope(firstEditable, 64, 8), AuthorHints.exactResync(true), SearchBudget.referenceRepair()),
                Cancellation.NONE);
    }

    @Test
    void oneEditedRowResynchronisesAndTheOriginalRowsResume() {
        ReferencePath reference = recordedOnFlat();
        assertTrue(reference.hasStates(), "an extracted reference records exact states");
        PlanResult result = repair(SyntheticRouteWorlds.flat().withNudge(60), reference, 50);
        var solved = assertInstanceOf(PlanResult.Solved.class, result, result.toString());
        RepairReport report = solved.repair();
        assertEquals(1, report.authoredRows(), report.summary());
        assertEquals(199, report.keptRows(), report.summary());
        assertEquals(200, solved.route().tape().size(), "edits replace rows; the row count is kept");
        int changed = 0;
        for (int i = 0; i < 200; i++) {
            if (!solved.route().tape().row(i).equals(original().tape().row(i))) changed++;
        }
        assertEquals(1, changed, "exactly one row differs from the original");
        assertTrue(report.firstChangedRow() >= 59, report.summary());
        assertTrue(solved.engineEvidence().coverage().stream().anyMatch(c -> c.startsWith("exact-recorded-states")),
                solved.engineEvidence().toString());
        assertEquals(reference.x(199), solved.replayEnd().p1().centreX(), "the run ends where the recorded run did");
        assertEquals(solved.searchEnd(), solved.replayEnd());
    }

    @Test
    void immutableRowsMustReproduceTheirRecordedStatesExactly() {
        PlanResult result = repair(SyntheticRouteWorlds.flat().withNudge(60), recordedOnFlat(), 100);
        var stopped = assertInstanceOf(PlanResult.Stopped.class, result);
        assertEquals(StopReason.PREFIX_DIVERGED, stopped.reason(), stopped.detail());
    }

    @Test
    void aReferenceWithoutExactStatesIsRefused() {
        ReferencePath positions = recordedOnFlat();
        int[] xs = new int[positions.size()];
        int[] ys = new int[positions.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = positions.x(i);
            ys[i] = positions.y(i);
        }
        ReferencePath bare = ReferencePath.recorded(positions.launch(), positions.source(), xs, ys,
                new long[xs.length], "test");
        assertFalse(bare.hasStates());
        assertThrows(IllegalArgumentException.class, () -> repair(SyntheticRouteWorlds.flat(), bare, 0));
    }

    @Test
    void statesMustAgreeWithTheRecordedCentre() {
        ReferencePath positions = recordedOnFlat();
        var p1 = new java.util.ArrayList<ReferencePath.PlayerState>();
        for (int i = 0; i < positions.size(); i++) {
            p1.add(new ReferencePath.PlayerState(positions.x(i) + (i == 7 ? 1 : 0), positions.y(i), 0, 0, 0, 0, 0,
                    false, false));
        }
        assertThrows(IllegalArgumentException.class, () -> positions.withStates(p1, List.of()));
    }

    @Test
    void distanceIsZeroOnlyWhenEveryFieldMatches() {
        var state = new ReferencePath.PlayerState(100, 200, 0x4C00, 0x0800, 24, 0, 24, false, false);
        var same = new PlayerView(100, 200, 0x4C00, 0x0800, 24, 0, 24, 0, false, false, false, false, false, false, 0,
                false, false, false, PlayerView.Motion.NONE);
        assertEquals(0, state.distance(same));
        var subpixel = new PlayerView(100, 200, 0x4C01, 0x0800, 24, 0, 24, 0, false, false, false, false, false, false,
                0, false, false, false, PlayerView.Motion.NONE);
        assertTrue(state.distance(subpixel) > 0, "a sub-256 subpixel difference still counts");
        var airborne = new PlayerView(100, 200, 0x4C00, 0x0800, 24, 0, 24, 0, true, false, false, false, false, false,
                0, false, false, false, PlayerView.Motion.NONE);
        assertTrue(state.distance(airborne) >= 1000);
    }
}

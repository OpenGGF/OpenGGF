package com.openggf.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The one search, on a synthetic world: preservation, windows, boundaries, budgets and determinism. */
class TestPrefixSearch {
    private static final Provenance PROVENANCE = new Provenance("test", "", "", List.of());
    private static final RepairScope WHOLE = new RepairScope(0, 400, 8);

    /** The original route: rows of Right, recorded where the world had no pits. */
    private static RouteArtifact original(int rows) {
        return RouteArtifact.of(SyntheticRouteWorlds.launch(), SyntheticRouteWorlds.right(rows), PROVENANCE);
    }

    private static RouteArtifact original() {
        return original(200);
    }

    private static ReferencePath recordedOn(SyntheticRouteWorlds old) {
        return recordedOn(old, original());
    }

    private static ReferencePath recordedOn(SyntheticRouteWorlds old, RouteArtifact route) {
        return ReferencePath.extract(old, route.launch(), route.tape(), route.tape().size());
    }

    private static PlanResult repair(SyntheticRouteWorlds now, ReferencePath reference, RepairScope scope,
                                     SearchBudget budget, Cancellation cancel) {
        RouteArtifact original = reference.size() == 200 ? original() : original(reference.size());
        return new RoutePlanner(now).plan(new RouteTask.Repair(original, reference, scope,
                AuthorHints.referenceFollowing(), budget), cancel);
    }

    private static PlanResult repair(SyntheticRouteWorlds now, ReferencePath reference) {
        return repair(now, reference, WHOLE, SearchBudget.referenceRepair(), Cancellation.NONE);
    }

    @Test
    void anUnchangedRouteIsKeptWhole() {
        var solved = assertInstanceOf(PlanResult.Solved.class,
                repair(SyntheticRouteWorlds.flat(), recordedOn(SyntheticRouteWorlds.flat())));
        assertEquals(original().tape(), solved.route().tape());
        assertEquals(200, solved.repair().keptRows());
        assertEquals(1.0, solved.repair().keptShare());
        assertEquals(solved.searchEnd(), solved.replayEnd(), "search and cold replay land identically");
    }

    @Test
    void aDriftOpensAWindowAndOriginalRowsResumeAfterIt() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, false);
        var solved = assertInstanceOf(PlanResult.Solved.class, repair(now, recordedOn(SyntheticRouteWorlds.flat())));
        RepairReport report = solved.repair();
        assertTrue(report.authoredRows() > 0, report.summary());
        assertTrue(report.keptShare() > 0.6 && report.keptShare() < 1.0, report.summary());
        var first = assertInstanceOf(RepairReport.Span.Original.class, report.spans().getFirst());
        assertEquals(0, first.sourceStart());
        assertEquals(solved.route().tape().slice(0, first.length()), original().tape().slice(0, first.length()),
                "kept rows are the original rows verbatim");
        var lastSpan = assertInstanceOf(RepairReport.Span.Original.class, report.spans().getLast(),
                "original rows resume after the window: " + report.summary());
        assertEquals(solved.route().tape().slice(lastSpan.outputStart(), lastSpan.outputStart() + lastSpan.length()),
                original().tape().slice(lastSpan.sourceStart(), lastSpan.sourceStart() + lastSpan.length()));
        assertFalse(solved.replayEnd().p1().dead());
        assertEquals(solved.searchEnd(), solved.replayEnd());
    }

    @Test
    void aShortRejoinFollowedByALateDriftOpensASecondWindow() {
        // The second pit lies beyond the first window's trial horizon, so the first window rejoins
        // the original rows, which then drift again much later.
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66),
                new SyntheticRouteWorlds.Pit(300, 306)), Integer.MAX_VALUE, Integer.MIN_VALUE, false);
        var result = repair(now, recordedOn(SyntheticRouteWorlds.flat(), original(400)));
        var solved = assertInstanceOf(PlanResult.Solved.class, result, result.toString());
        long windows = solved.repair().spans().stream().filter(s -> s instanceof RepairReport.Span.Authored).count();
        assertEquals(2, windows, solved.repair().summary());
        assertTrue(solved.engineEvidence().coverage().stream().anyMatch(c -> c.startsWith("reference-progress")));
    }

    @Test
    void plansAreDeterministicIncludingTheirWork() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, false);
        ReferencePath reference = recordedOn(SyntheticRouteWorlds.flat());
        var a = assertInstanceOf(PlanResult.Solved.class, repair(now, reference));
        var b = assertInstanceOf(PlanResult.Solved.class, repair(now, reference));
        assertEquals(a.route().tape(), b.route().tape());
        assertEquals(a.work(), b.work());
        assertEquals(a.repair(), b.repair());
    }

    @Test
    void aTrialThatCrossesAnUnexpectedBoundaryIsDiscardedAndTheHostRebuilt() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE, -5, false);
        var result = repair(now, recordedOn(SyntheticRouteWorlds.flat()));
        var solved = assertInstanceOf(PlanResult.Solved.class, result, result.toString());
        assertTrue(solved.work().freshBoots() > 0, "hold-away trials reach the left boundary: " + solved.work());
        assertEquals(0, solved.replayEnd().epoch(), "no committed row crossed the boundary");
    }

    @Test
    void aBoundaryTheReferenceCrossesStartsANewSegment() {
        var old = new SyntheticRouteWorlds(List.of(), 100, Integer.MIN_VALUE, false);
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(150, 156)), 100, Integer.MIN_VALUE, false);
        var solved = assertInstanceOf(PlanResult.Solved.class, repair(now, recordedOn(old)));
        assertEquals(1, solved.replayEnd().epoch());
        assertTrue(solved.repair().authoredRows() > 0);
        assertEquals(0, solved.work().freshBoots(), "the post-boundary window restores its own segment root");
    }

    @Test
    void immutableRowsThatNoLongerTrackStopAsPrefixDiverged() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, false);
        var stopped = assertInstanceOf(PlanResult.Stopped.class, repair(now, recordedOn(SyntheticRouteWorlds.flat()),
                new RepairScope(100, 400, 8), SearchBudget.referenceRepair(), Cancellation.NONE));
        assertEquals(StopReason.PREFIX_DIVERGED, stopped.reason(), stopped.detail());
    }

    @Test
    void cancellationAndExhaustionYieldNoRoute() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, false);
        ReferencePath reference = recordedOn(SyntheticRouteWorlds.flat());
        var calls = new AtomicInteger();
        var cancelled = assertInstanceOf(PlanResult.Stopped.class,
                repair(now, reference, WHOLE, SearchBudget.referenceRepair(), () -> calls.incrementAndGet() > 1));
        assertEquals(StopReason.CANCELLED, cancelled.reason());
        var exhausted = assertInstanceOf(PlanResult.Stopped.class,
                repair(now, reference, WHOLE, new SearchBudget(2, 1_000_000, 4, 4), Cancellation.NONE));
        assertEquals(StopReason.BUDGET_EXHAUSTED, exhausted.reason());
        assertTrue(exhausted.bestDraft().size() > 0, "the stop carries its best unqualified draft");
    }

    @Test
    void anIncompleteHostCheckpointIsCaughtWhenRecordedRowsReplayDifferently() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, true);
        var failure = assertThrows(IllegalStateException.class, () -> repair(now, recordedOn(SyntheticRouteWorlds.flat())));
        assertTrue(failure.getMessage().contains("checkpoint is incomplete"), failure.getMessage());
    }

    @Test
    void aRequiredEditReauthorsATrackingWindowAndSkipsRejectedTapes() {
        ReferencePath reference = recordedOn(SyntheticRouteWorlds.flat());
        var flat = assertInstanceOf(PlanResult.Solved.class, repair(SyntheticRouteWorlds.flat(), reference));
        var required = new RepairScope(0, 400, 8, new RepairTrigger.RequiredEdit(120, Set.of(flat.route().tape().hash())));
        var edited = assertInstanceOf(PlanResult.Solved.class, repair(SyntheticRouteWorlds.flat(), reference, required,
                SearchBudget.referenceRepair(), Cancellation.NONE));
        assertNotEquals(flat.route().tape().hash(), edited.route().tape().hash());
        assertTrue(edited.repair().authoredRows() > 0, edited.repair().summary());
        var firstSpan = assertInstanceOf(RepairReport.Span.Original.class, edited.repair().spans().getFirst());
        assertTrue(firstSpan.length() >= 100, "rows before the required window are kept: " + edited.repair().summary());
    }

    @Test
    void aRepairKeepsTheOriginalsGoalsAndTheColdReplayReevaluatesThem() {
        var now = new SyntheticRouteWorlds(List.of(new SyntheticRouteWorlds.Pit(60, 66)), Integer.MAX_VALUE,
                Integer.MIN_VALUE, false);
        ReferencePath reference = recordedOn(SyntheticRouteWorlds.flat());
        var reachable = GoalProgram.of(Guard.safe(), Leg.of("far-end", new RouteGoal.Achieve(new SamplePredicate.PlayerIn(
                PlayerSlot.P1, new Region(190, 0, 210, 2000))), 400));
        var withNative = original().withChecks(reachable);
        var stamped = new RouteArtifact(withNative.manifest().withNativeFacts(new NativeFacts(java.util.Optional.empty(),
                List.of(new NativeFacts.LagFact(30, "harness", withNative.content())))), withNative.tape());
        var solved = assertInstanceOf(PlanResult.Solved.class, new RoutePlanner(now).plan(new RouteTask.Repair(
                stamped, reference, WHOLE, AuthorHints.referenceFollowing(), SearchBudget.referenceRepair()),
                Cancellation.NONE));
        assertEquals(reachable, solved.route().checks(), "the original's goal program travels with its rows");
        assertTrue(solved.engineEvidence().coverage().contains("goals[far-end]"), solved.engineEvidence().coverage().toString());
        assertEquals(NativeFacts.NONE, solved.route().manifest().nativeFacts(), "native facts describe the old rows");
        assertTrue(solved.repair().nativeStale(), solved.repair().summary());
        assertTrue(solved.repair().firstChangedRow() > 0 && solved.repair().firstChangedRow() <= 60,
                solved.repair().summary());

        // Tracking the reference is not enough: a goal the repaired rows cannot meet fails qualification.
        var unreachable = original().withChecks(GoalProgram.of(Guard.NONE, Leg.of("beyond", new RouteGoal.Achieve(
                new SamplePredicate.PlayerIn(PlayerSlot.P1, new Region(900, 0, 910, 2000))), 1000)));
        var stopped = assertInstanceOf(PlanResult.Stopped.class, new RoutePlanner(now).plan(new RouteTask.Repair(
                unreachable, reference, WHOLE, AuthorHints.referenceFollowing(), SearchBudget.referenceRepair()),
                Cancellation.NONE));
        assertEquals(StopReason.QUALIFICATION_FAILED, stopped.reason(), stopped.detail());
        assertTrue(stopped.detail().contains("beyond"), stopped.detail());
    }
}

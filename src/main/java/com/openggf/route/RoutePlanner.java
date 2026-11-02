package com.openggf.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The one route author. Front-ends submit a {@link RouteTask}; the planner runs one
 * deterministic, count-budgeted prefix search, freezes the rows the host accepted, closes every
 * search host, and cold-replays the frozen rows from the launch. Only a passing replay yields
 * {@link PlanResult.Solved}; a cancelled, exhausted or failing attempt has no applicable route.
 */
public final class RoutePlanner {
    /** Bumped when search semantics change; recorded in provenance. */
    static final String PLANNER_REVISION = "prefix-search/1";

    private final RouteWorldFactory hosts;

    public RoutePlanner(RouteWorldFactory hosts) {
        this.hosts = Objects.requireNonNull(hosts, "hosts");
    }

    public PlanResult plan(RouteTask task, Cancellation cancellation) {
        return plan(task, cancellation, PlanProgress.Listener.NONE);
    }

    /** Plans with progress reports after each search expansion and before qualification. */
    public PlanResult plan(RouteTask task, Cancellation cancellation, PlanProgress.Listener listener) {
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(listener, "listener");
        return switch (task) {
            case RouteTask.Repair repair -> repair(repair, cancellation, listener);
        };
    }

    private PlanResult repair(RouteTask.Repair task, Cancellation cancellation, PlanProgress.Listener listener) {
        var search = new PrefixSearch(hosts, task, cancellation, listener);
        PrefixSearch.Draft draft = search.run();
        if (draft.stop() != null) {
            PrefixSearch.Node best = draft.best();
            return new PlanResult.Stopped(draft.stop(), draft.detail(), best == null ? -1 : best.k,
                    best == null ? InputTape.empty() : PrefixSearch.tape(best), search.work(0));
        }
        PrefixSearch.Node end = draft.end();
        InputTape tape = PrefixSearch.tape(end);
        RepairReport report = search.report(end);
        var provenance = new Provenance("RoutePlanner " + PLANNER_REVISION + " " + task.hints(), hosts.environment(),
                (search.sourceIsOriginal() ? "repair of " : "continuation of ") + task.original().content().shortForm()
                        + " from row " + task.scope().firstEditable() + " following " + task.reference(),
                List.of(report.summary().lines().findFirst().orElse("")));
        RouteArtifact route = RouteArtifact.of(task.original().launch(), tape, provenance);
        listener.report(new PlanProgress("qualify", search.expansions, search.steps, search.freshBoots, tape.size(),
                tape.size(), end.k, task.reference().to()));
        var checks = new ArrayList<ReplayCheck>();
        var progress = new ReferenceProgress(task.reference());
        checks.add(progress.reaches(task.scope().firstEditable(), search.rootProgress(end), task.hints()));
        if (search.sourceIsOriginal()) {
            checks.add(progress.tracksPrefix(task.scope().firstEditable(), task.hints().syncTolerance));
        }
        ReplayResult result = new RouteReplay(hosts).replay(route, checks, cancellation, RouteReplay.Observer.NONE);
        return switch (result) {
            case ReplayResult.Passed passed -> new PlanResult.Solved(route, passed.evidence(), report,
                    search.work(passed.evidence().rows()), end.sample, passed.end());
            case ReplayResult.Failed failed -> new PlanResult.Stopped(StopReason.QUALIFICATION_FAILED,
                    "cold replay failed after row " + failed.row() + ": " + failed.reason(), end.k, tape,
                    search.work(failed.row()));
            case ReplayResult.Unsupported unsupported -> new PlanResult.Stopped(StopReason.HOST_UNAVAILABLE,
                    unsupported.reason(), end.k, tape, search.work(0));
        };
    }
}

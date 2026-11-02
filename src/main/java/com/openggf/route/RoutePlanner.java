package com.openggf.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The one route author. Front-ends submit a {@link RouteTask}; the planner runs one
 * deterministic, count-budgeted prefix search, freezes the rows the host accepted, closes every
 * search host, and cold-replays the frozen rows from the launch. Only a passing replay yields
 * {@link PlanResult.Solved}; a cancelled, exhausted or failing attempt has no applicable route.
 *
 * <p>A draft tier (for example a static-terrain probe) may search instead of the qualification
 * hosts; its rows are only a hypothesis until the qualification hosts replay them.
 */
public final class RoutePlanner {
    /** Bumped when search semantics change; recorded in provenance. */
    static final String PLANNER_REVISION = "prefix-search/2";

    private final RouteWorldFactory hosts;
    private final RouteWorldFactory draft;

    public RoutePlanner(RouteWorldFactory hosts) {
        this(hosts, null);
    }

    private RoutePlanner(RouteWorldFactory hosts, RouteWorldFactory draft) {
        this.hosts = Objects.requireNonNull(hosts, "hosts");
        this.draft = draft;
    }

    /** A planner that searches on {@code draftTier} and qualifies on this planner's hosts. */
    public RoutePlanner withDraftTier(RouteWorldFactory draftTier) {
        return new RoutePlanner(hosts, Objects.requireNonNull(draftTier, "draftTier"));
    }

    public PlanResult plan(RouteTask task, Cancellation cancellation) {
        return plan(task, cancellation, PlanProgress.Listener.NONE);
    }

    /** Plans with progress reports after each search expansion and before qualification. */
    public PlanResult plan(RouteTask task, Cancellation cancellation, PlanProgress.Listener listener) {
        return plan(task, cancellation, listener, RouteReplay.Observer.NONE);
    }

    /**
     * Plans, reporting progress, and shows {@code observer} every post-row sample of the single
     * qualification replay (for overlay paths and front-end shot checks).
     */
    public PlanResult plan(RouteTask task, Cancellation cancellation, PlanProgress.Listener listener,
                           RouteReplay.Observer observer) {
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(observer, "observer");
        return switch (task) {
            case RouteTask.Repair repair -> repair(repair, cancellation, listener, observer);
            case RouteTask.Author author -> author(author, cancellation, listener, observer);
            case RouteTask.Point point -> point(point, cancellation, listener, observer);
        };
    }

    private PlanResult repair(RouteTask.Repair task, Cancellation cancellation, PlanProgress.Listener listener,
                              RouteReplay.Observer observer) {
        var expander = new ReferenceExpander(task);
        var search = new PrefixSearch(draft != null ? draft : hosts, task.original().launch(),
                task.original().tape().slice(0, task.scope().firstEditable()), expander, task.budget(), cancellation,
                listener);
        PrefixSearch.Draft result = search.run();
        if (result.stop() != null) return stopped(result, search);
        PrefixSearch.Node end = result.end();
        InputTape tape = PrefixSearch.tape(end);
        RouteManifest originalManifest = task.original().manifest();
        boolean nativeEvidence = !originalManifest.nativeFacts().equals(NativeFacts.NONE)
                || originalManifest.qualification().stream().anyMatch(s -> s.scope() == ReplayScope.BIZHAWK);
        RepairReport report = PrefixSearch.report(end, task.original().tape().size(), expander.sourceIsOriginal())
                .comparedWith(task.original().tape(), tape, nativeEvidence);
        var provenance = new Provenance("RoutePlanner " + PLANNER_REVISION + " " + task.hints(), hosts.environment(),
                (expander.sourceIsOriginal() ? "repair of " : "continuation of ") + task.original().content().shortForm()
                        + " from row " + task.scope().firstEditable() + " following " + task.reference(),
                List.of(report.summary().lines().findFirst().orElse("")));
        // The original's goal program travels with its rows and is re-evaluated by the cold replay.
        // Native facts and native stamps do not: they describe the old rows (see the report).
        RouteArtifact route = RouteArtifact.of(task.original().launch(), task.original().checks(), tape, provenance);
        var checks = new ArrayList<ReplayCheck>();
        var progress = new ReferenceProgress(task.reference());
        checks.add(progress.reaches(task.scope().firstEditable(), PrefixSearch.rootProgress(end), task.hints()));
        if (expander.sourceIsOriginal()) {
            checks.add(progress.tracksPrefix(task.scope().firstEditable(), task.hints().syncTolerance));
        }
        return qualify(route, checks, report, search, end, task.reference().to(), cancellation, listener, observer);
    }

    private PlanResult author(RouteTask.Author task, Cancellation cancellation, PlanProgress.Listener listener,
                              RouteReplay.Observer observer) {
        var expander = new GoalExpander(task.goals(), task.prefix().size());
        var search = new PrefixSearch(draft != null ? draft : hosts, task.launch(), task.prefix(), expander,
                task.budget(), cancellation, listener);
        PrefixSearch.Draft result = search.run();
        if (result.stop() != null) return stopped(result, search);
        PrefixSearch.Node end = result.end();
        InputTape tape = PrefixSearch.tape(end);
        RepairReport report = PrefixSearch.report(end, task.prefix().size(), true)
                .comparedWith(task.prefix(), tape, false);
        var provenance = new Provenance("RoutePlanner " + PLANNER_REVISION + " goal program", hosts.environment(),
                "authored after a " + task.prefix().size() + "-row prefix toward legs "
                        + task.goals().legs().stream().map(Leg::id).toList(), List.of());
        RouteArtifact route = RouteArtifact.of(task.launch(), task.goals(), tape, provenance);
        return qualify(route, List.of(), report, search, end, task.goals().legs().size(), cancellation, listener,
                observer);
    }

    private PlanResult point(RouteTask.Point task, Cancellation cancellation, PlanProgress.Listener listener,
                             RouteReplay.Observer observer) {
        var expander = new PointExpander(task.goal());
        var search = new PrefixSearch(draft != null ? draft : hosts, task.launch(), InputTape.empty(), expander,
                task.budget(), cancellation, listener);
        PrefixSearch.Draft result = search.run();
        if (result.stop() != null) return stopped(result, search);
        PrefixSearch.Node end = result.end();
        InputTape tape = PrefixSearch.tape(end);
        RepairReport report = PrefixSearch.report(end, 0, false);
        var provenance = new Provenance("RoutePlanner " + PLANNER_REVISION + " point goal", hosts.environment(),
                "authored toward (" + task.goal().targetX() + ", " + task.goal().targetY() + ")"
                        + (draft != null ? " on draft tier " + draft.environment() : ""), List.of());
        RouteArtifact route = RouteArtifact.of(task.launch(), task.checks(), tape, provenance);
        return qualify(route, List.of(), report, search, end, task.goal().maxRows(), cancellation, listener, observer);
    }

    private PlanResult qualify(RouteArtifact route, List<ReplayCheck> checks, RepairReport report, PrefixSearch search,
                               PrefixSearch.Node end, int target, Cancellation cancellation,
                               PlanProgress.Listener listener, RouteReplay.Observer observer) {
        InputTape tape = route.tape();
        listener.report(new PlanProgress("qualify", search.expansions, search.steps, search.freshBoots, tape.size(),
                tape.size(), end.k, target));
        ReplayResult result = new RouteReplay(hosts).replay(route, checks, cancellation, observer);
        return switch (result) {
            case ReplayResult.Passed passed -> new PlanResult.Solved(route, passed.evidence(), report,
                    search.work(passed.evidence().rows()), end.sample, passed.end());
            case ReplayResult.Failed failed -> new PlanResult.Stopped(StopReason.QUALIFICATION_FAILED,
                    "cold replay failed at row " + failed.row() + ": " + failed.reason(), end.k, tape,
                    search.work(failed.row()));
            case ReplayResult.Unsupported unsupported -> new PlanResult.Stopped(StopReason.HOST_UNAVAILABLE,
                    unsupported.reason(), end.k, tape, search.work(0));
        };
    }

    private static PlanResult stopped(PrefixSearch.Draft result, PrefixSearch search) {
        PrefixSearch.Node best = result.best();
        return new PlanResult.Stopped(result.stop(), result.detail(), best == null ? -1 : best.k,
                best == null ? InputTape.empty() : PrefixSearch.tape(best), search.work(0));
    }
}

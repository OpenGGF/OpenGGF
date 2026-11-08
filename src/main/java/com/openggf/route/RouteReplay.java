package com.openggf.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The only qualifier: boots a fresh host from the route's launch and offers every frozen row in
 * order, evaluating the route's goal program. Checks and observers only observe; nothing can
 * choose, correct or seek a row. A complete pass mints {@link ReplayEvidence} for exactly this
 * content in this environment.
 */
public final class RouteReplay {
    /** Receives every post-row observation of a replay, for progress and overlay paths. */
    @FunctionalInterface
    public interface Observer {
        Observer NONE = (row, sample) -> { };

        /** Called after zero-based row {@code row} with the observation it produced. */
        void afterRow(int row, RouteSample sample);
    }

    private final RouteWorldFactory hosts;

    public RouteReplay(RouteWorldFactory hosts) {
        this.hosts = Objects.requireNonNull(hosts, "hosts");
    }

    /** Opens forward-only playback for tests that assert on the live world as rows play. */
    public RoutePlayback open(RouteArtifact route) {
        return new RoutePlayback(route, hosts.openColdReplay(route));
    }

    /** Replays every row and evaluates the route's goal program. */
    public ReplayResult replay(RouteArtifact route, Cancellation cancellation) {
        return replay(route, cancellation, Observer.NONE);
    }

    /** Replays every row, evaluating the goal program and reporting each post-row sample to {@code observer}. */
    public ReplayResult replay(RouteArtifact route, Cancellation cancellation, Observer observer) {
        return replay(route, List.of(), cancellation, observer);
    }

    /** As {@link #replay(RouteArtifact, Cancellation, Observer)}, with additional planner checks. */
    ReplayResult replay(RouteArtifact route, List<ReplayCheck> extra, Cancellation cancellation, Observer observer) {
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(observer, "observer");
        var checks = new ArrayList<ReplayCheck>();
        checks.add(route.checks().check());
        checks.addAll(extra);
        try (ReplayWorld world = hosts.openColdReplay(route)) {
            InputTape tape = route.tape();
            RouteSample sample = world.observe();
            for (ReplayCheck check : checks) check.begin(sample);
            for (int i = 0; i < tape.size(); i++) {
                if (cancellation.cancelled()) return new ReplayResult.Failed(i, "cancelled");
                sample = world.step(tape.row(i)).after();
                observer.afterRow(i, sample);
                for (ReplayCheck check : checks) {
                    String failure = check.afterRow(i, sample);
                    if (failure != null) return new ReplayResult.Failed(i, check.name() + ": " + failure);
                }
            }
            var nativeEntry = route.manifest().nativeFacts().entry();
            if (nativeEntry.isPresent() && !nativeEntry.get().anchorBoundary().isEmpty()) {
                var entry = nativeEntry.get();
                Integer observed = sample.facts().get("input.gameplayAnchorRows");
                if (!entry.anchorBoundary().equals("first-ordinary-level-lfc-zero") || observed == null
                        || observed != entry.engineAnchorRows())
                    return new ReplayResult.Failed(tape.size(), "engine gameplay anchor disagrees with native export metadata");
            }
            var coverage = new ArrayList<String>();
            coverage.add("rows");
            for (ReplayCheck check : checks) {
                String failure = check.finish(sample);
                if (failure != null) return new ReplayResult.Failed(tape.size(), check.name() + ": " + failure);
                coverage.add(check.name());
            }
            return new ReplayResult.Passed(new ReplayEvidence(ReplayScope.ENGINE, route.content(),
                    hosts.environment(), tape.size(), coverage), sample);
        } catch (RouteHostException refused) {
            return new ReplayResult.Unsupported(refused.getMessage());
        }
    }
}

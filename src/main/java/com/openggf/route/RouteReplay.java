package com.openggf.route;

import java.util.List;
import java.util.Objects;

/**
 * The only qualifier: boots a fresh host from the route's launch and offers every frozen row in
 * order. Checks observe; nothing can choose, correct or seek a row. A complete pass mints
 * {@link ReplayEvidence} for exactly this content in this environment.
 */
public final class RouteReplay {
    private final RouteWorldFactory hosts;

    public RouteReplay(RouteWorldFactory hosts) {
        this.hosts = Objects.requireNonNull(hosts, "hosts");
    }

    /** Opens forward-only playback for tests that assert on the live world as rows play. */
    public RoutePlayback open(RouteArtifact route) {
        return new RoutePlayback(route, hosts.openColdReplay(route.launch()));
    }

    /** Replays every row and evaluates the route's declared checks. */
    public ReplayResult replay(RouteArtifact route, Cancellation cancellation) {
        try (ReplayWorld world = hosts.openColdReplay(route.launch())) {
            InputTape tape = route.tape();
            for (int i = 0; i < tape.size(); i++) {
                if (cancellation.cancelled()) return new ReplayResult.Failed(i, "cancelled");
                world.step(tape.row(i));
            }
            return new ReplayResult.Passed(new ReplayEvidence(ReplayScope.ENGINE, route.content(),
                    hosts.environment(), tape.size(), List.of("rows")));
        } catch (RouteHostException refused) {
            return new ReplayResult.Unsupported(refused.getMessage());
        }
    }
}

package com.openggf.route;

/** Trusted composition point for route hosts; not a plugin or creator callback surface. */
public interface RouteWorldFactory {
    /** A checkpointable host booted from {@code launch}. */
    RouteWorld openSearch(LaunchContract launch);

    /** A fresh replay-only host booted from {@code launch}. */
    ReplayWorld openColdReplay(LaunchContract launch);

    /**
     * A fresh replay-only host for {@code route}. Most hosts need only the launch; a scene host
     * installs the route's rows into its document before replaying it.
     */
    default ReplayWorld openColdReplay(RouteArtifact route) {
        return openColdReplay(route.launch());
    }

    /** Identity of the engine and adapters these hosts run, bound into evidence. */
    String environment();
}

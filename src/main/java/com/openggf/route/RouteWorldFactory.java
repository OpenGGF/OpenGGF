package com.openggf.route;

/** Trusted composition point for route hosts; not a plugin or creator callback surface. */
public interface RouteWorldFactory {
    /** A checkpointable host booted from {@code launch}. */
    RouteWorld openSearch(LaunchContract launch);

    /** A fresh replay-only host booted from {@code launch}. */
    ReplayWorld openColdReplay(LaunchContract launch);

    /** Identity of the engine and adapters these hosts run, bound into evidence. */
    String environment();
}

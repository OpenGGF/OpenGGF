package com.openggf.route;

/**
 * Forward-only playback of one route's rows in a fresh host, for tests that layer domain and
 * rewind assertions on the observations. The next input is always tape-index lookup: there is
 * no controller, correction, seek or clamping.
 */
public final class RoutePlayback implements AutoCloseable {
    private final RouteArtifact route;
    private final ReplayWorld world;

    RoutePlayback(RouteArtifact route, ReplayWorld world) {
        this.route = route;
        this.world = world;
    }

    public RouteArtifact route() {
        return route;
    }

    /** Rows played so far; the next row to play. */
    public int row() {
        return world.rowsOffered();
    }

    public int size() {
        return route.tape().size();
    }

    public boolean finished() {
        return row() >= size();
    }

    /** Plays the next row; past the end is an error. */
    public RouteSample step() {
        if (finished()) throw new IndexOutOfBoundsException("route has " + size() + " rows; all played");
        return world.step(route.tape().row(row())).after();
    }

    /** Plays rows until {@code row()} equals {@code target}. */
    public RouteSample stepTo(int target) {
        if (target < row() || target > size()) {
            throw new IndexOutOfBoundsException("cannot play from row " + row() + " to " + target + " of " + size());
        }
        RouteSample last = world.observe();
        while (row() < target) last = step();
        return last;
    }

    public RouteSample sample() {
        return world.observe();
    }

    /**
     * A host-specific read view (for example the engine capture session) for domain assertions:
     * object state, rewind windows, pictures. Driving the host through it would leave the tape;
     * callers only read.
     */
    public <V> V view(Class<V> type) {
        if (type.isInstance(world) && type != ReplayWorld.class && type != RouteWorld.class) return type.cast(world);
        throw new IllegalStateException("this playback's host offers no " + type.getSimpleName());
    }

    @Override
    public void close() {
        world.close();
    }
}

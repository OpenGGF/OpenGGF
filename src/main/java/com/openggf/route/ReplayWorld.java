package com.openggf.route;

/**
 * A forward-only route host. Trusted engine and scene integrations implement it; front-ends and
 * tests never do. The host owns its session and its driver's previous-row history.
 */
public interface ReplayWorld extends AutoCloseable {
    /** The current observation (entry before the first row). */
    RouteSample observe();

    /** Offers one row at the host's recorded-input boundary and returns what it accepted. */
    RecordedStep step(PadRow offered);

    /** Rows offered since boot. */
    int rowsOffered();

    @Override
    void close();
}

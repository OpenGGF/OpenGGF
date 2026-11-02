package com.openggf.route;

/** A read-only check evaluated by {@link RouteReplay} on every post-row sample and at the end. */
interface ReplayCheck {
    String name();

    /** Sees the entry sample before the first row. */
    default void begin(RouteSample entry) {
    }

    /** A failure reason after row {@code row} (zero-based), or {@code null}. */
    String afterRow(int row, RouteSample sample);

    /** A failure reason at the end of the tape, or {@code null}. */
    String finish(RouteSample last);
}

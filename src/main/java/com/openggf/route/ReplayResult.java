package com.openggf.route;

/** The outcome of one forward replay. */
public sealed interface ReplayResult {
    /** Every row played and every check held; {@code end} is the observation after the last row. */
    record Passed(ReplayEvidence evidence, RouteSample end) implements ReplayResult { }

    /**
     * The first failure: {@code row} is the zero-based row whose post-row observation failed (the
     * first diverging row), the tape length for an end condition, or the next unplayed row when
     * cancelled.
     */
    record Failed(int row, String reason) implements ReplayResult { }

    /** The host cannot replay this route here (no ROM, unsupported inlet, input not owned). */
    record Unsupported(String reason) implements ReplayResult { }

    default boolean passed() {
        return this instanceof Passed;
    }
}

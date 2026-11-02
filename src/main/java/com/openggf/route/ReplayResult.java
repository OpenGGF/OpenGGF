package com.openggf.route;

/** The outcome of one forward replay. */
public sealed interface ReplayResult {
    record Passed(ReplayEvidence evidence) implements ReplayResult { }

    /** The first failed check: after {@code row} rows, {@code reason}. */
    record Failed(int row, String reason) implements ReplayResult { }

    /** The host cannot replay this route here (no ROM, unsupported inlet, input not owned). */
    record Unsupported(String reason) implements ReplayResult { }

    default boolean passed() {
        return this instanceof Passed;
    }
}

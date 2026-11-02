package com.openggf.route;

/** Why a plan stopped without an applicable route. Exhaustion means "not found within bounds", not unreachable. */
public enum StopReason {
    /** Rows before the first editable row no longer track their own recorded path. */
    PREFIX_DIVERGED,
    /** A count budget ran out. */
    BUDGET_EXHAUSTED,
    /** Every frontier node was a dead end (no progress within the stuck limit). */
    NO_PROGRESS,
    /** The caller cancelled. */
    CANCELLED,
    /** The frozen output failed its cold replay. */
    QUALIFICATION_FAILED,
    /** A host refused the launch (ROM, inlet, input ownership). */
    HOST_UNAVAILABLE,
    /** The goal is outside what the search supports (for example too far from the entry). */
    UNSUPPORTED_GOAL,
    /** Every solution found had already been rejected. */
    ALL_CANDIDATES_REJECTED
}

package com.openggf.route;

/**
 * A progress report from a running plan, for job UIs. Counts only; it never steers the search.
 *
 * @param phase         {@code search} or {@code qualify}
 * @param committedRows output rows of the prefix the search is extending (qualify: the frozen tape)
 * @param bestRow       output rows of the furthest prefix found so far
 * @param bestProgress  the furthest reference index any searched prefix reached
 * @param target        the index that completes the task
 */
public record PlanProgress(String phase, long expansions, long steps, long freshBoots, int committedRows,
                           int bestRow, int bestProgress, int target) {
    /** Receives progress reports on the planning thread. */
    @FunctionalInterface
    public interface Listener {
        Listener NONE = progress -> { };

        void report(PlanProgress progress);
    }
}

package com.openggf.route;

/**
 * Which candidate families and tracking shape the search uses. Numbers inside a preset are
 * authoring choices, never gameplay rules, and stay private to the planner.
 */
public final class AuthorHints {
    enum Preset { REFERENCE_FOLLOWING }

    final Preset preset;
    /** Reference radius for progress, in pixels. */
    final int radius;
    /** Rows one trial runs. */
    final int horizon;
    /** Rows of a selected trial a child keeps (the frontier's edge length). */
    final int commit;
    /** How far ahead of the current reference index progress may be found. */
    final int look;
    /** Expansions without progress after which a line is a dead end. */
    final int stuckLimit;
    /** Tracking tolerance for verbatim source rows, in pixels. */
    final int syncTolerance;

    private AuthorHints(Preset preset, int radius, int horizon, int commit, int look, int stuckLimit, int syncTolerance) {
        this.preset = preset;
        this.radius = radius;
        this.horizon = horizon;
        this.commit = commit;
        this.look = look;
        this.stuckLimit = stuckLimit;
        this.syncTolerance = syncTolerance;
    }

    /**
     * Follow an engine reference path: verbatim source rows while P1 stays within six pixels,
     * otherwise time-shifted and delayed source rows, reactive steer/jump policies, spin dashes
     * and wait-then-jump families scored by reference progress. The values are the ones the cold
     * LRZ→HPZ→SSZ campaign used (2026-10-15).
     */
    public static AuthorHints referenceFollowing() {
        return new AuthorHints(Preset.REFERENCE_FOLLOWING, 28, 120, 20, 300, 20, 6);
    }

    /** Reference rows past a window's end that progress scoring may look at; extract that many more. */
    public int lookahead() {
        return look;
    }

    @Override
    public String toString() {
        return preset.name().toLowerCase() + "(radius " + radius + ", horizon " + horizon + ", commit " + commit + ")";
    }
}

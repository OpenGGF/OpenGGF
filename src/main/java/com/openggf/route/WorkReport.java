package com.openggf.route;

/**
 * Counts of the work a plan did. Search steps include reconstruction; qualification steps are the
 * final cold replay. Counts decide budgets; nothing here is wall-clock.
 */
public record WorkReport(long expansions, long searchSteps, long trials, long captures, long restores,
                         long freshBoots, long qualificationSteps) {
}

package com.openggf.route;

/**
 * Count budgets for one plan. Every row a host steps (trials, reconstruction, the immutable prefix
 * and the final qualification) consumes {@code steps}. A wall clock may only cancel a plan.
 */
public record SearchBudget(long expansions, long steps, int retainedCheckpoints, int freshBoots) {
    public SearchBudget {
        if (expansions <= 0 || steps <= 0 || retainedCheckpoints < 1 || freshBoots < 0) {
            throw new IllegalArgumentException("budgets must be positive: " + expansions + "/" + steps + "/"
                    + retainedCheckpoints + "/" + freshBoots);
        }
    }

    /** The cold-campaign reference author's scale: a few hundred reference indices per window. */
    public static SearchBudget referenceRepair() {
        return new SearchBudget(4_000, 20_000_000, 16, 64);
    }

    /** A boss encounter: the boss authors' scale (a few thousand rows of trials per committed edge). */
    public static SearchBudget encounter() {
        return new SearchBudget(2_000, 10_000_000, 16, 32);
    }
}

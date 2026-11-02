package com.openggf.route;

import java.util.Objects;

/** The outcome of one plan. Only {@link Solved} carries a route, and only after cold replay passed. */
public sealed interface PlanResult {
    /**
     * @param searchEnd the observation the search reached after its last row
     * @param replayEnd the observation the cold qualification replay reached after the same rows
     */
    record Solved(RouteArtifact route, ReplayEvidence engineEvidence, RepairReport repair, WorkReport work,
                  RouteSample searchEnd, RouteSample replayEnd) implements PlanResult {
        public Solved {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(engineEvidence, "engineEvidence");
            if (!engineEvidence.content().equals(route.content())) {
                throw new IllegalArgumentException("evidence is for other content");
            }
        }
    }

    /**
     * No applicable route. {@code bestDraft} is the furthest unqualified row prefix the search
     * held, a diagnostic for a person to inspect; it cannot be written as a route.
     */
    record Stopped(StopReason reason, String detail, int bestProgress, InputTape bestDraft, WorkReport work)
            implements PlanResult {
        /** Output rows of the furthest prefix the search held. */
        public int furthestRow() {
            return bestDraft.size();
        }
    }
}

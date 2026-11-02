package com.openggf.route;

import java.util.Objects;

/** A job for {@link RoutePlanner}. Front-ends build these; they never run their own search. */
public sealed interface RouteTask {
    /**
     * Keep {@code original}'s rows {@code [0, scope.firstEditable())} verbatim, then follow
     * {@code reference} to the end of its window, reusing its source rows wherever play tracks
     * and authoring rows only where it drifts. When the reference's source rows are the original's
     * own rows, this is a repair of the original and its kept share is reported.
     */
    record Repair(RouteArtifact original, ReferencePath reference, RepairScope scope, AuthorHints hints,
                  SearchBudget budget) implements RouteTask {
        public Repair {
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(hints, "hints");
            Objects.requireNonNull(budget, "budget");
            if (scope.firstEditable() > original.tape().size()) {
                throw new IllegalArgumentException("first editable row " + scope.firstEditable()
                        + " is past the original's " + original.tape().size() + " rows");
            }
        }
    }

    /**
     * Author new rows from {@code launch} that reach and settle at {@code goal}. {@code checks}
     * are the route's replayed checks (usually the goal's arrival plus a guard); a scene front-end
     * adds its own shot checks on the replay observer.
     */
    record Author(LaunchContract launch, PointGoal goal, GoalProgram checks, SearchBudget budget) implements RouteTask {
        public Author {
            Objects.requireNonNull(launch, "launch");
            Objects.requireNonNull(goal, "goal");
            Objects.requireNonNull(checks, "checks");
            Objects.requireNonNull(budget, "budget");
        }
    }
}

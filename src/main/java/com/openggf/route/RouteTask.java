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
     * Author rows after {@code prefix} (kept verbatim) that complete {@code goals}' legs in order.
     * The goal program is the route's replayed checks; set its {@code startRow} to the prefix
     * length so the inherited rows are not checked.
     */
    record Author(LaunchContract launch, InputTape prefix, GoalProgram goals, SearchBudget budget) implements RouteTask {
        public Author {
            Objects.requireNonNull(launch, "launch");
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(goals, "goals");
            Objects.requireNonNull(budget, "budget");
            if (goals.legs().isEmpty()) throw new IllegalArgumentException("an authored route needs at least one leg");
        }
    }

    /**
     * Author new rows from {@code launch} that reach and settle at {@code goal} (a scene shot).
     * {@code checks} are the route's replayed checks; a scene front-end adds its own shot checks
     * on the replay observer.
     */
    record Point(LaunchContract launch, PointGoal goal, GoalProgram checks, SearchBudget budget) implements RouteTask {
        public Point {
            Objects.requireNonNull(launch, "launch");
            Objects.requireNonNull(goal, "goal");
            Objects.requireNonNull(checks, "checks");
            Objects.requireNonNull(budget, "budget");
        }
    }
}

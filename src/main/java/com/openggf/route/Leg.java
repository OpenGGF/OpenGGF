package com.openggf.route;

import java.util.Objects;

/** One stage of a {@link GoalProgram}: a goal, the guard enforced on its every row, and a row bound. */
public record Leg(String id, RouteGoal goal, Guard guard, int maxRows) {
    public Leg {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(guard, "guard");
        if (id.isBlank()) throw new IllegalArgumentException("a leg needs an id");
        if (maxRows < 1) throw new IllegalArgumentException("a leg needs a positive row bound");
    }

    public static Leg of(String id, RouteGoal goal, int maxRows) {
        return new Leg(id, goal, Guard.NONE, maxRows);
    }

    public Leg guard(Guard next) {
        return new Leg(id, goal, next, maxRows);
    }
}

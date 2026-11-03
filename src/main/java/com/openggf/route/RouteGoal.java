package com.openggf.route;

import java.util.Objects;

/**
 * What completes one {@link Leg}. Goals are predicates over observations; none of them acts on
 * the world. Object goals (acquire, maintain, phase, defeat) join this vocabulary with owner facts.
 */
public sealed interface RouteGoal {
    /** The condition holds on some post-row sample of the leg. */
    record Achieve(SamplePredicate condition) implements RouteGoal {
        public Achieve {
            Objects.requireNonNull(condition, "condition");
        }
    }

    /** A boundary (load, seamless transition or mode change) is crossed during the leg. */
    record EnterBoundary() implements RouteGoal { }
}

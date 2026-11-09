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

    /** Hold neutral input for a bounded number of rows; the leg guard still applies. */
    record Wait(int rows) implements RouteGoal {
        public Wait { if (rows < 1) throw new IllegalArgumentException("positive wait required"); }
    }

    /** A boundary (load, seamless transition or mode change) is crossed during the leg. */
    record EnterBoundary() implements RouteGoal { }
}

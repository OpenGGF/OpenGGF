package com.openggf.route;

/**
 * Conditions enforced on every row of a leg (or of the whole route), in search and in replay
 * alike. A guard is not a goal: it never completes anything, it only fails.
 */
public record Guard(boolean allowHurt, boolean allowRingLoss, boolean allowDeath, boolean allowBoundary,
                    java.util.Optional<SamplePredicate> holds) {
    /** Nothing is forbidden. */
    public static final Guard NONE = new Guard(true, true, true, true);

    public Guard {
        java.util.Objects.requireNonNull(holds, "holds");
    }

    public Guard(boolean allowHurt, boolean allowRingLoss, boolean allowDeath, boolean allowBoundary) {
        this(allowHurt, allowRingLoss, allowDeath, allowBoundary, java.util.Optional.empty());
    }

    /** The same guard, also requiring {@code condition} on every row (a ride or a hold). */
    public Guard holding(SamplePredicate condition) {
        return new Guard(allowHurt, allowRingLoss, allowDeath, allowBoundary, java.util.Optional.of(condition));
    }

    /** P1 is never hurt or killed and no boundary is crossed unless a leg's goal asks for it. */
    public static Guard safe() {
        return new Guard(false, true, false, false);
    }

    /** The reason this sample violates the guard, given the previous sample, or {@code null}. */
    String violation(RouteSample previous, RouteSample now, boolean boundaryExpected) {
        if (!allowDeath && now.p1().dead()) return "P1 died";
        if (!allowHurt && now.p1().hurt()) return "P1 was hurt";
        if (!allowRingLoss && previous != null && now.p1().rings() < previous.p1().rings()) {
            return "P1 lost rings " + previous.p1().rings() + " -> " + now.p1().rings();
        }
        if (!allowBoundary && !boundaryExpected && previous != null && now.epoch() != previous.epoch()) {
            return "an unexpected boundary was crossed";
        }
        if (holds.isPresent() && !holds.get().test(now)) return "stopped holding " + holds.get();
        return null;
    }
}

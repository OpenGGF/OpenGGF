package com.openggf.route;

/**
 * Goal legs in the words routes are authored in: board, ride, attach, hold, await a phase,
 * defeat, cross, reach. Each is an ordinary {@link Leg} over the closed predicate vocabulary;
 * the ones that must keep holding put their condition in the leg's guard, enforced on every row.
 */
public final class RouteGoals {
    private RouteGoals() {
    }

    /** Stand on the target. */
    public static Leg board(String id, PlayerSlot player, ObjectTarget target, int maxRows) {
        return Leg.of(id, new RouteGoal.Achieve(new SamplePredicate.Riding(player, target)), maxRows);
    }

    /** Keep standing on the target until {@code until} holds. */
    public static Leg ride(String id, PlayerSlot player, ObjectTarget target, SamplePredicate until, int maxRows) {
        return new Leg(id, new RouteGoal.Achieve(until), Guard.NONE.holding(new SamplePredicate.Riding(player, target)),
                maxRows);
    }

    /** Be held by the target in this way (grab, hang, carry, tube, airflow, latch). */
    public static Leg attach(String id, PlayerSlot player, ObjectTarget target, InteractionKind kind, int maxRows) {
        return Leg.of(id, new RouteGoal.Achieve(new SamplePredicate.Attached(player, target, kind)), maxRows);
    }

    /** Stay held by the target until {@code until} holds. */
    public static Leg hold(String id, PlayerSlot player, ObjectTarget target, InteractionKind kind, SamplePredicate until,
                           int maxRows) {
        return new Leg(id, new RouteGoal.Achieve(until),
                Guard.NONE.holding(new SamplePredicate.Attached(player, target, kind)), maxRows);
    }

    /** Wait until the target reports {@code phase}. */
    public static Leg awaitPhase(String id, ObjectTarget target, String phase, int maxRows) {
        return Leg.of(id, new RouteGoal.Achieve(new SamplePredicate.PhaseIs(target, phase)), maxRows);
    }

    /** Defeat the target, as its owner counts hits. */
    public static Leg defeat(String id, ObjectTarget target, int maxRows) {
        return Leg.of(id, new RouteGoal.Achieve(new SamplePredicate.Defeated(target)), maxRows);
    }

    /** Cross a boundary (act load, seamless transition, mode change). */
    public static Leg cross(String id, int maxRows) {
        return Leg.of(id, new RouteGoal.EnterBoundary(), maxRows);
    }

    /** Be inside a region. */
    public static Leg reach(String id, PlayerSlot player, Region region, int maxRows) {
        return Leg.of(id, new RouteGoal.Achieve(new SamplePredicate.PlayerIn(player, region)), maxRows);
    }
}

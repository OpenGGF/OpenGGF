package com.openggf.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The checks a route declares: a route-wide guard and an ordered list of legs. Replay evaluates
 * it on every post-row sample; a search uses the same evaluator. An empty program checks rows only.
 */
public record GoalProgram(Guard routeGuard, List<Leg> legs) {
    public static final GoalProgram NONE = new GoalProgram(Guard.NONE, List.of());

    public GoalProgram {
        Objects.requireNonNull(routeGuard, "routeGuard");
        legs = List.copyOf(legs);
        var ids = new HashSet<String>();
        for (Leg leg : legs) {
            if (!ids.add(leg.id())) throw new IllegalArgumentException("duplicate leg id " + leg.id());
        }
    }

    public static GoalProgram of(Guard routeGuard, Leg... legs) {
        return new GoalProgram(routeGuard, List.of(legs));
    }

    public boolean isEmpty() {
        return legs.isEmpty() && routeGuard.equals(Guard.NONE);
    }

    /** A replay check evaluating this program; legs complete in order. */
    ReplayCheck check() {
        return new ReplayCheck() {
            private int leg;
            private int legStart;
            private RouteSample previous;
            private final List<String> completed = new ArrayList<>();

            @Override public void begin(RouteSample entry) {
                previous = entry;
            }

            @Override public String name() {
                return legs.isEmpty() ? "route-guard" : "goals[" + String.join(",", legs.stream().map(Leg::id).toList()) + "]";
            }

            @Override public String afterRow(int row, RouteSample sample) {
                Leg current = leg < legs.size() ? legs.get(leg) : null;
                boolean boundaryGoal = current != null && current.goal() instanceof RouteGoal.EnterBoundary;
                String failure = routeGuard.violation(previous, sample, boundaryGoal);
                if (failure == null && current != null) failure = current.guard().violation(previous, sample, boundaryGoal);
                if (failure != null) {
                    previous = sample;
                    return (current == null ? "route" : "leg " + current.id()) + ": " + failure;
                }
                if (current != null) {
                    boolean done = switch (current.goal()) {
                        case RouteGoal.Achieve achieve -> achieve.condition().test(sample);
                        case RouteGoal.EnterBoundary ignored -> previous != null && sample.epoch() != previous.epoch();
                    };
                    if (done) {
                        completed.add(current.id());
                        leg++;
                        legStart = row + 1;
                    } else if (row + 1 - legStart >= current.maxRows()) {
                        previous = sample;
                        return "leg " + current.id() + " not complete within " + current.maxRows() + " rows";
                    }
                }
                previous = sample;
                return null;
            }

            @Override public String finish(RouteSample last) {
                return leg >= legs.size() ? null : "leg " + legs.get(leg).id() + " never completed";
            }
        };
    }
}

package com.openggf.route;

import java.util.List;
import java.util.Objects;

/**
 * A closed, data-only vocabulary of conditions on one post-row {@link RouteSample}. Extensions are
 * new typed records with owner facts behind them, never arbitrary world callbacks.
 */
public sealed interface SamplePredicate {
    boolean test(RouteSample sample);

    /** The player's centre is inside {@code region}. */
    record PlayerIn(PlayerSlot player, Region region) implements SamplePredicate {
        public PlayerIn {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(region, "region");
        }

        @Override public boolean test(RouteSample sample) {
            if (player == PlayerSlot.P2 && sample.p2().isEmpty()) return false;
            PlayerView view = sample.player(player);
            return region.contains(view.centreX(), view.centreY());
        }
    }

    /** The player stands on ground (not airborne) and moves no faster than {@code maxSpeed} raw 8.8 units. */
    record Settled(PlayerSlot player, int maxSpeed) implements SamplePredicate {
        @Override public boolean test(RouteSample sample) {
            if (player == PlayerSlot.P2 && sample.p2().isEmpty()) return false;
            PlayerView view = sample.player(player);
            return !view.air() && Math.abs(view.xSpeed()) <= maxSpeed && Math.abs(view.ySpeed()) <= maxSpeed;
        }
    }

    /** The actual zone and act are these (the game module's own indices). */
    record LevelIs(int zone, int act) implements SamplePredicate {
        @Override public boolean test(RouteSample sample) {
            return sample.level().zone() == zone && sample.level().act() == act;
        }
    }

    /** Every clause holds. */
    record All(List<SamplePredicate> clauses) implements SamplePredicate {
        public All {
            clauses = List.copyOf(clauses);
        }

        @Override public boolean test(RouteSample sample) {
            for (SamplePredicate clause : clauses) if (!clause.test(sample)) return false;
            return true;
        }
    }
}

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

    /** The player stands on (rides) the target: OnObj with the target as its ride owner. */
    record Riding(PlayerSlot player, ObjectTarget target) implements SamplePredicate {
        public Riding {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(target, "target");
        }

        @Override public boolean test(RouteSample sample) {
            return holds(sample, player, target, InteractionKind.STANDING);
        }
    }

    /** The target reports custody of the player of this kind (grab, hang, carry, tube, airflow, latch). */
    record Attached(PlayerSlot player, ObjectTarget target, InteractionKind kind) implements SamplePredicate {
        public Attached {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(kind, "kind");
        }

        @Override public boolean test(RouteSample sample) {
            return holds(sample, player, target, kind);
        }
    }

    /** The target reports this phase (one of its declared phase names). */
    record PhaseIs(ObjectTarget target, String phase) implements SamplePredicate {
        public PhaseIs {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(phase, "phase");
        }

        @Override public boolean test(RouteSample sample) {
            return target.resolve(sample, PlayerSlot.P1).map(o -> o.phase().equals(phase)).orElse(false);
        }
    }

    /** The target's owner reports its defeat; absence is never defeat. */
    record Defeated(ObjectTarget target) implements SamplePredicate {
        public Defeated {
            Objects.requireNonNull(target, "target");
        }

        @Override public boolean test(RouteSample sample) {
            return target.resolve(sample, PlayerSlot.P1).map(ObjectView::defeated).orElse(false);
        }
    }

    private static boolean holds(RouteSample sample, PlayerSlot player, ObjectTarget target, InteractionKind kind) {
        var owner = target.resolve(sample, player);
        return owner.isPresent() && sample.interactions().stream().anyMatch(i -> i.participant() == player
                && i.kind() == kind && i.owner().equals(owner.get().instance()));
    }
}

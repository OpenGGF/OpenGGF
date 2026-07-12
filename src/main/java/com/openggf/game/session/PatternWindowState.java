package com.openggf.game.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Engine-neutral view of virtual pattern ranges retained by a world session.
 * Implementations may originate in optional-content layers, while level code
 * consumes only this stable registration boundary.
 */
public interface PatternWindowState {
    PatternWindowState EMPTY = new PatternWindowState() {
        @Override public List<Assignment> assignments() { return List.of(); }
    };

    List<Assignment> assignments();

    static PatternWindowState copyOf(PatternWindowState state) {
        Objects.requireNonNull(state, "state");
        return ofAssignments(state.assignments());
    }

    static PatternWindowState ofAssignments(List<Assignment> assignments) {
        List<Assignment> snapshot = List.copyOf(
                Objects.requireNonNull(assignments, "assignments"));
        return snapshot.isEmpty() ? EMPTY : () -> snapshot;
    }

    default Optional<Assignment> assignment(String owner) {
        Objects.requireNonNull(owner, "owner");
        return assignments().stream().filter(value -> value.owner().equals(owner)).findFirst();
    }

    default int totalWindows() {
        return assignments().stream().mapToInt(Assignment::windows).sum();
    }

    default void registerRanges(RangeRegistrar registrar) {
        Objects.requireNonNull(registrar, "registrar");
        for (Assignment assignment : assignments()) {
            registrar.register(assignment.base(), assignment.size(), "mod:" + assignment.owner());
        }
    }

    record Assignment(String owner, int base, int windows, int windowSize) {
        public Assignment {
            Objects.requireNonNull(owner, "owner");
            if (owner.isBlank() || base < 0 || windows <= 0 || windowSize <= 0) {
                throw new IllegalArgumentException("Invalid pattern-window assignment");
            }
            Math.addExact(base, Math.multiplyExact(windows, windowSize));
        }

        public int size() { return Math.multiplyExact(windows, windowSize); }

        public int endExclusive() { return Math.addExact(base, size()); }
    }

    @FunctionalInterface
    interface RangeRegistrar {
        void register(int base, int size, String category);
    }
}

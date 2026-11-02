package com.openggf.route;

import java.util.Objects;
import java.util.Optional;

/**
 * One immutable observation after {@code rowsOffered} rows (zero is the entry, before row 0).
 * It holds copied values only: no object instance, mutable collection or service handle.
 *
 * @param epoch increments at every boundary (level object, game mode or gameplay context change)
 */
public record RouteSample(int rowsOffered, long epoch, Level level, Clocks clocks, PlayerView p1,
                          Optional<PlayerView> p2, int cameraX, int cameraY) {
    public RouteSample {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(clocks, "clocks");
        Objects.requireNonNull(p1, "p1");
        Objects.requireNonNull(p2, "p2");
    }

    /** Actual zone/act, apparent act and game mode name. */
    public record Level(int zone, int act, int apparentAct, String mode) { }

    /** The engine clocks a gate can read. They are not interchangeable. */
    public record Clocks(int vIntRunCount, int levelFrameCounter) { }

    public PlayerView player(PlayerSlot slot) {
        return slot == PlayerSlot.P1 ? p1 : p2.orElseThrow(() -> new IllegalStateException("no P2 participant"));
    }
}

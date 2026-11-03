package com.openggf.route;

import java.util.Objects;
import java.util.Optional;

/**
 * One immutable observation after {@code rowsOffered} rows (zero is the entry, before row 0).
 * It holds copied values only: no object instance, mutable collection or service handle.
 *
 * @param epoch        increments at every boundary (level object, game mode or gameplay context change)
 * @param verticalWrap the camera's vertical wrap range in pixels, or zero when the level does not wrap
 * @param objects      live objects that report a capability or carry a player, in timeline order
 * @param interactions players held by objects this tick (solid rides and owner-reported custody)
 */
public record RouteSample(int rowsOffered, long epoch, Level level, Clocks clocks, PlayerView p1,
                          Optional<PlayerView> p2, int cameraX, int cameraY, int verticalWrap,
                          java.util.List<ObjectView> objects, java.util.List<InteractionView> interactions) {
    public RouteSample {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(clocks, "clocks");
        Objects.requireNonNull(p1, "p1");
        Objects.requireNonNull(p2, "p2");
        objects = java.util.List.copyOf(objects);
        interactions = java.util.List.copyOf(interactions);
    }

    /** A sample without object observations (hosts with no live objects). */
    public RouteSample(int rowsOffered, long epoch, Level level, Clocks clocks, PlayerView p1, Optional<PlayerView> p2,
                       int cameraX, int cameraY, int verticalWrap) {
        this(rowsOffered, epoch, level, clocks, p1, p2, cameraX, cameraY, verticalWrap, java.util.List.of(),
                java.util.List.of());
    }

    /** Actual zone/act, apparent act and game mode name. */
    public record Level(int zone, int act, int apparentAct, String mode) { }

    /** The engine clocks a gate can read. They are not interchangeable. */
    public record Clocks(int vIntRunCount, int levelFrameCounter) { }

    public PlayerView player(PlayerSlot slot) {
        return slot == PlayerSlot.P1 ? p1 : p2.orElseThrow(() -> new IllegalStateException("no P2 participant"));
    }
}

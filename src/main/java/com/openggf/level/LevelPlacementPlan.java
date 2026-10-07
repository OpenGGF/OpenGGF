package com.openggf.level;

import com.openggf.game.ModKeySyntax;
import com.openggf.level.rings.RingSpawn;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, bounded edit to decoded stock placements. Inside {@link #bounds()},
 * only the explicitly retained native rings survive, followed by any bounded
 * recovery-ring additions. Every native object and
 * every outside ring remains unchanged; additions name transaction-local factories.
 * Register through {@code ModContext}, never by supplying a claimed owner.
 */
@com.openggf.game.ModApi
public record LevelPlacementPlan(Bounds bounds, List<RingSpawn> retainedRings,
                                 List<ObjectAddition> additions, List<RingAddition> ringAdditions) {
    public LevelPlacementPlan {
        Objects.requireNonNull(bounds, "bounds");
        retainedRings = List.copyOf(retainedRings);
        additions = List.copyOf(additions);
        ringAdditions = List.copyOf(ringAdditions);
        if (retainedRings.size() > 64 || additions.size() > 32 || ringAdditions.size() > 8) {
            throw new IllegalArgumentException("Placement plan exceeds 64 retained rings, 32 objects or eight added rings");
        }
        if (new HashSet<>(retainedRings).size() != retainedRings.size()) {
            throw new IllegalArgumentException("Duplicate retained native ring");
        }
        var nativeIds = new HashSet<Integer>();
        for (RingSpawn ring : retainedRings) {
            if (!nativeIds.add(ring.placementId())) {
                throw new IllegalArgumentException("Duplicate retained native ring identity");
            }
            if (ring.placementId() < 0 || !bounds.contains(ring.x(), ring.y())) {
                throw new IllegalArgumentException("Retained ring needs a native identity inside bounds");
            }
        }
        if (new HashSet<>(additions).size() != additions.size()) {
            throw new IllegalArgumentException("Duplicate object addition");
        }
        for (ObjectAddition object : additions) {
            if (!bounds.contains(object.x(), object.y())) {
                throw new IllegalArgumentException("Object addition is outside bounds");
            }
        }
        if (new HashSet<>(ringAdditions).size() != ringAdditions.size()) {
            throw new IllegalArgumentException("Duplicate ring addition");
        }
        for (RingAddition ring : ringAdditions) {
            if (!bounds.contains(ring.x(), ring.y()) || retainedRings.stream()
                    .anyMatch(nativeRing -> nativeRing.x() == ring.x() && nativeRing.y() == ring.y())) {
                throw new IllegalArgumentException("Ring addition is outside bounds or overlaps a retained native ring");
            }
        }
    }

    /** Compatibility constructor for plans that only retain native rings. */
    public LevelPlacementPlan(Bounds bounds, List<RingSpawn> retainedRings, List<ObjectAddition> additions) {
        this(bounds, retainedRings, additions, List.of());
    }

    /** A native ring position; the engine allocates its unique placement identity. */
    @com.openggf.game.ModApi
    public record RingAddition(int x, int y) {
        public RingAddition {
            if (x < 0 || x >= 0xFFFF || y < 0 || y > 0x0FFF) {
                throw new IllegalArgumentException("Ring addition exceeds bounded native coordinates");
            }
        }
    }

    /** Inclusive native centre coordinates, at most 1024 pixels along either axis. */
    @com.openggf.game.ModApi
    public record Bounds(int minX, int minY, int maxX, int maxY) {
        public Bounds {
            if (minX < 0 || minY < 0 || maxX > 0xFFFF || maxY > 0xFFFF
                    || minX > maxX || minY > maxY
                    || maxX - minX >= 1024 || maxY - minY >= 1024) {
                throw new IllegalArgumentException("Invalid or oversized placement bounds");
            }
        }
        public boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
        boolean intersects(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY;
        }
    }

    /** A local factory key, never a numeric stock id or a creator-supplied owner. */
    @com.openggf.game.ModApi
    public record ObjectAddition(String localKey, int x, int y, int subtype, int renderFlags) {
        public ObjectAddition {
            localKey = ModKeySyntax.requireLocalName(localKey);
            if (x < 0 || x >= 0xFFFF || y < 0 || y > 0x0FFF
                    || subtype < 0 || subtype > 0xFF || renderFlags < 0 || renderFlags > 3) {
                throw new IllegalArgumentException("Object addition fields exceed native widths");
            }
        }
    }
}

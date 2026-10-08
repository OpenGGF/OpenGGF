package com.openggf.level;

import com.openggf.game.ModKeySyntax;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.rings.RingSpawn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Engine-internal frozen placement authority served by an active content patch.
 * Its private constructor is reached only by the registration publisher, through
 * a private-constructor lookup. A creator plan cannot assert an owner or forge this
 * final service through the public API. No mod package dependency enters level assembly.
 */
public final class RegisteredLevelPlacements {
    private final String owner;
    private final String gameId;
    private final Map<Integer, LevelPlacementPlan> plans;
    private final Consumer<Runnable> boundary;
    private final RegisteredLevelPlacements inherited;
    private final ObjectPlacementEncoding encoding;

    private RegisteredLevelPlacements(String owner, String gameId,
            Map<Integer, LevelPlacementPlan> plans, Consumer<Runnable> boundary,
            RegisteredLevelPlacements inherited, ObjectPlacementEncoding encoding) {
        this.owner = ModKeySyntax.requireManifestId(owner);
        this.gameId = Objects.requireNonNull(gameId, "gameId");
        this.plans = Map.copyOf(plans);
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        this.inherited = inherited;
        this.encoding = Objects.requireNonNull(encoding, "encoding");
    }

    /**
     * Validates the whole chain in detached lists, then changes only placements
     * on the concrete decoded level. Reinstalling the same level/source is a no-op.
     * Preview/editor swaps and snapshot restores never call this admission boundary.
     */
    public void install(String actualGameId, int levelIndex, Level level, boolean stockDecoded) {
        if (!hasPlan(levelIndex)) return;
        if (level instanceof AbstractLevel nativeLevel && nativeLevel.initialPlacementAuthority == this) return;
        List<ObjectSpawn> objects = new ArrayList<>(level.getObjects());
        List<RingSpawn> rings = new ArrayList<>(level.getRings());
        List<LevelPlacementPlan.Bounds> occupied = new ArrayList<>();
        // Reserve beyond ALL original native identities, including any removed by retention.
        long[] nextRingIdentity = { (long) rings.stream().mapToInt(RingSpawn::placementId).max().orElse(-1) + 1 };
        apply(actualGameId, levelIndex, level, stockDecoded, objects, rings, occupied, nextRingIdentity);
        AbstractLevel target = (AbstractLevel) level; // validated under the registering owner's boundary
        target.objects = List.copyOf(objects);
        target.rings = List.copyOf(rings);
        target.initialPlacementAuthority = this;
    }

    private boolean hasPlan(int levelIndex) {
        return plans.containsKey(levelIndex) || inherited != null && inherited.hasPlan(levelIndex);
    }

    private void apply(String actualGameId, int levelIndex, Level level, boolean stockDecoded,
            List<ObjectSpawn> objects, List<RingSpawn> rings, List<LevelPlacementPlan.Bounds> occupied, long[] nextRingIdentity) {
        if (inherited != null) inherited.apply(actualGameId, levelIndex, level, stockDecoded, objects, rings, occupied, nextRingIdentity);
        LevelPlacementPlan plan = plans.get(levelIndex);
        if (plan == null) return;
        boundary.accept(() -> {
            if (!gameId.equals(actualGameId) || !stockDecoded || !(level instanceof AbstractLevel nativeLevel)) {
                throw new IllegalArgumentException("Placement plan requires its stock decoded base game");
            }
            if (nativeLevel.initialPlacementAuthority != null) {
                throw new IllegalStateException("Decoded level already has another placement authority");
            }
            if (occupied.stream().anyMatch(bounds -> bounds.intersects(plan.bounds()))) {
                throw new IllegalArgumentException("Active placement plans overlap");
            }
            Set<RingSpawn> retained = new HashSet<>(plan.retainedRings());
            if (!rings.containsAll(retained)) {
                throw new IllegalArgumentException("Retained ring identity is absent from native placements");
            }
            rings.removeIf(ring -> plan.bounds().contains(ring.x(), ring.y()) && !retained.contains(ring));
            for (LevelPlacementPlan.ObjectAddition addition : plan.additions()) {
                String key = ModKeySyntax.requireOwnedKey(owner, addition.localKey());
                // Numeric id 0 is deliberately inert: factory dispatch uses the owner/key.
                // Appended indices retain every original native layout identity and order.
                int index = objects.stream().mapToInt(ObjectSpawn::layoutIndex).max().orElse(-1) + 1;
                objects.add(encoding.createKeyed(addition.x(), addition.y(), key, addition.subtype(),
                        addition.renderFlags(), true, index));
            }
            for (LevelPlacementPlan.RingAddition addition : plan.ringAdditions()) {
                if (rings.stream().anyMatch(ring -> ring.x() == addition.x() && ring.y() == addition.y())
                        || level.getRings().stream().anyMatch(ring -> ring.x() == addition.x() && ring.y() == addition.y())) {
                    throw new IllegalArgumentException("Ring addition overlaps an existing ring");
                }
                if (nextRingIdentity[0] > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Native ring identity space exhausted");
                }
                rings.add(new RingSpawn(addition.x(), addition.y(), (int) nextRingIdentity[0]++));
            }
            occupied.add(plan.bounds());
        });
    }
}

package com.openggf.net.hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Spatially bucketed near/far classification with exit hysteresis. */
public final class RelevanceClassifier {
    public static final int BUCKET_PX = 512;
    public static final int NEAR_ENTER_PX = 480;
    public static final int NEAR_EXIT_PX = 800;
    public static final int NEAR_CAP = 8;

    public record Pos(int x, int y) {
    }

    private final Map<Integer, Pos> positions = new HashMap<>();
    private final Map<Integer, List<Integer>> buckets = new HashMap<>();
    private final Map<Integer, Set<Integer>> previousNear = new HashMap<>();

    public void updatePosition(int slot, int x, int y) {
        positions.put(slot, new Pos(x, y));
    }

    public Pos positionOf(int slot) {
        return positions.get(slot);
    }

    public void remove(int slot) {
        positions.remove(slot);
        previousNear.remove(slot);
        previousNear.values().forEach(set -> set.remove(slot));
    }

    public void rebucket() {
        buckets.clear();
        for (Map.Entry<Integer, Pos> entry : positions.entrySet()) {
            int bucket = Math.floorDiv(entry.getValue().x(), BUCKET_PX);
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>())
                    .add(entry.getKey());
        }
    }

    public Set<Integer> nearSetFor(int slot) {
        Pos self = positions.get(slot);
        if (self == null) {
            return Set.of();
        }
        Set<Integer> wasNear = previousNear.getOrDefault(slot, Set.of());
        int bucketRange = Math.floorDiv(NEAR_EXIT_PX, BUCKET_PX) + 1;
        int selfBucket = Math.floorDiv(self.x(), BUCKET_PX);
        List<Integer> near = new ArrayList<>();
        for (int bucketIndex = selfBucket - bucketRange;
             bucketIndex <= selfBucket + bucketRange; bucketIndex++) {
            for (int peer : buckets.getOrDefault(bucketIndex, List.of())) {
                if (peer == slot) {
                    continue;
                }
                Pos other = positions.get(peer);
                int distance = distance(self, other);
                int threshold = wasNear.contains(peer) ? NEAR_EXIT_PX : NEAR_ENTER_PX;
                if (distance <= threshold) {
                    near.add(peer);
                }
            }
        }
        near.sort(Comparator.comparingInt((Integer peer) ->
                        distance(self, positions.get(peer)))
                .thenComparingInt(Integer::intValue));
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (int peer : near) {
            if (result.size() >= NEAR_CAP) {
                break;
            }
            result.add(peer);
        }
        previousNear.put(slot, new HashSet<>(result));
        return Set.copyOf(result);
    }

    private static int distance(Pos left, Pos right) {
        return Math.max(Math.abs(right.x() - left.x()),
                Math.abs(right.y() - left.y()));
    }
}

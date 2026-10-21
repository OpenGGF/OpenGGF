package com.openggf.net.hub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    private static final int[] NO_NEAR = new int[0];
    private final Map<Integer, int[]> previousNear = new HashMap<>();
    private final int[] nearestSlots = new int[NEAR_CAP];
    private final int[] nearestDistances = new int[NEAR_CAP];

    public void updatePosition(int slot, int x, int y) {
        positions.put(slot, new Pos(x, y));
    }

    public Pos positionOf(int slot) {
        return positions.get(slot);
    }

    public void remove(int slot) {
        positions.remove(slot);
        previousNear.remove(slot);
        previousNear.replaceAll((recipient, near) -> without(near, slot));
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
        int[] near = nearSlotsFor(slot);
        if (near.length == 0) {
            return Set.of();
        }
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (int peer : near) {
            result.add(peer);
        }
        return Collections.unmodifiableSet(result);
    }

    /** Nearest-first classifier-owned snapshot. Callers must not mutate the returned array. */
    int[] nearSlotsFor(int slot) {
        Pos self = positions.get(slot);
        if (self == null) {
            return NO_NEAR;
        }
        int[] wasNear = previousNear.getOrDefault(slot, NO_NEAR);
        int bucketRange = Math.floorDiv(NEAR_EXIT_PX, BUCKET_PX) + 1;
        int selfBucket = Math.floorDiv(self.x(), BUCKET_PX);
        int count = 0;
        for (int bucketIndex = selfBucket - bucketRange;
             bucketIndex <= selfBucket + bucketRange; bucketIndex++) {
            for (int peer : buckets.getOrDefault(bucketIndex, List.of())) {
                if (peer == slot) {
                    continue;
                }
                Pos other = positions.get(peer);
                // A slow consumer can be removed after rebucketing, during the hub tick.
                if (other == null) {
                    continue;
                }
                int distance = distance(self, other);
                if (distance > NEAR_EXIT_PX
                        || (distance > NEAR_ENTER_PX && !contains(wasNear, peer))) {
                    continue;
                }
                // Keep only the nearest eight, ordered by distance then slot. This
                // avoids sorting/allocating every eligible peer in a crowded bucket.
                int index = count;
                while (index > 0 && (distance < nearestDistances[index - 1]
                        || (distance == nearestDistances[index - 1]
                        && peer < nearestSlots[index - 1]))) {
                    index--;
                }
                if (index < NEAR_CAP) {
                    int move = Math.min(count, NEAR_CAP - 1) - index;
                    System.arraycopy(nearestSlots, index, nearestSlots, index + 1, move);
                    System.arraycopy(nearestDistances, index, nearestDistances, index + 1, move);
                    nearestSlots[index] = peer;
                    nearestDistances[index] = distance;
                    count = Math.min(count + 1, NEAR_CAP);
                }
            }
        }
        int[] result = count == 0 ? NO_NEAR : Arrays.copyOf(nearestSlots, count);
        previousNear.put(slot, result);
        return result;
    }

    private static boolean contains(int[] slots, int slot) {
        for (int peer : slots) {
            if (peer == slot) {
                return true;
            }
        }
        return false;
    }

    private static int[] without(int[] slots, int slot) {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == slot) {
                int[] result = new int[slots.length - 1];
                System.arraycopy(slots, 0, result, 0, index);
                System.arraycopy(slots, index + 1, result, index, result.length - index);
                return result;
            }
        }
        return slots;
    }

    private static int distance(Pos left, Pos right) {
        return Math.max(Math.abs(right.x() - left.x()),
                Math.abs(right.y() - left.y()));
    }
}

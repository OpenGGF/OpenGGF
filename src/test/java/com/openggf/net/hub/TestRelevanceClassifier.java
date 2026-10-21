package com.openggf.net.hub;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestRelevanceClassifier {
    @Test
    void appliesEnterDistanceAndVerticalDistance() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 1000, 500);
        classifier.updatePosition(1, 1000 + RelevanceClassifier.NEAR_ENTER_PX, 500);
        classifier.updatePosition(2, 1000, 500 + RelevanceClassifier.NEAR_EXIT_PX + 1);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0));
        assertTrue(classifier.nearSetFor(2).isEmpty());
    }

    @Test
    void hysteresisRequiresExitThenReentry() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 1000, 500);
        classifier.updatePosition(1, 1200, 500);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0));

        classifier.updatePosition(1, 1000 + RelevanceClassifier.NEAR_EXIT_PX - 10, 500);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0));
        classifier.updatePosition(1, 1000 + RelevanceClassifier.NEAR_EXIT_PX + 10, 500);
        classifier.rebucket();
        assertTrue(classifier.nearSetFor(0).isEmpty());
        classifier.updatePosition(1, 1000 + RelevanceClassifier.NEAR_ENTER_PX + 10, 500);
        classifier.rebucket();
        assertTrue(classifier.nearSetFor(0).isEmpty());
    }

    @Test
    void capsAtNearestEightAndRemovesPlayers() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 5000, 500);
        for (int slot = 1; slot <= 12; slot++) {
            classifier.updatePosition(slot, 5000 + slot * 10, 500);
        }
        classifier.rebucket();
        Set<Integer> near = classifier.nearSetFor(0);
        assertEquals(RelevanceClassifier.NEAR_CAP, near.size());
        for (int slot = 1; slot <= RelevanceClassifier.NEAR_CAP; slot++) {
            assertTrue(near.contains(slot));
        }
        classifier.remove(1);
        classifier.rebucket();
        assertFalse(classifier.nearSetFor(0).contains(1));
    }

    @Test
    void matchesFullSortOracleAcrossMovementTiesRemovalAndReentry() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        Map<Integer, RelevanceClassifier.Pos> positions = new HashMap<>();
        Map<Integer, Set<Integer>> history = new HashMap<>();
        Random random = new Random(0x480800);
        int[] offsets = {-801, -800, -513, -512, -481, -480, -1,
                0, 1, 480, 481, 512, 513, 800, 801};
        for (int round = 0; round < 120; round++) {
            for (int slot = 0; slot < 64; slot++) {
                if (random.nextInt(10) == 0) {
                    classifier.remove(slot);
                    positions.remove(slot);
                    history.remove(slot);
                    for (Set<Integer> near : history.values()) near.remove(slot);
                } else {
                    // Deliberately dense, with exact bucket/threshold boundaries and ties.
                    int x = offsets[random.nextInt(offsets.length)] + (round % 3 - 1) * 512;
                    int y = offsets[random.nextInt(offsets.length)];
                    classifier.updatePosition(slot, x, y);
                    positions.put(slot, new RelevanceClassifier.Pos(x, y));
                }
            }
            classifier.rebucket();
            for (int slot = 0; slot < 64; slot++) {
                RelevanceClassifier.Pos self = positions.get(slot);
                if (self == null) {
                    assertTrue(classifier.nearSetFor(slot).isEmpty());
                    continue;
                }
                List<Integer> expected = new ArrayList<>();
                Set<Integer> wasNear = history.getOrDefault(slot, Set.of());
                for (Map.Entry<Integer, RelevanceClassifier.Pos> peer : positions.entrySet()) {
                    int distance = oracleDistance(self, peer.getValue());
                    int threshold = wasNear.contains(peer.getKey())
                            ? RelevanceClassifier.NEAR_EXIT_PX : RelevanceClassifier.NEAR_ENTER_PX;
                    if (peer.getKey() != slot && distance <= threshold) expected.add(peer.getKey());
                }
                expected.sort(Comparator.comparingInt((Integer peer) ->
                        oracleDistance(self, positions.get(peer))).thenComparingInt(Integer::intValue));
                expected = expected.subList(0, Math.min(expected.size(), RelevanceClassifier.NEAR_CAP));
                Set<Integer> actual = classifier.nearSetFor(slot);
                assertEquals(expected, new ArrayList<>(actual), "round " + round + ", slot " + slot);
                history.put(slot, new HashSet<>(expected));
            }
        }
    }

    @Test
    void removingBucketedPeerKeepsReturnedSnapshotsAndLaterQueriesValid() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 100, 100);
        classifier.updatePosition(1, 110, 100);
        classifier.updatePosition(2, 120, 100);
        classifier.rebucket();
        Set<Integer> snapshot = classifier.nearSetFor(0);
        classifier.remove(1);
        assertEquals(List.of(1, 2), new ArrayList<>(snapshot));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(3));
        assertEquals(Set.of(2), classifier.nearSetFor(0));
    }

    @Test
    void excludedSenderYieldsItsPlaceToNextNearestButKeepsItsOwnNearSet() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 5000, 500);
        for (int slot = 1; slot <= RelevanceClassifier.NEAR_CAP + 1; slot++) {
            classifier.updatePosition(slot, 5000 + slot * 10, 500);
        }
        classifier.rebucket();
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), new ArrayList<>(classifier.nearSetFor(0)));

        classifier.setSenderExcluded(1, true);
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), new ArrayList<>(classifier.nearSetFor(0)));
        assertEquals(List.of(0, 2, 3, 4, 5, 6, 7, 8), new ArrayList<>(classifier.nearSetFor(1)),
                "an excluded sender may still be a spectating recipient");
        assertEquals(5010, classifier.positionOf(1).x());

        classifier.setSenderExcluded(1, false);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), new ArrayList<>(classifier.nearSetFor(0)));
    }

    @Test
    void excludedSenderLeavesHysteresisAndReentersOnlyWithinEnterDistance() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 1000, 500);
        classifier.updatePosition(1, 1200, 500);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0));
        classifier.updatePosition(1, 1000 + RelevanceClassifier.NEAR_EXIT_PX - 10, 500);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0), "hysteresis keeps a known peer");

        classifier.setSenderExcluded(1, true);
        assertTrue(classifier.nearSetFor(0).isEmpty());
        classifier.setSenderExcluded(1, false);
        assertTrue(classifier.nearSetFor(0).isEmpty(),
                "exclusion forgets the peer, so the exit distance no longer applies");
        classifier.updatePosition(1, 1000 + RelevanceClassifier.NEAR_ENTER_PX, 500);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0));
    }

    @Test
    void removingPlayerClearsSenderExclusion() {
        RelevanceClassifier classifier = new RelevanceClassifier();
        classifier.updatePosition(0, 100, 100);
        classifier.updatePosition(1, 110, 100);
        classifier.setSenderExcluded(1, true);
        classifier.remove(1);
        classifier.updatePosition(1, 110, 100);
        classifier.rebucket();
        assertEquals(Set.of(1), classifier.nearSetFor(0));
    }

    private static int oracleDistance(RelevanceClassifier.Pos left, RelevanceClassifier.Pos right) {
        return Math.max(Math.abs(right.x() - left.x()), Math.abs(right.y() - left.y()));
    }
}

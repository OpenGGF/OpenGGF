package com.openggf.net.hub;

import org.junit.jupiter.api.Test;

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
}

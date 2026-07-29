package com.openggf.game.sonic3k;

import com.openggf.game.GameStateManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kEmeraldProgression {

    @Test
    void mixedRomStatesPreserveIdentityAndCounts() {
        GameStateManager state = new GameStateManager();

        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                state, List.of(0, 1, 1, 0, 1, 0, 3), false);

        assertEquals(List.of(0, 1, 1, 0, 1, 0, 3), progression.states());
        assertEquals(List.of(1, 2, 4, 6), state.getCollectedChaosEmeraldIndices());
        assertEquals(List.of(6), state.getCollectedSuperEmeraldIndices());
        assertEquals(4, state.getEmeraldCount());
    }

    @Test
    void sanctuaryConversionLatchesBeforeAnyPedestalChanges() {
        GameStateManager state = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                state, List.of(0, 1, 1, 0, 1, 0, 3), false);

        assertTrue(progression.beginSanctuaryConversion());

        assertTrue(progression.isConverted());
        assertEquals(List.of(0, 1, 1, 0, 1, 0, 3), progression.states());
    }

    @Test
    void conversionAndAwardOnlyAcceptTheirRomSourceStates() {
        GameStateManager state = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                state, List.of(0, 1, 2, 0, 1, 0, 3), true);

        assertEquals(2, progression.convert(1));
        assertEquals(2, progression.convert(2));
        assertEquals(0, progression.convert(0));
        assertEquals(3, progression.awardSuper(2));
        assertEquals(3, progression.awardSuper(6));
        assertEquals(0, progression.awardSuper(0));
        assertEquals(List.of(0, 2, 3, 0, 1, 0, 3), progression.states());
    }

    @Test
    void conversionDoesNotLatchWhenNoChaosStateExists() {
        GameStateManager state = new GameStateManager();
        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                state, List.of(0, 0, 2, 0, 0, 0, 3), true);

        assertFalse(progression.beginSanctuaryConversion());
        assertTrue(progression.isConverted());
    }

    @Test
    void restoredGrayOrSuperStateImpliesConvertedFlag() {
        GameStateManager state = new GameStateManager();

        S3kEmeraldProgression progression = S3kEmeraldProgression.restore(
                state, List.of(0, 0, 2, 0, 0, 0, 0), false);

        assertTrue(progression.isConverted());
    }
}

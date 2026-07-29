package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.GameStateManager;
import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestSonic3kSuperEmeraldConversion {
    private GameStateManager gameState;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        gameState = new GameStateManager();
        for (int i = 0; i < 7; i++) {
            gameState.markEmeraldCollected(i);
        }
    }

    @Test
    void explicitSuperEmeraldStageDoesNotOwnSanctuaryConversionFlag() {
        assertTrue(Sonic3kSpecialStageManager.isSuperEmeraldReward(
                EmeraldRewardKind.SUPER_EMERALD));
        org.junit.jupiter.api.Assertions.assertFalse(gameState.isEmeraldsConverted());
    }

    @Test
    void forcedSuperEmeraldRequestKeepsExactStageAndRewardKind() {
        SpecialStageEntryRequest request =
                new SpecialStageEntryRequest(5, EmeraldRewardKind.SUPER_EMERALD);

        assertEquals(5, request.forcedStageIndex());
        assertEquals(EmeraldRewardKind.SUPER_EMERALD, request.rewardKind());
    }

    @Test
    void s3kSuperRewardPublishesExactlyOnce() {
        GameStateManager state = new GameStateManager();
        state.restoreS3kEmeraldProgress(java.util.List.of(0, 0, 0, 0, 2, 0, 0), true);

        boolean published = Sonic3kSpecialStageManager.publishEmeraldReward(
                state, 4, true, false);
        Sonic3kSpecialStageManager.publishEmeraldReward(state, 4, true, published);

        assertEquals(java.util.List.of(0, 0, 0, 0, 3, 0, 0),
                state.getS3kEmeraldStates());
    }

    @Test
    void superRewardCannotPromoteAbsentChaosOrAlreadySuperStates() {
        for (int initial : new int[] {0, 1, 3}) {
            GameStateManager state = new GameStateManager();
            state.restoreS3kEmeraldProgress(
                    java.util.List.of(initial, 0, 0, 0, 0, 0, 0), true);

            org.junit.jupiter.api.Assertions.assertFalse(
                    Sonic3kSpecialStageManager.publishEmeraldReward(
                            state, 0, true, false));

            assertEquals(initial, state.getS3kEmeraldStates().getFirst());
        }
    }

    @Test
    void ordinaryS3kRewardRemainsChaosReward() {
        GameStateManager state = mock(GameStateManager.class);

        Sonic3kSpecialStageManager.publishEmeraldReward(state, 1, false, false);

        verify(state, times(1)).markEmeraldCollected(1);
        verify(state, never()).markSuperEmeraldCollected(1);
    }
}

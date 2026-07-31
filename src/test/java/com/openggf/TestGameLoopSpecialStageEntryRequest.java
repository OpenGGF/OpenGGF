package com.openggf;

import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.GameStateManager;
import com.openggf.level.LevelTransitionCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class TestGameLoopSpecialStageEntryRequest {

    @Test
    void typedRequestIsConsumedExactlyOnce() {
        LevelTransitionCoordinator coordinator = new LevelTransitionCoordinator();
        SpecialStageEntryRequest request =
                new SpecialStageEntryRequest(4, EmeraldRewardKind.SUPER_EMERALD);

        coordinator.requestSpecialStageEntry(request);

        assertEquals(request, coordinator.consumeSpecialStageEntryRequest());
        assertNull(coordinator.consumeSpecialStageEntryRequest());
    }

    @Test
    void ordinaryCompatibilityRequestRetainsPeekAndBooleanConsumption() {
        LevelTransitionCoordinator coordinator = new LevelTransitionCoordinator();

        coordinator.requestSpecialStageEntry();

        assertTrue(coordinator.isSpecialStageRequested());
        assertTrue(coordinator.consumeSpecialStageRequest());
        assertFalse(coordinator.isSpecialStageRequested());
        assertFalse(coordinator.consumeSpecialStageRequest());
    }

    @Test
    void forcedIndexBypassesProviderCursor() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class, CALLS_REAL_METHODS);
        GameStateManager gameState = new GameStateManager();

        assertEquals(4, SpecialStageTransitionSupport.resolveStageIndex(
                new SpecialStageEntryRequest(4, EmeraldRewardKind.SUPER_EMERALD),
                provider, gameState));

        verify(provider, never()).consumeStageIndexForEntry(gameState);
    }

    @Test
    void ordinaryRequestUsesProviderCursor() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class, CALLS_REAL_METHODS);
        GameStateManager gameState = new GameStateManager();
        when(provider.consumeStageIndexForEntry(gameState)).thenReturn(3);

        assertEquals(3, SpecialStageTransitionSupport.resolveStageIndex(
                SpecialStageEntryRequest.ordinary(), provider, gameState));

        verify(provider).consumeStageIndexForEntry(gameState);
    }

    @Test
    void loopOwnedChaosRewardPublishesOnce() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class, CALLS_REAL_METHODS);
        GameStateManager gameState = mock(GameStateManager.class);

        SpecialStageTransitionSupport.publishRewardIfLoopOwned(
                provider, gameState, 2, EmeraldRewardKind.CHAOS_EMERALD);

        verify(gameState, times(1)).markEmeraldCollected(2);
        verify(gameState, never()).markSuperEmeraldCollected(2);
    }

    @Test
    void providerOwnedRewardIsNotRepublishedByGameLoop() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class, CALLS_REAL_METHODS);
        GameStateManager gameState = mock(GameStateManager.class);
        when(provider.ownsEmeraldReward()).thenReturn(true);

        SpecialStageTransitionSupport.publishRewardIfLoopOwned(
                provider, gameState, 2, EmeraldRewardKind.SUPER_EMERALD);

        verify(gameState, never()).markEmeraldCollected(2);
        verify(gameState, never()).markSuperEmeraldCollected(2);
    }

    @Test
    void loopOwnedExplicitSuperRewardUpgradesRequestedIndex() {
        SpecialStageProvider provider = mock(SpecialStageProvider.class, CALLS_REAL_METHODS);
        GameStateManager gameState = mock(GameStateManager.class);

        SpecialStageTransitionSupport.publishRewardIfLoopOwned(
                provider, gameState, 4, EmeraldRewardKind.SUPER_EMERALD);

        verify(gameState, times(1)).markSuperEmeraldCollected(4);
        verify(gameState, never()).markEmeraldCollected(4);
    }

    @Test
    void superEmeraldResultsReturnDirectlyToSanctuaryWithoutTitleCard() {
        assertTrue(SpecialStageTransitionSupport.returnsDirectlyToSanctuary(
                EmeraldRewardKind.SUPER_EMERALD));
        assertFalse(SpecialStageTransitionSupport.returnsDirectlyToSanctuary(
                EmeraldRewardKind.CHAOS_EMERALD));
        assertFalse(SpecialStageTransitionSupport.returnsDirectlyToSanctuary(null));
    }
}

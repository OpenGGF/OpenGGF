package com.openggf;

import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.GameStateManager;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTransitionCoordinator;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

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
    void onlyASuperEmeraldRewardIdentifiesASanctuaryLaunchedStage() {
        assertTrue(SpecialStageTransitionSupport.enteredFromSanctuary(
                EmeraldRewardKind.SUPER_EMERALD));
        assertFalse(SpecialStageTransitionSupport.enteredFromSanctuary(
                EmeraldRewardKind.CHAOS_EMERALD));
        assertFalse(SpecialStageTransitionSupport.enteredFromSanctuary(null));
    }

    @Test
    void superEmeraldResultsLoadTheBigRingOriginRatherThanTheSanctuary() throws IOException {
        // loc_90926 sets Special_bonus_entry_flag = 1, so Load_Starpost_Settings takes
        // loc_2D2C2 and restores Saved2_* — the zone the Big Ring was collected in.
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.requestSanctuaryExit()).thenReturn(true);
        when(levelManager.getRequestedZone()).thenReturn(0);
        when(levelManager.getRequestedAct()).thenReturn(1);

        assertFalse(SpecialStageTransitionSupport.loadSpecialStageReturnLevel(
                levelManager, EmeraldRewardKind.SUPER_EMERALD, 3, true),
                "an origin return is not a sanctuary hub return");

        verify(levelManager).loadZoneAndAct(0, 1);
        verify(levelManager, never()).loadCurrentLevel();
        verify(levelManager, never()).markSanctuaryReentry(anyInt(), anyBoolean());
    }

    @Test
    void superEmeraldResultsWithoutASavedOriginRebuildTheSanctuaryHub() throws IOException {
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.requestSanctuaryExit()).thenReturn(false);

        assertTrue(SpecialStageTransitionSupport.loadSpecialStageReturnLevel(
                levelManager, EmeraldRewardKind.SUPER_EMERALD, 3, true));

        // The controller reads its re-entry context while the load spawns it.
        InOrder order = inOrder(levelManager);
        order.verify(levelManager).markSanctuaryReentry(3, true);
        order.verify(levelManager).loadCurrentLevel();
        verify(levelManager, never()).loadZoneAndAct(anyInt(), anyInt());
    }

    @Test
    void ordinaryResultsReloadTheCurrentLevelAndNeverTouchTheSanctuary() throws IOException {
        LevelManager levelManager = mock(LevelManager.class);

        assertFalse(SpecialStageTransitionSupport.loadSpecialStageReturnLevel(
                levelManager, EmeraldRewardKind.CHAOS_EMERALD, 3, true));

        verify(levelManager).loadCurrentLevel();
        verify(levelManager, never()).requestSanctuaryExit();
        verify(levelManager, never()).markSanctuaryReentry(anyInt(), anyBoolean());
    }
}

package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadMode;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLevelZoneLoadExecutor {
    private LevelManager level;
    private LevelTransitionCoordinator transitions;
    private LevelZoneLoadExecutor executor;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        level = spy(GameServices.level());
        transitions = level.getTransitions();
        executor = new LevelZoneLoadExecutor(level, transitions);
        doNothing().when(level).revokeNativePlcTargetAdmission();
    }

    @Test
    void successfulFreshLoadKeepsCarryAndRoutesOriginalPresentationFlags() throws Exception {
        LevelContinuationCarry.request(level, 2, 1, 7, 99, null);
        doAnswer(call -> {
            assertEquals(2, level.getCurrentZone());
            assertEquals(1, level.getCurrentAct());
            assertEquals(1, level.getApparentAct());
            assertTrue(LevelContinuationCarry.bypassInitialPresentation(transitions));
            return null;
        }).when(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, true, true);

        executor.execute(2, 1, LevelLoadMode.FULL, true, true);

        assertEquals(LevelContinuationCarry.Phase.LOADED, transitions.continuationCarry.phase());
        verify(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, true, true);
        verify(level, never()).revokeNativePlcTargetAdmission();
    }

    @Test
    void bonusReturnPreservesSavedApparentActBeforeTheProfileLoads() throws Exception {
        var saved = new com.openggf.game.BonusStageState(1, 0, 0, 0, 0, 0,
                0, 0, 0x400, 0x500, 0x360, 0x480, (byte) 0x0C, (byte) 0x0D,
                0x590, 0);
        new com.openggf.game.BonusStageTransitionCoordinator()
                .prepareReturnLoad(() -> level, saved);
        doAnswer(call -> {
            assertEquals(1, level.getCurrentAct());
            assertEquals(0, level.getApparentAct(),
                    "loc_6034 restores apparent act before CheckLevelForWater and ScreenInit");
            return null;
        }).when(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, false, true);

        executor.execute(0, 1, LevelLoadMode.FULL, false, true);
    }

    @Test
    void profileFailureDiscardsCarryAndRevokesTheActualTargetWithoutRetry() {
        LevelContinuationCarry.request(level, 2, 1, 7, 99, null);
        var failure = new IllegalStateException("load failed");
        doThrow(failure).when(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, false, true);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> executor.execute(2, 1, LevelLoadMode.FULL, false, true)));

        assertNull(transitions.continuationCarry);
        var order = inOrder(level);
        order.verify(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, false, true);
        order.verify(level).revokeNativePlcTargetAdmission();
    }

    @Test
    void previewRetainsItsOriginalNonPresentingNonRuntimeLoadFlags() throws Exception {
        doNothing().when(level).loadCurrentLevel(false, LevelLoadMode.PREVIEW_CAPTURE, false, false, false);

        executor.execute(2, 1, LevelLoadMode.PREVIEW_CAPTURE, false, false);

        verify(level).loadCurrentLevel(false, LevelLoadMode.PREVIEW_CAPTURE, false, false, false);
        verify(level, never()).revokeNativePlcTargetAdmission();
    }

    @Test
    void bonusReturnPreservesPreparedApparentActBeforeAnyProfileWork() throws Exception {
        level.setBonusStageReturnCheckpointIndex(0);
        level.setApparentAct(0);
        doAnswer(call -> {
            assertEquals(1, level.getCurrentAct());
            assertEquals(0, level.getApparentAct(), "CheckLevelForWater reads the saved apparent act");
            return null;
        }).when(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, false, false);
        executor.execute(0, 1, LevelLoadMode.FULL, false, false);
        assertEquals(0, level.getApparentAct());
    }

    @Test
    void pausedLoadKeepsReturnBankAndCarryUntilActualCompletion() throws Exception {
        var pending = installPausedLoad();
        var bank = new com.openggf.level.objects.PersistentRespawnState(
                new long[]{1}, new long[]{2}, new long[]{4}, new long[]{8}, new byte[0]);
        level.restorePersistentRespawnOnNextObjectReset(bank);
        executor.execute(2, 1, LevelLoadMode.FULL, false, true);

        assertEquals(LevelContinuationCarry.Phase.LOADING, transitions.continuationCarry.phase());
        assertSame(bank, level.checkpointCoordinator.consumePersistentRespawn(),
                "returning at HOLD must not clear the unconsumed object/ring bank");
        level.restorePersistentRespawnOnNextObjectReset(bank);
        pending.zoneLoadFinished.accept(true);
        assertEquals(LevelContinuationCarry.Phase.LOADED, transitions.continuationCarry.phase());
        assertNull(level.checkpointCoordinator.consumePersistentRespawn());
        verify(level, never()).revokeNativePlcTargetAdmission();
    }

    @Test
    void failedPausedLoadDiscardsCarryAndReturnBankAndRevokesTarget() throws Exception {
        var pending = installPausedLoad();
        executor.execute(2, 1, LevelLoadMode.FULL, false, true);
        level.restorePersistentRespawnOnNextObjectReset(new com.openggf.level.objects.PersistentRespawnState(
                new long[]{1}, new long[0], new long[0], new long[]{2}, new byte[0]));
        pending.zoneLoadFinished.accept(false);
        assertNull(transitions.continuationCarry);
        assertNull(level.checkpointCoordinator.consumePersistentRespawn());
        verify(level).revokeNativePlcTargetAdmission();
    }

    private NativeLevelLoadController.PendingLoad installPausedLoad() {
        LevelContinuationCarry.request(level, 2, 1, 7, 99, null);
        var pending = new NativeLevelLoadController.PendingLoad(mock(LevelLoadAttempt.class),
                new com.openggf.game.LevelLoadContext(), 0, LevelLoadMode.FULL, null);
        doAnswer(call -> { level.nativeLoads.pending = pending; return null; })
                .when(level).loadCurrentLevel(true, LevelLoadMode.FULL, false, false, true);
        return pending;
    }
}

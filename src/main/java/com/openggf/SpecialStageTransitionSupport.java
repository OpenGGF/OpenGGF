package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.SpecialStageProvider;
import com.openggf.level.BigRingReturnState;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

final class SpecialStageTransitionSupport {
    private SpecialStageTransitionSupport() {}

    static int resolveStageIndex(
            SpecialStageEntryRequest request,
            SpecialStageProvider provider,
            GameStateManager gameState) {
        return request.forcedStageIndex() != null
                ? request.forcedStageIndex()
                : provider.consumeStageIndexForEntry(gameState);
    }

    static void publishRewardIfLoopOwned(
            SpecialStageProvider provider,
            GameStateManager gameState,
            int stageIndex,
            EmeraldRewardKind rewardKind) {
        if (!provider.ownsEmeraldReward()) {
            provider.publishEmeraldReward(gameState, stageIndex, rewardKind);
        }
    }

    /**
     * ROM: only the HPZ pedestal ({@code loc_90926}) launches a stage that awards a
     * Super Emerald, so the reward kind identifies a stage entered from the sanctuary.
     */
    static boolean enteredFromSanctuary(EmeraldRewardKind rewardKind) {
        return rewardKind == EmeraldRewardKind.SUPER_EMERALD;
    }

    /**
     * Loads the level a finished Special Stage returns to, and reports whether the
     * destination is the HPZ sanctuary hub rather than an ordinary level.
     *
     * <p>ROM {@code Load_Starpost_Settings} branches to {@code loc_2D2C2} whenever
     * {@code Special_bonus_entry_flag} is set, and the pedestal sets it to 1 — so a
     * sanctuary-launched stage restores {@code Saved2_*} and resumes in the zone the
     * Big Ring was collected in. Only a sanctuary visit with no saved origin (level
     * select) falls back to rebuilding the hub in place, which needs its re-entry
     * context recorded before the load spawns the controller.
     */
    static boolean loadSpecialStageReturnLevel(
            LevelManager levelManager,
            EmeraldRewardKind rewardKind,
            int stageIndex,
            boolean succeeded) {
        if (enteredFromSanctuary(rewardKind)) {
            if (loadSanctuaryOriginLevel(levelManager)) {
                return false;
            }
            levelManager.markSanctuaryReentry(stageIndex, succeeded);
        }
        levelManager.consumeSpecialStageReturnLevelReloadRequest();
        levelManager.loadCurrentLevel();
        return enteredFromSanctuary(rewardKind);
    }

    private static boolean loadSanctuaryOriginLevel(LevelManager levelManager) {
        if (!levelManager.requestSanctuaryExit()) {
            return false;
        }
        // The origin is loaded synchronously here, so the queued request must not also
        // fire the LEVEL tick's zone/act fade.
        levelManager.consumeZoneActRequest();
        int zone = levelManager.getRequestedZone();
        int act = levelManager.getRequestedAct();
        levelManager.consumeSpecialStageReturnLevelReloadRequest();
        try {
            levelManager.loadZoneAndAct(zone, act);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load Big Ring origin zone " + zone + " act " + act, e);
        }
        return true;
    }

    static void restoreBigRingReturn(
            BigRingReturnState returnState,
            AbstractPlayableSprite playable,
            Camera camera,
            LevelManager levelManager,
            WaterSystem waterSystem) {
        if (returnState == null) {
            return;
        }
        returnState.restoreToPlayer(
                playable, camera, levelManager.getLevelGamestate(), waterSystem,
                levelManager.getFeatureZoneId(), levelManager.getFeatureActId());
        returnState.restoreCheckpointState(levelManager.getCheckpointState());
        if (returnState.apparentZoneAndAct() >= 0) {
            levelManager.setApparentAct(returnState.apparentZoneAndAct() & 0xFF);
        }
        var eventProvider = GameServices.module().getLevelEventProvider();
        if (eventProvider instanceof AbstractLevelEventManager eventManager) {
            eventManager.restoreEventRoutineState(returnState.dynamicResizeRoutine(), 0);
        }
        // A super-emerald origin return retires the sanctuary context now that the
        // destination has consumed every saved field.
        if (levelManager.isSanctuaryOriginRestorePending(
                levelManager.getCurrentZone(), levelManager.getCurrentAct())) {
            levelManager.completeSanctuaryOriginRestore();
        } else {
            levelManager.clearBigRingReturn();
        }
    }
}

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

    static boolean returnsDirectlyToSanctuary(EmeraldRewardKind rewardKind) {
        return rewardKind == EmeraldRewardKind.SUPER_EMERALD;
    }

    static void restoreBigRingReturn(
            BigRingReturnState returnState,
            AbstractPlayableSprite playable,
            Camera camera,
            LevelManager levelManager,
            WaterSystem waterSystem,
            boolean sanctuaryOriginRestore) {
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
        if (sanctuaryOriginRestore) {
            levelManager.completeSanctuaryOriginRestore();
        } else {
            levelManager.clearBigRingReturn();
        }
    }
}

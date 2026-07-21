package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.OscillationManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

import java.io.IOException;
import java.util.List;

/**
 * Owns ROM-aligned in-place act-transition execution.
 */
final class LevelActTransitionExecutor {
    private final LevelManager levelManager;

    LevelActTransitionExecutor(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    void execute(SeamlessLevelTransitionRequest request) throws IOException {
        if (request == null) {
            return;
        }

        Camera cam = levelManager.camera;

        GameStateManager gameState = GameServices.gameState();
        boolean endOfLevelActive = gameState.isEndOfLevelActive();
        boolean endOfLevelFlag = gameState.isEndOfLevelFlag();
        gameState.resetForLevel();
        if (request.preserveEndOfLevelActive()) {
            gameState.setEndOfLevelActive(endOfLevelActive);
        }
        if (request.preserveEndOfLevelFlag()) {
            gameState.setEndOfLevelFlag(endOfLevelFlag);
        }

        if (request.preserveMusic()) {
            levelManager.setSuppressNextMusicChange(true);
        }

        if (GameServices.zoneRuntimeRegistry().current()
                .advancesOscillationOnSeamlessTransition()) {
            OscillationManager.advanceForSeamlessTransition();
        }

        levelManager.writeCurrentZone(request.targetZone());
        levelManager.writeCurrentAct(request.targetAct());

        if (levelManager.levels.isEmpty()) {
            levelManager.gameModule = GameServices.module();
            levelManager.refreshZoneList();
        }
        LevelDescriptor levelData = levelManager.levels.get(levelManager.currentZone).get(levelManager.currentAct);
        levelManager.beginSeamlessTransitionLoad(request);
        try {
            levelManager.loadLevelData(levelData.levelIndex());
        } finally {
            levelManager.endSeamlessTransitionLoad();
        }

        if (request.mutationKey() != null && !request.mutationKey().isBlank()) {
            levelManager.applySeamlessMutation(request.mutationKey());
        }

        levelManager.initAnimatedContent();

        ObjectArtProvider artProvider = levelManager.gameModule != null
                ? levelManager.gameModule.getObjectArtProvider()
                : null;
        if (artProvider != null) {
            artProvider.reloadStandaloneArtForActTransition(levelManager.currentZone);
            artProvider.registerLevelTileArt(levelManager.level, levelManager.currentZone);
            if (levelManager.objectRenderManager != null) {
                levelManager.objectRenderManager.ensurePatternsCached(
                        levelManager.graphicsManager, LevelManager.OBJECT_PATTERN_BASE);
            }
        }

        levelManager.initWater(true);
        if (!request.preserveCheckpointUntilResults()) {
            levelManager.checkpointCoordinator.clear();
        }

        if (!request.preserveLevelGamestate()) {
            levelManager.levelGamestate = levelManager.gameModule.createLevelState();
        }

        List<TransitionSstOccupant> carriedOccupants = levelManager.objectManager == null
                ? List.of()
                : request.objectSurvivalPolicy()
                        == SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST
                        ? levelManager.objectManager.snapshotAllLiveSstObjectsForTransition()
                        : levelManager.objectManager.snapshotPersistentDynamicObjectsForTransition();

        int postOffsetCameraX = cam.getX() + request.cameraOffsetX();
        levelManager.rebuildManagersForActTransition(cam, carriedOccupants, request,
                postOffsetCameraX);
        levelManager.applySeamlessOffsets(request, cam);
        levelManager.offsetCarriedObjectsForTransition(carriedOccupants, request);

        levelManager.restoreCameraBoundsForCurrentLevel(cam);
        levelManager.applyPostTransitionCameraOverrides(request, cam);
        if (!request.preserveOffsetCameraPosition()) {
            cam.updatePosition(true);
        }

        resetSidekickCpuBoundsAfterTransition(cam);
        levelManager.initLevelEventsForCurrentZoneAct();

        // loadLevelData only swaps immutable level data; the live object manager
        // and level-event owner are replaced later in this executor. Rebind the
        // registry after both replacements, while still inside the outer seamless
        // transition boundary, so no Act-2 keyframe can target dead Act-1 owners.
        var gameplayMode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.registerLevelAdapters(levelManager);
        }

        try {
            levelManager.reinitializeZoneFeaturesForActTransition();
        } catch (IOException e) {
            LevelManager.LOGGER.warning("Failed to reinitialize zone features: " + e.getMessage());
        }

        if (request.musicOverrideId() >= 0) {
            levelManager.audioManager.playMusic(request.musicOverrideId());
        }

        if (request.showInLevelTitleCard()) {
            levelManager.requestInLevelTitleCard(
                    levelManager.currentZone,
                    levelManager.currentAct,
                    request.resetLevelGamestateAtInLevelTitleCardDisplay(),
                    request.inLevelTitleCardResetAdditionalDispatches(),
                    request.inLevelTitleCardResetPhaseOneDispatchOverlap(),
                    request.lockPlayerControlForInLevelTitleCard(),
                    request.inLevelTitleCardExitAdditionalDispatches(),
                    request.inLevelTitleCardExitPhaseOneDispatchOverlap());
        }
    }

    private void resetSidekickCpuBoundsAfterTransition(Camera cam) {
        for (AbstractPlayableSprite sidekick : levelManager.spriteManager.getSidekicks()) {
            SidekickCpuController cpu = sidekick.getCpuController();
            if (cpu != null) {
                cpu.setLevelBounds(
                        (int) cam.getMinX(),
                        (int) cam.getMaxX(),
                        (int) Math.max(cam.getMaxY(), cam.getMaxYTarget()));
            }
        }
    }
}

package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.OscillationManager;
import com.openggf.level.animation.SeamlessTransitionAnimationClock;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.level.resources.DeferredLevelResourceTracker;

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

        SeamlessTransitionResourceHandoff handoff =
                request.resourceHandoffId() != null
                        ? GameServices.seamlessTransitionResourceHandoffs()
                                .claim(request.resourceHandoffId())
                        : null;
        DeferredLevelResourceTracker deferredResources =
                handoff != null
                        ? handoff.deferredResources().newTracker()
                        : DeferredLevelResourceTracker.none();

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

        // NOTE: preserveMusic() needs no action here. An in-place act transition
        // never runs the level-init profile, so InitAudio — the only consumer of
        // the suppress-next-music flag — never fires. Setting the flag here left
        // it latched and silenced the *next* real level load instead (music then
        // stayed off until a respawn or another load cleared it).

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
        LevelData levelData = levelManager.levels.get(levelManager.currentZone).get(levelManager.currentAct);
        levelManager.loadLevelData(
                levelData.getLevelIndex(), deferredResources);
        deferredResources.verifyFullyConsumed();

        if (request.mutationKey() != null && !request.mutationKey().isBlank()) {
            levelManager.applySeamlessMutation(request.mutationKey());
        }

        levelManager.initAnimatedContent();
        if (levelManager.animatedPatternManager instanceof SeamlessTransitionAnimationClock clock) {
            clock.advanceForSeamlessTransition();
        }

        ObjectArtProvider artProvider = levelManager.gameModule != null
                ? levelManager.gameModule.getObjectArtProvider()
                : null;
        if (artProvider != null) {
            artProvider.prepareRuntimeArtForActTransition(
                    levelManager.currentZone,
                    request.runtimeArtAdmissionPolicy());
            artProvider.registerLevelTileArt(levelManager.level, levelManager.currentZone);
            if (levelManager.objectRenderManager != null) {
                levelManager.objectRenderManager.ensurePatternsCached(
                        levelManager.graphicsManager, LevelManager.OBJECT_PATTERN_BASE);
            }
        }

        levelManager.initWater(true);
        levelManager.checkpointCoordinator.clear();

        if (!request.preserveLevelGamestate()) {
            levelManager.levelGamestate = levelManager.gameModule.createLevelState();
        }

        List<ObjectInstance> persistentDynamicObjects = levelManager.objectManager != null
                ? levelManager.objectManager.snapshotPersistentDynamicObjectsForTransition()
                : List.of();

        levelManager.rebuildManagersForActTransition(
                cam,
                persistentDynamicObjects,
                request.preserveEndOfLevelActive());
        levelManager.applySeamlessOffsets(request, cam);
        levelManager.offsetCarriedObjectsForTransition(persistentDynamicObjects, request);

        levelManager.restoreCameraBoundsForCurrentLevel(cam);
        levelManager.applyPostTransitionCameraOverrides(request, cam);
        if (!request.preserveOffsetCameraPosition()) {
            cam.updatePosition(true);
        }

        resetSidekickCpuBoundsAfterTransition(cam);
        levelManager.initLevelEventsForCurrentZoneAct();
        if (handoff != null) {
            handoff.transferAfterTargetInit();
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

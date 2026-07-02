package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.ObjectArtProvider;
import com.openggf.level.objects.ObjectInstance;
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

        GameServices.gameState().resetForLevel();

        if (request.preserveMusic()) {
            levelManager.setSuppressNextMusicChange(true);
        }

        levelManager.writeCurrentZone(request.targetZone());
        levelManager.writeCurrentAct(request.targetAct());

        if (levelManager.levels.isEmpty()) {
            levelManager.gameModule = GameServices.module();
            levelManager.refreshZoneList();
        }
        LevelData levelData = levelManager.levels.get(levelManager.currentZone).get(levelManager.currentAct);
        levelManager.loadLevelData(levelData.getLevelIndex());

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
        levelManager.checkpointCoordinator.clear();

        if (!request.preserveLevelGamestate()) {
            levelManager.levelGamestate = levelManager.gameModule.createLevelState();
        }

        List<ObjectInstance> persistentDynamicObjects = levelManager.objectManager != null
                ? levelManager.objectManager.snapshotPersistentDynamicObjectsForTransition()
                : List.of();

        levelManager.rebuildManagersForActTransition(cam, persistentDynamicObjects);
        levelManager.applySeamlessOffsets(request, cam);
        levelManager.offsetCarriedObjectsForTransition(persistentDynamicObjects, request);

        levelManager.restoreCameraBoundsForCurrentLevel(cam);
        levelManager.applyPostTransitionCameraOverrides(request, cam);
        if (!request.preserveOffsetCameraPosition()) {
            cam.updatePosition(true);
        }

        resetSidekickCpuBoundsAfterTransition(cam);
        levelManager.initLevelEventsForCurrentZoneAct();

        try {
            levelManager.reinitializeZoneFeaturesForActTransition();
        } catch (IOException e) {
            LevelManager.LOGGER.warning("Failed to reinitialize zone features: " + e.getMessage());
        }

        if (request.musicOverrideId() >= 0) {
            levelManager.audioManager.playMusic(request.musicOverrideId());
        }

        if (request.showInLevelTitleCard() && !levelManager.graphicsManager.isHeadlessMode()) {
            levelManager.requestInLevelTitleCard(levelManager.currentZone, levelManager.currentAct);
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

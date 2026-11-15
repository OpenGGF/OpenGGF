package com.openggf.level;

import com.openggf.game.GameStateManager;
import com.openggf.game.ZoneKey;
import com.openggf.level.resources.PreparableLevelLoader;
import com.openggf.level.resources.PreparedLevelBuild;

/**
 * {@link LevelManager}'s level builds prepared ahead of their install on the shared
 * {@link LevelLoadPreparer} thread: a seamless act transition's target (started where
 * the ROM starts that target's resource work) and a committed reload of the current act
 * (started when a death respawn's fade to black begins). Preparation never moves an
 * install; the install takes a matching build or loads synchronously.
 */
final class LevelPreparedLoads {
    final LevelLoadPreparer preparer = new LevelLoadPreparer();

    /** See {@link LevelManager#prepareActTransitionLevelLoad}. */
    boolean prepareActTransition(LevelManager manager, int zone, int act, String mutationKey) {
        if (!(manager.game instanceof PreparableLevelLoader loader)) {
            return false;
        }
        int levelIndex = stockLevelIndex(manager, zone, act);
        if (levelIndex < 0) {
            return false;
        }
        // Resolve gameplay configuration on the frame thread. The submitted task
        // owns its ROM and immutable load inputs, never a live feature provider.
        preparer.prepare(loader, levelIndex, mutationKey, loader.prepareLevelBuildTask(levelIndex, mutationKey));
        return true;
    }

    /**
     * Starts decoding the current act for a reload of it the game has committed to: a death
     * respawn whose fade to black has begun. The decode is pure ROM work, so it runs during
     * the fade instead of on the frame the fade completes. The reload happens exactly where
     * it did; it installs the build only if the inputs a synchronous load would use at that
     * moment equal those the build was made from (otherwise it loads synchronously).
     */
    boolean prepareReload(LevelManager manager) {
        if (!(manager.game instanceof PreparableLevelLoader loader) || manager.level == null
                || manager.gameModule == null) {
            return false;
        }
        int levelIndex = stockLevelIndex(manager, manager.currentZone, manager.currentAct);
        var task = levelIndex < 0 ? null : loader.prepareReloadBuildTask(levelIndex);
        if (task == null) {
            return false;
        }
        // The reload creates a new Game for the same module (InitGameModule), so the build
        // belongs to the module; the new Game decides at install whether it still matches.
        preparer.prepare(manager.gameModule, LevelLoadPreparer.Purpose.RELOAD, levelIndex, null, task);
        return true;
    }

    /** A prepared reload of {@code levelIndex} whose inputs still match a synchronous load now. */
    PreparedLevelBuild takeReload(LevelManager manager, int levelIndex) {
        if (!(manager.game instanceof PreparableLevelLoader loader) || manager.gameModule == null) {
            return null;
        }
        return preparer.take(manager.gameModule, LevelLoadPreparer.Purpose.RELOAD, levelIndex, null,
                loader::reloadInputsStillMatch);
    }

    PreparedLevelBuild takeActTransition(LevelManager manager, int levelIndex, String mutationKey) {
        return preparer.take(manager.game, levelIndex, mutationKey);
    }

    /**
     * Builds whole-level tilemaps for an installed prepared act transition so its first
     * render does not, unless the foreground streams (a GPU renderer is present): then the
     * first draw streams the foreground from the camera out and builds the background, and
     * a whole-level build of both here would only move that cost onto the install frame.
     */
    void adoptTilemaps(LevelManager manager, GameStateManager gameState, PreparedLevelBuild prepared) {
        LevelTilemapManager tilemaps = manager.tilemapManager;
        if (prepared == null || tilemaps == null || tilemaps.streamsForegroundBuild()) {
            return;
        }
        // Wrapping policy belongs to the installed target and its live zone state.
        // Build on the frame thread, after installation; ROM decoding remains async.
        PrebuiltTilemaps built = LevelTilemapPrebuilder.build(manager.level, manager.graphicsManager, gameState,
                manager.zoneFeatureProvider, manager.currentZone, manager.parallaxManager,
                manager.verticalWrapEnabled);
        if (built != null) {
            tilemaps.adoptPrebuiltTilemaps(built);
        }
    }

    /** The stock ROM level index of {@code zone}/{@code act}, or -1 (out of range or mod-owned). */
    private static int stockLevelIndex(LevelManager manager, int zone, int act) {
        var levels = manager.levels;
        if (zone < 0 || zone >= levels.size() || act < 0 || act >= levels.get(zone).size()) {
            return -1;
        }
        // Additive levels belong to the module override loader, not the stock ROM preparer.
        if (manager.gameModule != null
                && manager.gameModule.getZoneRegistry().zoneKey(zone) instanceof ZoneKey.Mod) {
            return -1;
        }
        return levels.get(zone).get(act).levelIndex();
    }
}

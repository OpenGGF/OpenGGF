package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.PersistentRespawnState;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.level.rings.RingManager;
import com.openggf.audio.AudioManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.logging.Logger;

final class LevelManagerInitializationSupport {
    private LevelManagerInitializationSupport() {
    }

    /** Rebuilds the immutable geometry caches after a level is loaded or restored. */
    static void cacheLevelDimensions(LevelManager owner, Logger logger) {
        if (owner.level != null) {
            owner.cachedFgWidthPx = owner.getLayerLevelWidthPx((byte) 0);
            owner.cachedFgHeightPx = owner.getLayerLevelHeightPx((byte) 0);
            owner.cachedBgWidthPx = owner.getLayerLevelWidthPx((byte) 1);  // Full map width (matches reference)
            owner.cachedBgContiguousWidthPx = computeActualBgDataWidthPx(owner.level, owner.blockPixelSize, logger);  // For bgTilemapBaseX wrapping
            owner.cachedBgHeightPx = owner.getLayerLevelHeightPx((byte) 1);
        } else {
            owner.cachedFgWidthPx = owner.blockPixelSize;
            owner.cachedFgHeightPx = owner.blockPixelSize;
            owner.cachedBgWidthPx = owner.blockPixelSize;
            owner.cachedBgContiguousWidthPx = owner.blockPixelSize;
            owner.cachedBgHeightPx = owner.blockPixelSize;
        }
        owner.blockGrid = new BlockGridIndexer(owner.blockPixelSize);
    }

    /**
     * Scan the BG layer (layer 1) to find the contiguous data width.
     * On the Mega Drive, the BG nametable is a 512px-wide ring buffer.
     * The scroll handler fills it from the BG map, wrapping at the map's
     * data width.  The Map stores both FG and BG with the same total width
     * (e.g., 128 blocks = 16384px), but BG data typically only spans a
     * small contiguous region from column 0 (e.g., 8 blocks for HTZ).
     * <p>
     * Using the contiguous BG data width for X wrapping ensures that queries
     * at large camera X positions wrap back to valid BG data rather than
     * reading empty columns in the unused portion of the map.
     * <p>
     * Example: HTZ BG data spans 8 contiguous columns (1024px) within a
     * 128-column map.  Without this fix, bgTilemapBaseX=6144 queries
     * column 48 (empty).  With contiguous width = 1024px wrapping,
     * 6144 mod 1024 = 0 → column 0 (valid).
     */
    private static int computeActualBgDataWidthPx(Level level, int blockPixelSize, Logger logger) {
        int dataWidthPx = LevelGeometry.contiguousBgDataWidthPx(level, blockPixelSize);
        if (level != null && level.getMap() != null
                && dataWidthPx < level.getMap().getWidth() * blockPixelSize) {
            logger.fine("BG contiguous data width: " + dataWidthPx / blockPixelSize + " blocks ("
                    + dataWidthPx + "px) out of " + level.getMap().getWidth() + " map columns");
        }
        return dataWidthPx;
    }

    static void registerWorldRenderEffects(com.openggf.game.session.WorldSession world,
                                          com.openggf.game.render.SpecialRenderEffectRegistry registry) {
        var motion = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                world, com.openggf.game.mutators.MotionMutatorRuntime.class);
        if (motion != null && registry != null) {
            registry.register(new com.openggf.game.mutators.MotionFluidRenderEffect(motion));
        }
    }

    static void rebindPowerUpSpawners(
            ObjectManager objectManager,
            SpriteManager spriteManager,
            String mainCharacterCode) {
        DefaultPowerUpSpawner spawner = new DefaultPowerUpSpawner(objectManager);
        Sprite player = spriteManager.getSprite(mainCharacterCode);
        if (player instanceof AbstractPlayableSprite playable) {
            playable.setPowerUpSpawner(spawner);
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.setPowerUpSpawner(spawner);
        }
    }

    static void resetCameraBounds(
            Camera camera,
            Level level,
            ObjectManager objectManager,
            PersistentRespawnState persistentRespawnState) {
        camera.setFrozen(false);
        camera.setMinX((short) level.getMinX());
        camera.setMaxX((short) level.getMaxX());
        objectManager.reset(camera.getX(), persistentRespawnState);
    }

    /**
     * @param keptRingStatusTable ROM {@code Ring_status_table} preserved across
     *     a reload the ROM performed with {@code Respawn_table_keep} set, or
     *     {@code null} for an ordinary load that starts with a clean table.
     *     Applied immediately after {@link RingManager#reset(int)} -- which is
     *     the engine's equivalent of {@code sub_EB1A}'s wipe -- and before any
     *     ring can be drawn or touched.
     * @see com.openggf.level.LevelTransitionCoordinator#bigRingReturnRingStatusTable()
     */
    static RingManager initializeRings(
            LevelManager levelManager,
            TouchResponseTable touchResponseTable,
            AudioManager audioManager,
            Camera camera,
            GraphicsManager graphicsManager,
            long[] keptRingStatusTable) {
        Level level = levelManager.getCurrentLevel();
        RingManager rings = new RingManager(
                level.getRings(), level.getRingSpriteSheet(), levelManager, touchResponseTable, audioManager);
        rings.reset(camera.getX());
        rings.restoreRingStatusTable(keptRingStatusTable);
        rings.ensurePatternsCached(graphicsManager, level.getPatternCount());
        var gameplayMode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            gameplayMode.registerRingAdapter(rings);
        }
        return rings;
    }
}

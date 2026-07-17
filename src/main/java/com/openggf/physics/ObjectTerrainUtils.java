package com.openggf.physics;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;

/**
 * Terrain collision detection for game objects.
 * Mirrors ROM's ObjCheckFloorDist (s2.asm:43738) for floor/ceiling/wall detection
 * used by falling objects, animals, monitors, etc.
 *
 * Unlike player collision (paired sensors), object collision uses single-point checks.
 */
public final class ObjectTerrainUtils {

    /** Solidity bit for top-solid collision (walkable from above) */
    private static final int SOLIDITY_TOP = 0x0C;

    /** Solidity bit for all-sides-solid collision */
    private static final int SOLIDITY_ALL = 0x0D;

    private static final int FULL_TILE = 16;

    private ObjectTerrainUtils() {}

    // ========================================
    // PUBLIC API
    // ========================================

    /** Check distance to floor from object bottom (x, y + yRadius) */
    public static TerrainCheckResult checkFloorDist(int x, int y, int yRadius) {
        return checkFloorDistAtPoint(x, y + yRadius, false);
    }

    /**
     * Check distance to floor from object bottom (x, y + yRadius), returning the
     * ROM-transformed terrain angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkFloorDistWithFlipAwareAngle(int x, int y, int yRadius) {
        return checkFloorDistAtPoint(x, y + yRadius, true);
    }

    /** Check distance to floor from exact point */
    public static TerrainCheckResult checkFloorDist(int x, int y) {
        return checkFloorDistAtPoint(x, y, false);
    }

    /** Object-owned variant that does not resolve gameplay services globally. */
    public static TerrainCheckResult checkFloorDist(LevelManager levelManager, int x, int y) {
        return checkFloorDist(levelManager, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                false, x, y);
    }

    public static TerrainCheckResult checkFloorDist(LevelManager levelManager,
                                                     BackgroundPlaneCollisionProvider provider,
                                                     boolean secondaryCollisionPath,
                                                     int x, int y) {
        return checkFloorDistAtPoint(levelManager, provider, secondaryCollisionPath, x, y, false);
    }

    /**
     * Check distance to floor from exact point, returning the ROM-transformed terrain
     * angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkFloorDistWithFlipAwareAngle(int x, int y) {
        return checkFloorDistAtPoint(x, y, true);
    }

    /** Check distance to ceiling from object top (x, y - yRadius) */
    public static TerrainCheckResult checkCeilingDist(int x, int y, int yRadius) {
        return checkCeilingDistAtPoint(x, y - yRadius, false);
    }

    /**
     * Check distance to ceiling from object top (x, y - yRadius), returning the
     * ROM-transformed terrain angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkCeilingDistWithFlipAwareAngle(int x, int y, int yRadius) {
        return checkCeilingDistAtPoint(x, y - yRadius, true);
    }

    /** S3K RingCheckFloorDist_ReverseGravity using top-solidity on both planes. */
    public static TerrainCheckResult checkReverseGravityRingDist(int x, int y) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();
        return checkReverseGravityRingDist(lm, x, y);
    }

    /** Object-owned reverse-gravity ring probe. */
    public static TerrainCheckResult checkReverseGravityRingDist(LevelManager lm, int x, int y) {
        return checkReverseGravityRingDist(lm, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                false, x, y);
    }

    public static TerrainCheckResult checkReverseGravityRingDist(LevelManager lm,
                                                                  BackgroundPlaneCollisionProvider provider,
                                                                  boolean secondaryCollisionPath,
                                                                  int x, int y) {
        if (lm == null) return TerrainCheckResult.noCollision();
        TerrainCheckResult foreground = checkReverseGravityRingDist(
                lm, (byte) 0, x, y, secondaryCollisionPath);
        BackgroundPlaneCollisionProvider.State state = provider.state(lm);
        if (!state.active()) return foreground;
        int bgX = provider.backgroundX(state, x, Direction.UP);
        int bgY = provider.backgroundY(state, y);
        TerrainCheckResult background = checkReverseGravityRingDist(
                lm, (byte) 1, bgX, bgY, secondaryCollisionPath);
        if (!background.foundSurface()) return foreground;
        return !foreground.foundSurface() || background.distance() <= foreground.distance()
                ? background : foreground;
    }

    private static TerrainCheckResult checkReverseGravityRingDist(LevelManager lm, byte layer,
                                                                  int x, int y,
                                                                  boolean secondaryCollisionPath) {
        ChunkDesc desc = lm.getChunkDescAt(layer, x, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_TOP, secondaryCollisionPath);
        int metric = getHeightMetric(tile, desc, x);
        int checkY = y;
        if (metric == 0) return TerrainCheckResult.noCollision();
        if (metric == FULL_TILE) {
            int nextY = y + 16;
            ChunkDesc nextDesc = lm.getChunkDescAt(layer, x, nextY);
            SolidTile nextTile = getSolidTile(lm, nextDesc, SOLIDITY_TOP, secondaryCollisionPath);
            int nextMetric = getHeightMetric(nextTile, nextDesc, x);
            if (nextMetric > 0 && nextMetric < FULL_TILE) {
                tile = nextTile;
                desc = nextDesc;
                metric = nextMetric;
                checkY = nextY;
            }
        }
        int tileY = checkY & ~0x0F;
        return new TerrainCheckResult((tileY + 16 - metric) - y,
                getAngle(tile, desc), getTileIndex(desc));
    }

    /** Check distance to right wall (ROM: ObjCheckRightWallDist s2.asm:43871) */
    public static TerrainCheckResult checkRightWallDist(int x, int y) {
        return checkWallDistAtPoint(x, y, false, false);
    }

    public static TerrainCheckResult checkRightWallDist(LevelManager levelManager, int x, int y) {
        return checkWallDistAtPoint(levelManager, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                false, x, y, false, false);
    }

    /**
     * Check distance to right wall (ROM: ObjCheckRightWallDist), returning the
     * ROM-transformed terrain angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkRightWallDistWithFlipAwareAngle(int x, int y) {
        return checkWallDistAtPoint(x, y, false, true);
    }

    /** Check distance to left wall (ROM: ObjCheckLeftWallDist s2.asm:44063) */
    public static TerrainCheckResult checkLeftWallDist(int x, int y) {
        return checkWallDistAtPoint(x, y, true, false);
    }

    public static TerrainCheckResult checkLeftWallDist(LevelManager levelManager, int x, int y) {
        return checkWallDistAtPoint(levelManager, BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                false, x, y, true, false);
    }

    public static TerrainCheckResult checkLeftWallDist(LevelManager levelManager,
                                                        BackgroundPlaneCollisionProvider provider,
                                                        boolean secondaryCollisionPath,
                                                        int x, int y) {
        return checkWallDistAtPoint(levelManager, provider, secondaryCollisionPath,
                x, y, true, false);
    }

    public static TerrainCheckResult checkRightWallDist(LevelManager levelManager,
                                                         BackgroundPlaneCollisionProvider provider,
                                                         boolean secondaryCollisionPath,
                                                         int x, int y) {
        return checkWallDistAtPoint(levelManager, provider, secondaryCollisionPath,
                x, y, false, false);
    }

    /**
     * Check distance to left wall (ROM: ObjCheckLeftWallDist), returning the
     * ROM-transformed terrain angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkLeftWallDistWithFlipAwareAngle(int x, int y) {
        return checkWallDistAtPoint(x, y, true, true);
    }

    // ========================================
    // FLOOR COLLISION
    // ========================================

    private static TerrainCheckResult checkFloorDistAtPoint(int x, int y, boolean flipAwareAngle) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();
        BackgroundPlaneCollisionProvider provider =
                com.openggf.game.GameServices.backgroundPlaneCollisionOrNull();
        return checkFloorDistAtPoint(lm,
                provider != null ? provider : BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                legacySecondaryCollisionPath(), x, y, flipAwareAngle);
    }

    private static TerrainCheckResult checkFloorDistAtPoint(LevelManager lm,
                                                             BackgroundPlaneCollisionProvider provider,
                                                             boolean secondaryCollisionPath,
                                                             int x, int y,
                                                             boolean flipAwareAngle) {

        TerrainCheckResult foreground = checkFloorDistAtPoint(
                lm, (byte) 0, x, y, flipAwareAngle, secondaryCollisionPath);
        BackgroundPlaneCollisionProvider.State state = provider.state(lm);
        if (!state.active()) return foreground;
        TerrainCheckResult background = checkFloorDistAtPoint(
                lm, (byte) 1, provider.backgroundX(state, x, Direction.DOWN),
                provider.backgroundY(state, y), flipAwareAngle, secondaryCollisionPath);
        if (!background.foundSurface()) return foreground;
        if (!foreground.foundSurface() || background.distance() <= foreground.distance()) {
            return background;
        }
        return foreground;
    }

    private static TerrainCheckResult checkFloorDistAtPoint(LevelManager lm, byte layer,
                                                             int x, int y, boolean flipAwareAngle,
                                                             boolean secondaryCollisionPath) {

        ChunkDesc desc = lm.getChunkDescAt(layer, x, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_TOP, secondaryCollisionPath);
        byte metric = getHeightMetric(tile, desc, x);

        if (metric == 0) {
            // No surface - extend 16 pixels down
            return checkFloorExtension(lm, layer, x, y, flipAwareAngle, secondaryCollisionPath);
        }

        // ROM: neg.w produces negative metric for V-flipped tiles.
        // FindFloor handles this via the negative metric path (s2.asm:42984-43000).
        if (metric < 0) {
            int yInTile = y & 0x0F;
            int adjusted = metric + yInTile;
            if (adjusted >= 0) {
                // No collision in this tile - extend to next tile
                return checkFloorExtension(lm, layer, x, y, flipAwareAngle, secondaryCollisionPath);
            }
            // Collision found - regress to previous tile
            return checkFloorRegress(lm, layer, tile, desc, x, y, flipAwareAngle, secondaryCollisionPath);
        }

        if (metric == FULL_TILE) {
            // Full tile - check previous tile up for edge detection
            TerrainCheckResult edgeResult = checkFloorEdge(
                    lm, layer, tile, desc, x, y, flipAwareAngle, secondaryCollisionPath);
            if (edgeResult != null) return edgeResult;
        }

        return createFloorResult(tile, desc, metric, y, y, flipAwareAngle);
    }

    private static TerrainCheckResult checkFloorExtension(LevelManager lm, byte layer,
                                                           int x, int y, boolean flipAwareAngle,
                                                           boolean secondaryCollisionPath) {
        int nextY = y + 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, nextY);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_TOP, secondaryCollisionPath);
        byte metric = getHeightMetric(tile, desc, x);

        if (metric > 0) {
            return createFloorResult(tile, desc, metric, y, nextY, flipAwareAngle);
        }
        // Handle negative metric in extension (FindFloor2 path)
        if (metric < 0) {
            int yInTile = y & 0x0F;
            int adjusted = metric + yInTile;
            if (adjusted < 0) {
                // ROM FindFloor2: not.w d1 where d1 = yInTile
                int dist = ~yInTile + 16; // +16 for extension tile offset
                return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
            }
        }
        return TerrainCheckResult.noCollision();
    }

    /** Regress to previous tile when negative metric indicates collision from below */
    private static TerrainCheckResult checkFloorRegress(LevelManager lm, byte layer, SolidTile origTile,
                                                         ChunkDesc origDesc, int x, int y,
                                                         boolean flipAwareAngle,
                                                         boolean secondaryCollisionPath) {
        int prevY = y - 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, prevY);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_TOP, secondaryCollisionPath);
        byte metric = getHeightMetric(tile, desc, x);

        return createPreviousFloorResult(tile, desc, origTile, origDesc, metric, x, y, prevY, flipAwareAngle);
    }

    // Compatibility entry point retained for focused ROM-helper tests.
    private static TerrainCheckResult checkFloorRegress(LevelManager lm, SolidTile origTile,
                                                         ChunkDesc origDesc, int x, int y,
                                                         boolean flipAwareAngle) {
        return checkFloorRegress(lm, (byte) 0, origTile, origDesc, x, y, flipAwareAngle,
                legacySecondaryCollisionPath());
    }

    private static TerrainCheckResult checkFloorEdge(LevelManager lm, byte layer,
                                                     SolidTile origTile, ChunkDesc origDesc,
                                                     int x, int y, boolean flipAwareAngle,
                                                     boolean secondaryCollisionPath) {
        int prevY = y - 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, prevY);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_TOP, secondaryCollisionPath);
        byte metric = getHeightMetric(tile, desc, x);

        return createPreviousFloorResult(tile, desc, origTile, origDesc, metric, x, y, prevY, flipAwareAngle);
    }

    // Compatibility entry point retained for focused ROM-helper tests.
    private static TerrainCheckResult checkFloorEdge(LevelManager lm, SolidTile origTile,
                                                      ChunkDesc origDesc, int x, int y,
                                                      boolean flipAwareAngle) {
        return checkFloorEdge(lm, (byte) 0, origTile, origDesc, x, y, flipAwareAngle,
                legacySecondaryCollisionPath());
    }

    private static TerrainCheckResult createPreviousFloorResult(SolidTile tile, ChunkDesc desc,
                                                                SolidTile origTile, ChunkDesc origDesc,
                                                                byte metric, int x, int y, int prevY,
                                                                boolean flipAwareAngle) {
        int yInTile = y & 0x0F;
        if (metric == 0) {
            int dist = 15 - yInTile - 16;
            return new TerrainCheckResult(dist, getAngle(origTile, origDesc, flipAwareAngle), getTileIndex(origDesc));
        }
        if (metric < 0) {
            int adjusted = metric + yInTile;
            if (adjusted >= 0) {
                int dist = 15 - yInTile - 16;
                return new TerrainCheckResult(dist, getAngle(origTile, origDesc, flipAwareAngle), getTileIndex(origDesc));
            }
            int dist = ~yInTile - 16;
            return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
        }
        return createFloorResult(tile, desc, metric, y, prevY, flipAwareAngle);
    }

    private static TerrainCheckResult createFloorResult(SolidTile tile, ChunkDesc desc,
                                                        byte metric, int checkY, int tileY,
                                                        boolean flipAwareAngle) {
        // ROM formula (FindFloor s2.asm:42994-42999):
        // dist = 15 - (metric + (tileY & 0xF)) + (tileY - checkY)
        int yInTile = tileY & 0x0F;
        int dist = 15 - (metric + yInTile) + (tileY - checkY);
        return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
    }

    // ========================================
    // CEILING COLLISION
    // ========================================

    private static TerrainCheckResult checkCeilingDistAtPoint(int x, int y, boolean flipAwareAngle) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();

        TerrainCheckResult foreground = checkCeilingDistAtPoint(lm, (byte) 0, x, y, flipAwareAngle);
        BackgroundPlaneCollisionProvider provider =
                com.openggf.game.GameServices.backgroundPlaneCollisionOrNull();
        if (provider == null) return foreground;
        BackgroundPlaneCollisionProvider.State state = provider.state(lm);
        if (!state.active()) return foreground;
        TerrainCheckResult background = checkCeilingDistAtPoint(
                lm, (byte) 1, provider.backgroundX(state, x, Direction.UP),
                provider.backgroundY(state, y), flipAwareAngle);
        if (!background.foundSurface()) return foreground;
        if (!foreground.foundSurface() || background.distance() <= foreground.distance()) {
            return background;
        }
        return foreground;
    }

    private static TerrainCheckResult checkCeilingDistAtPoint(LevelManager lm, byte layer,
                                                               int x, int y, boolean flipAwareAngle) {

        ChunkDesc desc = lm.getChunkDescAt(layer, x, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL);
        byte metric = getCeilingMetric(tile, desc, y);

        if (metric == 0) {
            return checkCeilingExtension(lm, layer, x, y, flipAwareAngle);
        }

        if (metric == FULL_TILE) {
            TerrainCheckResult edgeResult = checkCeilingEdge(lm, layer, x, y, flipAwareAngle);
            if (edgeResult != null) return edgeResult;
        }

        return createCeilingResult(tile, desc, metric, y, y, flipAwareAngle);
    }

    private static TerrainCheckResult checkCeilingExtension(LevelManager lm, byte layer, int x, int y,
                                                            boolean flipAwareAngle) {
        int prevY = y - 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, prevY);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL);
        byte metric = getCeilingMetric(tile, desc, prevY);

        if (metric > 0) {
            return createCeilingResult(tile, desc, metric, y, prevY, flipAwareAngle);
        }
        return TerrainCheckResult.noCollision();
    }

    private static TerrainCheckResult checkCeilingEdge(LevelManager lm, byte layer, int x, int y,
                                                       boolean flipAwareAngle) {
        int nextY = y + 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, nextY);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL);
        byte metric = getCeilingMetric(tile, desc, nextY);

        if (metric > 0 && metric < FULL_TILE) {
            return createCeilingResult(tile, desc, metric, y, nextY, flipAwareAngle);
        }
        return null;
    }

    private static TerrainCheckResult createCeilingResult(SolidTile tile, ChunkDesc desc,
                                                          byte metric, int checkY, int tileY,
                                                          boolean flipAwareAngle) {
        int tileTop = tileY & ~0x0F;
        int surfaceY = tileTop + metric - 1;
        int dist = checkY - surfaceY;
        return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
    }

    // ========================================
    // WALL COLLISION
    // ========================================

    private static TerrainCheckResult checkWallDistAtPoint(int x, int y, boolean checkingLeft,
                                                           boolean flipAwareAngle) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();
        BackgroundPlaneCollisionProvider provider =
                com.openggf.game.GameServices.backgroundPlaneCollisionOrNull();
        return checkWallDistAtPoint(lm,
                provider != null ? provider : BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                legacySecondaryCollisionPath(), x, y, checkingLeft, flipAwareAngle);
    }

    private static TerrainCheckResult checkWallDistAtPoint(LevelManager lm,
                                                            BackgroundPlaneCollisionProvider provider,
                                                            boolean secondaryCollisionPath,
                                                            int x, int y,
                                                            boolean checkingLeft,
                                                            boolean flipAwareAngle) {

        TerrainCheckResult foreground = checkWallDistAtPoint(
                lm, (byte) 0, x, y, checkingLeft, flipAwareAngle, secondaryCollisionPath);
        BackgroundPlaneCollisionProvider.State state = provider.state(lm);
        if (!state.active()) return foreground;
        Direction direction = checkingLeft ? Direction.LEFT : Direction.RIGHT;
        TerrainCheckResult background = checkWallDistAtPoint(
                lm, (byte) 1, provider.backgroundX(state, x, direction),
                provider.backgroundY(state, y), checkingLeft, flipAwareAngle,
                secondaryCollisionPath);
        if (!background.foundSurface()) return foreground;
        return !foreground.foundSurface() || background.distance() <= foreground.distance()
                ? background : foreground;
    }

    private static TerrainCheckResult checkWallDistAtPoint(LevelManager lm, byte layer,
                                                            int x, int y, boolean checkingLeft,
                                                            boolean flipAwareAngle,
                                                            boolean secondaryCollisionPath) {

        ChunkDesc desc = lm.getChunkDescAt(layer, x, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL, secondaryCollisionPath);
        byte metric = getWallMetric(tile, desc, y, checkingLeft);

        if (metric == 0) {
            return checkWallExtension(lm, layer, x, y, checkingLeft, flipAwareAngle,
                    secondaryCollisionPath);
        }

        if (metric == FULL_TILE) {
            TerrainCheckResult edgeResult = checkWallEdge(
                    lm, layer, tile, desc, x, y, checkingLeft, flipAwareAngle,
                    secondaryCollisionPath);
            if (edgeResult != null) return edgeResult;
        }

        return createWallResult(tile, desc, metric, x, checkingLeft, 0, flipAwareAngle);
    }

    private static TerrainCheckResult checkWallExtension(LevelManager lm, byte layer,
                                                         int x, int y, boolean checkingLeft,
                                                         boolean flipAwareAngle,
                                                         boolean secondaryCollisionPath) {
        // ROM adds 16 to distance when extending (s2.asm:43207)
        int nextX = checkingLeft ? (x - 16) : (x + 16);
        ChunkDesc desc = lm.getChunkDescAt(layer, nextX, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL, secondaryCollisionPath);
        byte metric = getWallMetric(tile, desc, y, checkingLeft);

        if (metric > 0) {
            return createWallResult(tile, desc, metric, x, checkingLeft, 16, flipAwareAngle);
        }
        return TerrainCheckResult.noCollision();
    }

    private static TerrainCheckResult checkWallEdge(LevelManager lm, byte layer,
                                                    SolidTile origTile, ChunkDesc origDesc,
                                                    int x, int y, boolean checkingLeft,
                                                    boolean flipAwareAngle,
                                                    boolean secondaryCollisionPath) {
        // ROM subtracts 16 from distance when checking previous (s2.asm:43264)
        int prevX = checkingLeft ? (x + 16) : (x - 16);
        ChunkDesc desc = lm.getChunkDescAt(layer, prevX, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL, secondaryCollisionPath);
        byte metric = getWallMetric(tile, desc, y, checkingLeft);

        return createPreviousWallResult(tile, desc, origTile, origDesc, metric, x, checkingLeft, flipAwareAngle);
    }

    private static TerrainCheckResult createPreviousWallResult(SolidTile tile, ChunkDesc desc,
                                                               SolidTile origTile, ChunkDesc origDesc,
                                                               byte metric, int x, boolean checkingLeft,
                                                               boolean flipAwareAngle) {
        int xInTile = x & 0x0F;
        int xAdjusted = checkingLeft ? (15 - xInTile) : xInTile;
        if (metric == 0) {
            int dist = 15 - xAdjusted - 16;
            return new TerrainCheckResult(dist, getAngle(origTile, origDesc, flipAwareAngle), getTileIndex(origDesc));
        }
        if (metric < 0) {
            int adjusted = metric + xAdjusted;
            if (adjusted >= 0) {
                int dist = 15 - xAdjusted - 16;
                return new TerrainCheckResult(dist, getAngle(origTile, origDesc, flipAwareAngle), getTileIndex(origDesc));
            }
            int dist = ~xAdjusted - 16;
            return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
        }
        return createWallResult(tile, desc, metric, x, checkingLeft, -16, flipAwareAngle);
    }

    private static TerrainCheckResult createWallResult(SolidTile tile, ChunkDesc desc,
                                                       byte metric, int checkX, boolean checkingLeft, int tileOffset,
                                                       boolean flipAwareAngle) {
        // ROM formula (FindWall s2.asm:43246-43251)
        int xInTile = checkX & 0x0F;
        int dist = checkingLeft
                ? (xInTile - metric) + tileOffset
                : (15 - (metric + xInTile)) + tileOffset;
        return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
    }

    // ========================================
    // METRIC HELPERS
    // ========================================

    private static SolidTile getSolidTile(LevelManager lm, ChunkDesc desc, int solidityBit,
                                          boolean secondaryCollisionPath) {
        if (desc == null || !desc.isSolidityBitSet(solidityBit)) {
            return null;
        }
        return lm.getSolidTileForChunkDesc(desc, solidityBit, secondaryCollisionPath);
    }

    /** Legacy non-object compatibility path. */
    private static SolidTile getSolidTile(LevelManager lm, ChunkDesc desc, int solidityBit) {
        return getSolidTile(lm, desc, solidityBit, legacySecondaryCollisionPath());
    }

    // Compatibility entry point retained for focused ROM-helper tests.
    private static TerrainCheckResult checkWallEdge(LevelManager lm, SolidTile origTile,
                                                     ChunkDesc origDesc, int x, int y,
                                                     boolean checkingLeft,
                                                     boolean flipAwareAngle) {
        return checkWallEdge(lm, (byte) 0, origTile, origDesc, x, y,
                checkingLeft, flipAwareAngle, legacySecondaryCollisionPath());
    }

    private static boolean legacySecondaryCollisionPath() {
        var camera = com.openggf.game.GameServices.cameraOrNull();
        var focused = camera != null ? camera.getFocusedSprite() : null;
        return focused != null && (focused.getTopSolidBit() & 0xFF) != SOLIDITY_TOP;
    }

    private static byte getHeightMetric(SolidTile tile, ChunkDesc desc, int x) {
        if (tile == null) return 0;

        int index = x & 0x0F;
        if (desc != null && desc.getHFlip()) {
            index = 15 - index;
        }

        byte metric = tile.getHeightAt((byte) index);
        if (metric != 0 && metric != FULL_TILE && desc != null && desc.getVFlip()) {
            // ROM: neg.w d0 (s2.asm:42984-42987) - simple negation
            metric = (byte) -metric;
        }
        return metric;
    }

    private static byte getCeilingMetric(SolidTile tile, ChunkDesc desc, int y) {
        if (tile == null) return 0;

        int index = y & 0x0F;
        if (desc != null && desc.getVFlip()) {
            index = 15 - index;
        }

        byte metric = tile.getWidthAt((byte) index);
        if (metric != 0 && metric != FULL_TILE && desc != null && desc.getHFlip()) {
            metric = (byte) (16 - metric);
        }
        return metric;
    }

    private static byte getWallMetric(SolidTile tile, ChunkDesc desc, int y, boolean checkingLeft) {
        if (tile == null) return 0;

        int index = y & 0x0F;
        if (desc != null && desc.getVFlip()) {
            index = 15 - index;
        }

        byte metric = tile.getWidthAt((byte) index);
        boolean hFlip = desc != null && desc.getHFlip();

        // Handle H-flip for opposite side collision
        if (checkingLeft != hFlip && metric != 0 && metric != FULL_TILE) {
            metric = (byte) (16 - metric);
        }
        return metric;
    }

    private static byte getAngle(SolidTile tile, ChunkDesc desc) {
        return getAngle(tile, desc, false);
    }

    private static byte getAngle(SolidTile tile, ChunkDesc desc, boolean flipAwareAngle) {
        if (tile == null) {
            return 0;
        }
        if (!flipAwareAngle) {
            return tile.getAngle();
        }
        boolean hFlip = desc != null && desc.getHFlip();
        boolean vFlip = desc != null && desc.getVFlip();
        return tile.getAngle(hFlip, vFlip);
    }

    private static int getTileIndex(ChunkDesc desc) {
        return desc != null ? desc.getChunkIndex() : -1;
    }
}

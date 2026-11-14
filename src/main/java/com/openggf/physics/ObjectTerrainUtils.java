package com.openggf.physics;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;

/**
 * Terrain collision detection for game objects.
 * Mirrors ROM's ObjCheckFloorDist (s2.asm:44210) for floor/ceiling/wall detection
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
        return checkFloorDistAtPoint(x, y + yRadius, false, (byte) 0);
    }

    /**
     * Check distance to floor from object bottom (x, y + yRadius), returning the
     * ROM-transformed terrain angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkFloorDistWithFlipAwareAngle(int x, int y, int yRadius) {
        return checkFloorDistAtPoint(x, y + yRadius, true, (byte) 0);
    }

    /** Check distance to floor from exact point */
    public static TerrainCheckResult checkFloorDist(int x, int y) {
        return checkFloorDistAtPoint(x, y, false, (byte) 0);
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

    /** Injected explicit-layer adapter; the existing native height-map state machine remains authoritative. */
    public static TerrainCheckResult checkFloorDistOnLayer(LevelManager levelManager,int x,int y,
            byte layer,boolean secondaryCollisionPath,boolean flipAwareAngle) {
        if(layer!=0&&layer!=1)throw new IllegalArgumentException("Collision layer must be foreground or background");
        if(levelManager==null)return TerrainCheckResult.noCollision();
        return checkFloorDistAtPoint(levelManager,layer,x,y,flipAwareAngle,secondaryCollisionPath);
    }

    /**
     * Check floor distance against an explicit level layout layer. ROM helpers
     * such as S3K {@code Ring_FindFloor} run the same height-map state machine
     * against both foreground and background collision layouts.
     */
    public static TerrainCheckResult checkFloorDistOnLayer(int x, int y, byte layer) {
        LevelManager levelManager = com.openggf.game.GameServices.levelOrNull();
        if (levelManager == null) return TerrainCheckResult.noCollision();
        return checkFloorDistAtPoint(
                levelManager, layer, x, y, false, legacySecondaryCollisionPath());
    }

    /**
     * Check distance to floor from exact point, returning the ROM-transformed terrain
     * angle after chunk H/V flip handling.
     */
    public static TerrainCheckResult checkFloorDistWithFlipAwareAngle(int x, int y) {
        return checkFloorDistAtPoint(x, y, true, (byte) 0);
    }

    /**
     * Game-internal player FindFloor entry (Sonic_CheckFloor, skdisasm044:19875..19927).
     * The caller supplies the querying player's live bit and collision-index path;
     * object entry points retain their own fixed solidity and legacy camera defaults.
     */
    public static TerrainCheckResult checkPlayerFloorDistWithFlipAwareAngle(
            int x, int y, int solidityBit, boolean secondaryCollisionPath) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();
        BackgroundPlaneCollisionProvider provider =
                com.openggf.game.GameServices.backgroundPlaneCollisionOrNull();
        return checkFloorDistAtPoint(lm,
                provider != null ? provider : BackgroundPlaneCollisionProvider.FOREGROUND_ONLY,
                secondaryCollisionPath, x, y, true, solidityBit);
    }

    /**
     * Player Sonic_CheckCeiling (20278..20311), also used by reverse sub_11FD6.
     * D5 remains the caller's live LRB bit through the existing foreground scan.
     */
    public static TerrainCheckResult checkPlayerCeilingDistWithFlipAwareAngle(
            int x, int y, int yRadius, int solidityBit, boolean secondaryCollisionPath) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();
        return checkNativeUpwardCeilingDistAtPoint(lm, (byte) 0, x, y - yRadius,
                true, solidityBit, secondaryCollisionPath);
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
        return checkNativeUpwardCeilingDistAtPoint(x, y - yRadius, true);
    }

    /**
     * Check distance to the ceiling through the S3K upward FindFloor entry.
     */
    public static TerrainCheckResult checkNativeUpwardCeilingDist(int x, int y, int yRadius) {
        return checkNativeUpwardCeilingDistAtPoint(x, y - yRadius, false);
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

    private static TerrainCheckResult checkFloorDistAtPoint(
            int x, int y, boolean flipAwareAngle, byte layer) {
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
        return checkFloorDistAtPoint(lm, provider, secondaryCollisionPath,
                x, y, flipAwareAngle, SOLIDITY_TOP);
    }

    private static TerrainCheckResult checkFloorDistAtPoint(LevelManager lm,
                                                             BackgroundPlaneCollisionProvider provider,
                                                             boolean secondaryCollisionPath,
                                                             int x, int y,
                                                             boolean flipAwareAngle, int solidityBit) {
        TerrainCheckResult foreground = checkFloorDistAtPoint(
                lm, (byte) 0, x, y, flipAwareAngle, secondaryCollisionPath, solidityBit);
        BackgroundPlaneCollisionProvider.State state = provider.state(lm);
        if (!state.active()) return foreground;
        TerrainCheckResult background = checkFloorDistAtPoint(
                lm, (byte) 1, provider.backgroundX(state, x, Direction.DOWN),
                provider.backgroundY(state, y), flipAwareAngle, secondaryCollisionPath, solidityBit);
        if (!background.foundSurface()) return foreground;
        if (!foreground.foundSurface() || background.distance() <= foreground.distance()) {
            return background;
        }
        return foreground;
    }

    private static TerrainCheckResult checkFloorDistAtPoint(LevelManager lm, byte layer,
                                                             int x, int y, boolean flipAwareAngle,
                                                             boolean secondaryCollisionPath) {
        return checkFloorDistAtPoint(lm, layer, x, y, flipAwareAngle,
                secondaryCollisionPath, SOLIDITY_TOP);
    }

    private static TerrainCheckResult checkFloorDistAtPoint(LevelManager lm, byte layer,
                                                             int x, int y, boolean flipAwareAngle,
                                                             boolean secondaryCollisionPath, int solidityBit) {

        ChunkDesc desc = lm.getChunkDescAt(layer, x, y);
        SolidTile tile = getSolidTile(lm, desc, solidityBit, secondaryCollisionPath);
        byte metric = getFloorHeightMetric(lm, tile, desc, x);
        if (metric == 0) {
            // No surface - extend 16 pixels down
            return checkFloorExtension(lm, layer, tile, desc, x, y, flipAwareAngle, secondaryCollisionPath, solidityBit);
        }

        if (metric < 0 && negativeFloorIsFlatFull(lm)) return createFlatFullFloorResult(desc, y, y);

        // ROM: neg.w produces negative metric for V-flipped tiles.
        // FindFloor handles this via the negative metric path (s2.asm:43406-43422).
        if (metric < 0) {
            int yInTile = y & 0x0F;
            int adjusted = metric + yInTile;
            if (adjusted >= 0) {
                // No collision in this tile - extend to next tile
                return checkFloorExtension(lm, layer, tile, desc, x, y, flipAwareAngle, secondaryCollisionPath, solidityBit);
            }
            // Collision found - regress to previous tile
            return checkFloorRegress(lm, layer, tile, desc, x, y, flipAwareAngle, secondaryCollisionPath, solidityBit);
        }

        if (metric == FULL_TILE) {
            // Full tile - check previous tile up for edge detection
            TerrainCheckResult edgeResult = checkFloorEdge(
                    lm, layer, tile, desc, x, y, flipAwareAngle, secondaryCollisionPath, solidityBit);
            if (edgeResult != null) return edgeResult;
        }

        return createFloorResult(tile, desc, metric, y, y, flipAwareAngle);
    }

    private static TerrainCheckResult checkFloorExtension(LevelManager lm, byte layer,
                                                           SolidTile firstTile, ChunkDesc firstDesc,
                                                           int x, int y, boolean flipAwareAngle,
                                                           boolean secondaryCollisionPath, int solidityBit) {
        int nextY = y + 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, nextY);
        SolidTile tile = getSolidTile(lm, desc, solidityBit, secondaryCollisionPath);
        byte metric = getFloorHeightMetric(lm, tile, desc, x);

        if (metric > 0) {
            return createFloorResult(tile, desc, metric, y, nextY, flipAwareAngle);
        }
        if (metric < 0 && negativeFloorIsFlatFull(lm)) return createFlatFullFloorResult(desc, y, nextY);
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
        if (com.openggf.game.internal.ObjectFloorProbeBlankBlocks.forHost(lm.getGameModule())
                == com.openggf.game.internal.ObjectFloorProbeBlankBlocks.NEXT_BLOCK_DISTANCE) {
            // FindLevelFloor2 .IsBlank returns $F - (y & $F) and FindLevelFloor adds $10
            // (scddisasm src/Level/Collision Floor.asm:697-702, 788-793). Each solid block read
            // writes its angle first (.IsSolid, :704-711, 795-802), so the angle is the last
            // solid block's, else the caller's preset 0 (ObjGetFloorDist, Collision Check.asm:166-167).
            SolidTile angleTile = tile != null ? tile : firstTile;
            ChunkDesc angleDesc = tile != null ? desc : firstDesc;
            return new TerrainCheckResult(0x1F - (y & 0x0F), getAngle(angleTile, angleDesc, flipAwareAngle),
                    angleTile != null ? getTileIndex(angleDesc) : -1);
        }
        return TerrainCheckResult.noCollision();
    }

    /** Regress to previous tile when negative metric indicates collision from below */
    private static TerrainCheckResult checkFloorRegress(LevelManager lm, byte layer, SolidTile origTile,
                                                         ChunkDesc origDesc, int x, int y,
                                                         boolean flipAwareAngle,
                                                         boolean secondaryCollisionPath, int solidityBit) {
        int prevY = y - 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, prevY);
        SolidTile tile = getSolidTile(lm, desc, solidityBit, secondaryCollisionPath);
        byte metric = getFloorHeightMetric(lm, tile, desc, x);

        return createPreviousFloorResult(tile, desc, origTile, origDesc, metric, x, y, prevY, flipAwareAngle);
    }

    // Compatibility entry point retained for focused ROM-helper tests.
    private static TerrainCheckResult checkFloorRegress(LevelManager lm, SolidTile origTile,
                                                         ChunkDesc origDesc, int x, int y,
                                                         boolean flipAwareAngle) {
        return checkFloorRegress(lm, (byte) 0, origTile, origDesc, x, y, flipAwareAngle,
                legacySecondaryCollisionPath(), SOLIDITY_TOP);
    }

    private static TerrainCheckResult checkFloorEdge(LevelManager lm, byte layer,
                                                     SolidTile origTile, ChunkDesc origDesc,
                                                     int x, int y, boolean flipAwareAngle,
                                                     boolean secondaryCollisionPath, int solidityBit) {
        int prevY = y - 16;
        ChunkDesc desc = lm.getChunkDescAt(layer, x, prevY);
        SolidTile tile = getSolidTile(lm, desc, solidityBit, secondaryCollisionPath);
        byte metric = getFloorHeightMetric(lm, tile, desc, x);
        // .MaxFloor keeps a3 = $10, so FindLevelFloor2's .CheckNegFloor applies to the tile above.
        if (metric < 0 && negativeFloorIsFlatFull(lm)) return createFlatFullFloorResult(desc, y, prevY);

        return createPreviousFloorResult(tile, desc, origTile, origDesc, metric, x, y, prevY, flipAwareAngle);
    }

    // Compatibility entry point retained for focused ROM-helper tests.
    private static TerrainCheckResult checkFloorEdge(LevelManager lm, SolidTile origTile,
                                                      ChunkDesc origDesc, int x, int y,
                                                      boolean flipAwareAngle) {
        return checkFloorEdge(lm, (byte) 0, origTile, origDesc, x, y, flipAwareAngle,
                legacySecondaryCollisionPath(), SOLIDITY_TOP);
    }

    private static TerrainCheckResult createPreviousFloorResult(SolidTile tile, ChunkDesc desc,
                                                                SolidTile origTile, ChunkDesc origDesc,
                                                                byte metric, int x, int y, int prevY,
                                                                boolean flipAwareAngle) {
        int yInTile = y & 0x0F;
        if (metric == 0) {
            int dist = 15 - yInTile - 16;
            return createFloorRegressDefaultResult(
                    tile, desc, origTile, origDesc, dist, flipAwareAngle);
        }
        if (metric < 0) {
            int adjusted = metric + yInTile;
            if (adjusted >= 0) {
                int dist = 15 - yInTile - 16;
                return createFloorRegressDefaultResult(
                        tile, desc, origTile, origDesc, dist, flipAwareAngle);
            }
            int dist = ~yInTile - 16;
            return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
        }
        return createFloorResult(tile, desc, metric, y, prevY, flipAwareAngle);
    }

    private static TerrainCheckResult createFloorRegressDefaultResult(
            SolidTile tile, ChunkDesc desc, SolidTile origTile, ChunkDesc origDesc,
            int distance, boolean flipAwareAngle) {
        // ROM sub_F30C writes the prior tile's angle before sampling its height.
        // Only a missing collision shape leaves the original tile's angle intact.
        if (tile != null) {
            return new TerrainCheckResult(
                    distance, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
        }
        return new TerrainCheckResult(
                distance, getAngle(origTile, origDesc, flipAwareAngle), getTileIndex(origDesc));
    }

    /**
     * Sonic CD's {@code FindLevelFloor}/{@code FindLevelFloor2} {@code .CheckNegFloor}: a downward
     * probe ({@code cmpa.w #$10,a3}) that meets a negative height forces height $10 and angle 0 and
     * takes {@code .FoundFloor} (scddisasm src/Level/Collision Floor.asm:754-759, 843-848).
     */
    private static boolean negativeFloorIsFlatFull(LevelManager lm) {
        return lm != null && com.openggf.game.internal.FloorProbeNegativeHeight.forHost(lm.getGameModule())
                == com.openggf.game.internal.FloorProbeNegativeHeight.FULL_HEIGHT_FLAT;
    }

    /**
     * The floor probe's height: {@link #getHeightMetric}, except that on a
     * {@code .CheckNegFloor} host a vertically flipped full column is the negative $F0 the
     * ROM's {@code neg.w d0} makes. Elsewhere +16 and -16 take the same branches
     * ({@code .maxfloor}; FindFloor2's {@code not.w} equals 15 - (y + 16)), so the sign is kept.
     */
    private static byte getFloorHeightMetric(LevelManager lm, SolidTile tile, ChunkDesc desc, int x) {
        byte metric = getHeightMetric(tile, desc, x);
        if (metric == FULL_TILE && desc != null && desc.getVFlip() && negativeFloorIsFlatFull(lm)) {
            return (byte) -FULL_TILE;
        }
        return metric;
    }

    private static TerrainCheckResult createFlatFullFloorResult(ChunkDesc desc, int checkY, int tileY) {
        int dist = 15 - (FULL_TILE + (tileY & 0x0F)) + (tileY - checkY);
        return new TerrainCheckResult(dist, (byte) 0, getTileIndex(desc));
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

    private static TerrainCheckResult checkNativeUpwardCeilingDistAtPoint(
            int x, int y, boolean flipAwareAngle) {
        LevelManager lm = com.openggf.game.GameServices.levelOrNull();
        if (lm == null) return TerrainCheckResult.noCollision();

        return checkNativeUpwardCeilingDistAtPoint(lm, x, y, flipAwareAngle);
    }

    /** Native ObjCheckCeilingDist with an injected level owner and object radii. */
    public static TerrainCheckResult checkNativeUpwardCeilingDist(LevelManager levelManager,
                                                                 int x, int y, int yRadius) {
        if (levelManager == null) return TerrainCheckResult.noCollision();
        return checkNativeUpwardCeilingDistAtPoint(levelManager, x, y - yRadius, false);
    }

    /**
     * Native ObjCheckCeilingDist on an explicit collision path: {@code FindFloor} entered at
     * {@code (y - yRadius) ^ $F} with {@code a3 = -$10}, {@code d6 = $1000} and the all-sides solidity
     * bit (Sonic CD {@code ObjGetCeilDist}/{@code CheckBlockUp}, scddisasm src/Level/Collision
     * Check.asm:318-339, src/common/object_block_collide.asm:301-327), as the explicit-path wall
     * probes take it, instead of the focused player's path.
     */
    public static TerrainCheckResult checkNativeUpwardCeilingDist(LevelManager levelManager,
                                                                 boolean secondaryCollisionPath,
                                                                 int x, int y, int yRadius) {
        if (levelManager == null) return TerrainCheckResult.noCollision();
        return checkNativeUpwardCeilingDistAtPoint(levelManager, (byte) 0, x, y - yRadius, false,
                SOLIDITY_ALL, secondaryCollisionPath);
    }

    // Internal authoring probes use explicit owners/path selection. Native callers
    // retain the existing entry points and their shipped arithmetic unchanged.
    static TerrainCheckResult routeCeilingDist(LevelManager lm, int x, int y, boolean secondary) {
        TerrainCheckResult current = scanCeilingTile(lm, (byte) 0, x, y, y, false, true, SOLIDITY_ALL, secondary);
        if (current != null) return current;
        TerrainCheckResult extension = scanCeilingTile(lm, (byte) 0, x, y, y - FULL_TILE, true, true, SOLIDITY_ALL, secondary);
        return extension != null ? extension : TerrainCheckResult.noCollision();
    }
    static TerrainCheckResult routeWallDist(LevelManager lm, int x, int y, boolean secondary) {
        return checkWallDistAtPoint(lm, (byte) 0, x, y, false, true, secondary);
    }

    private static TerrainCheckResult checkNativeUpwardCeilingDistAtPoint(
            LevelManager lm, int x, int y, boolean flipAwareAngle) {
        return checkNativeUpwardCeilingDistAtPoint(lm, (byte) 0, x, y, flipAwareAngle,
                SOLIDITY_ALL, legacySecondaryCollisionPath());
    }

    private static TerrainCheckResult checkNativeUpwardCeilingDistAtPoint(
            LevelManager lm, byte layer, int x, int y, boolean flipAwareAngle,
            int solidityBit, boolean secondaryCollisionPath) {
        TerrainCheckResult current = scanCeilingTile(lm, layer, x, y, y, false,
                flipAwareAngle, solidityBit, secondaryCollisionPath);
        if (current != null) {
            return current;
        }
        TerrainCheckResult extension = scanCeilingTile(
                lm, layer, x, y, y - FULL_TILE, true, flipAwareAngle,
                solidityBit, secondaryCollisionPath);
        return extension != null ? extension : TerrainCheckResult.noCollision();
    }

    /**
     * Mirrors FindFloor/FindFloor2 as entered by the native upward probe. The
     * caller's EOR #$F changes the low-nibble arithmetic, while collision data
     * still comes from the height map indexed by X (not the rotated width map).
     */
    private static TerrainCheckResult scanCeilingTile(
            LevelManager lm, byte layer, int x, int originalY, int checkY,
            boolean extension, boolean flipAwareAngle,
            int solidityBit, boolean secondaryCollisionPath) {
        ChunkDesc desc = lm.getChunkDescAt(layer, x, checkY);
        SolidTile tile = getSolidTile(lm, desc, solidityBit, secondaryCollisionPath);
        if (tile == null) {
            return null;
        }

        int metric = getCeilingHeightMetric(tile, desc, x);
        if (metric == 0) {
            return extension
                    ? createCeilingResult(tile, desc, 0, originalY, checkY, flipAwareAngle)
                    : null;
        }

        if (metric < 0) {
            int mirroredYInTile = (originalY ^ 0x0F) & 0x0F;
            if (metric + mirroredYInTile >= 0) {
                return extension
                        ? createCeilingResult(tile, desc, 0, originalY, checkY, flipAwareAngle)
                        : null;
            }
            if (extension) {
                return new TerrainCheckResult(
                        ~mirroredYInTile + FULL_TILE,
                        getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
            }

            int regressY = checkY + FULL_TILE;
            TerrainCheckResult regress = scanCeilingTile(
                    lm, layer, x, originalY, regressY, true, flipAwareAngle,
                    solidityBit, secondaryCollisionPath);
            if (regress != null) {
                return regress;
            }
            return createCeilingResult(tile, desc, 0, originalY, regressY, flipAwareAngle);
        }

        if (metric == FULL_TILE && !extension) {
            int regressY = checkY + FULL_TILE;
            TerrainCheckResult regress = scanCeilingTile(
                    lm, layer, x, originalY, regressY, true, flipAwareAngle,
                    solidityBit, secondaryCollisionPath);
            if (regress != null) {
                return regress;
            }
            return createCeilingResult(tile, desc, 0, originalY, regressY, flipAwareAngle);
        }

        return createCeilingResult(tile, desc, metric, originalY, checkY, flipAwareAngle);
    }

    private static TerrainCheckResult createCeilingResult(
            SolidTile tile, ChunkDesc desc, int metric,
            int originalY, int tileY, boolean flipAwareAngle) {
        int surfaceY = (tileY & ~0x0F) + metric;
        return new TerrainCheckResult(
                originalY - surfaceY,
                getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
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
        if (checkingLeft && com.openggf.game.internal.ObjectLeftWallProbeEntry.forHost(lm.getGameModule())
                == com.openggf.game.internal.ObjectLeftWallProbeEntry.RAW) {
            // The wall read below models the eori.w #$F the other games' left probe applies;
            // a host whose probe passes X unflipped (Sonic CD's ObjGetLWallDist / CheckBlockLeft,
            // scddisasm src/Level/Collision Check.asm:401-409, FixBugs = 0) undoes it here.
            x ^= 0x0F;
        }

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
        if (com.openggf.game.internal.ObjectWallProbeWidth.forHost(lm.getGameModule())
                == com.openggf.game.internal.ObjectWallProbeWidth.NEGATE) {
            return signedWidthWallDist(lm, layer, x, y, checkingLeft, flipAwareAngle, secondaryCollisionPath);
        }

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

    /**
     * {@code FindLevelWall}/{@code FindLevelWall2} with the signed width (scddisasm
     * src/Level/Collision Floor.asm:874-1028), entered as the object probes enter it: the right
     * probe at X with {@code a3 = $10}, {@code d6 = 0}; the left probe at {@code X ^ $F} with
     * {@code a3 = -$10}, {@code d6 = $800}, which flips the H-flip test. A flipped width is negated;
     * zero or a blank block reads the next block (+16), a full width or a probe inside a negative
     * width regresses to the previous block (-16), and the regress block's negative width returns
     * {@code ~(x & $F)} (:1021-1027). The angle is the last solid block's, as each read writes it.
     */
    private static TerrainCheckResult signedWidthWallDist(LevelManager lm, byte layer, int x, int y,
                                                          boolean checkingLeft, boolean flipAwareAngle,
                                                          boolean secondaryCollisionPath) {
        int probeX = checkingLeft ? x ^ 0x0F : x;
        int step = checkingLeft ? -FULL_TILE : FULL_TILE;
        SignedWidthRead first = signedWidthRead(lm, layer, probeX, y, checkingLeft, secondaryCollisionPath);
        int xInTile = probeX & 0x0F;
        int width = first.width();
        SignedWidthRead last = first;
        int distance;
        if (width == 0 || width < 0 && xInTile + width >= 0) {
            last = signedWidthRead(lm, layer, probeX + step, y, checkingLeft, secondaryCollisionPath);
            distance = signedWidthNextBlock(last, (probeX + step) & 0x0F) + FULL_TILE;
            if (last.tile() == null) last = first;
        } else if (width < 0 || width == FULL_TILE) {
            last = signedWidthRead(lm, layer, probeX - step, y, checkingLeft, secondaryCollisionPath);
            distance = signedWidthNextBlock(last, (probeX - step) & 0x0F) - FULL_TILE;
            if (last.tile() == null) last = first;
        } else {
            distance = 15 - (xInTile + width);
        }
        if (last.tile() == null) {
            return new TerrainCheckResult(distance, (byte) 0, -1);
        }
        return new TerrainCheckResult(distance, getAngle(last.tile(), last.desc(), flipAwareAngle),
                getTileIndex(last.desc()));
    }

    /** {@code FindLevelWall2}: the distance from the next or previous block, with no further step. */
    private static int signedWidthNextBlock(SignedWidthRead read, int xInTile) {
        int width = read.width();
        if (width > 0) {
            return 15 - (xInTile + width);
        }
        if (width < 0 && xInTile + width < 0) {
            return ~xInTile;
        }
        return 15 - xInTile;
    }

    private record SignedWidthRead(SolidTile tile, ChunkDesc desc, int width) { }

    /** One block's row width: Y flipped by the block's V-flip, negated when its H-flip differs from the probe's. */
    private static SignedWidthRead signedWidthRead(LevelManager lm, byte layer, int x, int y,
                                                   boolean checkingLeft, boolean secondaryCollisionPath) {
        ChunkDesc desc = lm.getChunkDescAt(layer, x, y);
        SolidTile tile = getSolidTile(lm, desc, SOLIDITY_ALL, secondaryCollisionPath);
        if (tile == null) {
            return new SignedWidthRead(null, desc, 0);
        }
        int row = desc.getVFlip() ? ~y : y;
        int width = tile.getWidthAt((byte) (row & 0x0F));
        if (desc.getHFlip() != checkingLeft) {
            width = -width;
        }
        return new SignedWidthRead(tile, desc, width);
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
            return createWallRegressDefaultResult(
                    tile, desc, origTile, origDesc, dist, flipAwareAngle);
        }
        if (metric < 0) {
            int adjusted = metric + xAdjusted;
            if (adjusted >= 0) {
                int dist = 15 - xAdjusted - 16;
                return createWallRegressDefaultResult(
                        tile, desc, origTile, origDesc, dist, flipAwareAngle);
            }
            int dist = ~xAdjusted - 16;
            return new TerrainCheckResult(dist, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
        }
        return createWallResult(tile, desc, metric, x, checkingLeft, -16, flipAwareAngle);
    }

    private static TerrainCheckResult createWallRegressDefaultResult(
            SolidTile tile, ChunkDesc desc, SolidTile origTile, ChunkDesc origDesc,
            int distance, boolean flipAwareAngle) {
        // ROM sub_F584 writes the prior tile's angle before sampling its width.
        // Only a missing collision shape leaves the original tile's angle intact.
        if (tile != null) {
            return new TerrainCheckResult(
                    distance, getAngle(tile, desc, flipAwareAngle), getTileIndex(desc));
        }
        return new TerrainCheckResult(
                distance, getAngle(origTile, origDesc, flipAwareAngle), getTileIndex(origDesc));
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

    private static int getCeilingHeightMetric(SolidTile tile, ChunkDesc desc, int x) {
        if (tile == null) return 0;

        int index = x & 0x0F;
        if (desc != null && desc.getHFlip()) {
            index = 15 - index;
        }

        int metric = tile.getHeightAt((byte) index);
        if (metric != 0 && metric != FULL_TILE
                && (desc == null || !desc.getVFlip())) {
            metric = -metric;
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

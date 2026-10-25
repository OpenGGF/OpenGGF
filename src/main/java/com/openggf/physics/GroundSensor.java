package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.ModApi;
import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.sprites.SensorConfiguration;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Sensor implementation for ground/floor collision detection.
 * Handles both vertical (floor/ceiling) and horizontal (wall) scanning
 * with ground mode rotation for slopes.
 */
@ModApi
public class GroundSensor extends Sensor {

    /**
     * Optional override for tests. When non-null, getLevelManager() returns
     * this instead of the singleton. Pass null to revert.
     */
    private static LevelManager overrideLevelManager;

    // Native helpers can retain Collision_addr independently of their live d5 bit.
    // Ordinary sensors keep the existing bit-derived path when this is null.
    private final Boolean secondaryCollisionPathOverride;

    // Full-height tile constant
    private static final byte FULL_TILE = 16;

    // Default flagged angle for missing tiles (ROM: odd angles trigger cardinal snap)
    private static final byte FLAGGED_ANGLE = 0x03;

    private static final Runnable UNBOUNDED_SCAN = () -> {} ;
    private final NativePlayerTerrainScan verticalScan = new NativePlayerTerrainScan(this::getSolidTile);

    private final SensorResult reusableResult = new SensorResult();
    private final SensorResult bgScanResult = new SensorResult();
    private final SensorResult bgWallScanResult = new SensorResult();
    private final WallScanResult wallResult1 = new WallScanResult();
    private final WallScanResult wallResult2 = new WallScanResult();

    /**
     * Inject a mock LevelManager for unit tests. Pass null to revert
     * to the default runtime level manager.
     */
    public static void setLevelManager(LevelManager lm) {
        overrideLevelManager = lm;
    }

    private LevelManager getLevelManager() {
        if (com.openggf.sprites.playable.PlayableRuntimeContext.isBound(sprite)) return sprite.currentLevelManager();
        LevelManager override = overrideLevelManager;
        if (override != null) return override;
        return GameServices.level();
    }

    public GroundSensor(AbstractPlayableSprite sprite, Direction direction, byte x, byte y, boolean active) {
        super(sprite, direction, x, y, active);
        secondaryCollisionPathOverride = null;
    }

    GroundSensor(AbstractPlayableSprite sprite, Direction direction, byte x, byte y,
                 boolean active, boolean secondaryCollisionPath) {
        super(sprite, direction, x, y, active);
        secondaryCollisionPathOverride = secondaryCollisionPath;
    }

    @Override
    protected SensorResult doScan(short dx, short dy) {
        if (!active) {
            return null;
        }

        LevelManager levelManager = getLevelManager();
        SensorConfiguration config = SpriteManager.getSensorConfigurationForGroundModeAndDirection(
                sprite.getGroundMode(), direction);
        Direction globalDirection = config.direction();

        computeRotatedOffset();
        short originalX = (short) (sprite.getCentreX() + getRotatedX() + dx);
        short originalY = (short) (sprite.getCentreY() + getRotatedY() + dy);

        // Solidity bit selection must match the ROM's per-routine d5 register:
        //   - Ground attachment (direction=DOWN): topSolidBit in ALL modes.
        //     ROM: Player_AnglePos loads d5=top_solid_bit ONCE (line 18735)
        //     and passes it to WalkSpeed, WalkVertL, WalkCeiling, WalkVertR.
        //   - Ceiling sensors (direction=UP): lrbSolidBit (ceiling HIT, not attachment)
        //   - Push sensors (direction=LEFT/RIGHT): lrbSolidBit (CalcRoomInFront)
        int solidityBit = (direction == Direction.DOWN)
                ? sprite.getTopSolidBit()
                : sprite.getLrbSolidBit();

        SensorResult fgResult;
        if (config.vertical()) {
            boolean mirrorEmptyDefault = direction == Direction.DOWN && globalDirection == Direction.UP;
            fgResult = scanVertical(levelManager, originalX, originalY, solidityBit, globalDirection, mirrorEmptyDefault);
        } else {
            fgResult = scanHorizontal(levelManager, originalX, originalY, solidityBit, globalDirection);
        }

        // ROM: Background_collision_flag dual-path scan (FindFloor/FindWall/Ring_FindFloor).
        // When the flag is set, scan FG first, then BG (layer 1) with coordinates adjusted
        // by Camera_X_diff/Camera_Y_diff. Keep the result with the greater distance
        // (more lenient collision = less penetration).
        BackgroundPlaneCollisionProvider provider = com.openggf.sprites.playable.PlayableRuntimeContext.background(sprite);
        BackgroundPlaneCollisionProvider.State collisionState = provider != null
                ? provider.state(levelManager) : BackgroundPlaneCollisionProvider.State.INACTIVE;
        if (collisionState.active()) {
            SensorResult bgResult = scanBackgroundCollision(
                    levelManager, originalX, originalY, solidityBit, globalDirection, config.vertical(),
                    provider, collisionState);
            // ROM: FindFloor/FindWall keep the BG result unless the FG distance is
            // strictly smaller. When FG wins, the angle register is restored from
            // Primary_Angle_save; when BG wins, both distance and angle come from
            // the BG scan.
            return combineDualPlaneResults(fgResult, bgResult);
        }

        return fgResult;
    }

    /**
     * Scan using explicit world-space offsets and direction instead of deriving them
     * from the sprite's current ground mode. This is used for ROM-accurate helper
     * routines like {@code CalcRoomInFront}, which probe from predicted world
     * coordinates rather than reusing the rotated push sensor layout.
     */
    SensorResult scanWorld(Direction globalDirection,
                           short worldOffsetX,
                           short worldOffsetY,
                           short dx,
                           short dy,
                           int solidityBit) {
        LevelManager levelManager = getLevelManager();
        short originalX = (short) (sprite.getCentreX() + worldOffsetX + dx);
        short originalY = (short) (sprite.getCentreY() + worldOffsetY + dy);
        SensorResult foreground;
        if (globalDirection == Direction.UP || globalDirection == Direction.DOWN) {
            foreground = scanVertical(levelManager, originalX, originalY, solidityBit, globalDirection, false);
        } else {
            foreground = scanHorizontal(levelManager, originalX, originalY, solidityBit, globalDirection);
        }
        BackgroundPlaneCollisionProvider provider = com.openggf.sprites.playable.PlayableRuntimeContext.background(sprite);
        BackgroundPlaneCollisionProvider.State collisionState = provider != null
                ? provider.state(levelManager) : BackgroundPlaneCollisionProvider.State.INACTIVE;
        if (!collisionState.active()) {
            return foreground;
        }
        SensorResult background = scanBackgroundCollision(levelManager, originalX, originalY,
                solidityBit, globalDirection,
                globalDirection == Direction.UP || globalDirection == Direction.DOWN,
                provider, collisionState);
        return combineDualPlaneResults(foreground, background);
    }

    /**
     * Apply FindFloor/FindWall's foreground-save, background-scan, and optional
     * foreground-restore semantics to the selected pooled result.
     *
     * <p>The returned distance/tile still belongs to the winning plane. The
     * attached primitive write trace separately preserves every mutation of
     * the caller-provided angle byte. This distinction matters when FG writes
     * an angle, BG returns an equal-distance empty result, and BG therefore
     * wins without overwriting the earlier FG angle.</p>
     */
    private static SensorResult combineDualPlaneResults(SensorResult foreground,
                                                        SensorResult background) {
        boolean foregroundWritten = foreground != null && foreground.foregroundAngleWritten();
        byte foregroundAngle = foreground != null ? foreground.foregroundAngle() : 0;
        boolean backgroundWritten = background != null && background.foregroundAngleWritten();
        byte backgroundAngle = background != null ? background.foregroundAngle() : 0;

        // Native compare: BG wins on equality (ble); FG is restored only when
        // its saved distance is strictly smaller.
        boolean backgroundWins = background != null
                && (foreground == null || background.distance() <= foreground.distance());
        SensorResult selected = backgroundWins ? background : foreground;
        if (selected == null) {
            return null;
        }
        return selected.setNativeAngleWriteTrace(
                foregroundWritten,
                foregroundAngle,
                true,
                backgroundWritten,
                backgroundAngle,
                !backgroundWins);
    }

    // ========================================
    // BACKGROUND COLLISION (dual-path scan)
    // ========================================

    /**
     * Check whether Background_collision_flag is active.
     * ROM: tst.b (Background_collision_flag).w at the top of FindFloor/FindWall.
     */
    /**
     * Scan against the background (layer 1) collision data.
     * ROM: FindFloor/FindWall with {@code lea (Find_Tile_BG).l,a5} and coordinate
     * adjustment by Camera_X_diff / Camera_Y_diff.
     *
     * <p>Camera_X_diff = Camera_X_pos_copy - Camera_X_pos_BG_copy
     * Camera_Y_diff = Camera_Y_pos_copy - Camera_Y_pos_BG_copy
     * The BG scan subtracts these diffs from the probe coordinates to convert
     * from FG world space to BG world space, then adds them back after scanning.
     *
     * @return the BG scan result, or null if no BG collision found
     */
    private SensorResult scanBackgroundCollision(LevelManager lm, short fgX, short fgY,
                                                  int solidityBit,
                                                  Direction globalDirection,
                                                  boolean vertical) {
        BackgroundPlaneCollisionProvider provider = com.openggf.sprites.playable.PlayableRuntimeContext.background(sprite);
        if (provider == null) return null;
        BackgroundPlaneCollisionProvider.State state = provider.state(lm);
        // The production callers gate this helper on state.active() before
        // entering it. Keep the scan primitive independently usable with a
        // zero camera delta so focused collision-state-machine tests exercise
        // the same layer-1 FindFloor/FindWall path without manufacturing a
        // gameplay event flag.
        if (!state.active()) {
            state = new BackgroundPlaneCollisionProvider.State(true, 0, 0);
        }
        return scanBackgroundCollision(lm, fgX, fgY, solidityBit, globalDirection, vertical,
                provider, state);
    }

    private SensorResult scanBackgroundCollision(LevelManager lm, short fgX, short fgY,
                                                  int solidityBit, Direction globalDirection,
                                                  boolean vertical,
                                                  BackgroundPlaneCollisionProvider provider,
                                                  BackgroundPlaneCollisionProvider.State state) {
        if (lm == null || !state.active()) return null;
        short bgX = (short) provider.backgroundX(state, fgX, globalDirection);
        short bgY = (short) provider.backgroundY(state, fgY);
        var currentLevel = lm.getCurrentLevel();
        if (currentLevel != null && !currentLevel.hasBackgroundCollisionRowAt(bgY)) {
            return null;
        }

        // Perform the scan using the BG-adjusted coordinates. Do not bail out
        // early on an empty first tile: the ROM still runs the extension pass
        // against BG collision data exactly like the FG path.
        if (vertical) {
            return scanVerticalBg(lm, bgX, bgY, solidityBit, globalDirection);
        } else {
            return scanHorizontalBg(lm, bgX, bgY, solidityBit, globalDirection);
        }
    }

    /**
     * Vertical scan against BG layer tiles. Simplified version of scanVertical
     * that reads from layer 1 instead of layer 0.
     */
    private SensorResult scanVerticalBg(LevelManager lm, short x, short y, int solidityBit, Direction direction) {
        // FindFloor/FindCeiling run the same height-metric state machine after
        // Find_Tile_BG selects layer 1. In particular, negative and full-height
        // metrics can regress into the opposite adjacent tile before returning
        // a distance; a simplified current/extension-only scan is 16 pixels off
        // at those boundaries (HCZ2 moving-wall ceiling).
        return scanVerticalLayer(lm, x, y, solidityBit, direction, false,
                (byte) 1, false, bgScanResult);
    }

    /**
     * Horizontal scan against BG layer tiles.
     */
    private SensorResult scanHorizontalBg(LevelManager lm, short x, short y, int solidityBit, Direction direction) {
        return scanHorizontalLayer(lm, x, y, solidityBit, direction,
                (byte) 1, false, bgWallScanResult);
    }

    // ========================================
    // VERTICAL SCANNING (Floor/Ceiling)
    // ========================================

    private SensorResult scanVertical(LevelManager lm, short x, short y, int solidityBit, Direction direction,
                                      boolean mirrorEmptyDefault) {
        return scanVerticalLayer(lm, x, y, solidityBit, direction, mirrorEmptyDefault,
                (byte) 0, sprite.isLoopLowPlane(), reusableResult);
    }

    private SensorResult scanVerticalLayer(LevelManager lm, short x, short y, int solidityBit,
                                           Direction direction, boolean mirrorEmptyDefault,
                                           byte layer, boolean loopLowPlane, SensorResult resultTarget) {
        return verticalScan.scan(lm, x, y, solidityBit, direction, mirrorEmptyDefault,
                layer, loopLowPlane, resultTarget, collisionLayoutYMask(), UNBOUNDED_SCAN);
    }

    static int verticalTileLookupY(short y, Direction direction, int layoutYMask) {
        return NativePlayerTerrainScan.verticalTileLookupY(y, direction, layoutYMask);
    }

    private int collisionLayoutYMask() {
        var rules = sprite != null ? sprite.getGameRules() : null;
        var collisionRules = rules != null ? rules.collision() : null;
        return collisionRules != null ? collisionRules.defaultCollisionLayoutYMask() : 0x07FF;
    }

    // ========================================
    // HORIZONTAL SCANNING (Walls)
    // ========================================

    private SensorResult scanHorizontal(LevelManager lm, short x, short y, int solidityBit, Direction direction) {
        return scanHorizontalLayer(lm, x, y, solidityBit, direction,
                (byte) 0, sprite.isLoopLowPlane(), reusableResult);
    }

    private SensorResult scanHorizontalLayer(LevelManager lm, short x, short y, int solidityBit,
                                             Direction direction, byte layer, boolean loopLowPlane,
                                             SensorResult resultTarget) {
        WallScanResult result = evaluateWallTile(lm, x, y, solidityBit, direction, layer, loopLowPlane);

        switch (result.state) {
            case FOUND:
                return createResultWithDistance(resultTarget, result.tile, result.desc,
                        (byte) result.distance, direction);

            case REGRESS: {
                // Check previous tile (opposite direction)
                // ROM behavior: if adjacent tile has no collision, preserve angle from current tile
                int prevX = x + (direction == Direction.LEFT ? 16 : -16);
                WallScanResult prev = scanWallTileSimple(
                        lm, prevX, y, solidityBit, direction, layer, loopLowPlane);
                // Use current tile's angle if adjacent tile has no collision (ROM: angle buffer not modified)
                SolidTile prevTile = prev.tile != null ? prev.tile : result.tile;
                ChunkDesc prevDesc = prev.tile != null ? prev.desc : result.desc;
                return createResultWithDistance(resultTarget, prevTile, prevDesc,
                        (byte) (prev.distance - 16), direction);
            }

            case EXTEND:
            default: {
                // Check next tile (same direction)
                // ROM behavior: if adjacent tile has no collision, preserve angle from current tile
                int nextX = x + (direction == Direction.LEFT ? -16 : 16);
                WallScanResult next = scanWallTileSimple(
                        lm, nextX, y, solidityBit, direction, layer, loopLowPlane);
                // Use current tile's angle if adjacent tile has no collision (ROM: angle buffer not modified)
                SolidTile nextTile = next.tile != null ? next.tile : result.tile;
                ChunkDesc nextDesc = next.tile != null ? next.desc : result.desc;
                return createResultWithDistance(resultTarget, nextTile, nextDesc,
                        (byte) (next.distance + 16), direction);
            }
        }
    }

    private WallScanResult evaluateWallTile(LevelManager lm, int x, int y, int solidityBit,
                                            Direction direction, byte layer, boolean loopLowPlane) {
        ChunkDesc desc = lm.getChunkDescAt(layer, x, y, loopLowPlane);
        SolidTile tile = getSolidTile(lm, desc, solidityBit);

        if (tile == null) {
            return wallResult1.set(WallScanState.EXTEND, 0, null, null);
        }

        int metric = getWallMetric(tile, desc, y, direction);

        if (metric == 0) {
            // ROM: tile IS solid, angle register d3 loaded before metric check.
            // Pass tile/desc so EXTEND handler can fall back to this tile's angle
            // if the next tile has no collision (matching ROM d3 preservation).
            return wallResult1.set(WallScanState.EXTEND, 0, tile, desc);
        }

        int xInTile = x & 0x0F;
        int xAdjusted = (direction == Direction.LEFT) ? (15 - xInTile) : xInTile;

        // Negative metric: partial collision from opposite side
        if (metric < 0) {
            boolean extend = (metric + xAdjusted >= 0);
            // Pass tile/desc so angle can be preserved if adjacent tile has no collision
            return extend
                    ? wallResult1.set(WallScanState.EXTEND, 0, tile, desc)
                    : wallResult1.set(WallScanState.REGRESS, 0, tile, desc);
        }

        // Full-width tile: need to check previous tile
        if (metric == FULL_TILE) {
            // Pass tile/desc so angle can be preserved if adjacent tile has no collision
            return wallResult1.set(WallScanState.REGRESS, 0, tile, desc);
        }

        // Standard case: calculate distance to wall surface
        // ROM FindWall: d1 = 15 - metric - xInTile (same formula for both directions)
        // But the engine's getWallMetric already accounts for the ROM's d6-based h-flip toggle,
        // and the engine doesn't XOR the x position like the ROM does for LEFTWALL.
        // The combined effect of these two compensations means:
        //   LEFT:  metric is negated by getWallMetric, x is raw → xInTile - metric
        //   RIGHT: metric is raw, x is raw → 15 - metric - xInTile
        int distance = (direction == Direction.LEFT)
                ? (xInTile - metric)
                : (15 - metric - xInTile);
        return wallResult1.set(WallScanState.FOUND, distance, tile, desc);
    }

    private WallScanResult scanWallTileSimple(LevelManager lm, int x, int y, int solidityBit,
                                              Direction direction, byte layer, boolean loopLowPlane) {
        ChunkDesc desc = lm.getChunkDescAt(layer, x, y, loopLowPlane);
        SolidTile tile = getSolidTile(lm, desc, solidityBit);
        int xInTile = x & 0x0F;
        int xAdjusted = (direction == Direction.LEFT) ? (15 - xInTile) : xInTile;

        if (tile == null) {
            int dist = 15 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, null, null);
        }

        int metric = getWallMetric(tile, desc, y, direction);

        if (metric == 0) {
            // ROM: tile IS solid, d3 was loaded with this tile's angle before the metric
            // check. Default distance path preserves the angle. Pass tile/desc so the
            // caller can use this tile's angle as fallback (matching ROM d3 preservation).
            int dist = 15 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
        }

        if (metric < 0) {
            if (metric + xAdjusted >= 0) {
                // ROM: d3 already loaded before negative metric branch.
                // Default distance path preserves angle — pass tile/desc.
                int dist = 15 - xAdjusted;
                return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
            }
            int dist = -1 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
        }

        // ROM formula: same as evaluateWallTile (see comments there)
        int distance = (direction == Direction.LEFT)
                ? (xInTile - metric)
                : (15 - metric - xInTile);
        return wallResult2.set(WallScanState.FOUND, distance, tile, desc);
    }

    // ========================================
    // METRIC CALCULATIONS
    // ========================================

    private int getWallMetric(SolidTile tile, ChunkDesc desc, int y, Direction direction) {
        if (tile == null) return 0;

        int rawIndex = y & 0x0F;
        int index = rawIndex;
        boolean vFlip = desc != null && desc.getVFlip();
        if (vFlip) {
            index = 15 - index;
        }

        int metric = tile.getWidthAt((byte) index);
        boolean hFlip = desc != null && desc.getHFlip();
        boolean xMirror = hFlip ^ (direction == Direction.LEFT);
        if (xMirror) {
            metric = -metric;
        }

        return metric;
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * {@code lm} must be non-null: the scan entry points resolve it once per scan,
     * and {@code scanBackgroundCollision} null-guards before any helper is reached.
     */
    private SolidTile getSolidTile(LevelManager lm, ChunkDesc desc, int solidityBit) {
        if (desc == null || !desc.isSolidityBitSet(solidityBit)) {
            return null;
        }
        return secondaryCollisionPathOverride == null
                ? lm.getSolidTileForChunkDesc(desc, solidityBit)
                : lm.getSolidTileForChunkDesc(desc, solidityBit, secondaryCollisionPathOverride);
    }

    private SensorResult createResultWithDistance(SensorResult target,
                                                  SolidTile tile,
                                                  ChunkDesc desc,
                                                  byte distance,
                                                  Direction direction) {
        byte angle = FLAGGED_ANGLE;
        int tileIndex = 0;

        if (tile != null) {
            boolean hFlip = desc != null && desc.getHFlip();
            boolean vFlip = desc != null && desc.getVFlip();
            angle = tile.getAngle(hFlip, vFlip);
            tileIndex = tile.getIndex();
        }

        return target.set(angle, distance, tileIndex, direction);
    }

    // ========================================
    // WALL SCAN RESULT
    // ========================================

    private enum WallScanState { FOUND, EXTEND, REGRESS }

    private static final class WallScanResult {
        WallScanState state;
        int distance;
        SolidTile tile;
        ChunkDesc desc;

        WallScanResult set(WallScanState state, int distance, SolidTile tile, ChunkDesc desc) {
            this.state = state;
            this.distance = distance;
            this.tile = tile;
            this.desc = desc;
            return this;
        }
    }
}

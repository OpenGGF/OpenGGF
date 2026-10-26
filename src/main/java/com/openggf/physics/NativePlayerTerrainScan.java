package com.openggf.physics;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;

/** Native vertical FindFloor/FindCeiling kernel extracted without arithmetic changes.
 * Pooled SensorResult writes are identical to GroundSensor at 0ffa7f3c7.
 * Geometric provenance is separate: native empty/angle-only defaults remain intact.
 */
final class NativePlayerTerrainScan {
    enum SupportKind { GEOMETRIC_SURFACE, ANGLE_ONLY_DEFAULT, EMPTY_DEFAULT }
    private static final byte FULL_TILE = 16;
    private static final byte FLAGGED_ANGLE = 3;
    private int layoutYMask;
    private Runnable charge;
    private SupportKind kind;
    SupportKind kind() { return kind; }

    @FunctionalInterface
    interface SolidTileLookup {
        SolidTile lookup(LevelManager level, ChunkDesc desc, int solidityBit);
    }

    private final SolidTileLookup solidTileLookup;

    NativePlayerTerrainScan() {
        this(LevelManager::getSolidTileForChunkDesc);
    }

    NativePlayerTerrainScan(SolidTileLookup solidTileLookup) {
        this.solidTileLookup = solidTileLookup;
    }

    SensorResult scan(LevelManager lm, short x, short y, int solidityBit,
                                           Direction direction, boolean mirrorEmptyDefault,
                                           byte layer, boolean loopLowPlane, SensorResult resultTarget, int layoutYMask, Runnable charge) {
        this.layoutYMask = layoutYMask;
        this.charge = charge;
        kind = SupportKind.EMPTY_DEFAULT;
        // Check current tile (ROM: FindFloor - first pass)
        SensorResult result = scanTileVertical(lm, x, y, x, y, solidityBit, direction, false,
                layer, loopLowPlane, resultTarget);
        if (result != null) {
            return result;
        }

        // Extend to next tile in scan direction (ROM: FindFloor2 - second pass)
        short nextY = (short) (y + (direction == Direction.DOWN ? 16 : -16));
        result = scanTileVertical(lm, x, y, x, nextY, solidityBit, direction, true,
                layer, loopLowPlane, resultTarget);
        if (result != null) {
            return result;
        }

        // No collision found in the foot tile or the extension tile.
        //
        // ROM FindFloor: when the FOOT tile is solid with a valid collision block,
        // its angle is written to the angle buffer (sub FindNearestTile & FindFloor
        // & FindWall.asm:131 `move.b (a2,d0.w),(a4)`) BEFORE the height value is
        // read. A zero/overflowing height then branches to .isblank (line 159
        // `beq .isblank`, or the .negfloor `bpl .isblank` at line 175) and extends
        // to the tile below WITHOUT clearing that angle. FindFloor2 only overwrites
        // the buffer if the extension tile is itself solid; its .isblank2 path
        // (line 199) leaves the buffer untouched. So a top-solid foot tile that is
        // empty (height 0) at the probed column still reports its real surface angle
        // even though the surface alignment comes from the blank extension.
        //
        // The engine's first-pass scanTileVertical returns null for a solid tile
        // with metric == 0 (preserving ROM's "extend" branch), which discarded that
        // angle and fell back to FLAGGED_ANGLE -> spurious cardinal snap. Mirror the
        // ROM angle-buffer retention: if the foot tile is solid, default to its angle.
        charge.run();
        byte distance = calculateExtensionDefaultDistance(y, mirrorEmptyDefault);
        ChunkDesc footDesc = lm.getChunkDescAt(
                layer, x, verticalTileLookupY(y, direction, layoutYMask), loopLowPlane);
        SolidTile footTile = getSolidTile(lm, footDesc, solidityBit);
        if (footTile != null) {
            kind = SupportKind.ANGLE_ONLY_DEFAULT;
            byte angle = footTile.getAngle(
                    footDesc != null && footDesc.getHFlip(),
                    footDesc != null && footDesc.getVFlip());
            return resultTarget.set(angle, distance, footTile.getIndex(), direction);
        }
        return resultTarget.set(FLAGGED_ANGLE, distance, 0, direction);
    }

    /**
     * @param isExtension true for second-pass (FindFloor2) behavior, false for first-pass (FindFloor).
     *                    ROM difference: FindFloor2's negfloor with adjusted < 0 returns ~yInTile
     *                    instead of recursing to the previous tile.
     */
    private SensorResult scanTileVertical(LevelManager lm, short origX, short origY, short checkX, short checkY,
                                          int solidityBit, Direction direction, boolean isExtension,
                                          byte layer, boolean loopLowPlane, SensorResult resultTarget) {
        charge.run();
        int lookupY = verticalTileLookupY(checkY, direction, layoutYMask);
        ChunkDesc desc = lm.getChunkDescAt(layer, checkX, lookupY, loopLowPlane);
        SolidTile tile = getSolidTile(lm, desc, solidityBit);

        if (tile == null) {
            return null;
        }

        byte metric = getHeightMetric(tile, desc, checkX, direction);

        if (metric == 0) {
            if (isExtension) {
                // ROM FindFloor2: when the tile IS solid but metric=0, the angle register
                // d3 was already loaded from this tile (move.b (a4,d0.w),d3 runs before
                // the metric check). The ROM branches to loc_1E88A (default distance)
                    // which returns d1=15-yInTile and d3=this tile's angle.
                    // Engine must return a result (not null) to preserve the extension tile's angle.
                byte distance = calculateVerticalDistance((byte) 0, origY, checkY, direction);
                kind = SupportKind.ANGLE_ONLY_DEFAULT;
                return createResultWithDistance(resultTarget, tile, desc, distance, direction);
            }
            return null;
        }

        // Handle negative metrics (ROM: FindFloor loc_1E85E / FindFloor2 loc_1E900)
        // Negative metric means collision starts from opposite edge (V-flipped tiles)
        if (metric < 0) {
            // ROM: FindCeiling applies eori.w #$F,d2 (XOR Y's bottom 4 bits) before
            // calling FindFloor, mirroring yInTile within the 16px tile. (s2.asm:43895)
            int yInTile = (direction == Direction.UP)
                    ? ((origY ^ 0x0F) & 0x0F)
                    : (origY & 0x0F);
            int adjusted = metric + yInTile;

            if (adjusted >= 0) {
                if (isExtension) {
                    // ROM FindFloor2: adjusted >= 0 → loc_1E88A (default distance).
                    // d3 already holds this tile's angle from earlier load.
                    byte distance = calculateVerticalDistance((byte) 0, origY, checkY, direction);
                    kind = SupportKind.ANGLE_ONLY_DEFAULT;
                    return createResultWithDistance(resultTarget, tile, desc, distance, direction);
                }
                // ROM FindFloor (first pass): bpl → loc_1E7E2 (extend to next tile)
                return null;
            }

            if (isExtension) {
                // Second pass (FindFloor2): ROM loc_1E900 → not.w d1 where d1 = yInTile.
                // The caller (FindFloor, loc_1E7E2) then applies addi.w #$10,d1 (+16).
                // calculateVerticalDistance already embeds +16 for positive-metric extension
                // tiles (by measuring from origY to tileY). For the negative-metric case we
                // must add 16 explicitly so the final distance matches the ROM:
                //   FindFloor2 raw:  ~yInTile
                //   After addi #$10: ~yInTile + 16
                // Without the +16, a partial ceiling in the extension tile (one tile above the
                // probe) returns distance = -1, causing a spurious ceiling hit even though the
                // ROM sees distance = 15 (positive → no collision).  s2.asm:43064-43068.
                kind = SupportKind.GEOMETRIC_SURFACE;
                byte distance = (byte) (~yInTile + 16);
                return createResultWithDistance(resultTarget, tile, desc, distance, direction);
            }

            // First pass (FindFloor): regress to previous tile via FindFloor2
            short prevCheckY = (short) (checkY + (direction == Direction.DOWN ? -16 : 16));
            SensorResult prevResult = scanTileVertical(lm, origX, origY, checkX, prevCheckY, solidityBit,
                    direction, true, layer, loopLowPlane, resultTarget);

            if (prevResult != null) {
                kind = SupportKind.GEOMETRIC_SURFACE;
                // ROM loc_1E86A does `subi.w #$10,d1` after FindFloor2 because FindFloor2's
                // distance is relative to the shifted d2. The engine's scanTileVertical always
                // calculates distance relative to origY (see calculateVerticalDistance), so the
                // -16 offset is already embedded. No extra subtraction needed — same reasoning
                // as the FULL_TILE path above.
                return prevResult;
            }

            // prevResult null means FindFloor2 found no solid tile in the extension tile.
            // ROM FindFloor loc_1E85E (adjusted<0) → loc_1E86A: it ALWAYS calls FindFloor2 on
            // the previous tile and applies subi.w #$10,d1 to the result — it never bails out.
            // When FindFloor2 hits a blank tile (loc_1E88A, s2.asm:43457-43462) it returns
            // d1 = 15 - (d2 & $F) relative to the shifted d2; the caller's subi #$10 then makes
            // it negative, placing the surface one tile up. The engine's calculateVerticalDistance
            // measures from origY (embedding the ±16), so the blank-tile default here mirrors the
            // FULL_TILE first-pass path exactly (s2.asm:43420-43431 loc_1E85E/loc_1E86A,
            // s2.asm:43457-43462 FindFloor2 loc_1E88A). Returning null instead dropped this
            // negative distance, so a single-tile ceiling lip in the extension tile was never
            // reported and Sonic overshot upward (ARZ1 frame 1106, frontier 1102→1106).
            kind = SupportKind.GEOMETRIC_SURFACE;
            byte defaultDistance = calculateVerticalDistance((byte) 0, origY, prevCheckY, direction);
            return createResultWithDistance(resultTarget, tile, desc, defaultDistance, direction);
        }

        // Full-height tile handling differs between FindFloor and FindFloor2.
        if (metric == FULL_TILE) {
            if (!isExtension) {
                // FindFloor (first pass, s2.asm:43008-43013 loc_1E86A):
                // Recurse to FindFloor2 on the previous tile.
                // ROM subtracts 16 because FindFloor2's distance is relative to the
                // shifted d2. But the engine's scanTileVertical always calculates
                // distance relative to origY, so no adjustment is needed.
                short prevY = (short) (checkY + (direction == Direction.DOWN ? -16 : 16));
                SensorResult prevResult = scanTileVertical(lm, origX, origY, checkX, prevY, solidityBit,
                        direction, true, layer, loopLowPlane, resultTarget);

                if (prevResult != null) {
                    kind = SupportKind.GEOMETRIC_SURFACE;
                    return prevResult;
                }

                // prevResult null means FindFloor2 found no solid tile — use default
                // distance. ROM FindFloor2 loc_1E88A: d1 = 15 - yInTile (relative to
                // shifted d2), then FindFloor subtracts 16. Engine equivalent: calculate
                // distance from origY to the surface implied by a blank tile at prevY.
                kind = SupportKind.GEOMETRIC_SURFACE;
                byte distance = calculateVerticalDistance((byte) 0, origY, prevY, direction);
                return createResultWithDistance(resultTarget, tile, desc, distance, direction);
            }
            // FindFloor2 (second pass): NO special full-tile handling in ROM.
            // Falls through to standard distance calculation below.
        }

        kind = SupportKind.GEOMETRIC_SURFACE;
        return createVerticalResult(resultTarget, tile, desc, checkX, origY, checkY, direction);
    }

    static int verticalTileLookupY(short y, Direction direction, int layoutYMask) {
        if (direction != Direction.UP || y >= 0) {
            return y;
        }
        // ROM FindCeiling transforms the top-edge probe with eori.w #$F before
        // Find_Tile_FG applies Layout_row_index_mask to the transformed row.
        // S1/S2 use a 0x800-pixel window; S3K's default $7C row-index mask
        // represents a 0x1000-pixel window. This
        // is not an absolute top-boundary clamp: wrapped rows can be solid in
        // some layouts and blank in others.
        return (y ^ 0x0F) & layoutYMask;
    }

    private SensorResult createVerticalResult(SensorResult target, SolidTile tile, ChunkDesc desc,
                                              short checkX, short origY, short tileY, Direction direction) {
        byte metric = getHeightMetric(tile, desc, checkX, direction);
        byte distance = calculateVerticalDistance(metric, origY, tileY, direction);
        return createResultWithDistance(target, tile, desc, distance, direction);
    }

    // ROM-accurate: distance is a signed byte (move.b), limiting sensor range to -128..+127 pixels.
    // The 68000 uses the same signed byte range for FindFloor/FindCeiling distances.
    private byte calculateVerticalDistance(byte metric, short origY, short tileY, Direction direction) {
        // ROM parity: distance is always relative to the PROBE's tile, not
        // the tile being scanned. When the extension pass checks the next
        // tile (tileY = origY ± 16), the distance must still be computed
        // from origY's tile base. Using tileY here caused a 16px offset
        // on extension scans, producing wrong-sign results at tile boundaries.
        // ROM: FindFloor uses d2 (original probe Y) for yInTile:
        //   andi.w #$F,d1  → d1 = origY & 0xF
        //   add.w d1,d0    → d0 = metric + yInTile
        //   move.w #$F,d1  → d1 = 15
        //   sub.w d0,d1    → d1 = 15 - metric - yInTile
        short probeBase = (short) (origY & ~0x0F);
        short checkBase = (short) (tileY & ~0x0F);
        // Use checkBase (the tile where collision was found) for the surface
        // position, but the distance is measured from origY.
        if (direction == Direction.DOWN) {
            return (byte) ((checkBase + 15 - metric) - origY);
        } else {
            return (byte) (origY - (checkBase + metric));
        }
    }

    private byte calculateExtensionDefaultDistance(short origY, boolean mirrorLowNibble) {
        // ROM FindFloor first-pass miss:
        //   add.w a3,d2
        //   bsr.w FindFloor2
        //   sub.w a3,d2
        //   addi.w #$10,d1
        // FindFloor2's default path is 15 - (d2 & $F). Since +/-16 preserves
        // the low nibble, the combined no-collision distance is 31 - yInTile.
        // Ceiling attachment enters FindFloor with WalkCeiling's eori.w #$F
        // already applied to d2 (S1 Sonic AnglePos.asm:289-306; S2 s2.asm:43175-43191;
        // S3K sonic3k.asm:19013-19029), so the empty-tile default must mirror
        // the probe low nibble even though the engine's tile lookup coordinate is
        // kept unmirrored for existing solid-tile parity.
        int yInTile = mirrorLowNibble
                ? ((origY ^ 0x0F) & 0x0F)
                : (origY & 0x0F);
        return (byte) (0x1F - yInTile);
    }

    private byte getHeightMetric(SolidTile tile, ChunkDesc desc, int x, Direction direction) {
        if (tile == null) return 0;

        int index = x & 0x0F;
        if (desc != null && desc.getHFlip()) {
            index = 15 - index;
        }

        byte metric = tile.getHeightAt((byte) index);
        if (metric != 0 && metric != FULL_TILE) {
            // ROM: eor.w d6,d4 / btst #$C,d4 / neg.w d0
            // V-flip (for floor scan) or non-V-flip (for ceiling scan) negates
            // the height, producing a negative metric processed through the
            // negative-height path (scanTileVertical's metric < 0 branch).
            boolean negate = (desc != null && desc.getVFlip()) ^ (direction == Direction.UP);
            if (negate) {
                metric = (byte) -metric;
            }
        }
        return metric;
    }

    private SolidTile getSolidTile(LevelManager lm, ChunkDesc desc, int solidityBit) {
        if (desc == null || !desc.isSolidityBitSet(solidityBit)) {
            return null;
        }
        return solidTileLookup.lookup(lm, desc, solidityBit);
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

}

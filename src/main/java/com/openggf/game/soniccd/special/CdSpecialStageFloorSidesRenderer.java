package com.openggf.game.soniccd.special;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

import static com.openggf.game.soniccd.special.CdSpecialStageFloorSides.TILE_EDGE;

/** Draws the extra floor columns with the stage picture's current palette. */
final class CdSpecialStageFloorSidesRenderer {
    private static final int PATTERN_BASE = PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.base();
    private static final int FLOOR_TOP = 128;
    private final Pattern pattern = new Pattern();
    private final PatternDesc floor = new PatternDesc(0x2000);
    private final byte[] tile = new byte[32];
    private CdSpecialStageFloorSides uploaded;

    void reset() { uploaded = null; }

    void draw(GraphicsManager graphics, int nativeOrigin, CdSpecialStageFloorSides sides) {
        if (sides == null) return;
        graphics.beginPatternBatch();
        int id = drawSide(graphics, nativeOrigin - sides.width(), sides, false, PATTERN_BASE);
        drawSide(graphics, nativeOrigin + CdStampFloor.IMAGE_WIDTH, sides, true, id);
        graphics.flushPatternBatch();
        graphics.flushScreenSpace();
        uploaded = sides;
    }

    private int drawSide(GraphicsManager graphics, int origin, CdSpecialStageFloorSides sides, boolean right, int id) {
        for (int y = 0; y < CdStampFloor.IMAGE_HEIGHT; y += TILE_EDGE) {
            for (int x = 0; x < sides.width(); x += TILE_EDGE) {
                if (sides != uploaded) {
                    sides.readTile(right, x, y, tile);
                    pattern.fromSegaFormat(tile);
                    graphics.cachePatternTexture(pattern, id);
                }
                graphics.renderPatternWithId(id++, floor, origin + x, FLOOR_TOP + y);
            }
        }
        return id;
    }
}

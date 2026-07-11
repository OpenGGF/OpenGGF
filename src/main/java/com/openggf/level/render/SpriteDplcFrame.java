package com.openggf.level.render;

import java.util.List;

/**
 * Tile streaming plan for a single sprite frame.
 */
@com.openggf.game.ModApi
public record SpriteDplcFrame(List<TileLoadRequest> requests) {
}

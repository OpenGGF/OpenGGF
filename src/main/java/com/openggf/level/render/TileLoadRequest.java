package com.openggf.level.render;

/**
 * Describes a contiguous tile load from source art.
 */
@com.openggf.game.ModApi
public record TileLoadRequest(int startTile, int count) {
}
